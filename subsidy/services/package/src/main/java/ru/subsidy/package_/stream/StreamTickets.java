package ru.subsidy.package_.stream;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Одноразовые билеты SSE на 30 с: EventSource не умеет слать Authorization (API.md).
 */
@Component
public class StreamTickets {

    /** Время жизни билета. */
    public static final Duration TTL = Duration.ofSeconds(30);
    private static final int TOKEN_BYTES = 24;

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    /**
     * Конструктор.
     *
     * @param clock часы
     */
    public StreamTickets(Clock clock) {
        this.clock = clock;
    }

    /**
     * Выдаёт билет.
     *
     * @param userId пользователь
     * @return билет
     */
    public String issue(String userId) {
        Instant now = clock.instant();
        tickets.values().removeIf(ticket -> ticket.expiresAt().isBefore(now));
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(token, new Ticket(userId, now.plus(TTL)));
        return token;
    }

    /**
     * Гасит билет.
     *
     * @param token билет
     * @return пользователь, если билет действителен
     */
    public Optional<String> redeem(String token) {
        Ticket ticket = token == null ? null : tickets.remove(token);
        if (ticket == null || ticket.expiresAt().isBefore(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(ticket.userId());
    }

    private record Ticket(String userId, Instant expiresAt) {
    }
}
