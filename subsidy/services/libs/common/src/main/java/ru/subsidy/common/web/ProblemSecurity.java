package ru.subsidy.common.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import ru.subsidy.common.exception.ErrorCode;

/**
 * Отказы входа и прав — тоже problem+json с кодом и подсказкой: тот, кто вызывает
 * эндпоинт без токена, видит внятный ответ, а не пустое тело (FR-06).
 */
public final class ProblemSecurity {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ProblemSecurity() {
    }

    /**
     * Нет токена или он не подошёл.
     *
     * @return точка входа
     */
    public static AuthenticationEntryPoint entryPoint() {
        return (request, response, exception) -> write(request, response, HttpStatus.UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED, "Нужен вход", "Войдите в рабочее место и повторите запрос");
    }

    /**
     * Роли не хватает.
     *
     * @return обработчик
     */
    public static AccessDeniedHandler accessDenied() {
        return (request, response, exception) -> write(request, response, HttpStatus.FORBIDDEN,
                ErrorCode.FORBIDDEN, "Недостаточно прав для этого действия", "Обратитесь к старшему специалисту");
    }

    private static void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
            ErrorCode code, String detail, String hint) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", code.code());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        body.put("code", code.code());
        body.put("hint", hint);
        response.setStatus(status.value());
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        JSON.writeValue(response.getOutputStream(), body);
    }
}
