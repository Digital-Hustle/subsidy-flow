package ru.subsidy.package_.api.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.package_.applicant.ApplicantAccess;
import ru.subsidy.package_.applicant.ApplicantAccess.Access;
import ru.subsidy.package_.applicant.ApplicantPrograms;
import ru.subsidy.package_.applicant.ApplicantPrograms.ProgramItem;
import ru.subsidy.package_.applicant.ApplicantService;
import ru.subsidy.package_.applicant.ApplicantService.Saved;
import ru.subsidy.package_.applicant.ApplicantService.Submitted;
import ru.subsidy.package_.applicant.ApplicantService.Uploaded;
import ru.subsidy.package_.applicant.ApplicantViews.ApplicantApplication;
import ru.subsidy.package_.pkg.ApplicationData;
import ru.subsidy.package_.stream.StreamHub;
import ru.subsidy.package_.stream.StreamTickets;
import ru.subsidy.package_.upload.UploadService.IncomingFile;

/**
 * Кабинет заявителя (без входа сотрудника): доступ по {@code Authorization: Applicant <accessKey>}.
 */
@RestController
@RequestMapping("/api/v1/applicant")
public class ApplicantController {

    private static final String ID = "/applications/{id}";
    private static final int CONTACT_MAX = 254;
    private static final String TICKET_PREFIX = "applicant:";

    private final ApplicantService service;
    private final ApplicantAccess access;
    private final ApplicantPrograms programs;
    private final StreamTickets tickets;
    private final StreamHub hub;

    /**
     * Конструктор.
     *
     * @param service сценарии
     * @param access доступ
     * @param programs программы
     * @param tickets билеты SSE
     * @param hub подписки SSE
     */
    public ApplicantController(ApplicantService service, ApplicantAccess access, ApplicantPrograms programs,
                               StreamTickets tickets, StreamHub hub) {
        this.service = service;
        this.access = access;
        this.programs = programs;
        this.tickets = tickets;
        this.hub = hub;
    }

    /**
     * Открытые программы.
     *
     * @return программы
     */
    @GetMapping("/programs")
    public Items<ProgramItem> programs() {
        return new Items<>(programs.open());
    }

    /**
     * Новая заявка.
     *
     * @param request согласие, программа, контакты
     * @param idempotencyKey ключ идемпотентности
     * @return доступ (один раз)
     */
    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public Access create(@Valid @RequestBody CreateRequest request,
                         @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return service.create(Boolean.TRUE.equals(request.consentPd()), request.programCode(),
                request.contact().email(), request.contact().phone(), idempotencyKey);
    }

    /**
     * Возобновление по коду.
     *
     * @param request почта или телефон и код
     * @return новый доступ
     */
    @PostMapping("/resume")
    public Access resume(@Valid @RequestBody ResumeRequest request) {
        return access.resume(request.phoneOrEmail(), request.resumeCode());
    }

    /**
     * Заявка.
     *
     * @param id заявка
     * @param key ключ
     * @return заявка
     */
    @GetMapping(ID)
    public ApplicantApplication get(@PathVariable UUID id, @RequestHeader(value = HttpHeaders.AUTHORIZATION,
            required = false) String key) {
        return service.get(id, key);
    }

    /**
     * Сохраняет анкету.
     *
     * @param id заявка
     * @param key ключ
     * @param request ревизия и анкета
     * @return ревизия и чек-лист
     */
    @PutMapping(ID + "/application")
    public Saved saveApplication(@PathVariable UUID id,
                                 @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String key,
                                 @Valid @RequestBody SaveRequest request) {
        return service.saveApplication(id, key, request.expectedRevision(), request.application());
    }

    /**
     * Загружает документ.
     *
     * @param id заявка
     * @param key ключ
     * @param file файл
     * @param slotType пункт чек-листа
     * @return документ
     */
    @PostMapping(value = ID + "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Uploaded upload(@PathVariable UUID id,
                           @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String key,
                           @RequestPart("file") MultipartFile file,
                           @RequestParam(required = false) String slotType) {
        return service.upload(id, key, incoming(file), slotType);
    }

