package ru.subsidy.rules.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import ru.subsidy.rules.config.RulesProperties;
import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.engine.RuleStatus;
import ru.subsidy.rules.engine.declarative.ProgramRules;
import ru.subsidy.rules.program.ProgramParams;

/**
 * Правила п. 4.3 кейса из миграции программ: каждое правило — тест с числами (граница, нет данных, ошибка параметров).
 * Сегодня в тестах — 20.03.2026.
 */
class ProgramRulesRuleTest {

    private static final String MIGRATION = "/db/changelog/changesets/20261010090000-case-programs.sql";
    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-20T09:00:00Z"), ZONE);
    private static final String INN = "inn";
    private static final String DATE = "date";
    private static final String TOTAL = "totalCost";
    private static final String OWN = "ownFunds";
    private static final String REQUESTED = "requestedAmount";

    private final ProgramRulesRule rule = new ProgramRulesRule(CLOCK,
            new RulesProperties(Duration.ofSeconds(2), ZONE, 30, "http://docproc"));

    private static ObjectNode migrationParams() {
        try (InputStream in = ProgramRulesRuleTest.class.getResourceAsStream(MIGRATION)) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher match = Pattern.compile("'(\\{\"rules\".*?)'::jsonb").matcher(sql);
            assertThat(match.find()).isTrue();
            return (ObjectNode) Fixture.JSON.readTree(match.group(1).replace("''", "'"));
        } catch (IOException broken) {
            throw new IllegalStateException(broken);
        }
    }

    private static String status(List<RuleResult> results, String code) {
        return result(results, code).status();
    }

    private static RuleResult result(List<RuleResult> results, String code) {
        return results.stream().filter(res -> code.equals(res.rule())).findFirst().orElseThrow();
    }

    private List<RuleResult> run(Fixture pkg) {
        return rule.evaluate(pkg.facts(), new ProgramParams(migrationParams()));
    }

    @Test
    void migrationRulesAreValid() {
        assertThat(new ProgramRules().parse(migrationParams().path("rules"))).hasSize(9);
    }

    @Test
    void innFormat_tenOrTwelveDigits() {
        assertThat(status(run(Fixture.pkg().doc(Docs.APPLICATION, INN, "7707083893")), "INN_FORMAT"))
                .isEqualTo(RuleStatus.PASSED);
        assertThat(status(run(Fixture.pkg().doc(Docs.APPLICATION, INN, "610305078268")), "INN_FORMAT"))
                .isEqualTo(RuleStatus.PASSED);
        RuleResult bad = result(run(Fixture.pkg().doc(Docs.APPLICATION, INN, "77070838")), "INN_FORMAT");
        assertThat(bad.status()).isEqualTo(RuleStatus.FAILED);
        assertThat(bad.message()).contains("77070838");
        assertThat(status(run(Fixture.pkg().doc(Docs.APPLICATION, DATE, "2026-03-01")), "INN_FORMAT"))
                .isEqualTo(RuleStatus.DATA_MISSING);
    }

    @Test
    void innSame_acrossDocumentsAndApplicantCard() {
        assertThat(status(run(Fixture.pkg().doc(Docs.APPLICATION, INN, Fixture.APPLICANT)
                .doc(Docs.BANK_STATEMENT, INN, Fixture.APPLICANT)), "INN_SAME")).isEqualTo(RuleStatus.PASSED);
        RuleResult differs = result(run(Fixture.pkg().doc(Docs.APPLICATION, INN, Fixture.APPLICANT)
                .doc(Docs.BANK_STATEMENT, INN, "610305078268")), "INN_SAME");
        assertThat(differs.status()).isEqualTo(RuleStatus.FAILED);
        assertThat(differs.message()).contains("610305078268").contains("Выписка из банка");
    }

    @Test
    void dateNotFuture_boundaryIsToday() {
        assertThat(status(run(Fixture.pkg().doc(Docs.APPLICATION, DATE, "2026-03-20")), "DATE_NOT_FUTURE"))
                .isEqualTo(RuleStatus.PASSED);
        assertThat(status(run(Fixture.pkg().doc(Docs.GUARANTEE_LETTER, DATE, "2026-03-21")), "DATE_NOT_FUTURE"))
                .isEqualTo(RuleStatus.FAILED);
    }

    @Test
    void notExpired_failedWarningPassedAndNotApplicable() {
        assertThat(status(run(Fixture.pkg().doc(Docs.CERTIFICATE, "validUntil", "2026-03-19")), "NOT_EXPIRED"))
                .isEqualTo(RuleStatus.FAILED);
        assertThat(status(run(Fixture.pkg().doc(Docs.CERTIFICATE, "validUntil", "2026-04-10")), "NOT_EXPIRED"))
                .isEqualTo(RuleStatus.WARNING);
        assertThat(status(run(Fixture.pkg().doc(Docs.GIMS_DECLARATION, "validUntil", "2026-12-31")), "NOT_EXPIRED"))
                .isEqualTo(RuleStatus.PASSED);
        assertThat(status(run(Fixture.pkg().doc(Docs.APPLICATION, DATE, "2026-03-01")), "NOT_EXPIRED"))
                .isEqualTo(RuleStatus.NOT_APPLICABLE);
    }

    @Test
    void ownFundsShare_fiftyPercentBoundaryInKopecks() {
        assertThat(status(run(Fixture.pkg().doc(Docs.COST_JUSTIFICATION, TOTAL, "1000000.00", OWN, "500000.00")),
                "OWN_FUNDS_SHARE")).isEqualTo(RuleStatus.PASSED);
        RuleResult below = result(run(Fixture.pkg().doc(Docs.COST_JUSTIFICATION, TOTAL, "1000000.00", OWN,
                "499999.99")), "OWN_FUNDS_SHARE");
        assertThat(below.status()).isEqualTo(RuleStatus.FAILED);
        assertThat(below.message()).contains("499 999,99").contains("1 000 000,00").contains("50,00 %");
    }

    @Test
    void ownFundsShare_fromTotalMinusRequestedInApplication() {
        RuleResult result = result(run(Fixture.pkg().doc(Docs.COST_JUSTIFICATION, TOTAL, "6 306 800,00")
                .doc(Docs.APPLICATION, REQUESTED, "2964196.00")), "OWN_FUNDS_SHARE");
        assertThat(result.status()).isEqualTo(RuleStatus.PASSED);
        assertThat(result.message()).contains("3 342 604,00").contains("53,00 %");
        assertThat(status(run(Fixture.pkg().doc(Docs.APPLICATION, DATE, "2026-03-01")), "OWN_FUNDS_SHARE"))
                .isEqualTo(RuleStatus.DATA_MISSING);
    }

    @Test
    void requestMatch_toleranceOneKopeck_andFallbackPairs() {
        assertThat(status(run(Fixture.pkg().doc(Docs.APPLICATION, REQUESTED, "500000.00")
                .doc(Docs.COST_JUSTIFICATION, REQUESTED, "500000.01")), "REQUEST_MATCH")).isEqualTo(RuleStatus.PASSED);
        assertThat(status(run(Fixture.pkg().doc(Docs.APPLICATION, REQUESTED, "500000.00")
                .doc(Docs.COST_JUSTIFICATION, REQUESTED, "500001.00")), "REQUEST_MATCH")).isEqualTo(RuleStatus.FAILED);
        assertThat(status(run(Fixture.pkg().doc(Docs.APPLICATION, REQUESTED, "400000.00")
                .doc(Docs.COST_JUSTIFICATION, TOTAL, "1000000.00", OWN, "600000.00")), "REQUEST_MATCH"))
                .isEqualTo(RuleStatus.PASSED);
        assertThat(status(run(Fixture.pkg().doc(Docs.APPLICATION, TOTAL, "6306800.00")
                .doc(Docs.COST_JUSTIFICATION, TOTAL, "6306800.00")), "REQUEST_MATCH")).isEqualTo(RuleStatus.PASSED);
    }

    @Test
    void cadastralFormat_andPresentationNotEmpty() {
        assertThat(status(run(Fixture.pkg().doc(Docs.PROPERTY_DOC, "cadastralNumber", "61:18: 0600022:891")),
                "CADASTRAL_FORMAT")).isEqualTo(RuleStatus.PASSED);
        assertThat(status(run(Fixture.pkg().doc(Docs.PROPERTY_DOC, "cadastralNumber", "61-18-0600022-891")),
                "CADASTRAL_FORMAT")).isEqualTo(RuleStatus.FAILED);
        assertThat(status(run(Fixture.pkg().doc(Docs.PRESENTATION, "orgName", "ООО Ромб")), "PRESENTATION_NOT_EMPTY"))
                .isEqualTo(RuleStatus.FAILED);
        assertThat(status(run(Fixture.pkg().doc(Docs.PRESENTATION, "projectName", "Пляж")), "PRESENTATION_NOT_EMPTY"))
                .isEqualTo(RuleStatus.PASSED);
    }

    @Test
    void minFields_documentWithoutMinimumIsNotParsed() {
        RuleResult result = result(run(Fixture.pkg().doc(Docs.APPLICATION, INN, Fixture.APPLICANT)), "MIN_FIELDS");
        assertThat(result.status()).isEqualTo(RuleStatus.WARNING);
        assertThat(result.message()).contains("запрашиваемая сумма").contains("дата");
    }

    @Test
    void adminChangesThreshold_andDisablesRule_withoutCode() {
        ObjectNode params = migrationParams();
        for (JsonNode spec : params.path("rules")) {
            if ("OWN_FUNDS_SHARE".equals(spec.path("code").asText())) {
                ((ObjectNode) spec).put("min", "0.60");
            }
            if ("INN_FORMAT".equals(spec.path("code").asText())) {
                ((ObjectNode) spec).put("enabled", false);
            }
        }
        List<RuleResult> results = rule.evaluate(Fixture.pkg().doc(Docs.COST_JUSTIFICATION, TOTAL, "1000000.00",
                OWN, "550000.00", INN, "1").facts(), new ProgramParams(params));
        assertThat(status(results, "OWN_FUNDS_SHARE")).isEqualTo(RuleStatus.FAILED);
        assertThat(results).noneMatch(res -> "INN_FORMAT".equals(res.rule()));
    }

    @Test
    void range_maxAmount() {
        ObjectNode params = Fixture.JSON.createObjectNode();
        params.putArray("rules").addObject().put("code", "MAX_REQUEST").put("op", "RANGE").put("max", "3000000")
                .putArray("value").add("APPLICATION.requestedAmount");
        assertThat(status(rule.evaluate(Fixture.pkg().doc(Docs.APPLICATION, REQUESTED, "3000000.01").facts(),
                new ProgramParams(params)), "MAX_REQUEST")).isEqualTo(RuleStatus.FAILED);
        assertThat(status(rule.evaluate(Fixture.pkg().doc(Docs.APPLICATION, REQUESTED, "3000000.00").facts(),
                new ProgramParams(params)), "MAX_REQUEST")).isEqualTo(RuleStatus.PASSED);
    }

    @Test
    void invalidRules_explainedToAdmin() {
        ProgramRules rules = new ProgramRules();
        assertThatThrownBy(() -> rules.parse(array("{\"code\":\"X1\",\"op\":\"MAGIC\"}")))
                .hasMessageContaining("неизвестная операция");
        assertThatThrownBy(() -> rules.parse(array("{\"code\":\"X1\",\"op\":\"PATTERN\",\"types\":[\"*\"],"
                + "\"field\":\"inn\",\"pattern\":\"(\"}"))).hasMessageContaining("шаблон");
        assertThatThrownBy(() -> rules.parse(array("{\"code\":\"X1\",\"op\":\"RATIO_MIN\",\"numerator\":"
                + "[\"COST_JUSTIFICATION.ownFunds\"],\"denominator\":[\"COST_JUSTIFICATION.totalCost\"],\"min\":\"1.5\"}")))
                .hasMessageContaining("min");
        assertThatThrownBy(() -> rules.parse(array("{\"code\":\"X1\",\"op\":\"NOT_EMPTY\",\"types\":[\"INVOICE\"],"
                + "\"fields\":[\"a\"]}"))).hasMessageContaining("неизвестный вид");
        assertThatThrownBy(() -> rules.parse(array("{\"code\":\"X1\",\"op\":\"NOT_EMPTY\",\"types\":[\"*\"],"
                + "\"fields\":[\"a\"]}", "{\"code\":\"X1\",\"op\":\"NOT_EMPTY\",\"types\":[\"*\"],\"fields\":[\"a\"]}")))
                .hasMessageContaining("дважды");
    }

    @Test
    void brokenRulesInStoredProgram_giveErrorNotCrash() {
        ObjectNode params = Fixture.JSON.createObjectNode().put("rules", "not-an-array");
        List<RuleResult> results = rule.evaluate(Fixture.pkg().facts(), new ProgramParams(params));
        assertThat(results).singleElement().extracting(RuleResult::status).isEqualTo(RuleStatus.ERROR);
    }

    private static ArrayNode array(String... specs) {
        ArrayNode out = Fixture.JSON.createArrayNode();
        for (String spec : specs) {
            try {
                out.add(Fixture.JSON.readTree(spec));
            } catch (IOException broken) {
                throw new IllegalStateException(broken);
            }
        }
        return out;
    }
}
