# Данные и события

## База
- Database-per-service в одном PostgreSQL 16: БД `<svc>` и роль `<svc>` (владелец только своей БД, `CONNECT` к чужим отозван). Схема `public`, preliquibase не нужен.
- Журнал проходов (access) — только дозапись: триггер `BEFORE UPDATE OR DELETE ON pass_attempts … RAISE EXCEPTION` в changeset. Работает независимо от роли и проверяется integration-тестом.
- Деньги не храним; время — `timestamptz` + `OffsetDateTime`; id — `uuid` (генерирует приложение, `UuidCreator.getTimeOrderedEpoch()` / UUIDv7), enum — `text` + `CHECK`.

## Liquibase
```
src/main/resources/db/changelog/
  db.changelog-master.yaml
  changesets/20261010093000-create-workers.sql
  changesets/20261010101500-seed-demo-workers.sql      # context:dev
```
```yaml
# db.changelog-master.yaml
databaseChangeLog:
  - includeAll:
      path: changesets/
      relativeToChangelogFile: true
      errorIfMissingOrEmpty: true
```
```sql
--liquibase formatted sql
--changeset b1:20261010093000-create-workers
create table workers (
    id         uuid primary key,
    full_name  text        not null,
    phone      text        not null unique,
    status     text        not null check (status in ('ACTIVE','BLOCKED','DISMISSED')),
    site_id    uuid        not null,
    hired_at   timestamptz not null,
    version    int         not null default 0
);
create index workers_site_idx on workers (site_id);
--rollback drop table workers;
```
- Создавать только `scripts/new-migration.sh <svc> <slug>`: метка времени в имени → порядок `includeAll` детерминирован и параллельные PR не конфликтуют (в эталоне `V1__`/`V3_` вперемешку ломали сортировку).
- Changeset после мержа неизменяем. Исправление — новый changeset.
- Демо-данные — `--changeset b1:... context:dev`, в проде `spring.liquibase.contexts=prod`.

## jOOQ
- Плагин `nu.studer.jooq` в convention-плагине `proxodnaya.jooq`. Генерация из живой БД после `./gradlew :<svc>:update` (Liquibase-плагин) — локально Postgres из `infra/docker-compose.yml`, в CI — service container (см. `build.md`).
- Пакет генерации `ru.proxodnaya.<svc>.jooq`, выход `build/generated-src/jooq`, в git не коммитим, руками не правим.
- Схема поменялась → `./gradlew :<svc>:update :<svc>:generateJooq`. Компиляция упала — это и есть проверка контракта с БД.
- `DSLContext` только в `*Repository`. Наверх — модели фичи.
- Блокировка под изменение — `selectFrom(...).where(...).forUpdate()`; оптимистичная — колонка `version`.
- Атомарное гашение одноразового QR (access): уникальный ключ `(device_id, window)` + `ON CONFLICT DO NOTHING` — гонку решает БД:
```java
int n = dsl.insertInto(QR_USES)
        .set(QR_USES.DEVICE_ID, deviceId).set(QR_USES.WINDOW, window).set(QR_USES.USED_AT, now)
        .onConflictDoNothing()
        .execute();
if (n == 0) throw new DomainException(ErrorCode.QR_REUSED);
```

## Транзакции
- `@Transactional` — на application/command-методе, который должен быть атомарным; `readOnly = true` — на query-сервисах.
- Внутри транзакции нет HTTP/LLM/face-вызовов и `Thread.sleep`. Порядок: собрать данные из внешних систем → открыть короткую транзакцию → записать + outbox.
- `spring.jpa`/`open-in-view` нет (JPA не используем). Пул Hikari 10 на сервис.

## Kafka
- Один брокер `apache/kafka:3.9` в KRaft-режиме (без ZooKeeper), `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`; топики создаёт `infra/kafka/topics.sh` (partitions 3, rf 1).
- Имя топика `<сервис-владелец>.<агрегат>.v<версия>`: `people.worker.v1`, `access.pass.v1`, `shift.shift.v1`. Ключ сообщения — id агрегата. Значение — JSON-конверт:
```json
{ "eventId": "uuid", "type": "PassGranted", "occurredAt": "2026-10-10T09:30:00Z", "aggregateId": "uuid",
  "traceparent": "00-…", "payload": { } }
```
- Схема каждого `type` — `contracts/events/<topic>/<Type>.schema.json`; тест в `libs/common` валидирует примеры.
- Константы топиков — `libs/common/.../messaging/Topics.java`.

### Transactional outbox (продюсер)
```sql
create table outbox (
    id           uuid primary key,
    topic        text        not null,
    message_key  text        not null,
    payload      jsonb       not null,
    created_at   timestamptz not null,
    published_at timestamptz
);
create index outbox_unpublished_idx on outbox (created_at) where published_at is null;
```
- `Outbox.add(topic, key, event)` — INSERT в текущей транзакции (`Propagation.MANDATORY`: без транзакции падает).
- `OutboxRelay` (`@Scheduled(fixedDelay = 200 ms)`, ShedLock не нужен — один инстанс): `select … where published_at is null order by created_at limit 100 for update skip locked` → `kafkaTemplate.send(...).get(2 s)` → `published_at = now()`. Продюсер `acks=all`, `enable.idempotence=true`.
- Доставка at-least-once → потребитель обязан быть идемпотентным.

### Потребитель
```java
@KafkaListener(topics = Topics.ACCESS_PASS, groupId = "shift")
public void on(EventEnvelope e) {
    idempotentConsumer.once(e.eventId(), () -> shiftCommandService.applyPass(mapper.convert(e)));
}
```
- `IdempotentConsumer.once` — `insert into processed_events(event_id) on conflict do nothing` + действие в одной транзакции.
- Ошибки: `DefaultErrorHandler` с 3 повторами (backoff 1 с) → `<topic>.dlt`. В DLT смотрит владелец; авто-повтор из DLT — только руками.
- `spring.kafka.consumer.auto-offset-reset=earliest`, `enable-auto-commit=false` (ack после обработки).

## Синхронные вызовы между сервисами
`RestClient` из `libs/common` (`ServiceClients.create(baseUrl, audience)`): service-токен Keycloak (client credentials), таймаут connect 500 мс / read 1,5 с, Resilience4j circuit breaker, `traceparent` пробрасывается автоматически. В пути прохода любая ошибка → `DENY SYSTEM_ERROR` (fail-closed).
