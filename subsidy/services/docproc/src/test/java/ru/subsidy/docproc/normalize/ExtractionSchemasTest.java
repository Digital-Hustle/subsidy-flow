package ru.subsidy.docproc.normalize;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.subsidy.docproc.classify.DocType;
import ru.subsidy.docproc.classify.TypeRegistry;

/**
 * Схемы извлечения для сверок (B2-1): адреса с назначением, правообладатель/арендатор, кадастровый номер,
 * гарант отдельно от заявителя/получателя. Тесты берут реальный реестр docs/DOC-TYPES.md.
 */
class ExtractionSchemasTest {

    private static final BigDecimal THRESHOLD = new BigDecimal("0.600");

    private final ObjectMapper json = new ObjectMapper();
    private final FieldValidator validator = new FieldValidator(json);
    private final TypeRegistry registry = new TypeRegistry(json);

    private DocType type(String code) {
        return registry.find(code).orElseThrow();
    }

    private List<FieldValue> normalize(String code, String data) throws Exception {
        JsonNode node = json.readTree(data);
        return validator.normalize(type(code), node, json.createObjectNode(), THRESHOLD);
    }

    private static FieldValue field(List<FieldValue> fields, String name) {
        return fields.stream().filter(item -> item.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void applicationAddressesKeepTheirPurpose_andCertificateApplicantIsSeparateFromManufacturer() throws Exception {
        List<FieldValue> addresses = normalize("APPLICATION", """
                {"legalAddress":"Москва, ул. Тестовая, 1","actualAddress":"Ростов, ул. Тестовая, 2"}
                """);
        assertThat(field(addresses, "legalAddress").value().asText()).isEqualTo("Москва, ул. Тестовая, 1");
        assertThat(field(addresses, "actualAddress").value().asText()).isEqualTo("Ростов, ул. Тестовая, 2");
        assertThat(addresses.stream().map(FieldValue::name)).doesNotContain("projectAddress");
        List<FieldValue> certificate = normalize("CERTIFICATE", """
                {"orgName":"Производитель","inn":"6103050782","applicantName":"ООО Заявитель","applicantInn":"7707083893"}
                """);
        assertThat(field(certificate, "applicantName").value().asText()).isEqualTo("ООО Заявитель");
        assertThat(field(certificate, "applicantInn").value().asText()).isEqualTo("7707083893");
        assertThat(type("APPLICATION").version()).isEqualTo("5");
        assertThat(type("CERTIFICATE").version()).isEqualTo("5");
    }

    @Test
    void propertyDoc_keepsAddressesWithPurposePerAddress() throws Exception {
        List<FieldValue> fields = normalize("PROPERTY_DOC", """
                {"cadastralNumber": "61:18:0600022:891",
                 "addresses": [
                   {"address": "г. Ростов-на-Дону, ул. Пляжная, 1", "purpose": "для обустройства пляжа"},
                   {"address": "г. Ростов-на-Дону, ул. Пляжная, 3", "purpose": ""}],
                 "rightHolder": "ООО Ромашка", "tenant": null}
                """);
        JsonNode addresses = field(fields, "addresses").value();
        assertThat(addresses).hasSize(2);
        assertThat(addresses.get(0).path("address").asText()).isEqualTo("г. Ростов-на-Дону, ул. Пляжная, 1");
        assertThat(addresses.get(0).path("purpose").asText()).isEqualTo("для обустройства пляжа");
        assertThat(addresses.get(1).path("purpose").isNull()).isTrue();
    }

    @Test
    void propertyDoc_emptyAddressRowsAreDropped_notInvented() throws Exception {
        List<FieldValue> fields = normalize("PROPERTY_DOC", """
                {"addresses": [{"address": "  ", "purpose": null}, {"address": null, "purpose": null}]}
                """);
        assertThat(field(fields, "addresses").value()).isEmpty();
    }

    @Test
    void propertyDoc_tenantAndRightHolderAreSeparateFields() throws Exception {
        List<FieldValue> fields = normalize("PROPERTY_DOC", """
                {"rightHolder": null, "tenant": "ИП Петров"}
                """);
        assertThat(field(fields, "tenant").value().asText()).isEqualTo("ИП Петров");
        // пустое значение не выдумываем и не выводим как поле: его нет в результате
        assertThat(fields.stream().map(FieldValue::name)).doesNotContain("rightHolder");
    }

    @Test
    void propertyDoc_cadastralNumberKeptAsWritten() throws Exception {
        List<FieldValue> fields = normalize("PROPERTY_DOC", "{\"cadastralNumber\": \"61:18:0600022:891\"}");
        assertThat(field(fields, "cadastralNumber").raw()).isEqualTo("61:18:0600022:891");
    }

    @Test
    void guarantee_separatesGuarantorFromBeneficiary() throws Exception {
        List<FieldValue> fields = normalize("GUARANTEE_LETTER", """
                {"guarantorName": "ООО Гарант", "guarantorInn": "7707083893",
                 "beneficiaryName": "ООО Заявитель", "beneficiaryInn": "5001007322"}
                """);
        assertThat(field(fields, "guarantorName").value().asText()).isEqualTo("ООО Гарант");
        assertThat(field(fields, "beneficiaryName").value().asText()).isEqualTo("ООО Заявитель");
        assertThat(field(fields, "guarantorInn").value().asText()).isEqualTo("7707083893");
        assertThat(field(fields, "beneficiaryInn").value().asText()).isEqualTo("5001007322");
    }

    @Test
    void guarantee_missingBeneficiaryIsNotInvented() throws Exception {
        List<FieldValue> fields = normalize("GUARANTEE_LETTER", "{\"guarantorName\": \"ООО Гарант\"}");
        assertThat(fields.stream().map(FieldValue::name)).doesNotContain("beneficiaryName", "beneficiaryInn");
    }

    @Test
    void registry_v5_hasNewFieldsAndVersion() {
        assertThat(type("PROPERTY_DOC").version()).isEqualTo("5");
        assertThat(type("PROPERTY_DOC").kinds()).containsEntry("addresses", "addresses")
                .containsEntry("tenant", "text");
        assertThat(type("GUARANTEE_LETTER").kinds()).containsKeys("guarantorInn", "beneficiaryInn", "beneficiaryName");
        assertThat(Map.of("PROPERTY_DOC", type("PROPERTY_DOC").schema().path("properties").has("addresses")))
                .containsEntry("PROPERTY_DOC", true);
    }
}
