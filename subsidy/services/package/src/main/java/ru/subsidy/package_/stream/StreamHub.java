package ru.subsidy.package_.stream;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ru.subsidy.package_.pipeline.PackageUpdate;

/**
 * Подписки SSE на пакет: стадии файлов, разобранные документы, готовый протокол (ADR-012).
 */
@Component
public class StreamHub {

    private static final long TIMEOUT_MINUTES = 30;
    private static final long TIMEOUT_MS = Duration.ofMinutes(TIMEOUT_MINUTES).toMillis();

    private final Map<UUID, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    /**
     * Подписка на пакет.
     *
     * @param packageId пакет
     * @return emitter
     */
    public SseEmitter subscribe(UUID packageId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Set<SseEmitter> set = subscribers.computeIfAbsent(packageId, id -> ConcurrentHashMap.newKeySet());
        set.add(emitter);
        Runnable remove = () -> set.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(failure -> remove.run());
        return emitter;
    }

    /**
     * Рассылает изменение подписчикам пакета.
     *
     * @param eventId id события (SSE id)
     * @param update изменение
     */
    public void publish(String eventId, PackageUpdate update) {
        Set<SseEmitter> set = subscribers.get(update.packageId());
        if (set == null) {
            return;
        }
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().id(eventId).name(update.event()).data(update.data()));
            } catch (IOException | IllegalStateException gone) {
                set.remove(emitter);
            }
        }
    }

    /**
     * Число активных подписчиков пакета.
     *
     * @param packageId пакет
     * @return число подписчиков
     */
    public int subscribers(UUID packageId) {
        Set<SseEmitter> set = subscribers.get(packageId);
        return set == null ? 0 : set.size();
    }

    /** Heartbeat каждые 15 с, чтобы прокси не рвали соединение. */
    @Scheduled(fixedRateString = "${app.stream.heartbeat-ms:15000}")
    public void heartbeat() {
        subscribers.values().forEach(set -> set.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException gone) {
                set.remove(emitter);
            }
        }));
    }
}
