package ru.subsidy.assistant.integrations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.assistant.config.AbstractIntegrationTest;
import ru.subsidy.assistant.rules.RulesClient;
import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.llm.api.LlmGateway;
import ru.subsidy.llm.api.LlmResult;
import ru.subsidy.llm.api.ModelUnavailableException;

class AskIntegrationTest extends AbstractIntegrationTest {

    private static final String FACTS = """
            {"facts":{"packageId":"%s","inn":"7707083893","programCode":"INNOVATION","documents":[
              {"documentId":"d1","type":"APPLICATION","fromPage":1,"toPage":1,
               "fields":{"requestedAmount":{"value":"1500000.00","source":"MODEL","confidence":0.95}}}],"files":[]},
             "protocol":{"version":1,"status":"INCOMPLETE","failed":1,"warnings":0,"needsReview":0,
              "completeness":{"percent":80,"missing":[{"type":"TAX","reason":"нет справки"}],"items":[]},
              "results":[{"rule":"SUM_SUBSIDY_CAP","status":"FAILED","message":"превышен максимум",
                "expected":"1200000.00","actual":"1500000.00"}]}}
            """;

    @MockitoBean
    private RulesClient rules;

    @MockitoBean
    private LlmGateway llm;

    private static CurrentUser user(String role) {
        Jwt token = Jwt.withTokenValue("test").header("alg", "none")
                .subject("user-" + role).claim("roles", List.of(role)).build();
        return new CurrentUser(token, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private LlmResult reply(String body) throws Exception {
        return new LlmResult(objectMapper.readTree(body), "test", "test-model", 1, null, null, 1);
    }

    private UUID givenPackage() throws Exception {
        UUID packageId = UUID.randomUUID();
        JsonNode facts = objectMapper.readTree(FACTS.formatted(packageId));
        when(rules.facts(eq(packageId))).thenReturn(facts);
        return packageId;
    }

    private String ask(String question) {
        return "{\"question\":\"" + question + "\"}";
    }

    @Test
    void ask_callsTool_thenAnswers_validatorOk_andHistorySaved() throws Exception {
        UUID packageId = givenPackage();
        when(llm.structured(any())).thenReturn(
                reply("{\"action\":\"tool\",\"tool\":\"get_protocol\"}"),
                reply("{\"action\":\"answer\",\"answer\":\"Запрошено 1 500 000,00 руб., максимум 1 200 000,00 руб.\","
                        + "\"sources\":[{\"rule\":\"SUM_SUBSIDY_CAP\"}]}"));
        mockMvc.perform(post("/api/v1/packages/" + packageId + "/ask").with(authentication(user("SPECIALIST")))
                        .contentType(MediaType.APPLICATION_JSON).content(ask("Почему не проходит сумма?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validator.status").value("OK"))
                .andExpect(jsonPath("$.tools[0]").value("get_protocol"))
                .andExpect(jsonPath("$.sources[0].rule").value("SUM_SUBSIDY_CAP"));
        mockMvc.perform(get("/api/v1/packages/" + packageId + "/messages").with(authentication(user("SPECIALIST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].role").value("USER"))
                .andExpect(jsonPath("$.items[1].validatorStatus").value("OK"));
    }

    @Test
    void inventedNumber_flagged() throws Exception {
        UUID packageId = givenPackage();
        when(llm.structured(any())).thenReturn(
                reply("{\"action\":\"tool\",\"tool\":\"get_fields\",\"argument\":\"application\"}"),
                reply("{\"action\":\"answer\",\"answer\":\"К выплате 750 000 руб.\"}"));
        mockMvc.perform(post("/api/v1/packages/" + packageId + "/ask").with(authentication(user("AUDITOR")))
                        .contentType(MediaType.APPLICATION_JSON).content(ask("Сколько выплатим?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validator.status").value("NUMBERS_MISMATCH"))
                .andExpect(jsonPath("$.validator.details[0]").value("750000"));
    }

    @Test
    void toolLimit_forcesAnswer() throws Exception {
        UUID packageId = givenPackage();
        when(llm.structured(any())).thenReturn(
                reply("{\"action\":\"tool\",\"tool\":\"get_missing\"}"),
                reply("{\"action\":\"tool\",\"tool\":\"get_missing\"}"),
                reply("{\"action\":\"tool\",\"tool\":\"get_missing\"}"),
                reply("{\"action\":\"tool\",\"tool\":\"get_missing\"}"),
                reply("{\"action\":\"answer\",\"answer\":\"Не хватает справки об отсутствии долгов (TAX).\"}"));
        mockMvc.perform(post("/api/v1/packages/" + packageId + "/ask").with(authentication(user("SPECIALIST")))
                        .contentType(MediaType.APPLICATION_JSON).content(ask("Чего не хватает?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tools.length()").value(4));
    }

    @Test
    void foreignPackage_404_modelDown_503_noRole_403_blank_400() throws Exception {
        UUID foreign = UUID.randomUUID();
        when(rules.facts(eq(foreign))).thenThrow(new DomainException(ErrorCode.PACKAGE_NOT_FOUND, "нет"));
        mockMvc.perform(post("/api/v1/packages/" + foreign + "/ask").with(authentication(user("SPECIALIST")))
                        .contentType(MediaType.APPLICATION_JSON).content(ask("Что с пакетом?")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PACKAGE_NOT_FOUND"));

        UUID packageId = givenPackage();
        when(llm.structured(any())).thenThrow(new ModelUnavailableException("down", null));
        mockMvc.perform(post("/api/v1/packages/" + packageId + "/ask").with(authentication(user("SPECIALIST")))
                        .contentType(MediaType.APPLICATION_JSON).content(ask("Что с пакетом?")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MODEL_UNAVAILABLE"));

        mockMvc.perform(post("/api/v1/packages/" + packageId + "/ask").with(authentication(user("APPLICANT")))
                        .contentType(MediaType.APPLICATION_JSON).content(ask("Что с пакетом?")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/packages/" + packageId + "/ask").with(authentication(user("SPECIALIST")))
                        .contentType(MediaType.APPLICATION_JSON).content(ask(" ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void suggestions_fromProtocol() throws Exception {
        UUID packageId = givenPackage();
        mockMvc.perform(get("/api/v1/packages/" + packageId + "/suggestions")
                        .with(authentication(user("SPECIALIST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0]").value("Какие проверки не пройдены и почему?"))
                .andExpect(jsonPath("$.items[1]").value("Каких документов не хватает и что запросить у организации?"));
    }
}
