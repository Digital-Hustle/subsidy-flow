package ru.subsidy.rules.stats;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.subsidy.rules.config.RulesProperties;
import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.facts.DocFacts;
import ru.subsidy.rules.facts.FactsAssembler;
import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.jooq.Tables;

/**
 * Истекающие документы (бонус кейса): сертификат соответствия и декларация ГИМС по пакетам без решения.
 */
@Service
public class ExpiringService {

    private static final String VALID_UNTIL = "validUntil";
    private static final List<String> TYPES = List.of(Docs.CERTIFICATE, Docs.GIMS_DECLARATION);

    private final DSLContext dsl;
    private final FactsAssembler assembler;
    private final Clock clock;
    private final RulesProperties props;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param assembler факты
     * @param clock часы
     * @param props настройки
     */
    public ExpiringService(DSLContext dsl, FactsAssembler assembler, Clock clock,
                           RulesProperties props) {
        this.dsl = dsl;
        this.assembler = assembler;
        this.clock = clock;
        this.props = props;
    }

    /**
     * Документы, срок которых истекает в ближайшие days дней (или уже истёк).
     *
     * @param days горизонт или null (по умолчанию из настроек)
     * @return по возрастанию оставшихся дней
     */
    @Transactional(readOnly = true)
    public List<Expiring> expiring(Integer days) {
        int horizon = days == null ? props.expiringDays() : days;
        LocalDate today = LocalDate.now(clock.withZone(props.zone()));
        List<Expiring> out = new ArrayList<>();
        List<UUID> open = dsl.select(Tables.PACKAGE_VIEW.PACKAGE_ID).from(Tables.PACKAGE_VIEW)
                .where(Tables.PACKAGE_VIEW.STATUS.ne("DECIDED")).fetch(Tables.PACKAGE_VIEW.PACKAGE_ID);
        for (UUID packageId : open) {
            assembler.assemble(packageId).ifPresent(facts -> collect(facts, today, horizon, out));
        }
        out.sort(Comparator.comparingLong(Expiring::daysLeft));
        return out;
    }

    private static void collect(PackageFacts facts, LocalDate today, int horizon, List<Expiring> out) {
        for (String type : TYPES) {
            for (DocFacts doc : facts.ofType(type)) {
                Optional<LocalDate> until = doc.date(VALID_UNTIL);
                if (until.isPresent()) {
                    long left = ChronoUnit.DAYS.between(today, until.get());
                    if (left <= horizon) {
                        out.add(new Expiring(facts.packageId(), facts.inn(), doc.documentId(), type,
                                Docs.title(type), doc.date("issueDate").orElse(null), until.get(), left));
                    }
                }
            }
        }
    }

    /**
     * Истекающий документ.
     *
     * @param packageId пакет
     * @param inn ИНН организации
     * @param documentId документ
     * @param type вид
     * @param title название
     * @param date дата документа
     * @param expiresAt истекает
     * @param daysLeft дней осталось (меньше нуля — истёк)
     */
    public record Expiring(UUID packageId, String inn, UUID documentId, String type, String title, LocalDate date,
                           LocalDate expiresAt, long daysLeft) {
    }
}
