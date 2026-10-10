package ru.subsidy.docproc.text;

import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import ru.subsidy.common.security.ServiceTokenProvider;
import ru.subsidy.docproc.config.DocprocProperties;

/**
 * Файл из package по /internal/package/files/{id} с токеном svc-docproc (имя файла не запрашивается).
 */
@Component
public class PackageClient {

    private final RestClient http;
    private final ObjectProvider<ServiceTokenProvider> tokens;

    /**
     * Конструктор.
     *
     * @param props настройки
     * @param tokens токен service account
     */
    public PackageClient(DocprocProperties props, ObjectProvider<ServiceTokenProvider> tokens) {
        this.http = RestClient.builder().baseUrl(props.packageUrl() == null ? "" : props.packageUrl()).build();
        this.tokens = tokens;
    }

    /**
     * Содержимое версии файла.
     *
     * @param fileId файл
     * @param version версия
     * @return байты
     * @throws IllegalStateException package не вернул файл
     */
    public byte[] download(UUID fileId, int version) {
        ServiceTokenProvider provider = tokens.getIfAvailable();
        byte[] body = http.get().uri("/internal/package/files/{id}?version={v}", fileId, version)
                .headers(headers -> {
                    if (provider != null) {
                        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + provider.token());
                    }
                })
                .retrieve().body(byte[].class);
        if (body == null) {
            throw new IllegalStateException("package не вернул файл " + fileId);
        }
        return body;
    }
}
