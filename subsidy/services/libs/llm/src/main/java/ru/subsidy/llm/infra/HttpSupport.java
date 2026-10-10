package ru.subsidy.llm.infra;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collection;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HTTP-клиент адаптеров: JSON, таймаут, 5xx/429 → IOException (повод для повтора).
 */
public final class HttpSupport {

    private static final int HTTP_ERROR = 400;
    private static final int CONNECT_TIMEOUT_S = 5;

    private final HttpClient client;
    private final ObjectMapper json;
    private final Duration timeout;

    public HttpSupport(ObjectMapper json, Duration timeout, String caCertPath) {
        this.json = json;
        this.timeout = timeout;
        HttpClient.Builder b = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_S));
        if (caCertPath != null && !caCertPath.isBlank()) {
            b.sslContext(sslWithExtraCa(Path.of(caCertPath)));
        }
        this.client = b.build();
    }

    /**
     * POST JSON → JSON.
     *
     * @param url     адрес
     * @param body    тело
     * @param headers пары имя/значение
     * @return ответ
     * @throws IOException сеть, таймаут, HTTP ≥ 400
     */
    public JsonNode postJson(String url, Object body, String... headers) throws IOException {
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)));
        return send(rb, headers);
    }

    /**
     * POST form → JSON.
     *
     * @param url     адрес
     * @param form    тело x-www-form-urlencoded
     * @param headers пары имя/значение
     * @return ответ
     * @throws IOException ошибка
     */
    public JsonNode postForm(String url, String form, String... headers) throws IOException {
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form));
        return send(rb, headers);
    }

    /**
     * GET → JSON.
     *
     * @param url     адрес
     * @param headers пары имя/значение
     * @return ответ
     * @throws IOException ошибка
     */
    public JsonNode get(String url, String... headers) throws IOException {
        return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(CONNECT_TIMEOUT_S)).GET(),
                headers);
    }

    private JsonNode send(HttpRequest.Builder rb, String... headers) throws IOException {
        for (int i = 0; i + 1 < headers.length; i += 2) {
            rb.header(headers[i], headers[i + 1]);
        }
        HttpResponse<InputStream> rs;
        try {
            rs = client.send(rb.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        try (InputStream in = rs.body()) {
            byte[] bytes = in.readAllBytes();
            if (rs.statusCode() >= HTTP_ERROR) {
                throw new IOException("HTTP " + rs.statusCode());
            }
            return json.readTree(bytes);
        }
    }

    private static SSLContext sslWithExtraCa(Path pem) {
        try (InputStream in = Files.newInputStream(pem)) {
            Collection<? extends Certificate> certs = CertificateFactory.getInstance("X.509").generateCertificates(in);
            TrustManagerFactory def = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            def.init((KeyStore) null);
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            int i = 0;
            for (var tm : def.getTrustManagers()) {
                if (tm instanceof X509TrustManager x) {
                    for (X509Certificate c : x.getAcceptedIssuers()) {
                        ks.setCertificateEntry("sys" + i++, c);
                    }
                }
            }
            for (Certificate c : certs) {
                ks.setCertificateEntry("extra" + i++, c);
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            return ctx;
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("cannot load CA certificate", e);
        }
    }
}
