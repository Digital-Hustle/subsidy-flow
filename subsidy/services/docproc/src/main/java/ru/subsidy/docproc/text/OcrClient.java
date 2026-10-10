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
                pages.add(new PageText(page.path("n").asInt(pages.size() + 1), text, sourceOf(page, text, minConf))
                        .withTables(Tables.render(page)));
            }
        }
        return pages;
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
        boolean weak = !page.path("textLayer").asBoolean(false) && page.path("meanConf").asDouble(1) < minConf;
        return weak ? PageText.OCR_WEAK : PageText.OCR;
    }
}
