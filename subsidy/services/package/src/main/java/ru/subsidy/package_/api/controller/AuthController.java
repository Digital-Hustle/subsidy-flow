package ru.subsidy.package_.api.controller;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.package_.staff.CodeSent;
import ru.subsidy.package_.staff.StaffLoginService;
import ru.subsidy.package_.staff.StaffService;
import ru.subsidy.package_.staff.StaffSession;
import ru.subsidy.package_.staff.StaffSettings;
import ru.subsidy.package_.staff.StaffUser;

/**
 * Вход сотрудника по коду из письма (FR-60, ADR-023). code/verify/refresh/logout/demo — без токена. Refresh живёт
 * в httpOnly-cookie: страница переживает перезагрузку, а скрипт токен сессии не видит.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /** Cookie сессии. */
    public static final String COOKIE = "subsidy_session";
    private static final String COOKIE_PATH = "/package/api/v1/auth";

    private final StaffLoginService login;
    private final StaffService staff;
    private final StaffSettings settings;

    /**
     * Конструктор.
     *
     * @param login    вход
     * @param staff    сотрудники
     * @param settings настройки
     */
    public AuthController(StaffLoginService login, StaffService staff, StaffSettings settings) {
        this.login = login;
        this.staff = staff;
        this.settings = settings;
    }

    /**
     * Режим стенда и демо-сотрудники для быстрого входа (только демо-стенд).
     *
     * @return режим
     */
    @GetMapping("/mode")
    public ModeRs mode() {
        return new ModeRs(settings.demo() ? "demo" : "prod", staff.demoUsers().stream().map(UserRs::of).toList(),
                settings.demo() ? StaffSettings.DEMO_CODE : null);
    }

    /**
     * Отправляет 6-значный код. Ответ одинаковый для любого адреса.
     *
     * @param request почта
     * @return маска адреса и сроки; 429 LOGIN_RESEND_TOO_SOON
     */
    @PostMapping("/code")
    public CodeSentRs code(@Valid @RequestBody CodeRq request) {
        CodeSent sent = login.requestCode(request.email());
        return new CodeSentRs(sent.sentTo(), sent.expiresAt(), sent.resendAt(), sent.demoCode());
    }

    /**
     * Проверяет код и открывает сессию.
     *
     * @param request  почта и код
     * @param response ответ: сюда ставим cookie сессии
     * @return access-токен и сотрудник, refresh — в cookie; 400 LOGIN_CODE_INVALID, 410 LOGIN_CODE_EXPIRED, 429 LOGIN_CODE_LOCKED
     */
    @PostMapping("/verify")
    public SessionRs verify(@Valid @RequestBody VerifyRq request, HttpServletResponse response) {
        return withCookie(login.verify(request.email(), request.code()), response);
    }

    /**
     * Продлевает сессию по cookie (refresh одноразовый).
     *
     * @param refresh  cookie сессии
     * @param response ответ: сюда ставим новую cookie
     * @return новые токены; 401 SESSION_EXPIRED
     */
    @PostMapping("/refresh")
    public SessionRs refresh(@CookieValue(name = COOKIE, required = false) String refresh, HttpServletResponse response) {
        return withCookie(login.refresh(refresh), response);
    }

    /**
     * Выход: закрывает сессию и стирает cookie.
     *
     * @param refresh  cookie сессии
     * @param response ответ: стираем cookie
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = COOKIE, required = false) String refresh, HttpServletResponse response) {
        login.logout(refresh);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    /**
     * Текущий сотрудник.
     *
     * @param user токен
     * @return сотрудник; 401 SESSION_EXPIRED
     */
    @GetMapping("/me")
    public UserRs me(CurrentUser user) {
        return UserRs.of(staff.me(UUID.fromString(user.userId())));
    }

    private SessionRs withCookie(StaffSession session, HttpServletResponse response) {
        Duration left = Duration.ofMillis(Math.max(0, session.sessionExpiresAt() - System.currentTimeMillis()));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(session.refreshToken(), left).toString());
        return new SessionRs(session.accessToken(), session.expiresAt(), session.sessionExpiresAt(), UserRs.of(session.user()));
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(settings.secureCookie()).sameSite("Strict").path(COOKIE_PATH)
                .maxAge(maxAge).build();
    }

    /**
     * Запрос кода.
     *
     * @param email почта
     */
    public record CodeRq(@NotBlank @Email String email) {
    }

    /**
     * Проверка кода.
     *
     * @param email почта
     * @param code  шесть цифр
     */
    public record VerifyRq(@NotBlank @Email String email, @NotBlank @Pattern(regexp = "[\\d\\s-]{6,9}") String code) {
    }

    /**
     * Код отправлен.
     *
     * @param sentTo    маска адреса
     * @param expiresAt код действует до, Unix ms
     * @param resendAt  повторно не раньше, Unix ms
     * @param demoCode  код — только на демо-стенде
     */
    public record CodeSentRs(String sentTo, long expiresAt, long resendAt, String demoCode) {
    }

    /**
     * Сессия.
     *
     * @param accessToken      JWT для сервисов
     * @param expiresAt        access истекает, Unix ms
     * @param sessionExpiresAt сессия истекает, Unix ms
     * @param user             сотрудник
     */
    public record SessionRs(String accessToken, long expiresAt, long sessionExpiresAt, UserRs user) {
    }

    /**
     * Режим стенда.
     *
     * @param mode     demo или prod
     * @param demo     демо-сотрудники (пусто на боевом стенде)
     * @param demoCode общий код демо-стенда или null
     */
    public record ModeRs(String mode, List<UserRs> demo, String demoCode) {
    }

    /**
     * Сотрудник.
     *
     * @param id       идентификатор
     * @param name     имя
     * @param email    почта
     * @param role     роль
     * @param status   статус
     * @param demo     демо-сотрудник
     * @param lastSeen последний вход, Unix ms, или null
     */
    public record UserRs(UUID id, String name, String email, String role, String status, boolean demo, Long lastSeen) {

        /**
         * Из модели.
         *
         * @param user сотрудник
         * @return ответ
         */
        public static UserRs of(StaffUser user) {
            return new UserRs(user.id(), user.name(), user.email(), user.role().name(), user.status().name(), user.demo(),
                    user.lastSeen() == null ? null : user.lastSeen().toEpochMilli());
        }
    }
}
