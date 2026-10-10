package ru.subsidy.rules.stats;

import java.math.BigDecimal;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ru.subsidy.rules.engine.ProgramBaseline;

/**
 * Средняя и σ сумм статей смет по программе (ANOMALY_OUTLIER): агрегат по {@code package_documents.fields}
 * последних результатов разбора. Правки специалиста не учитываются — это фон программы, а не текущий пакет.
 */
@Repository
public class EstimateBaselineRepository implements ProgramBaseline {

    private static final String SQL = """
            with items as (
                select d.package_id, replace(replace(item ->> 'amount', ' ', ''), ',', '.') as amount
                from package_documents d
                join package_view v on v.package_id = d.package_id
                cross join lateral jsonb_array_elements(d.fields) field
                cross join lateral jsonb_array_elements(case when jsonb_typeof(field -> 'value') = 'array'
                    then field -> 'value' else '[]'::jsonb end) item
                where v.program_code = ? and d.package_id <> ? and not d.removed
                  and d.type = 'COST_JUSTIFICATION' and coalesce(d.reason_code, '') <> 'TYPE_UNKNOWN'
                  and field ->> 'name' = 'items'
            )
            select count(distinct package_id), count(*), avg(amount::numeric), stddev_pop(amount::numeric)
            from items where amount ~ '^-?[0-9]+(\\.[0-9]+)?$'
            """;

    private static final int MEAN_SQUARE = 3;

    private final DSLContext dsl;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     */
    public EstimateBaselineRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional(readOnly = true)
    public Baseline estimateItems(String programCode, UUID excludePackage) {
        Record rec = dsl.resultQuery(SQL, programCode, excludePackage).fetchOne();
        if (rec == null) {
            return new Baseline(0, 0, null, null);
        }
        return new Baseline(rec.get(0, Integer.class), rec.get(1, Integer.class), rec.get(2, BigDecimal.class),
                rec.get(MEAN_SQUARE, BigDecimal.class));
    }
}
