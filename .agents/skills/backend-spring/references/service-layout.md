# Структура сервиса и шаблоны

Пакет: `ru.proxodnaya.<svc>` (одно слово, без `_`). Пример — сервис `people`, фича `worker`.

```
ru/subsidy/people/
  PeopleApplication.java
  api/
    constants/PathConstants.java
    controller/WorkerController.java
    controller/impl/WorkerControllerImpl.java
    dto/request/CreateWorkerRq.java  dto/request/WorkerFilterRq.java
    dto/response/WorkerRs.java  dto/response/WorkersRs.java  dto/response/CreateWorkerRs.java
    mapper/WorkerApiMapper.java
  features/worker/
    command/model/CreateWorkerCommand.java
    command/service/WorkerCommandService.java   (+ impl/WorkerCommandServiceImpl.java)
    command/repository/WorkerCommandRepository.java
    command/validator/WorkerValidator.java
    query/model/WorkerView.java  query/model/WorkerFilter.java
    query/service/WorkerQueryService.java       (+ impl/)
    query/repository/WorkerQueryRepository.java
    application/service/WorkerOnboardingService.java (+ impl/)   # только если оркестрация
    api/WorkerStatusQuery.java  api/WorkerStatusSnapshot.java  # для других фич этого сервиса
    gateway/FaceGateway.java  gateway/impl/FaceHttpGateway.java
    model/WorkerStatus.java   constants/WorkerConstants.java
  messaging/outbox/  messaging/consumer/
  config/  config/properties/
  exception/BaseExceptionHandler.java  exception/DomainExceptionHandler.java  exception/custom/
  shared/
```
Интерфейс + `impl/` — для контроллеров, сервисов, gateway (как в эталонах: контракт отдельно, моки в тестах проще). Репозитории — классы.

## Контроллер
```java
@Tag(name = "Workers")
@RequestMapping(PathConstants.WORKERS)            // "/api/v1/workers"
public interface WorkerController {

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','SECURITY_OFFICER')")
    @Operation(summary = "Список рабочих")
    WorkersRs list(@Valid WorkerFilterRq filter);

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','SECURITY_OFFICER')")
    WorkerRs get(@PathVariable UUID id);

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    CreateWorkerRs create(@RequestBody @Valid CreateWorkerRq rq,
                          @AuthenticationPrincipal CurrentUser user);

    @PostMapping("/{id}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    void block(@PathVariable UUID id, @RequestBody @Valid BlockWorkerRq rq,
               @AuthenticationPrincipal CurrentUser user);
}

@RestController
@RequiredArgsConstructor
public class WorkerControllerImpl implements WorkerController {
    private final WorkerQueryService queryService;
    private final WorkerCommandService commandService;
    private final WorkerApiMapper mapper;

    @Override
    public WorkersRs list(WorkerFilterRq filter) {
        return mapper.convert(queryService.list(mapper.convert(filter)));
    }
    @Override
    public WorkerRs get(UUID id) { return mapper.convert(queryService.get(id)); }
    @Override
    public CreateWorkerRs create(CreateWorkerRq rq, CurrentUser user) {
        return mapper.convertCreated(commandService.create(mapper.convert(rq, user.id())));
    }
    @Override
    public void block(UUID id, BlockWorkerRq rq, CurrentUser user) {
        commandService.block(id, rq.reason(), user.id());
    }
}
```
`/internal/**` — отдельный интерфейс `WorkerInternalController` с `@PreAuthorize("hasAuthority('SCOPE_svc:people')")`, наружу через gateway не маршрутизируется.

## DTO
```java
public record CreateWorkerRq(
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Size(max = 100) String firstName,
        @Pattern(regexp = ValidationConstants.PHONE) String phone,
        @NotNull UUID siteId) { }

public record WorkerRs(UUID id, String fullName, WorkerStatus status, OffsetDateTime hiredAt) { }
public record WorkersRs(List<WorkerRs> workers, PageMetaRs page) { }
```
Сообщения валидации — коды (`{worker.lastName.required}` в `messages.properties`), не склейка строк.

## Mapper
```java
@Mapper(config = BaseMapperConfig.class)       // componentModel = SPRING, unmappedTargetPolicy = ERROR
public interface WorkerApiMapper {
    @Mapping(target = "createdBy", source = "actorId")
    CreateWorkerCommand convert(CreateWorkerRq rq, UUID actorId);
    WorkerFilter convert(WorkerFilterRq rq);
    WorkerRs convert(WorkerView view);
    WorkersRs convert(PageView<WorkerView> page);
    CreateWorkerRs convertCreated(UUID id);
}
```

