package ru.subsidy.rules.engine;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Распределение сумм статей смет по программе — данные для ANOMALY_OUTLIER (B-06).
 * Считается агрегатом в PostgreSQL по проекции rules, не в памяти.
 */
public interface ProgramBaseline {

    /**
     * Статистика сумм статей смет других пакетов программы.
     *
     * @param programCode программа
     * @param excludePackage текущий пакет (в выборку не входит)
     * @return число пакетов, среднее и стандартное отклонение
     */
    Baseline estimateItems(String programCode, UUID excludePackage);

    /**
     * Статистика выборки.
     *
     * @param packages пакетов со статьями сметы
     * @param items статей в выборке
     * @param mean средняя сумма статьи или null
     * @param stddev стандартное отклонение (генеральное) или null
     */
    record Baseline(int packages, int items, BigDecimal mean, BigDecimal stddev) {
    }
}
