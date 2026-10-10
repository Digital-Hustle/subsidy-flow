package ru.subsidy.rules.recheck;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import ru.subsidy.rules.projection.ProjectionRebuild;
import ru.subsidy.rules.protocol.ProtocolService;

/**
 * Забирает созревшие запросы перепроверки (несколько инстансов — SKIP LOCKED) и строит протокол.
 */
@Component
@ConditionalOnProperty(name = "app.rules.recheck-enabled", havingValue = "true", matchIfMissing = true)
public class RecheckWorker {

    private static final Logger LOG = LoggerFactory.getLogger(RecheckWorker.class);
    private static final int BATCH = 20;
    private static final Duration RETRY = Duration.ofSeconds(30);

    private final RecheckRequests requests;
    private final ProtocolService protocols;
    private final TransactionTemplate transactions;
    private final ProjectionRebuild rebuild;

    /**
     * Конструктор.
     *
     * @param requests запросы
     * @param protocols протоколы
     * @param transactions транзакции
     * @param rebuild пересборка проекции, если событие разбора потеряно
     */
    public RecheckWorker(RecheckRequests requests, ProtocolService protocols, TransactionTemplate transactions,
                         ProjectionRebuild rebuild) {
        this.requests = requests;
        this.protocols = protocols;
        this.transactions = transactions;
        this.rebuild = rebuild;
    }

    /**
     * Цикл перепроверок.
     */
    @Scheduled(fixedDelayString = "${app.rules.recheck-poll:500ms}")
    public void poll() {
        for (int idx = 0; idx < BATCH; idx++) {
            Optional<UUID> done = once();
            if (done.isEmpty()) {
                break;
            }
        }
    }

    /**
     * Одна перепроверка в своей транзакции.
     *
     * @return обработанный пакет или пусто
     */
    public Optional<UUID> once() {
        Optional<UUID> due = transactions.execute(status -> requests.claimDue());
        if (due != null) {
            due.ifPresent(this::checkOrRetry);
        }
        return due == null ? Optional.empty() : due;
    }

    private void checkOrRetry(UUID packageId) {
        try {
            rebuild.rebuildIfIncomplete(packageId);
            protocols.check(packageId);
        } catch (RuntimeException failure) {
            LOG.warn("recheck failed package={}: {}", packageId, failure.getClass().getSimpleName(), failure);
            transactions.executeWithoutResult(status -> requests.requestAfter(packageId, RETRY));
        }
    }
}
