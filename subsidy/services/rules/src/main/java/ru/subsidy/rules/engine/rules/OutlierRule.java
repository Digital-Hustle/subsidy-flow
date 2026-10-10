package ru.subsidy.rules.engine.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.ProgramBaseline;
import ru.subsidy.rules.engine.ProgramBaseline.Baseline;
import ru.subsidy.rules.engine.Rule;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.engine.RuleStatus;
import ru.subsidy.rules.facts.DocFacts;
import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.money.Money;
import ru.subsidy.rules.program.ProgramParams;

/**
 * ANOMALY_OUTLIER: статья обоснования затрат больше «средняя + 3σ» сумм статей обоснований других пакетов программы.
 * Применяется только при выборке не меньше {@link #MIN_PACKAGES} пакетов, иначе NOT_APPLICABLE. Только WARNING.
 */
@Component
public class OutlierRule implements Rule {

    /** Минимум пакетов программы в выборке. */
    public static final int MIN_PACKAGES = 20;

    private static final String CODE = "ANOMALY_OUTLIER";
    private static final BigDecimal SIGMAS = BigDecimal.valueOf(3);
    private static final String NAME = "name";

    private final ProgramBaseline baseline;

    /**
     * Конструктор.
     *
     * @param baseline статистика программы
     */
    public OutlierRule(ProgramBaseline baseline) {
        this.baseline = baseline;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        Optional<DocFacts> estimate = facts.first(Docs.COST_JUSTIFICATION);
        List<JsonNode> rows = estimate.map(doc -> doc.rows(Docs.ITEMS)).orElse(List.of());
        if (rows.isEmpty()) {
            return List.of(RuleResult.missing(CODE, "Нет статей обоснования затрат для сравнения с программой"));
        }
        Baseline stats = baseline.estimateItems(facts.programCode(), facts.packageId());
        if (stats.packages() < MIN_PACKAGES || stats.mean() == null || stats.stddev() == null) {
            return List.of(RuleResult.notApplicable(CODE, "Мало пакетов программы для сравнения: "
                    + stats.packages() + " из " + MIN_PACKAGES));
        }
        return List.of(compare(estimate.get(), rows, stats));
    }

    private RuleResult compare(DocFacts estimate, List<JsonNode> rows, Baseline stats) {
        BigDecimal limit = Money.round(stats.mean().add(SIGMAS.multiply(stats.stddev())));
        List<String> outliers = new ArrayList<>();
        BigDecimal largest = null;
        for (JsonNode row : rows) {
            Optional<BigDecimal> amount = Sums.money(row, Docs.AMOUNT);
            if (amount.isPresent() && amount.get().compareTo(limit) > 0) {
                outliers.add(label(row) + " — " + Money.format(amount.get()));
                largest = largest == null || amount.get().compareTo(largest) > 0 ? amount.get() : largest;
            }
        }
        String basis = "средняя " + Money.format(stats.mean()) + " + 3σ (" + Money.format(stats.stddev())
                + ") по " + stats.packages() + " пакетам программы = " + Money.format(limit);
        List<Evidence> where = List.of(Evidence.of(estimate, Docs.ITEMS));
        if (outliers.isEmpty()) {
            return RuleResult.passed(CODE, "Статьи обоснования затрат в пределах программы: " + basis, where);
        }
        return new RuleResult(CODE, RuleStatus.WARNING, "Статьи обоснования затрат заметно выше обычных для программы ("
                + basis + "): " + String.join("; ", outliers), where, Money.plain(limit), Money.plain(largest));
    }

    private static String label(JsonNode row) {
        JsonNode name = row.get(NAME);
        return name == null || name.isNull() || name.asText().isBlank() ? "статья без названия" : name.asText();
    }
}
