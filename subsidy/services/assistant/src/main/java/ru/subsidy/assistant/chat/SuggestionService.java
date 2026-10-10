package ru.subsidy.assistant.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.assistant.rules.RulesClient;

/**
 * Подсказки вопросов по результатам проверки пакета: что стоит спросить в первую очередь.
 */
@Service
public class SuggestionService {

    private static final String STATUS = "status";
    private static final int MAX = 5;

    private final RulesClient rules;

    /**
     * Конструктор.
     *
     * @param rules клиент rules
     */
    public SuggestionService(RulesClient rules) {
        this.rules = rules;
    }

    /**
     * Вопросы для пакета.
     *
     * @param packageId пакет
     * @return до 5 вопросов
     */
    public List<String> suggest(UUID packageId) {
        JsonNode protocol = rules.facts(packageId).path("protocol");
        List<String> out = new ArrayList<>();
        if (protocol.path("failed").asInt() > 0) {
            out.add("Какие проверки не пройдены и почему?");
        }
        if (!protocol.path("completeness").path("missing").isEmpty()) {
            out.add("Каких документов не хватает и что запросить у организации?");
        }
        boolean anomalies = false;
        for (JsonNode result : protocol.path("results")) {
            anomalies |= result.path("rule").asText().startsWith("ANOMALY_")
                    && !"PASSED".equals(result.path(STATUS).asText());
        }
        if (anomalies) {
            out.add("Какие подозрительные признаки нашлись в документах?");
        }
        out.add("Какая сумма субсидии запрошена и сходится ли она с расчётом?");
        out.add("Совпадают ли реквизиты организации во всех документах?");
        out.add("Какие поля стоит перепроверить вручную?");
        return out.stream().limit(MAX).toList();
    }
}
