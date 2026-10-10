package ru.subsidy.rules.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import ru.subsidy.rules.config.RulesProperties;
import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.engine.RuleStatus;
import ru.subsidy.rules.engine.declarative.ProgramRules;
import ru.subsidy.rules.program.ProgramParams;

/**
 * B2-2: видимая подпись по результату vision-модели; отсутствие и неопределённость — замечания с разным текстом.
 */
class VisualSignatureRuleTest {

    private static final String MIGRATION = "/db/changelog/changesets/20261010140000-visual-signature.sql";
    private static final String RULE = "VISUAL_SIGNATURE";
    private static final String FIELD = "visualSignature";
    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private final ProgramRulesRule engine = new ProgramRulesRule(
            Clock.fixed(Instant.parse("2026-10-10T09:00:00Z"), ZONE),
            new RulesProperties(Duration.ofSeconds(2), ZONE, 30, "http://docproc"));

    private static ObjectNode params() {
        try (var in = VisualSignatureRuleTest.class.getResourceAsStream(MIGRATION)) {
            var match = Pattern.compile("'(\\{\"code\": \"VISUAL_SIGNATURE\".*?)'::jsonb").matcher(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8));
            assertThat(match.find()).isTrue();
            ObjectNode params = Fixture.JSON.createObjectNode();
            params.putArray("rules").add(Fixture.JSON.readTree(match.group(1)));
            return params;
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private RuleResult result(Fixture facts) {
        return engine.evaluate(facts.facts(), new ProgramParams(params())).stream()
                .filter(value -> RULE.equals(value.rule())).findFirst().orElseThrow();
    }

    @Test
    void seededRuleIsValid() {
        assertThat(new ProgramRules().parse(params().path("rules"))).hasSize(1);
    }

    @Test
    void detectedPasses_notDetectedWarns_undeterminedNeedsReview() {
        assertThat(result(Fixture.pkg().doc(Docs.APPLICATION, FIELD, "DETECTED")).status())
                .isEqualTo(RuleStatus.PASSED);
        RuleResult absent = result(Fixture.pkg().doc(Docs.GUARANTEE_LETTER, FIELD, "NOT_DETECTED"));
        assertThat(absent.status()).isEqualTo(RuleStatus.WARNING);
        assertThat(absent.message()).contains("Не обнаружена").contains("Гарантийное письмо");
        RuleResult unknown = result(Fixture.pkg().doc(Docs.APPLICATION, FIELD, "UNDETERMINED"));
        assertThat(unknown.status()).isEqualTo(RuleStatus.WARNING);
        assertThat(unknown.message()).contains("Не удалось определить");
        assertThat(result(Fixture.pkg().doc(Docs.APPLICATION, "inn", Fixture.APPLICANT)).status())
                .as("DOCX/не проверялось").isEqualTo(RuleStatus.WARNING);
    }

    @Test
    void electronicSignatureCountsAsSigned_andOtherTypesAreIgnored() {
        assertThat(result(Fixture.pkg().doc(Docs.APPLICATION, FIELD, "NOT_DETECTED").signature("PRESENT")).status())
                .isEqualTo(RuleStatus.PASSED);
        assertThat(result(Fixture.pkg().doc(Docs.BANK_STATEMENT, FIELD, "NOT_DETECTED")).status())
                .isEqualTo(RuleStatus.NOT_APPLICABLE);
    }
}
