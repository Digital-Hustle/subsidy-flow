package ru.subsidy.rules.api.dto;

import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.protocol.ProtocolView;

/**
 * Факты пакета и последний протокол — для assistant (только чтение).
 *
 * @param facts документы и поля (правка ?? модель)
 * @param protocol последний протокол или null
 */
public record FactsRs(PackageFacts facts, ProtocolView protocol) {
}
