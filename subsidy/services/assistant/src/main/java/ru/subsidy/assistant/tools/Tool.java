package ru.subsidy.assistant.tools;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Инструмент помощника: только чтение фактов пакета, полученных из rules.
 */
public interface Tool {

    /**
     * Имя инструмента для модели.
     *
     * @return имя
     */
    String name();

    /**
     * Описание для модели: что возвращает и какой аргумент принимает.
     *
     * @return описание
     */
    String description();

    /**
     * Выполняет инструмент над фактами пакета.
     *
     * @param factsRs ответ rules {facts, protocol}
     * @param argument аргумент (может быть пустым)
     * @return результат
     */
    JsonNode run(JsonNode factsRs, String argument);
}
