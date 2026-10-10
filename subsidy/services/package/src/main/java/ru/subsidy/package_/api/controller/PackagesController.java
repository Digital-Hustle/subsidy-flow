package ru.subsidy.package_.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.package_.pkg.DecisionService;
import ru.subsidy.package_.pkg.DecisionService.DecisionResult;
import ru.subsidy.package_.pkg.PackageCardService;
import ru.subsidy.package_.pkg.PackageCardService.PackageCard;
import ru.subsidy.package_.pkg.PackageQueryService;
import ru.subsidy.package_.pkg.PackageQueryService.QueueFilter;
import ru.subsidy.package_.pkg.PackageQueryService.QueuePage;
import ru.subsidy.package_.pkg.PackageService;
import ru.subsidy.package_.pkg.PackageService.PackageView;

/**
 * Пакеты документов (FR-01).
 */
@RestController
@RequestMapping("/api/v1/packages")
public class PackagesController {

    private static final int COMMENT_MAX = 2000;

    private final PackageService packages;
    private final PackageQueryService queue;
    private final PackageCardService cards;
    private final DecisionService decisions;

    /**
     * Конструктор.
     *
     * @param packages сервис пакетов
     * @param queue очередь пакетов
     * @param cards карточки пакетов
     * @param decisions решения по пакетам
     */
    public PackagesController(PackageService packages, PackageQueryService queue, PackageCardService cards,
                              DecisionService decisions) {
        this.packages = packages;
        this.queue = queue;
        this.cards = cards;
        this.decisions = decisions;
    }

    /**
     * Создаёт пакет.
     *
     * @param request ИНН и программа
     * @param idempotencyKey ключ идемпотентности
     * @param user текущий пользователь
     * @return пакет
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SPECIALIST','SENIOR_SPECIALIST')")
    public PackageResponse create(@Valid @RequestBody CreatePackageRequest request,
                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                  CurrentUser user) {
        return PackageResponse.of(packages.create(request.orgInn(), request.programCode(), idempotencyKey,
                user.userId()));
    }

    /**
     * Очередь пакетов (FR-61).
     *
     * @param status статус
     * @param inn ИНН заявителя
     * @param program код программы
     * @param cursor курсор из page.next
     * @param limit размер страницы (до 100)
     * @return страница очереди
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SPECIALIST','SENIOR_SPECIALIST','AUDITOR')")
    public QueuePage list(@RequestParam(required = false) String status, @RequestParam(required = false) String inn,
                          @RequestParam(required = false) String program,
                          @RequestParam(required = false) String cursor,
                          @RequestParam(required = false) Integer limit) {
        return queue.list(new QueueFilter(status, inn, program), cursor, limit);
    }

    /**
     * Карточка пакета: протокол, решение, файлы и документы.
     *
     * @param packageId id пакета
     * @return карточка
     */
    @GetMapping("/{packageId}")
    @PreAuthorize("hasAnyRole('SPECIALIST','SENIOR_SPECIALIST','AUDITOR')")
    public PackageCard get(@PathVariable UUID packageId) {
        return cards.card(packageId);
    }

    /**
     * Решение по пакету (только старший специалист, только после проверки).
     *
     * @param packageId id пакета
     * @param request решение и комментарий
     * @param user текущий пользователь
     * @return результат
     */
    @PostMapping("/{packageId}/decision")
    @PreAuthorize("hasRole('SENIOR_SPECIALIST')")
    public DecisionResult decide(@PathVariable UUID packageId, @Valid @RequestBody DecisionRequest request,
                                 CurrentUser user) {
        return decisions.decide(packageId, request.decision(), request.comment(), user.userId());
    }

    /**
     * Запрос решения.
     *
     * @param decision APPROVE, REJECT или RETURN
     * @param comment комментарий (обязателен для REJECT и RETURN)
     */
    public record DecisionRequest(@NotBlank @Pattern(regexp = "APPROVE|REJECT|RETURN") String decision,
                                  @Size(max = COMMENT_MAX) String comment) {
    }

    /**
     * Запрос создания пакета.
     *
     * @param orgInn ИНН заявителя
     * @param programCode программа
     */
    public record CreatePackageRequest(@NotBlank String orgInn, @NotBlank String programCode) {
    }

    /**
     * Ответ.
     *
     * @param packageId id
     * @param status статус
     * @param program программа
     * @param org организация
     */
    public record PackageResponse(UUID packageId, String status, String program, Org org) {

        static PackageResponse of(PackageView view) {
            return new PackageResponse(view.id(), view.status(), view.programCode(),
                    new Org(view.orgInn(), view.orgName()));
        }
    }

    /**
     * Организация в ответе.
     *
     * @param inn ИНН
     * @param name наименование
     */
    public record Org(String inn, String name) {
    }
}
