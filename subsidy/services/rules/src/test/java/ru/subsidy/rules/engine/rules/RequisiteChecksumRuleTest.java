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
 * FR-42: контрольные разряды ИНН и ОГРН — замечание, а не провал (ADR-025, синтетика открытого набора).
 */
class RequisiteChecksumRuleTest {

    private static final String MIGRATION = "/db/changelog/changesets/20261010150000-requisite-checksums.sql";
    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private final ProgramRulesRule engine = new ProgramRulesRule(
            Clock.fixed(Instant.parse("2026-10-10T09:00:00Z"), ZONE),
            new RulesProperties(Duration.ofSeconds(2), ZONE, 30, "http://docproc"));

    private static ObjectNode params() {
        try (var in = RequisiteChecksumRuleTest.class.getResourceAsStream(MIGRATION)) {
            var match = Pattern.compile("'(\\[\\{\"code\": \"INN_CHECKSUM\".*?)'::jsonb").matcher(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8));
            assertThat(match.find()).isTrue();
            ObjectNode params = Fixture.JSON.createObjectNode();
            params.set("rules", Fixture.JSON.readTree(match.group(1)));
            return params;
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private RuleResult result(Fixture facts, String code) {
        return engine.evaluate(facts.facts(), new ProgramParams(params())).stream()
                .filter(value -> code.equals(value.rule())).findFirst().orElseThrow();
    }

    @Test
    void seededRulesAreValid() {
        assertThat(new ProgramRules().parse(params().path("rules"))).hasSize(2);
    }

    @Test
    void innControlDigits() {
        assertThat(result(Fixture.pkg().doc(Docs.APPLICATION, "inn", "7707083893"), "INN_CHECKSUM").status())
                .isEqualTo(RuleStatus.PASSED);
        assertThat(result(Fixture.pkg().doc(Docs.APPLICATION, "inn", "500100732259"), "INN_CHECKSUM").status())
                .isEqualTo(RuleStatus.PASSED);
        RuleResult wrong = result(Fixture.pkg().doc(Docs.BANK_STATEMENT, "inn", "7707083894"), "INN_CHECKSUM");
        assertThat(wrong.status()).isEqualTo(RuleStatus.WARNING);
        assertThat(wrong.message()).contains("контрольные цифры ИНН не сходятся");
        assertThat(result(Fixture.pkg().doc(Docs.APPLICATION, "inn", "500100732258"), "INN_CHECKSUM").status())
                .isEqualTo(RuleStatus.WARNING);
    }

    @Test
    void ogrnAndOgrnipControlDigit() {
        assertThat(result(Fixture.pkg().doc(Docs.APPLICATION, "ogrn", "1027700132195"), "OGRN_CHECKSUM").status())
                .isEqualTo(RuleStatus.PASSED);
        assertThat(result(Fixture.pkg().doc(Docs.APPLICATION, "ogrn", "304500116000157"), "OGRN_CHECKSUM").status())
                .isEqualTo(RuleStatus.PASSED);
        assertThat(result(Fixture.pkg().doc(Docs.APPLICATION, "ogrn", "1027700132196"), "OGRN_CHECKSUM").status())
                .isEqualTo(RuleStatus.WARNING);
    }
}
