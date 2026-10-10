package ru.subsidy.rules.completeness;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.engine.RuleStatus;
import ru.subsidy.rules.facts.DocFacts;
import ru.subsidy.rules.facts.FileFacts;
import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.program.ChecklistItem;
import ru.subsidy.rules.program.Program;
import ru.subsidy.rules.program.ProgramParams;

class CompletenessEngineTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String APPLICATION = "APPLICATION";
    private static final String BANK = "BANK_STATEMENT";
    private static final String CERTIFICATE = "CERTIFICATE";

    private static Program program(boolean certificationRequired) {
        return new Program("INNOVATION", 1, "Инновационный проект", false,
                new ProgramParams(JSON.valueToTree(Map.of("needsCertificate", certificationRequired))),
                List.of(new ChecklistItem(APPLICATION, "Заявление", ChecklistItem.REQUIRED, List.of(APPLICATION),
                                null, null, true),
                        new ChecklistItem("BANK", "Выписка из банка", ChecklistItem.REQUIRED, List.of(BANK), null, null,
                                false),
                        new ChecklistItem(CERTIFICATE, "Сертификат соответствия", ChecklistItem.CONDITIONAL,
                                List.of(CERTIFICATE), "needsCertificate", "если продукция подлежит сертификации",
                                false)),
                "system", null);
    }

    private static DocFacts doc(String type, String reason) {
        return new DocFacts(UUID.randomUUID(), UUID.randomUUID(), type, BigDecimal.ONE, "PARSED", reason, 1, 1,
                Map.of());
    }

    private static PackageFacts facts(DocFacts... docs) {
        return new PackageFacts(UUID.randomUUID(), "7707083893", "INNOVATION", LocalDate.of(2026, 3, 1), List.of(docs),
                List.of(new FileFacts(UUID.randomUUID(), 1, "PARSED", null)));
    }

    private static String status(Completeness result, String rule) {
        return result.results().stream().filter(res -> rule.equals(res.rule())).map(RuleResult::status)
                .findFirst().orElseThrow();
    }

    @Test
    void byDocumentType_notByFile() {
        Completeness full = new CompletenessEngine().check(facts(doc(APPLICATION, null), doc(BANK, null),
                doc(CERTIFICATE, null)), program(false));
        assertThat(full.missing()).isEmpty();
        assertThat(full.percent()).isEqualTo(100);
        assertThat(status(full, "CHK_REQUIRED")).isEqualTo(RuleStatus.PASSED);
        assertThat(status(full, "CHK_CONDITIONAL")).isEqualTo(RuleStatus.PASSED);
        assertThat(full.request()).isNull();
    }

    @Test
    void missingRequiredIsError_missingConditionalIsRemark() {
        Completeness partial = new CompletenessEngine().check(facts(doc(BANK, null)), program(false));
        assertThat(partial.missing()).extracting(Missing::item).containsExactly(APPLICATION);
        assertThat(partial.percent()).isEqualTo(50);
        assertThat(status(partial, "CHK_REQUIRED")).isEqualTo(RuleStatus.FAILED);
        assertThat(status(partial, "CHK_CONDITIONAL")).isEqualTo(RuleStatus.WARNING);
        assertThat(partial.request()).contains("Заявление").contains("дошлите также").contains("Сертификат");

        Completeness onlyConditional = new CompletenessEngine().check(facts(doc(APPLICATION, null), doc(BANK, null)),
                program(false));
        assertThat(onlyConditional.missing()).isEmpty();
        assertThat(status(onlyConditional, "CHK_CONDITIONAL")).isEqualTo(RuleStatus.WARNING);
    }

    @Test
    void programFlagDoesNotSetProjectApplicability() {
        Completeness result = new CompletenessEngine().check(facts(doc(APPLICATION, null), doc(BANK, null)),
                program(true));
        assertThat(result.missing()).isEmpty();
        assertThat(status(result, "CHK_CONDITIONAL")).isEqualTo(RuleStatus.WARNING);
        assertThat(result.items()).filteredOn(item -> CERTIFICATE.equals(item.item()))
                .singleElement().extracting(ItemResult::applicable).isNull();
    }

    @Test
    void sameProgramDifferentProjects_trueWarns_falseIsNotApplicable_unknownIsExplicit() {
        PackageFacts base = facts(doc(APPLICATION, null), doc(BANK, null));
        var applicable = new PackageFacts(base.packageId(), base.inn(), base.programCode(), base.packageDate(),
                base.documents(), base.files(), 1, JSON.valueToTree(Map.of("conditions", Map.of("needsCertificate", true))));
        var irrelevant = new PackageFacts(UUID.randomUUID(), base.inn(), base.programCode(), base.packageDate(),
                base.documents(), base.files(), 1, JSON.valueToTree(Map.of("conditions", Map.of("needsCertificate", false))));
        Completeness needed = new CompletenessEngine().check(applicable, program(false));
        Completeness notNeeded = new CompletenessEngine().check(irrelevant, program(true));
        assertThat(status(needed, "CHK_CONDITIONAL")).isEqualTo(RuleStatus.WARNING);
        assertThat(needed.missing()).isEmpty();
        assertThat(needed.percent()).isEqualTo(66);
        assertThat(needed.request()).contains("Сертификат");
        assertThat(status(notNeeded, "CHK_CONDITIONAL")).isEqualTo(RuleStatus.NOT_APPLICABLE);
        assertThat(notNeeded.percent()).isEqualTo(100);
        assertThat(notNeeded.request()).isNull();
    }

    @Test
    void unknownTypeDoesNotCount_duplicatesWarn() {
        Completeness result = new CompletenessEngine().check(facts(doc(APPLICATION, "TYPE_UNKNOWN"),
                doc("OTHER", "TYPE_OTHER"), doc(BANK, null)), program(false));
        assertThat(result.missing()).extracting(Missing::item).containsExactly(APPLICATION);
        assertThat(status(result, "CHK_UNKNOWN")).isEqualTo(RuleStatus.WARNING);
        Completeness dup = new CompletenessEngine().check(facts(doc(APPLICATION, null), doc(APPLICATION, null),
                doc(BANK, null)), program(false));
        assertThat(status(dup, "CHK_DUPLICATE")).isEqualTo(RuleStatus.WARNING);
    }
}
