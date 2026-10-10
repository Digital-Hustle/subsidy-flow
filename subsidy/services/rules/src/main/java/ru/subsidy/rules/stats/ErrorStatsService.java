package ru.subsidy.rules.stats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.subsidy.rules.config.RulesProperties;
import ru.subsidy.rules.jooq.Tables;

/**
 * Частые ошибки (B-04): по последнему протоколу каждого пакета за период — доля пакетов с нарушением правила.
 */
@Service
public class ErrorStatsService {

    private static final String METHOD = "Берём последний протокол каждого пакета, созданный в периоде; "
            + "count — число пакетов, где правило FAILED; share — count / число пакетов.";
    private static final int SHARE_SCALE = 3;

    private final DSLContext dsl;
    private final RulesProperties props;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param props настройки
     */
    public ErrorStatsService(DSLContext dsl, RulesProperties props) {
        this.dsl = dsl;
        this.props = props;
    }

    /**
     * Статистика за период.
     *
     * @param from с (включительно) или null
     * @param till по (включительно) или null
     * @param program программа или null
     * @return правила по убыванию частоты
     */
    @Transactional(readOnly = true)
    public Stats errors(LocalDate from, LocalDate till, String program) {
        Condition period = DSL.trueCondition();
        if (from != null) {
            period = period.and(Tables.PROTOCOLS.CREATED_AT.ge(start(from)));
        }
        if (till != null) {
            period = period.and(Tables.PROTOCOLS.CREATED_AT.lt(start(till.plusDays(1))));
        }
        if (program != null && !program.isBlank()) {
            period = period.and(Tables.PROTOCOLS.PROGRAM_CODE.eq(program));
        }
        List<UUID> latest = dsl.selectDistinct(Tables.PROTOCOLS.PACKAGE_ID, Tables.PROTOCOLS.ID)
                .on(Tables.PROTOCOLS.PACKAGE_ID).from(Tables.PROTOCOLS).where(period)
                .orderBy(Tables.PROTOCOLS.PACKAGE_ID, Tables.PROTOCOLS.VERSION.desc())
                .fetch(Tables.PROTOCOLS.ID);
        int packages = latest.size();
        Field<Integer> count = DSL.countDistinct(Tables.RULE_RESULTS.PACKAGE_ID).as("cnt");
        List<RuleStat> items = latest.isEmpty() ? List.of() : dsl.select(Tables.RULE_RESULTS.RULE, count)
                .from(Tables.RULE_RESULTS)
                .where(Tables.RULE_RESULTS.PROTOCOL_ID.in(latest)).and(Tables.RULE_RESULTS.STATUS.eq("FAILED"))
                .groupBy(Tables.RULE_RESULTS.RULE).orderBy(count.desc(), Tables.RULE_RESULTS.RULE)
                .fetch(rec -> new RuleStat(rec.value1(), rec.value2(), BigDecimal.valueOf(rec.value2())
                        .divide(BigDecimal.valueOf(packages), SHARE_SCALE, RoundingMode.HALF_UP)));
        return new Stats(items, packages, METHOD);
    }

    private OffsetDateTime start(LocalDate day) {
        return day.atStartOfDay(props.zone()).toOffsetDateTime();
    }

    /**
     * Статистика.
     *
     * @param items правила
     * @param packages пакетов в выборке
     * @param method как посчитано
     */
    public record Stats(List<RuleStat> items, int packages, String method) {
    }

    /**
     * Частота нарушения правила.
     *
     * @param rule код
     * @param count пакетов с нарушением
     * @param share доля
     */
    public record RuleStat(String rule, int count, BigDecimal share) {
    }
}
