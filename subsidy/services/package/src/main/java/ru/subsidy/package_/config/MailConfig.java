package ru.subsidy.package_.config;

import java.util.Properties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * SMTP общего почтового ящика кейсов (корневой MAIL.md). Настройки — из окружения, а не из application.yml:
 * пароль ящика приходит отдельным файлом стенда ({@code infra/mail.env}) и в репозитории его нет.
 * Без {@code SMTP_HOST} отправитель создаётся «пустым» и письма не уходят — {@link ru.subsidy.package_.staff.StaffMailer}
 * это проверяет и на демо-стенде показывает код в интерфейсе.
 */
@Configuration
public class MailConfig {

    private static final int DEFAULT_PORT = 465;
    private static final String TIMEOUT_MS = "10000";
    private static final String CONNECT_TIMEOUT_MS = "5000";

    /**
     * Отправитель писем.
     *
     * @param env окружение (SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD, SMTP_PROTOCOL, SMTP_STARTTLS)
     * @return отправитель
     */
    @Bean
    public JavaMailSender javaMailSender(Environment env) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(env.getProperty("SMTP_HOST", ""));
        sender.setPort(env.getProperty("SMTP_PORT", Integer.class, DEFAULT_PORT));
        sender.setUsername(env.getProperty("SMTP_USERNAME", ""));
        sender.setPassword(env.getProperty("SMTP_PASSWORD", ""));
        sender.setProtocol(env.getProperty("SMTP_PROTOCOL", "smtps"));
        sender.setDefaultEncoding("UTF-8");
        Properties props = sender.getJavaMailProperties();
        // свойства читаются по имени протокола: для smtps — mail.smtps.*, иначе таймауты не действуют и запрос висит минутами
        for (String prefix : new String[] {"mail.smtp.", "mail.smtps."}) {
            props.put(prefix + "auth", "true");
            props.put(prefix + "starttls.enable", env.getProperty("SMTP_STARTTLS", "false"));
            props.put(prefix + "connectiontimeout", CONNECT_TIMEOUT_MS);
            props.put(prefix + "timeout", TIMEOUT_MS);
            props.put(prefix + "writetimeout", TIMEOUT_MS);
        }
        return sender;
    }
}
