package ru.subsidy.package_.api.controller;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.package_.api.dto.response.StreamTicketRs;
import ru.subsidy.package_.stream.StreamHub;
import ru.subsidy.package_.stream.StreamTickets;

/**
 * SSE пакета: билет по JWT, поток — по одноразовому билету (пути совпадают с gateway).
 */
@RestController
public class StreamController {

    private final StreamTickets tickets;
    private final StreamHub hub;

    /**
     * Конструктор.
     *
     * @param tickets билеты
     * @param hub подписки
     */
    public StreamController(StreamTickets tickets, StreamHub hub) {
        this.tickets = tickets;
        this.hub = hub;
    }

    /**
     * Выдаёт билет на 30 с.
     *
     * @param user текущий пользователь
     * @return билет
     */
    @GetMapping("/api/v1/stream/ticket")
    public StreamTicketRs ticket(CurrentUser user) {
        return new StreamTicketRs(tickets.issue(user.userId()), StreamTickets.TTL.toSeconds());
    }

    /**
     * Поток событий пакета: file.stage, document.parsed, package.checked.
     *
     * @param packageId пакет
     * @param ticket одноразовый билет
     * @return SSE
     * @throws DomainException билет недействителен
     */
    @GetMapping(value = "/api/v1/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam UUID packageId, @RequestParam(required = false) String ticket) {
        tickets.redeem(ticket).orElseThrow(() -> new DomainException(ErrorCode.FORBIDDEN,
                "Билет потока недействителен или истёк"));
        return hub.subscribe(packageId);
    }
}
