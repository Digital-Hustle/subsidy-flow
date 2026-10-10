package ru.subsidy.docproc.queue;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.github.f4b6a3.uuid.UuidCreator;

import ru.subsidy.docproc.config.DocprocProperties;
import ru.subsidy.docproc.jooq.Tables;

/**
 * Очередь jobs в PostgreSQL: захват FOR UPDATE SKIP LOCKED + аренда, переживает рестарт (rules.md §1.6).
 */
@Component
public class JobQueue {

    private final DSLContext dsl;
    private final Clock clock;
    private final DocprocProperties props;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param clock часы
     * @param props настройки
     */
    public JobQueue(DSLContext dsl, Clock clock, DocprocProperties props) {
        this.dsl = dsl;
        this.clock = clock;
        this.props = props;
    }

    /**
     * Захватывает готовое задание.
     *
     * @param workerId воркер
     * @return задание или пусто
     */
    @Transactional
    public Optional<Job> claim(String workerId) {
        final OffsetDateTime now = OffsetDateTime.now(clock);
        Optional<UUID> ready = dsl.select(Tables.JOBS.ID).from(Tables.JOBS)
                .where(Tables.JOBS.STAGE.notIn(Stage.FINAL))
                .and(Tables.JOBS.NEXT_RUN_AT.le(now))
                .and(Tables.JOBS.LEASE_UNTIL.isNull().or(Tables.JOBS.LEASE_UNTIL.lt(now)))
                .orderBy(Tables.JOBS.NEXT_RUN_AT)
                .limit(1)
                .forUpdate().skipLocked()
                .fetchOptional(Tables.JOBS.ID);
        return ready.map(id -> dsl.update(Tables.JOBS)
                .set(Tables.JOBS.LEASE_UNTIL, now.plus(props.lease()))
                .set(Tables.JOBS.WORKER_ID, workerId)
                .where(Tables.JOBS.ID.eq(id))
                .returning()
                .fetchOne(JobQueue::toJob));
    }

    /**
     * Продлевает аренду своего задания.
     *
     * @param jobId задание
     * @param workerId воркер
     */
    @Transactional
    public void renew(UUID jobId, String workerId) {
        dsl.update(Tables.JOBS)
                .set(Tables.JOBS.LEASE_UNTIL, OffsetDateTime.now(clock).plus(props.lease()))
                .where(Tables.JOBS.ID.eq(jobId)).and(Tables.JOBS.WORKER_ID.eq(workerId))
                .execute();
    }

    /**
     * Ставит файл в очередь (или переставляет на новую версию).
     *
     * @param fileId файл
     * @param packageId пакет
     * @param version версия
     * @param mime тип
     * @return true, если задание создано или обновлено
     */
    @Transactional
    public boolean enqueue(UUID fileId, UUID packageId, int version, String mime) {
        final OffsetDateTime now = OffsetDateTime.now(clock);
        return dsl.insertInto(Tables.JOBS)
                .set(Tables.JOBS.ID, UuidCreator.getTimeOrderedEpoch())
                .set(Tables.JOBS.FILE_ID, fileId)
                .set(Tables.JOBS.PACKAGE_ID, packageId)
                .set(Tables.JOBS.VERSION, version)
                .set(Tables.JOBS.MIME, mime)
                .set(Tables.JOBS.STAGE, Stage.QUEUED)
                .set(Tables.JOBS.NEXT_RUN_AT, now)
                .set(Tables.JOBS.CREATED_AT, now)
                .set(Tables.JOBS.UPDATED_AT, now)
                .onConflict(Tables.JOBS.FILE_ID).doUpdate()
                .set(Tables.JOBS.VERSION, version)
                .set(Tables.JOBS.MIME, mime)
                .set(Tables.JOBS.STAGE, Stage.QUEUED)
                .set(Tables.JOBS.ATTEMPTS, 0)
                .set(Tables.JOBS.NEXT_RUN_AT, now)
                .set(Tables.JOBS.LEASE_UNTIL, (OffsetDateTime) null)
                .set(Tables.JOBS.LAST_ERROR_CODE, (String) null)
                .set(Tables.JOBS.UPDATED_AT, now)
                .where(Tables.JOBS.VERSION.le(version))
                .execute() > 0;
    }

