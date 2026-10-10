package ru.subsidy.gateway.limit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Лимиты на входе (GW-04, GW-05): частота загрузок и вопросов помощнику на пользователя, размер тела запроса.
 *
 * @param uploadsPerMinute   загрузок файлов в минуту на пользователя
 * @param questionsPerMinute запросов к помощнику в минуту на пользователя
 * @param uploadMaxMb        максимальный размер тела загрузки, МБ
 * @param bodyMaxKb          максимальный размер тела остальных запросов, КБ
 * @param loginsPerMinute    запросов кода и проверок кода в минуту с одного адреса (ADR-023)
 */
@ConfigurationProperties("app.limits")
public record LimitProperties(int uploadsPerMinute, int questionsPerMinute, long uploadMaxMb, long bodyMaxKb, int loginsPerMinute) {
}
