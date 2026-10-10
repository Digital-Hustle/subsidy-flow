package ru.subsidy.package_.staff;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;

import jakarta.annotation.PreDestroy;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Письма сотрудникам через общий ящик кейсов ({@code SMTP_*}, {@code MAIL_FROM}, см. корневой MAIL.md): HTML в стиле
 * интерфейса и текстовая версия. Без {@code SMTP_HOST} письма не уходят; код пишется в лог только при
 * {@code STAFF_LOG_CODES=true} (локальная разработка). На демо-стенде код и так виден в интерфейсе.
 */
@Component
public class StaffMailer {

    private static final Logger LOG = LoggerFactory.getLogger(StaffMailer.class);
    private static final String LAYOUT = "mail/layout.html";
    private static final int CODE_HALF = 3;
    private static final String HELLO = "Здравствуйте, ";
    private static final String INVITED = "Вас пригласили в «Субсидии»";
    private static final long[] RETRY_PAUSES_MS = {3_000, 15_000, 60_000};

    private final ObjectProvider<JavaMailSender> sender;
    private final StaffSettings settings;
    private final String host;
    private final boolean logCodes;
    private final ExecutorService outbox = Executors.newFixedThreadPool(2, Thread.ofVirtual().name("mail-", 0).factory());

    /**
     * Конструктор.
     *
     * @param sender   SMTP (config/MailConfig)
     * @param settings настройки входа
     * @param host     SMTP_HOST
     * @param logCodes STAFF_LOG_CODES
     */
    public StaffMailer(ObjectProvider<JavaMailSender> sender, StaffSettings settings, @Value("${SMTP_HOST:}") String host,
                       @Value("${STAFF_LOG_CODES:false}") boolean logCodes) {
        this.sender = sender;
        this.settings = settings;
        this.host = host;
        this.logCodes = logCodes;
    }

    /**
     * Код входа.
     *
     * @param email   адрес
     * @param name    имя сотрудника
     * @param code    шесть цифр
     * @param minutes срок действия
     */
    public void loginCode(String email, String name, String code, long minutes) {
        String spaced = code.substring(0, CODE_HALF) + " " + code.substring(CODE_HALF);
        String text = HELLO + name + "!\n\nКод для входа в «Субсидии»: " + spaced + "\n\n"
                + "Код действует " + minutes + " мин. Никому его не сообщайте — сотрудники поддержки его не спрашивают.\n"
                + "Если вы не запрашивали вход, просто проигнорируйте письмо.\n\n" + settings.loginUrl();
        String html = layout(new Letter("Код для входа: " + spaced, "Код для входа",
                HELLO + HtmlUtils.htmlEscape(name) + "! Введите этот код на странице входа в «Субсидии».",
                "<div style=\"margin:24px 0;padding:20px;border-radius:16px;background:#EEF6F1;text-align:center;"
                        + "font:600 36px/1 'SB Sans Display',Arial,sans-serif;letter-spacing:8px;color:#107F3B\">" + spaced + "</div>"
                        + "<p style=\"margin:0;color:#5C6B60\">Код действует " + minutes + " мин. Никому его не сообщайте — "
                        + "сотрудники поддержки его не спрашивают.</p>",
                "Открыть страницу входа", "Если вы не запрашивали вход, просто проигнорируйте письмо — без кода в систему не войти."));
        if (!send(email, "Код входа: " + spaced, text, html) && logCodes) {
            LOG.warn("SMTP не настроен, код входа для {}: {}", email, code);
        }
    }

