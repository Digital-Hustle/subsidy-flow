package ru.subsidy.docproc.queue;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ru.subsidy.docproc.config.DocprocProperties;
import ru.subsidy.docproc.pipeline.PipelineRunner;

/**
 * Воркеры: забирают задания из jobs, держат аренду, пока работают; падение воркера → аренда истекает.
 */
@Component
public class WorkerPool implements DisposableBean {

    private final JobQueue queue;
    private final PipelineRunner runner;
    private final String workerId = "docproc-" + UUID.randomUUID();
    private final Semaphore slots;
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final boolean enabled;

    /**
     * Конструктор.
     *
     * @param queue очередь
     * @param runner машина стадий
     * @param props настройки
     * @param enabled включены ли воркеры (в тестах можно выключить)
     */
    public WorkerPool(JobQueue queue, PipelineRunner runner, DocprocProperties props,
                      @Value("${app.docproc.workers-enabled:true}") boolean enabled) {
        this.queue = queue;
        this.runner = runner;
        this.slots = new Semaphore(Math.max(1, props.workers()));
        this.enabled = enabled;
    }

    /**
     * Забирает задания, пока есть свободные слоты.
     */
    @Scheduled(fixedDelayString = "${app.docproc.poll-ms:1000}")
    public void poll() {
        while (enabled && slots.tryAcquire()) {
            Optional<Job> job = queue.claim(workerId);
            if (job.isEmpty()) {
                slots.release();
                return;
            }
            running.add(job.get().id());
            executor.submit(() -> {
                try {
                    runner.run(job.get());
                } finally {
                    running.remove(job.get().id());
                    slots.release();
                }
            });
        }
    }

    /**
     * Продлевает аренду работающих заданий.
     */
    @Scheduled(fixedDelayString = "${app.docproc.renew-ms:30000}")
    public void renew() {
        running.forEach(id -> queue.renew(id, workerId));
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
