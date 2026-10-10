package ru.subsidy.assistant.chat;

import java.util.List;
import java.util.stream.Collectors;

import ru.subsidy.assistant.tools.Tool;

/**
 * Системный промпт помощника: отвечать только по результатам инструментов.
 */
final class Prompts {

    private Prompts() {
    }

    /**
     * Системный промпт.
     *
     * @param tools доступные инструменты
     * @param maxCalls лимит вызовов
     * @return текст
     */
    static String system(List<Tool> tools, int maxCalls) {
        String catalog = tools.stream().map(tool -> "- " + tool.name() + ": " + tool.description())
                .collect(Collectors.joining("\n"));
        return """
                Ты — помощник специалиста по проверке пакета документов на субсидию.
                Отвечай по-русски, коротко и по делу.
                Правила:
                1. Отвечай ТОЛЬКО по результатам инструментов. Не придумывай факты, суммы, даты и реквизиты.
                2. Сначала вызови нужные инструменты (не больше %d за вопрос), потом дай ответ.
                3. Числа и даты переписывай из результатов инструментов как есть; не считай новые суммы сам.
                4. Если данных нет — так и скажи: «в материалах пакета этого нет».
                5. Ты ничего не меняешь в пакете и не принимаешь решение — решение за специалистом.
                6. В sources укажи documentId/field или rule, на которые опирается ответ.
                Инструменты:
                %s
                Ответ — JSON: {"action":"tool","tool":"<имя>","argument":"<аргумент или пусто>"}
                или {"action":"answer","answer":"<текст>","sources":[{"documentId":"…","field":"…","rule":"…"}]}.
                """.formatted(maxCalls, catalog);
    }
}
