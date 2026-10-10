package ru.subsidy.rules.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.ProgramBaseline.Baseline;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.engine.RuleStatus;

class OutlierRuleTest {

    private static final BigDecimal MEAN = new BigDecimal("100000.00");
    private static final BigDecimal SIGMA = new BigDecimal("10000.00");
    private static final Baseline ENOUGH = new Baseline(20, 140, MEAN, SIGMA);

    private static RuleResult run(Baseline stats, String... amounts) {
        Object[] rows = new Object[amounts.length * 3];
        for (int idx = 0; idx < amounts.length; idx++) {
            rows[idx * 3] = 1;
            rows[idx * 3 + 1] = amounts[idx];
            rows[idx * 3 + 2] = amounts[idx];
        }
        OutlierRule rule = new OutlierRule((program, exclude) -> stats);
        List<RuleResult> results = rule.evaluate(Fixture.pkg().doc(Docs.COST_JUSTIFICATION, Docs.ITEMS, Fixture.items(rows))
                .facts(), Fixture.params());
        assertThat(results).hasSize(1);
        return results.get(0);
    }

    @Test
    void exactlyThreeSigma_passed() {
        RuleResult result = run(ENOUGH, "130000.00", "95000.00");
        assertThat(result.status()).isEqualTo(RuleStatus.PASSED);
    }

    @Test
    void kopeckAboveThreeSigma_warningWithLimitAndAmount() {
        RuleResult result = run(ENOUGH, "95000.00", "130000.01");
        assertThat(result.status()).isEqualTo(RuleStatus.WARNING);
        assertThat(result.expected()).isEqualTo("130000.00");
        assertThat(result.actual()).isEqualTo("130000.01");
        assertThat(result.message()).contains("Позиция 2").doesNotContain("Позиция 1");
    }

    @Test
    void largestOutlierIsActual() {
        RuleResult result = run(ENOUGH, "200000.00", "500000.00");
        assertThat(result.actual()).isEqualTo("500000.00");
    }

    @Test
    void nineteenPackages_notApplicable() {
        RuleResult result = run(new Baseline(19, 100, MEAN, SIGMA), "900000.00");
        assertThat(result.status()).isEqualTo(RuleStatus.NOT_APPLICABLE);
        assertThat(result.message()).contains("19 из 20");
    }

    @Test
    void noEstimateItems_dataMissing() {
        OutlierRule rule = new OutlierRule((program, exclude) -> ENOUGH);
        List<RuleResult> results = rule.evaluate(Fixture.pkg().doc(Docs.BANK_STATEMENT, "balance", "100.00").facts(),
                Fixture.params());
        assertThat(results.get(0).status()).isEqualTo(RuleStatus.DATA_MISSING);
    }
}