## Command service
```java
@Service
@RequiredArgsConstructor
public class WorkerCommandServiceImpl implements WorkerCommandService {
    private final WorkerCommandRepository repository;
    private final WorkerValidator validator;
    private final Outbox outbox;
    private final Clock clock;

    @Override
    @Transactional
    public UUID create(CreateWorkerCommand cmd) {
        validator.validatePhoneUnique(cmd.phone());
        UUID id = repository.insert(cmd, OffsetDateTime.now(clock));
        outbox.add(Topics.PEOPLE_WORKER, id, new WorkerCreatedEvent(id, cmd.siteId()));
        return id;
    }

    @Override
    @Transactional
    public void block(UUID id, String reason, UUID actorId) {
        WorkerStatus current = repository.findStatusForUpdate(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.WORKER_NOT_FOUND, id));
        validator.validateCanBlock(current);
        repository.updateStatus(id, WorkerStatus.BLOCKED, reason, actorId);
        outbox.add(Topics.PEOPLE_WORKER, id, new WorkerBlockedEvent(id, reason));
    }
}
```

## Query repository (jOOQ)
```java
@Repository
@RequiredArgsConstructor
public class WorkerQueryRepository {
    private final DSLContext dsl;

    public PageView<WorkerView> find(WorkerFilter f) {
        Condition c = DSL.noCondition()
                .and(f.siteId() == null ? DSL.noCondition() : WORKERS.SITE_ID.eq(f.siteId()))
                .and(f.query() == null ? DSL.noCondition() : WORKERS.FULL_NAME.containsIgnoreCase(f.query()));
        Field<Integer> total = DSL.count().over().as("total");   // total окном, без второго запроса
        Result<Record5<UUID, String, String, OffsetDateTime, Integer>> rows = dsl
                .select(WORKERS.ID, WORKERS.FULL_NAME, WORKERS.STATUS, WORKERS.HIRED_AT, total)
                .from(WORKERS).where(c)
                .orderBy(WORKERS.FULL_NAME).limit(f.size()).offset(f.offset())
                .fetch();
        List<WorkerView> items = rows.map(r -> new WorkerView(
                r.value1(), r.value2(), WorkerStatus.valueOf(r.value3()), r.value4()));
        long totalCount = rows.isEmpty() ? 0 : rows.get(0).value5();
        return PageView.of(items, totalCount, f);
    }
}
```

## Gateway (внешняя система)
```java
public interface FaceGateway {
    FaceMatch match(UUID workerId, byte[] jpeg);          // бросает FaceUnavailableException
}
@Component
@RequiredArgsConstructor
class FaceHttpGateway implements FaceGateway {
    private final RestClient faceClient;                 // бин в config: baseUrl, таймауты 1.5 с, service-токен
    @Override
    @CircuitBreaker(name = "face")
    public FaceMatch match(UUID workerId, byte[] jpeg) {
        try {
            return faceClient.post().uri("/internal/v1/match/{id}", workerId)
                    .contentType(MediaType.IMAGE_JPEG).body(jpeg)
                    .retrieve().body(FaceMatch.class);
        } catch (RestClientException e) {
            throw new FaceUnavailableException(e);       // наружу — только наше исключение
        }
    }
}
```

## Исключения и ответ об ошибке
```java
public enum ErrorCode {                                  // в libs/common + свои в сервисе
    WORKER_NOT_FOUND(HttpStatus.NOT_FOUND, "Рабочий не найден"),
    QR_REUSED(HttpStatus.CONFLICT, "Этот QR уже использован — обновите код на телефоне");
    ...
}
public class DomainException extends RuntimeException { private final ErrorCode code; private final Object[] args; }
public class NotFoundException extends DomainException { ... }
```
`DomainExceptionHandler` → `ProblemDetail` (`type`, `title`, `status`, `detail` = messageRu, `code`, `traceId`, `errors` для валидации). `BaseExceptionHandler` — валидация, 401/403, нечитаемое тело, `Exception` → 500 без деталей.

## CurrentUser
`CurrentUser(UUID id, Set<Role> roles, Kind kind /*STAFF|WORKER_DEVICE|SERVICE*/)` собирается `JwtCurrentUserConverter` из JWT (Keycloak или device-issuer) в `libs/common`. ThreadLocal и доверие к `X-User-Id` от gateway — запрещены: каждый сервис сам валидирует JWT.
