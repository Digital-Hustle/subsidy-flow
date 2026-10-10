package ru.subsidy.package_.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import ru.subsidy.package_.staff.StaffService;
import ru.subsidy.package_.staff.StaffSettings;

/**
 * Вход сотрудников по коду из письма (ADR-023): настройки из окружения и заведение первых сотрудников при старте.
 */
@Configuration
public class StaffConfig {

    private static final int MAX_ATTEMPTS = 5;
    /** На демо-стенде повторный код — почти сразу: тестировщику не нужно ждать минуту. */
    private static final long DEMO_RESEND_SECONDS = 5;
    private static final long RESEND_SECONDS = 60;
    private static final long CODE_MINUTES = 10;
    private static final long SESSION_HOURS = 12;
    private static final long ACCESS_MINUTES = 5;
    private static final List<String> AUDIENCES = List.of("package", "docproc", "rules", "assistant");

    /**
     * Настройки: STAND_MODE (demo — демо-стенд), STAFF_* сроки и секреты, MAIL_FROM, адрес входа для писем.
     *
     * @param env окружение
     * @return настройки
     */
    @Bean
    public StaffSettings staffSettings(Environment env) {
        boolean demo = "demo".equalsIgnoreCase(env.getProperty("STAND_MODE", "prod").strip());
        long resend = env.getProperty("STAFF_RESEND_SECONDS", Long.class, RESEND_SECONDS);
        return new StaffSettings(demo,
                Duration.ofMinutes(env.getProperty("STAFF_CODE_MINUTES", Long.class, CODE_MINUTES)),
                Duration.ofSeconds(demo ? Math.min(resend, DEMO_RESEND_SECONDS) : resend),
                MAX_ATTEMPTS,
                Duration.ofHours(env.getProperty("STAFF_SESSION_HOURS", Long.class, SESSION_HOURS)),
                Duration.ofMinutes(env.getProperty("STAFF_ACCESS_MINUTES", Long.class, ACCESS_MINUTES)),
                env.getRequiredProperty("STAFF_CODE_PEPPER"),
                env.getRequiredProperty("app.staff-token.issuer"),
                AUDIENCES,
                env.getRequiredProperty("STAFF_TOKEN_SIGNING_KEY"),
                env.getProperty("STAFF_BOOTSTRAP_EMAIL", ""),
                env.getProperty("MAIL_FROM", "no-reply@digital-hustle.ru"),
                env.getProperty("STAFF_LOGIN_URL", "https://localhost/specialist"),
                env.getProperty("STAFF_SECURE_COOKIE", Boolean.class, true));
    }

    /**
     * Первые сотрудники при старте.
     *
     * @param staff сервис сотрудников
     * @return runner
     */
    @Bean
    public ApplicationRunner staffBootstrap(StaffService staff) {
        return args -> staff.bootstrap();
    }
}
