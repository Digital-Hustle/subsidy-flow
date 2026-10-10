package ru.subsidy.docproc.config;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки конвейера (docs/ARCHITECTURE.md §Конфигурация).
 *
 * @param workers параллельных заданий
 * @param lease аренда задания
 * @param maxAttempts попыток до FAILED
 * @param modelRetryDelay пауза при WAITING_MODEL
 * @param packageUrl адрес package
 * @param ocrUrl адрес ocr (пусто — без OCR)
 * @param classifyMinConfidence порог уверенности вида
 * @param fieldReviewThreshold порог уверенности поля
 * @param visionFallback страницы без текста отправлять модели картинкой
 * @param maxCharsPerPage символов страницы для сегментации
 * @param maxCharsPerDocument символов документа для извлечения полей
 * @param visionOcrMinConf средняя уверенность OCR страницы (0..1), ниже которой страница считается слабой и при
 *                         visionFallback уходит модели и картинкой (DP-14)
 */
@ConfigurationProperties("app.docproc")
public record DocprocProperties(int workers, Duration lease, int maxAttempts, Duration modelRetryDelay,
                                String packageUrl, String ocrUrl, BigDecimal classifyMinConfidence,
                                BigDecimal fieldReviewThreshold, boolean visionFallback, int maxCharsPerPage,
                                int maxCharsPerDocument, BigDecimal visionOcrMinConf) {
}
