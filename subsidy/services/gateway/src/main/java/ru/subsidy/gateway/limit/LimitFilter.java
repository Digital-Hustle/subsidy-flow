package ru.subsidy.gateway.limit;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import reactor.core.publisher.Mono;

/**
 * Лимит тела (413) и частоты (429 + Retry-After) до сервисов. Стоит после Spring Security: пользователь уже известен.
 * Загрузки — POST файлов и новых версий; помощник — любые POST в assistant.
 */
@Component
@Order(0)
public class LimitFilter implements WebFilter {

    private static final long KB = 1024;
    private static final long MB = KB * KB;
    private static final long SECOND_MS = 1000;
    private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;
    private static final PathPattern FILES = PARSER.parse("/package/api/v1/packages/{id}/files");
    private static final PathPattern VERSIONS = PARSER.parse("/package/api/v1/packages/{id}/files/{fileId}/versions");
    private static final PathPattern ASSISTANT = PARSER.parse("/assistant/api/v1/**");
    private static final PathPattern LOGIN = PARSER.parse("/package/api/v1/auth/{step:code|verify}");
    private static final String FORWARDED = "X-Forwarded-For";

    private final LimitProperties props;
    private final Clock clock;
    private final FixedWindows windows = new FixedWindows();

    /**
     * Конструктор.
     *
     * @param props лимиты
     */
    @Autowired
    public LimitFilter(LimitProperties props) {
        this(props, Clock.systemUTC());
    }

    LimitFilter(LimitProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    /**
     * Проверяет размер и частоту, затем пропускает дальше.
     *
     * @param exchange запрос
     * @param chain    цепочка
     * @return завершение
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        Kind kind = kindOf(request);
        long max = kind == Kind.UPLOAD ? props.uploadMaxMb() * MB : props.bodyMaxKb() * KB;
        if (request.getHeaders().getContentLength() > max) {
            String size = kind == Kind.UPLOAD ? props.uploadMaxMb() + " МБ" : props.bodyMaxKb() + " КБ";
            return reject(exchange, HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                    "Запрос больше " + size, "Разделите загрузку на несколько частей", 0);
        }
        return kind == Kind.OTHER ? chain.filter(exchange) : limited(exchange, chain, kind);
    }

    private Mono<Void> limited(ServerWebExchange exchange, WebFilterChain chain, Kind kind) {
        // до входа пользователя нет — попытки входа считаем по адресу клиента (Caddy кладёт его первым в X-Forwarded-For)
        Mono<String> key = kind == Kind.LOGIN ? Mono.just(client(exchange.getRequest()))
                : exchange.getPrincipal().map(Principal::getName).defaultIfEmpty("anonymous");
        int limit = switch (kind) {
            case UPLOAD -> props.uploadsPerMinute();
            case LOGIN -> props.loginsPerMinute();
            default -> props.questionsPerMinute();
        };
        return key.flatMap(user -> {
            long waitMs = windows.acquire(kind + ":" + user, limit, clock.millis());
            if (waitMs == 0) {
                return chain.filter(exchange);
            }
            String detail = switch (kind) {
                case UPLOAD -> "Слишком много загрузок подряд";
                case LOGIN -> "Слишком много попыток входа";
                default -> "Слишком много вопросов подряд";
            };
            return reject(exchange, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", detail, "Подождите минуту",
                    (waitMs + SECOND_MS - 1) / SECOND_MS);
        });
    }

    private static Kind kindOf(ServerHttpRequest request) {
        if (!HttpMethod.POST.equals(request.getMethod())) {
            return Kind.OTHER;
        }
        PathContainer path = request.getPath().pathWithinApplication();
        Kind kind = Kind.OTHER;
        if (FILES.matches(path) || VERSIONS.matches(path)) {
            kind = Kind.UPLOAD;
        } else if (LOGIN.matches(path)) {
            kind = Kind.LOGIN;
        } else if (ASSISTANT.matches(path)) {
            kind = Kind.ASK;
        }
        return kind;
    }

    private static String client(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst(FORWARDED);
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddress() == null ? "unknown" : request.getRemoteAddress().getAddress().getHostAddress();
    }

    private static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String code, String detail,
                                     String hint, long retryAfterSeconds) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (retryAfterSeconds > 0) {
            response.getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        }
        String body = "{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase() + "\",\"status\":"
                + status.value() + ",\"detail\":\"" + detail + "\",\"code\":\"" + code + "\",\"hint\":\"" + hint
                + "\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    private enum Kind {
        UPLOAD, ASK, LOGIN, OTHER
    }
}
