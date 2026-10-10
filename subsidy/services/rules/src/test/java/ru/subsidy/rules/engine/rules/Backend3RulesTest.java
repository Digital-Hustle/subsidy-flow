package ru.subsidy.rules.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import ru.subsidy.rules.config.RulesProperties;
import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.engine.RuleStatus;
import ru.subsidy.rules.engine.declarative.ProgramRules;
import ru.subsidy.rules.program.ProgramParams;

class Backend3RulesTest {

    private static final String MIGRATION = "/db/changelog/changesets/20261010112645-application-document-checks.sql";
    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private final ProgramRulesRule engine = new ProgramRulesRule(
            Clock.fixed(Instant.parse("2026-10-10T09:00:00Z"), ZONE),
            new RulesProperties(Duration.ofSeconds(2), ZONE, 30, "http://docproc"));

    static ObjectNode defaults() {
        try (var in = Backend3RulesTest.class.getResourceAsStream(MIGRATION)) {
            var match = Pattern.compile("'(\\{\\\"rules\\\".*?)'::jsonb").matcher(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8));
            assertThat(match.find()).isTrue();
            return (ObjectNode) Fixture.JSON.readTree(match.group(1).replace("''", "'"));
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static ObjectNode application() {
        ObjectNode form = Fixture.JSON.createObjectNode().put("orgInn", Fixture.APPLICANT)
                .put("orgName", "ООО Тест").put("ogrn", "1027700132195").put("requestedAmount", "500.00");
        form.putObject("address").put("value", "Москва, улица Тестовая, 1").put("purpose", "LEGAL");
        form.putObject("property").put("cadastralNumber", "61:18:0600022:891").put("address", "Ростов, берег, 1")
                .put("rightType", "LEASE");
        return form;
    }

    private RuleResult result(Fixture facts, String code) {
        return engine.evaluate(facts.facts(), new ProgramParams(defaults())).stream()
                .filter(value -> code.equals(value.rule())).findFirst().orElseThrow();
    }

    @Test
    void allSeededRulesAreValid_andComparisonsExposeBothSides() {
        assertThat(new ProgramRules().parse(defaults().path("rules"))).hasSize(23);
        RuleResult matched = result(Fixture.pkg().application(application()).doc(Docs.BANK_STATEMENT,
                "orgName", "ООО «Тест»", "inn", Fixture.APPLICANT), "APPLICATION_ORG");
        assertThat(matched.status()).isEqualTo(RuleStatus.PASSED);
        assertThat(matched.expected()).isEqualTo("ООО Тест");
        assertThat(matched.actual()).contains("ООО «Тест»");
        assertThat(matched.evidence()).isNotEmpty();
        RuleResult mismatch = result(Fixture.pkg().application(application()).doc(Docs.APPLICATION,
                "ogrn", "1027700132196"), "APPLICATION_OGRN");
        assertThat(mismatch.status()).isEqualTo(RuleStatus.FAILED);
        assertThat(mismatch.expected()).isEqualTo("1027700132195");
        assertThat(mismatch.actual()).contains("1027700132196");
    }

    @Test
    void addressUsesItsPurpose_andPlotAndTenantUseTheirOwnFields() {
        Fixture facts = Fixture.pkg().application(application()).doc(Docs.APPLICATION,
                "legalAddress", "Москва, улица Тестовая, 1", "actualAddress", "Другое место")
                .doc(Docs.PROPERTY_DOC, "cadastralNumber", "61:18:0600022:891", "tenant", "ООО Тест",
                        "rightHolder", "Другой собственник", "addresses", List.of(Map.of("address", "Ростов, берег, 1")));
        assertThat(result(facts, "APPLICATION_ADDRESS_LEGAL").status()).isEqualTo(RuleStatus.PASSED);
        assertThat(result(facts, "APPLICATION_ADDRESS_ACTUAL").status()).isEqualTo(RuleStatus.NOT_APPLICABLE);
        assertThat(result(facts, "APPLICATION_PLOT").status()).isEqualTo(RuleStatus.PASSED);
        assertThat(result(facts, "APPLICATION_PROPERTY_ADDRESS").status()).isEqualTo(RuleStatus.PASSED);
        assertThat(result(facts, "APPLICATION_TENANT").status()).isEqualTo(RuleStatus.PASSED);
        assertThat(result(facts, "APPLICATION_OWNER").status()).isEqualTo(RuleStatus.NOT_APPLICABLE);
        assertThat(result(Fixture.pkg().application(application()).doc(Docs.PROPERTY_DOC,
                "tenant", "Другой арендатор"), "APPLICATION_TENANT").status()).isEqualTo(RuleStatus.WARNING);
        ObjectNode owned = application();
        ((ObjectNode) owned.path("property")).put("rightType", "OWNERSHIP");
        assertThat(result(Fixture.pkg().application(owned).doc(Docs.PROPERTY_DOC, "rightHolder", "ООО Тест"),
                "APPLICATION_OWNER").status()).isEqualTo(RuleStatus.PASSED);
    }

    @Test
    void separateGuarantorDoesNotFailApplicantInn_butRecipientMustMatch() {
        Fixture facts = Fixture.pkg().application(application()).doc(Docs.APPLICATION, "inn", Fixture.APPLICANT)
                .doc(Docs.GUARANTEE_LETTER, "inn", "6103050782", "guarantorInn", "6103050782",
                        "beneficiaryInn", Fixture.APPLICANT, "beneficiaryName", "ООО Тест");
        assertThat(result(facts, "INN_SAME").status()).isEqualTo(RuleStatus.PASSED);
        assertThat(result(facts, "GUARANTEE_BENEFICIARY_INN").status()).isEqualTo(RuleStatus.PASSED);
        assertThat(result(facts, "GUARANTEE_BENEFICIARY_NAME").status()).isEqualTo(RuleStatus.PASSED);
        assertThat(result(Fixture.pkg().application(application()).doc(Docs.GUARANTEE_LETTER,
                "beneficiaryInn", "6103050782"), "GUARANTEE_BENEFICIARY_INN").status()).isEqualTo(RuleStatus.WARNING);
        // письмо пишет сам заявитель: получатель не указан, гарант — заявитель
        assertThat(result(Fixture.pkg().application(application()).doc(Docs.GUARANTEE_LETTER,
                "guarantorInn", Fixture.APPLICANT), "GUARANTEE_BENEFICIARY_INN").status())
                .isEqualTo(RuleStatus.PASSED);
    }

    @Test
    void manufacturerIsNotApplicant_andMissingFieldsNeverPass() {
        Fixture facts = Fixture.pkg().application(application()).doc(Docs.CERTIFICATE,
                "inn", "6103050782", "orgName", "Другой производитель", "applicantName", "ООО Тест",
                "applicantInn", Fixture.APPLICANT);
        assertThat(result(facts, "APPLICATION_INN").status()).isEqualTo(RuleStatus.PASSED);
        assertThat(result(facts, "APPLICATION_ORG").status()).isEqualTo(RuleStatus.PASSED);
        assertThat(result(Fixture.pkg().doc(Docs.APPLICATION, "inn", Fixture.APPLICANT), "APPLICATION_INN").status())
                .isEqualTo(RuleStatus.NOT_APPLICABLE);
        assertThat(result(Fixture.pkg().application(application()).doc(Docs.BANK_STATEMENT,
                "orgName", "Общество с ограниченной ответственностью «Тест»"), "APPLICATION_ORG").status())
                .isEqualTo(RuleStatus.PASSED);
    }

    @Test
    void issueDateIsCheckedEvenWhenDateIsValid() {
        assertThat(result(Fixture.pkg().doc(Docs.CERTIFICATE, "date", "2026-10-09", "issueDate", "2026-10-11"),
                "DATE_NOT_FUTURE").status()).isEqualTo(RuleStatus.FAILED);
        assertThat(result(Fixture.pkg().doc(Docs.GIMS_DECLARATION, "issueDate", "2026-10-10"),
                "DATE_NOT_FUTURE").status()).isEqualTo(RuleStatus.PASSED);
    }

    @Test
    void ownFundsComeOnlyFromCostDocument_withKopeckBoundary() {
        Fixture incomplete = Fixture.pkg().doc(Docs.COST_JUSTIFICATION, "totalCost", "1000.00")
                .doc(Docs.APPLICATION, "requestedAmount", "400.00", "ownFunds", "600.00", "totalCost", "1000.00");
        assertThat(result(incomplete, "OWN_FUNDS_SHARE").status()).isEqualTo(RuleStatus.DATA_MISSING);
        assertThat(result(Fixture.pkg().doc(Docs.COST_JUSTIFICATION, "totalCost", "1000.00",
                "requestedAmount", "500.00"), "OWN_FUNDS_SHARE").status()).isEqualTo(RuleStatus.PASSED);
        assertThat(result(Fixture.pkg().doc(Docs.COST_JUSTIFICATION, "totalCost", "1000.00",
                "ownFunds", "499.99"), "OWN_FUNDS_SHARE").status()).isEqualTo(RuleStatus.FAILED);
    }

    @Test
    void equalTotalsDoNotReplaceRequestedAmount_andSnapshotIsAuthoritative() {
        assertThat(result(Fixture.pkg().doc(Docs.APPLICATION, "totalCost", "1000.00")
                .doc(Docs.COST_JUSTIFICATION, "totalCost", "1000.00"), "REQUEST_MATCH").status())
                .isEqualTo(RuleStatus.DATA_MISSING);
        // ТЗ 4.3: заявление ↔ обоснование сверяются по извлечённым суммам; анкета — отдельное замечание
        Fixture facts = Fixture.pkg().application(application()).doc(Docs.APPLICATION, "requestedAmount", "400.00")
                .doc(Docs.COST_JUSTIFICATION, "requestedAmount", "400.00");
        assertThat(result(facts, "REQUEST_MATCH").status()).isEqualTo(RuleStatus.PASSED);
        assertThat(result(facts, "APPLICATION_REQUEST_MATCH").status()).isEqualTo(RuleStatus.WARNING);
        assertThat(result(facts, "APPLICATION_REQUEST_MATCH").expected()).isEqualTo("500.00");
        assertThat(result(facts, "APPLICATION_REQUEST_MATCH").actual()).isEqualTo("400.00");
        assertThat(result(Fixture.pkg().application(application()).doc(Docs.COST_JUSTIFICATION,
                "requestedAmount", "500.01"), "REQUEST_MATCH").status()).isEqualTo(RuleStatus.DATA_MISSING);
        assertThat(result(Fixture.pkg().doc(Docs.APPLICATION, "requestedAmount", "400.00"),
                "APPLICATION_REQUEST_MATCH").status()).isEqualTo(RuleStatus.NOT_APPLICABLE);
    }

    @Test
    void signaturesShowAbsenceAndUncertaintySeparately_andRequirementIsConfiguration() {
        Fixture absent = Fixture.pkg().doc(Docs.APPLICATION, "inn", Fixture.APPLICANT).signature("ABSENT");
        assertThat(result(absent, "PDF_SIGNATURE").status()).isEqualTo(RuleStatus.PASSED);
        assertThat(result(absent, "PDF_SIGNATURE").actual()).contains("ABSENT");
        assertThat(result(Fixture.pkg().doc(Docs.APPLICATION, "inn", Fixture.APPLICANT), "PDF_SIGNATURE").status())
                .isEqualTo(RuleStatus.PASSED);
        ObjectNode params = defaults();
        params.path("rules").forEach(rule -> {
            if ("PDF_SIGNATURE".equals(rule.path("code").asText())) {
                ((ObjectNode) rule).put("required", true).put("severity", "FAILED");
            }
        });
        assertThat(engine.evaluate(absent.facts(), new ProgramParams(params)))
                .anyMatch(value -> "PDF_SIGNATURE".equals(value.rule()) && RuleStatus.FAILED.equals(value.status()));
        assertThat(result(Fixture.pkg().doc(Docs.APPLICATION, "inn", Fixture.APPLICANT).signature("PRESENT"),
                "PDF_SIGNATURE").message()).contains("Действительность не проверена");
    }
}