    /**
     * Приглашение.
     *
     * @param email     адрес
     * @param name      имя
     * @param role      название роли
     * @param invitedBy кто пригласил
     */
    public void invitation(String email, String name, String role, String invitedBy) {
        String text = HELLO + name + "!\n\n" + invitedBy + " пригласил(а) вас в «Субсидии» с ролью «" + role + "».\n"
                + "Войдите по адресу " + settings.loginUrl() + " — введите эту почту, и мы пришлём код. Пароль не нужен.";
        String html = layout(new Letter(INVITED, INVITED,
                HtmlUtils.htmlEscape(invitedBy) + " открыл(а) вам доступ к проверке пакетов документов на субсидию.",
                "<p style=\"margin:24px 0 0;padding:16px 20px;border-radius:16px;background:#EEF6F1;color:#172419\">Роль: <b>"
                        + HtmlUtils.htmlEscape(role) + "</b></p><p style=\"margin:16px 0 0;color:#5C6B60\">Пароль не нужен: на странице "
                        + "входа введите эту почту — мы пришлём одноразовый код.</p>",
                "Войти", "Если вы не ждали приглашения, просто проигнорируйте письмо."));
        send(email, "Приглашение в «Субсидии»", text, html);
    }

    private String layout(Letter letter) {
        String html;
        try {
            html = new ClassPathResource(LAYOUT).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return html.replace("{{url}}", HtmlUtils.htmlEscape(settings.loginUrl()))
                .replace("{{preheader}}", letter.preheader())
                .replace("{{title}}", letter.title())
                .replace("{{lead}}", letter.lead())
                .replace("{{body}}", letter.body())
                .replace("{{action}}", letter.action())
                .replace("{{note}}", letter.note());
    }

    /**
     * Ставит письмо в очередь отправки и сразу возвращается: SMTP не держит HTTP-запрос и транзакцию с блокировками.
     * В транзакции письмо уходит только после коммита. Неудача — повтор с паузой, итог — в лог.
     *
     * @param email   адрес
     * @param subject тема
     * @param text    текст
     * @param html    HTML
     * @return false — SMTP не настроен, письмо не отправляется
     */
    private boolean send(String email, String subject, String text, String html) {
        JavaMailSender mail = sender.getIfAvailable();
        if (host.isBlank() || mail == null) {
            LOG.warn("SMTP не настроен (SMTP_HOST) — письмо «{}» не отправлено", masked(subject));
            return false;
        }
        Runnable task = () -> outbox.execute(() -> deliver(mail, email, subject, text, html));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
        return true;
    }

    private void deliver(JavaMailSender mail, String email, String subject, String text, String html) {
        String safe = masked(subject);
        boolean done = false;
        for (int attempt = 1; !done && attempt <= RETRY_PAUSES_MS.length + 1; attempt++) {
            try {
                MimeMessage message = mail.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
                helper.setFrom(settings.mailFrom(), "Субсидии");
                helper.setTo(email);
                helper.setSubject(subject);
                helper.setText(text, html);
                mail.send(message);
                LOG.info("Письмо «{}» отправлено (попытка {})", safe, attempt);
                done = true;
            } catch (MailException | MessagingException | java.io.UnsupportedEncodingException error) {
                LOG.warn("Письмо «{}» не отправлено (попытка {}): {}", safe, attempt, error.getMessage());
                if (attempt > RETRY_PAUSES_MS.length || !pause(RETRY_PAUSES_MS[attempt - 1])) {
                    LOG.error("Письмо «{}» так и не ушло — проверьте SMTP_* в infra/mail.env", safe);
                    done = true;
                }
            }
        }
    }

    private static String masked(String subject) {
        return subject.replaceAll("\\d", "•");
    }

    private static boolean pause(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Остановка приложения: письма в очереди дожидаются не дольше паузы повтора. */
    @PreDestroy
    void close() {
        outbox.shutdown();
    }

    /**
     * Части письма для шаблона mail/layout.html (HTML уже экранирован).
     *
     * @param preheader строка превью в почтовом клиенте
     * @param title     заголовок
     * @param lead      первый абзац
     * @param body      основной блок
     * @param action    текст кнопки
     * @param note      примечание под письмом
     */
    private record Letter(String preheader, String title, String lead, String body, String action, String note) {
    }
}