    /**
     * Переразбор текущей версии с начала.
     *
     * @param fileId файл
     * @return true, если задание найдено
     */
    @Transactional
    public boolean restart(UUID fileId) {
        final OffsetDateTime now = OffsetDateTime.now(clock);
        return dsl.update(Tables.JOBS)
                .set(Tables.JOBS.STAGE, Stage.QUEUED)
                .set(Tables.JOBS.ATTEMPTS, 0)
                .set(Tables.JOBS.NEXT_RUN_AT, now)
                .set(Tables.JOBS.LEASE_UNTIL, (OffsetDateTime) null)
                .set(Tables.JOBS.UPDATED_AT, now)
                .where(Tables.JOBS.FILE_ID.eq(fileId)).and(Tables.JOBS.STAGE.ne(Stage.REMOVED))
                .execute() > 0;
    }

    /**
     * Возобновляет задание с указанной стадии (смена вида специалистом).
     *
     * @param fileId файл
     * @param stage стадия
     * @return true, если задание найдено
     */
    @Transactional
    public boolean resume(UUID fileId, String stage) {
        final OffsetDateTime now = OffsetDateTime.now(clock);
        return dsl.update(Tables.JOBS)
                .set(Tables.JOBS.STAGE, stage)
                .set(Tables.JOBS.ATTEMPTS, 0)
                .set(Tables.JOBS.NEXT_RUN_AT, now)
                .set(Tables.JOBS.UPDATED_AT, now)
                .where(Tables.JOBS.FILE_ID.eq(fileId)).and(Tables.JOBS.STAGE.ne(Stage.REMOVED))
                .execute() > 0;
    }

    /**
     * Задание файла.
     *
     * @param fileId файл
     * @return задание
     */
    @Transactional(readOnly = true)
    public Optional<Job> byFile(UUID fileId) {
        return dsl.selectFrom(Tables.JOBS).where(Tables.JOBS.FILE_ID.eq(fileId)).fetchOptional(JobQueue::toJob);
    }

    /**
     * Переводит задание на стадию (в транзакции вызывающего), если версия файла не сменилась.
     *
     * @param job задание
     * @param stage стадия
     * @param errorCode код ошибки или null
     * @return true, если задание актуально
     */
    public boolean moveTo(Job job, String stage, String errorCode) {
        final OffsetDateTime now = OffsetDateTime.now(clock);
        var update = dsl.update(Tables.JOBS)
                .set(Tables.JOBS.STAGE, stage)
                .set(Tables.JOBS.LAST_ERROR_CODE, errorCode)
                .set(Tables.JOBS.UPDATED_AT, now);
        if (Stage.FINAL.contains(stage)) {
            update = update.set(Tables.JOBS.LEASE_UNTIL, (OffsetDateTime) null);
        }
        return update.where(Tables.JOBS.ID.eq(job.id())).and(Tables.JOBS.VERSION.eq(job.version()))
                .execute() == 1;
    }

    /**
     * Откладывает задание.
     *
     * @param job задание
     * @param delay пауза
     * @param countAttempt тратить ли попытку
     * @param errorCode код
     */
    @Transactional
    public void postpone(Job job, Duration delay, boolean countAttempt, String errorCode) {
        dsl.update(Tables.JOBS)
                .set(Tables.JOBS.NEXT_RUN_AT, OffsetDateTime.now(clock).plus(delay))
                .set(Tables.JOBS.LEASE_UNTIL, (OffsetDateTime) null)
                .set(Tables.JOBS.ATTEMPTS, countAttempt ? Tables.JOBS.ATTEMPTS.plus(1) : Tables.JOBS.ATTEMPTS)
                .set(Tables.JOBS.LAST_ERROR_CODE, errorCode)
                .set(Tables.JOBS.UPDATED_AT, OffsetDateTime.now(clock))
                .where(Tables.JOBS.ID.eq(job.id())).and(Tables.JOBS.VERSION.eq(job.version()))
                .execute();
    }

    /**
     * Позиция в очереди (для подсказки «в очереди, позиция N»).
     *
     * @param job задание
     * @return число заданий впереди
     */
    @Transactional(readOnly = true)
    public int position(Job job) {
        return dsl.fetchCount(dsl.selectOne().from(Tables.JOBS)
                .where(Tables.JOBS.STAGE.notIn(Stage.FINAL))
                .and(Tables.JOBS.CREATED_AT.lt(DSL.select(Tables.JOBS.CREATED_AT).from(Tables.JOBS)
                        .where(Tables.JOBS.ID.eq(job.id())))));
    }

    private static Job toJob(Record rec) {
        return new Job(rec.get(Tables.JOBS.ID), rec.get(Tables.JOBS.FILE_ID), rec.get(Tables.JOBS.PACKAGE_ID),
                rec.get(Tables.JOBS.VERSION), rec.get(Tables.JOBS.MIME), rec.get(Tables.JOBS.STAGE),
                rec.get(Tables.JOBS.ATTEMPTS));
    }
}
