package ru.subsidy.rules.config;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки rules (docs/ARCHITECTURE.md §Конфигурация).
 *
 * @param debounce пауза без новых событий перед перепроверкой
 * @param zone часовой пояс для дат пакета
 * @param expiringDays горизонт «истекающих» документов по умолчанию
 * @param docprocUrl адрес docproc для пересборки проекции (RUL-18)
 */
@ConfigurationProperties("app.rules")
public record RulesProperties(Duration debounce, ZoneId zone, int expiringDays, String docprocUrl) {
}
