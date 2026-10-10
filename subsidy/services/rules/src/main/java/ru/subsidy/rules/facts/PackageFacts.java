package ru.subsidy.rules.facts;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Всё, что правила знают о пакете: заявитель, документы с полями, файлы.
 *
 * @param packageId пакет
 * @param inn ИНН заявителя (из PackageCreated)
 * @param programCode программа
 * @param packageDate дата пакета (для сроков)
 * @param documents документы
 * @param files файлы
 */
public record PackageFacts(UUID packageId, String inn, String programCode, LocalDate packageDate,
                           List<DocFacts> documents, List<FileFacts> files) {

    /**
     * Документы вида (уверенно определённые).
     *
     * @param type вид
     * @return документы
     */
    public List<DocFacts> ofType(String type) {
        return documents.stream().filter(doc -> type.equals(doc.type()) && !"TYPE_UNKNOWN".equals(doc.reasonCode()))
                .toList();
    }

    /**
     * Первый документ вида.
     *
     * @param type вид
     * @return документ или пусто
     */
    public Optional<DocFacts> first(String type) {
        return ofType(type).stream().findFirst();
    }
}
