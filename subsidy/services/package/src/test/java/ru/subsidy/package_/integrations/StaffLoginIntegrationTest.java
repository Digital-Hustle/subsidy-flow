package ru.subsidy.package_.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.package_.api.controller.AuthController;
import ru.subsidy.package_.config.AbstractIntegrationTest;
import ru.subsidy.package_.jooq.Tables;
import ru.subsidy.package_.staff.StaffMailer;

/**
 * Вход сотрудника по коду из письма и управление сотрудниками (FR-60, ADR-023), боевой режим стенда.
 */
@TestPropertySource(properties = {"STAFF_BOOTSTRAP_EMAIL=root@subsidy.test", "STAFF_TOKEN_ISSUER=https://subsidy.test/package"})
class StaffLoginIntegrationTest extends AbstractIntegrationTest {

    private static final String ROOT = "root@subsidy.test";
    private static final String CODE = "/api/v1/auth/code";
    private static final String VERIFY = "/api/v1/auth/verify";
    private static final String REFRESH = "/api/v1/auth/refresh";
    private static final String STAFF = "/api/v1/staff";

    @MockitoSpyBean
    private StaffMailer mailer;

    @Autowired
    private DSLContext dsl;

    @Test
    void bootstrapSenior_logsInByCode_getsToken_cookieRotates_logoutEnds() throws Exception {
        // given
        JsonNode sent = requestCode(ROOT);
        String code = mailedCode(ROOT);

        // when
        MockHttpServletResponse response = login(ROOT, code);
        JsonNode session = body(response);

        // then
        assertThat(sent.path("sentTo").asText()).isEqualTo("roo•••••@subsidy.test");
        assertThat(sent.path("demoCode").isNull()).isTrue();
        assertThat(session.path("user").path("role").asText()).isEqualTo("SENIOR_SPECIALIST");
        assertThat(session.path("user").path("status").asText()).isEqualTo("ACTIVE");
        assertThat(session.has("refreshToken")).isFalse();
        JWTClaimsSet claims = SignedJWT.parse(session.path("accessToken").asText()).getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo("https://subsidy.test/package");
        assertThat(claims.getAudience()).contains("package", "docproc", "rules", "assistant");
        assertThat(claims.getStringListClaim("roles")).isEqualTo(List.of("SENIOR_SPECIALIST"));
        assertThat(claims.getClaim("azp")).isNull();
        Cookie cookie = response.getCookie(AuthController.COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();

        Cookie next = mockMvc.perform(post(REFRESH).cookie(cookie)).andExpect(status().isOk()).andReturn().getResponse()
                .getCookie(AuthController.COOKIE);
        mockMvc.perform(post(REFRESH).cookie(cookie)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        mockMvc.perform(post("/api/v1/auth/logout").cookie(next)).andExpect(status().isNoContent());
        mockMvc.perform(post(REFRESH).cookie(next)).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongCode_countsAttempts_thenLocks_resendTooSoon_andExpiry() throws Exception {
        // given
        String email = invite("Специалист Тест", "SPECIALIST");
        requestCode(email);
        mailedCode(email);

        // when / then: в боевом режиме общий демо-код не работает
        mockMvc.perform(json(post(CODE), Map.of("email", email)))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("LOGIN_RESEND_TOO_SOON"));
        mockMvc.perform(json(post(VERIFY), Map.of("email", email, "code", "000000")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LOGIN_CODE_INVALID"))
                .andExpect(jsonPath("$.detail").value("Неверный код. Осталось попыток: 4"));
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(json(post(VERIFY), Map.of("email", email, "code", "000000"))).andExpect(status().isBadRequest());
        }
        mockMvc.perform(json(post(VERIFY), Map.of("email", email, "code", "000000")))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("LOGIN_CODE_LOCKED"));

        dsl.update(Tables.STAFF_LOGIN_CODES).set(Tables.STAFF_LOGIN_CODES.RESEND_AT, OffsetDateTime.now().minusMinutes(1))
                .where(Tables.STAFF_LOGIN_CODES.EMAIL.eq(email)).execute();
        requestCode(email);
        String code = mailedCode(email);
        dsl.update(Tables.STAFF_LOGIN_CODES).set(Tables.STAFF_LOGIN_CODES.EXPIRES_AT, OffsetDateTime.now().minusSeconds(1))
                .where(Tables.STAFF_LOGIN_CODES.EMAIL.eq(email)).execute();
        mockMvc.perform(json(post(VERIFY), Map.of("email", email, "code", code)))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("LOGIN_CODE_EXPIRED"));
    }

    @Test
    void unknownEmail_sameAnswer_noMail_noLogin_andNoDemoUsers() throws Exception {
        // given
        String ghost = "ghost-" + UUID.randomUUID().toString().substring(0, 6) + "@subsidy.test";

        // when
        JsonNode sent = requestCode(ghost);

        // then
        assertThat(sent.path("sentTo").asText()).endsWith("@subsidy.test");
        verify(mailer, never()).loginCode(eq(ghost), anyString(), anyString(), anyLong());
        mockMvc.perform(json(post(VERIFY), Map.of("email", ghost, "code", "123456")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LOGIN_CODE_INVALID"));
        mockMvc.perform(get("/api/v1/auth/mode")).andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("prod")).andExpect(jsonPath("$.demo").isEmpty())
                .andExpect(jsonPath("$.demoCode").doesNotExist());
    }

    @Test
    void senior_invites_changesRole_disables_lastSeniorProtected_rolesEnforced() throws Exception {
        // given
        String email = invite("Аудитор Тест", "AUDITOR");
        final String id = staffId(email);
        verify(mailer).invitation(eq(email), eq("Аудитор Тест"), eq("Аудитор"), anyString());
        mockMvc.perform(as("SENIOR_SPECIALIST", post(STAFF)).content(objectMapper.writeValueAsString(Map.of("name", "Дубль", "email",
                email.toUpperCase(), "role", "AUDITOR")))).andExpect(status().isConflict());
        requestCode(email);
        Cookie cookie = login(email, mailedCode(email)).getCookie(AuthController.COOKIE);

        // when / then: новая роль — в следующем токене
        mockMvc.perform(as("SENIOR_SPECIALIST", patch(STAFF + "/" + id)).content("{\"role\":\"SPECIALIST\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("SPECIALIST"));
        MockHttpServletResponse refreshed = mockMvc.perform(post(REFRESH).cookie(cookie)).andExpect(status().isOk())
                .andReturn().getResponse();
        assertThat(SignedJWT.parse(body(refreshed).path("accessToken").asText()).getJWTClaimsSet().getStringListClaim("roles"))
                .isEqualTo(List.of("SPECIALIST"));

        mockMvc.perform(as("SENIOR_SPECIALIST", patch(STAFF + "/" + id)).content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
        mockMvc.perform(post(REFRESH).cookie(refreshed.getCookie(AuthController.COOKIE))).andExpect(status().isUnauthorized());

        mockMvc.perform(as("SPECIALIST", get(STAFF))).andExpect(status().isForbidden());
        mockMvc.perform(as("AUDITOR", get(STAFF))).andExpect(status().isForbidden());
        mockMvc.perform(get(STAFF)).andExpect(status().isUnauthorized());
        String root = staffId(ROOT);
        mockMvc.perform(as("SENIOR_SPECIALIST", patch(STAFF + "/" + root)).content("{\"role\":\"AUDITOR\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STAFF_LAST_SENIOR"));
    }

    private String invite(String name, String role) throws Exception {
        String email = "user-" + UUID.randomUUID().toString().substring(0, 6) + "@subsidy.test";
        mockMvc.perform(as("SENIOR_SPECIALIST", post(STAFF)).content(objectMapper.writeValueAsString(Map.of("name", name, "email", email,
                "role", role)))).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("INVITED"));
        return email;
    }

    private String staffId(String email) throws Exception {
        JsonNode list = body(mockMvc.perform(as("SENIOR_SPECIALIST", get(STAFF))).andExpect(status().isOk()).andReturn().getResponse());
        for (JsonNode user : list.path("items")) {
            if (user.path("email").asText().equals(email)) {
                return user.path("id").asText();
            }
        }
        throw new AssertionError("нет сотрудника " + email);
    }

    private JsonNode requestCode(String email) throws Exception {
        return body(mockMvc.perform(json(post(CODE), Map.of("email", email))).andExpect(status().isOk()).andReturn().getResponse());
    }

    private String mailedCode(String email) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailer, org.mockito.Mockito.atLeastOnce()).loginCode(eq(email), anyString(), code.capture(), anyLong());
        return code.getValue();
    }

    private MockHttpServletResponse login(String email, String code) throws Exception {
        return mockMvc.perform(json(post(VERIFY), Map.of("email", email, "code", code))).andExpect(status().isOk())
                .andReturn().getResponse();
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Map<String, String> body) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    private MockHttpServletRequestBuilder as(String role, MockHttpServletRequestBuilder request) {
        Jwt token = Jwt.withTokenValue("test").header("alg", "none").subject(UUID.randomUUID().toString())
                .claim("name", "Старший Тест").claim("roles", List.of(role)).build();
        return request.with(authentication(new CurrentUser(token, List.of(new SimpleGrantedAuthority("ROLE_" + role)))))
                .contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString());
    }
}
