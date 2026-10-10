package ru.subsidy.rules.engine;

import java.util.UUID;

import ru.subsidy.rules.facts.DocFacts;

/**
 * Где на странице подтверждение: документ, поле, страница.
 *
 * @param documentId документ
 * @param field поле
 * @param page страница
 */
public record Evidence(UUID documentId, String field, Integer page) {

    /**
     * Ссылка на поле документа.
     *
     * @param doc документ
     * @param field поле
     * @return evidence
     */
    public static Evidence of(DocFacts doc, String field) {
        return new Evidence(doc.documentId(), field, doc.fromPage());
    }
}