    /**
     * Добавляет или заменяет файл документа.
     *
     * @param id заявка
     * @param key ключ
     * @param documentId документ
     * @param file файл
     * @param mode add или replace
     * @return документ
     */
    @PostMapping(value = ID + "/documents/{documentId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Uploaded addFile(@PathVariable UUID id,
                            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String key,
                            @PathVariable UUID documentId, @RequestPart("file") MultipartFile file,
                            @RequestParam(defaultValue = "add") String mode) {
        return service.addFile(id, key, documentId, incoming(file), "replace".equals(mode));
    }

    /**
     * Удаляет документ.
     *
     * @param id заявка
     * @param key ключ
     * @param documentId документ
     */
    @DeleteMapping(ID + "/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDocument(@PathVariable UUID id,
                               @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String key,
                               @PathVariable UUID documentId) {
        service.removeDocument(id, key, documentId);
    }

    /**
     * Удаляет черновик и отзывает согласие.
     *
     * @param id заявка
     * @param key ключ
     */
    @DeleteMapping(ID)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id,
                       @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String key) {
        service.delete(id, key);
    }

    /**
     * Отправляет заявку специалисту.
     *
     * @param id заявка
     * @param key ключ
     * @return статус
     */
    @PostMapping(ID + "/submit")
    public Submitted submit(@PathVariable UUID id,
                            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String key) {
        return service.submit(id, key);
    }

    /**
     * Билет потока событий.
     *
     * @param id заявка
     * @param key ключ
     * @return билет
     */
    @GetMapping(ID + "/stream/ticket")
    public Ticket ticket(@PathVariable UUID id,
                         @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String key) {
        access.authorize(id, key);
        return new Ticket(tickets.issue(TICKET_PREFIX + id));
    }

    /**
     * Поток событий заявки (SSE).
     *
     * @param id заявка
     * @param ticket билет
     * @return emitter
     * @throws DomainException FORBIDDEN — билет чужой или истёк
     */
    @GetMapping(value = ID + "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID id, @RequestParam(required = false) String ticket) {
        String owner = tickets.redeem(ticket).orElse(null);
        if (!(TICKET_PREFIX + id).equals(owner)) {
            throw new DomainException(ErrorCode.FORBIDDEN, "Билет потока недействителен или истёк");
        }
        return hub.subscribe(id);
    }

    private static IncomingFile incoming(MultipartFile file) {
        try {
            return new IncomingFile(file.getOriginalFilename(), file.getBytes());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * Список.
     *
     * @param items элементы
     * @param <T> тип
     */
    public record Items<T>(List<T> items) {
    }

    /**
     * Контакты заявителя.
     *
     * @param email почта
     * @param phone телефон
     */
    public record Contact(@NotBlank @Email @Size(max = CONTACT_MAX) String email,
                          @Pattern(regexp = "\\+?[0-9 ()-]{10,20}") String phone) {
    }

    /**
     * Создание заявки.
     *
     * @param consentPd согласие на обработку персональных данных
     * @param programCode программа
     * @param contact контакты
     */
    public record CreateRequest(Boolean consentPd, @NotBlank @Size(max = CONTACT_MAX) String programCode,
                                @NotNull @Valid Contact contact) {
    }

    /**
     * Возобновление.
     *
     * @param phoneOrEmail почта или телефон
     * @param resumeCode код
     */
    public record ResumeRequest(@NotBlank @Size(max = CONTACT_MAX) String phoneOrEmail,
                                @NotBlank @Size(max = CONTACT_MAX) String resumeCode) {
    }

    /**
     * Сохранение анкеты.
     *
     * @param expectedRevision ожидаемая ревизия
     * @param application анкета
     */
    public record SaveRequest(@PositiveOrZero int expectedRevision, @NotNull @Valid ApplicationData application) {
    }

    /**
     * Билет SSE.
     *
     * @param ticket билет
     */
    public record Ticket(String ticket) {
    }
}
