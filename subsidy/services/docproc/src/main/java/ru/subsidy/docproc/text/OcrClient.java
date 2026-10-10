package ru.subsidy.docproc.text;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.common.security.ServiceTokenProvider;
import ru.subsidy.docproc.config.DocprocProperties;

/**
 * Клиент ocr (Python): POST /v1/extract → текст страниц (текстовый слой или Tesseract).
 */
@Component
public class OcrClient {

    private static final String QUALITY = "quality";
    private static final String BLUR = "blur";
    private static final String BRIGHTNESS = "brightness";
    private static final String GLARE = "glare";
    private static final String CUT_OFF = "cutOff";
    private static final String MEAN_CONF = "meanConf";
    private static final String TEXT_LAYER_KEY = "textLayer";

    private final double minConf;
    private final RestClient http;
    private final boolean enabled;
    private final ObjectProvider<ServiceTokenProvider> tokens;

    /**
     * Конструктор.
     *
     * @param props настройки
     * @param tokens токен сервисного аккаунта (aud=ocr)
     */
    public OcrClient(DocprocProperties props, ObjectProvider<ServiceTokenProvider> tokens) {
        this.tokens = tokens;
        this.minConf = props.visionOcrMinConf() == null ? 0 : props.visionOcrMinConf().doubleValue();
        this.enabled = props.ocrUrl() != null && !props.ocrUrl().isBlank();
        // HTTP/1.1: JDK-клиент по умолчанию шлёт h2c-upgrade, uvicorn его отвергает → 400 и «ocr unavailable».
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        this.http = RestClient.builder().requestFactory(new JdkClientHttpRequestFactory(client))
                .baseUrl(enabled ? props.ocrUrl() : "http://localhost").build();
    }

    /**
     * Включён ли OCR.
     *
     * @return true, если задан OCR_URL
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Распознаёт файл.
     *
     * @param content файл
     * @param mime тип
     * @return страницы
     */
    public List<PageText> extract(byte[] content, String mime) {
        MultipartBodyBuilder form = new MultipartBodyBuilder();
        form.part("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "document";
            }
        }).contentType(MediaType.parseMediaType(mime));
        ServiceTokenProvider provider = tokens.getIfAvailable();
        JsonNode body = http.post().uri("/v1/extract").contentType(MediaType.MULTIPART_FORM_DATA)
                .headers(headers -> {
                    if (provider != null) {
                        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + provider.token());
                    }
                })
                .body(form.build()).retrieve().body(JsonNode.class);
        List<PageText> pages = new ArrayList<>();
        if (body != null) {
            for (JsonNode page : body.path("pages")) {
                String text = page.path("text").asText("");
                PageText raw = new PageText(page.path("n").asInt(pages.size() + 1), text,
                        sourceOf(page, text, minConf), null, qualityOf(page));
                pages.add(raw.withTables(Tables.render(page)));
            }
        }
        return pages;
    }

    /**
     * Метрики качества страницы из ответа ocr: blur/brightness/glare/cutOff и средняя уверенность слов.
     * У страницы с текстовым слоем качество не измеряется (null), meanConf=1 (читаемость не проверяется).
     *
     * @param page страница ответа ocr
     * @return метрики или null
     */
    static PageQuality qualityOf(JsonNode page) {
        JsonNode metrics = page.path(QUALITY);
        Double blur = number(metrics, BLUR);
        Double brightness = number(metrics, BRIGHTNESS);
        Double glare = number(metrics, GLARE);
        Boolean cutOff = bool(metrics, CUT_OFF);
        Double meanConf = page.hasNonNull(MEAN_CONF) && !page.path(TEXT_LAYER_KEY).asBoolean(false)
                ? page.path(MEAN_CONF).asDouble() : null;
        if (blur == null && brightness == null && glare == null && cutOff == null && meanConf == null) {
            return null;
        }
        return new PageQuality(blur, brightness, glare, cutOff, meanConf);
    }

    private static Double number(JsonNode node, String key) {
        return node.hasNonNull(key) ? node.path(key).asDouble() : null;
    }

    private static Boolean bool(JsonNode node, String key) {
        return node.hasNonNull(key) ? node.path(key).asBoolean() : null;
    }

    /**
     * Источник текста страницы: пусто — NONE; распознано со средней уверенностью ниже порога — OCR_WEAK.
     *
     * @param page страница ответа ocr
     * @param text текст
     * @param minConf порог 0..1
     * @return NONE, OCR или OCR_WEAK
     */
    static String sourceOf(JsonNode page, String text, double minConf) {
        if (text.isBlank()) {
            return PageText.NONE;
        }
        boolean weak = !page.path(TEXT_LAYER_KEY).asBoolean(false) && page.path(MEAN_CONF).asDouble(1) < minConf;
        return weak ? PageText.OCR_WEAK : PageText.OCR;
    }
}
