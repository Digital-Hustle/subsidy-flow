package ru.subsidy.rules.api.controller.impl;

import java.time.LocalDate;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import ru.subsidy.rules.api.controller.InsightsController;
import ru.subsidy.rules.api.dto.Items;
import ru.subsidy.rules.history.OrgHistoryService;
import ru.subsidy.rules.history.OrgHistoryService.Entry;
import ru.subsidy.rules.stats.ErrorStatsService;
import ru.subsidy.rules.stats.ErrorStatsService.Stats;
import ru.subsidy.rules.stats.ExpiringService;
import ru.subsidy.rules.stats.ExpiringService.Expiring;

/**
 * Реализация {@link InsightsController}.
 */
@RestController
public class InsightsControllerImpl implements InsightsController {

    private static final String READERS = "hasAnyRole('SPECIALIST','SENIOR_SPECIALIST','AUDITOR')";

    private final OrgHistoryService history;
    private final ErrorStatsService stats;
    private final ExpiringService expiring;

    /**
     * Конструктор.
     *
     * @param history история
     * @param stats статистика
     * @param expiring истекающие
     */
    public InsightsControllerImpl(OrgHistoryService history, ErrorStatsService stats, ExpiringService expiring) {
        this.history = history;
        this.stats = stats;
        this.expiring = expiring;
    }

    @Override
    @PreAuthorize(READERS)
    public Items<Entry> history(String inn) {
        return new Items<>(history.history(inn));
    }

    @Override
    @PreAuthorize(READERS)
    public Stats errors(LocalDate from, LocalDate till, String program) {
        return stats.errors(from, till, program);
    }

    @Override
    @PreAuthorize(READERS)
    public Items<Expiring> expiring(Integer days) {
        return new Items<>(expiring.expiring(days));
    }
}
