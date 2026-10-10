package ru.subsidy.rules.api.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import ru.subsidy.rules.api.dto.Items;
import ru.subsidy.rules.history.OrgHistoryService.Entry;
import ru.subsidy.rules.stats.ErrorStatsService.Stats;
import ru.subsidy.rules.stats.ExpiringService.Expiring;

/**
 * История организации, статистика ошибок, истекающие документы.
 */
@RequestMapping("/api/v1")
public interface InsightsController {

    /**
     * История организации.
     *
     * @param inn ИНН
     * @return записи
     */
    @GetMapping("/organizations/{inn}/history")
    Items<Entry> history(@PathVariable String inn);

    /**
     * Частые ошибки.
     *
     * @param from с
     * @param till по
     * @param program программа
     * @return статистика и «как посчитано»
     */
    @GetMapping("/stats/errors")
    Stats errors(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                 @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                 LocalDate till,
                 @RequestParam(required = false) String program);

    /**
     * Истекающие документы.
     *
     * @param days горизонт, дней
     * @return документы
     */
    @GetMapping("/expiring")
    Items<Expiring> expiring(@RequestParam(required = false) Integer days);
}
