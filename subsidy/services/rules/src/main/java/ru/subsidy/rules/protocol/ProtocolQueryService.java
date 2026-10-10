package ru.subsidy.rules.protocol;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.subsidy.common.exception.DomainException;
import ru.subsidy.common.exception.ErrorCode;
import ru.subsidy.rules.jooq.Tables;
import ru.subsidy.rules.jooq.tables.records.ProtocolsRecord;

/**
 * Чтение протоколов: последний, версии, конкретная версия.
 */
@Service
public class ProtocolQueryService {

    private final DSLContext dsl;
    private final ObjectMapper json;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param json Jackson
     */
    public ProtocolQueryService(DSLContext dsl, ObjectMapper json) {
        this.dsl = dsl;
        this.json = json;
    }

    /**
     * Последний протокол пакета.
     *
     * @param packageId пакет
     * @return протокол
     * @throws DomainException PACKAGE_NOT_FOUND или NOT_CHECKED_YET (идёт разбор)
     */
    @Transactional(readOnly = true)
    public ProtocolView latest(UUID packageId) {
        return find(packageId, null).orElseThrow(() -> notChecked(packageId));
    }

    /**
     * Последний протокол, если есть.
     *
     * @param packageId пакет
     * @return протокол или пусто
     */
    @Transactional(readOnly = true)
    public Optional<ProtocolView> findLatest(UUID packageId) {
        return find(packageId, null);
    }

    /**
     * Версия протокола.
     *
     * @param packageId пакет
     * @param version версия
     * @return протокол
     * @throws DomainException PROTOCOL_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public ProtocolView version(UUID packageId, int version) {
        return find(packageId, version).orElseThrow(() -> new DomainException(ErrorCode.PROTOCOL_NOT_FOUND,
                "Нет версии протокола " + version));
    }

    /**
     * Все версии протокола пакета.
     *
     * @param packageId пакет
     * @return версии, новые сверху
     */
    @Transactional(readOnly = true)
    public List<ProtocolView.Summary> versions(UUID packageId) {
        return dsl.selectFrom(Tables.PROTOCOLS).where(Tables.PROTOCOLS.PACKAGE_ID.eq(packageId))
                .orderBy(Tables.PROTOCOLS.VERSION.desc())
                .fetch(rec -> new ProtocolView.Summary(rec.getId(), rec.getVersion(), rec.getStatus(),
                        rec.getCompletenessPct(), rec.getFailed(), rec.getWarnings(), rec.getNeedsReview(),
                        rec.getCreatedAt()));
    }

    private Optional<ProtocolView> find(UUID packageId, Integer version) {
        var query = dsl.selectFrom(Tables.PROTOCOLS).where(Tables.PROTOCOLS.PACKAGE_ID.eq(packageId));
        if (version != null) {
            query = query.and(Tables.PROTOCOLS.VERSION.eq(version));
        }
        return query.orderBy(Tables.PROTOCOLS.VERSION.desc()).limit(1).fetchOptional().map(this::toView);
    }

    private ProtocolView toView(ProtocolsRecord rec) {
        List<ProtocolView.Result> results = dsl.selectFrom(Tables.RULE_RESULTS)
                .where(Tables.RULE_RESULTS.PROTOCOL_ID.eq(rec.getId())).orderBy(Tables.RULE_RESULTS.POSITION)
                .fetch(this::toResult);
        return new ProtocolView(rec.getId(), rec.getPackageId(), rec.getVersion(), rec.getStatus(),
                rec.getProgramCode(), rec.getProgramVersion(), rec.getProgramVersion(), rec.getRulesVersion(),
                read(rec.getCompleteness().data()), results, rec.getFailed(), rec.getWarnings(), rec.getNeedsReview(),
                rec.getCreatedAt());
    }

    private ProtocolView.Result toResult(Record rec) {
        return new ProtocolView.Result(rec.get(Tables.RULE_RESULTS.RULE), rec.get(Tables.RULE_RESULTS.STATUS),
                rec.get(Tables.RULE_RESULTS.MESSAGE), read(rec.get(Tables.RULE_RESULTS.EVIDENCE).data()),
                rec.get(Tables.RULE_RESULTS.EXPECTED), rec.get(Tables.RULE_RESULTS.ACTUAL));
    }

    private DomainException notChecked(UUID packageId) {
        boolean known = dsl.fetchExists(Tables.PACKAGE_VIEW, Tables.PACKAGE_VIEW.PACKAGE_ID.eq(packageId))
                || dsl.fetchExists(Tables.PACKAGE_FILES, Tables.PACKAGE_FILES.PACKAGE_ID.eq(packageId));
        return known ? new DomainException(ErrorCode.NOT_CHECKED_YET, "Проверка ещё не готова — идёт разбор документов")
                : new DomainException(ErrorCode.PACKAGE_NOT_FOUND, "Пакет не найден");
    }

    private JsonNode read(String text) {
        try {
            return json.readTree(text);
        } catch (JsonProcessingException broken) {
            throw new IllegalStateException("protocol: битый JSON", broken);
        }
    }
}
