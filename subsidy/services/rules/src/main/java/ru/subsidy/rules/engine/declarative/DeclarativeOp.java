package ru.subsidy.rules.engine.declarative;

import java.util.List;

import ru.subsidy.rules.engine.RuleResult;

/**
 * Операция проверки: код, который выполняет правило-данные с параметрами. Модель здесь не вызывается.
 */
public interface DeclarativeOp {

    /**
     * Имя операции в JSON правила.
     *
     * @return PATTERN, RATIO_MIN, …
     */
    String name();

    /**
     * Что делает операция — для администратора.
     *
     * @return описание по-русски
     */
    String description();

    /**
     * Параметры операции — для редактора правил.
     *
     * @return имена параметров
     */
    List<String> parameters();

    /**
     * Проверяет параметры правила при сохранении программы.
     *
     * @param spec правило
     * @throws IllegalArgumentException с понятной причиной
     */
    void validate(RuleSpec spec);

    /**
     * Выполняет правило.
     *
     * @param spec правило
     * @param scope факты пакета и текущая дата
     * @return результат правила
     */
    RuleResult apply(RuleSpec spec, Scope scope);
}
