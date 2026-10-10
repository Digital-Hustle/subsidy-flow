package ru.subsidy.package_.api.dto.response;

import java.util.List;

import ru.subsidy.package_.pkg.ApplicationService.Change;

/**
 * История изменений заявки.
 *
 * @param items ревизии, новые сверху
 */
public record ApplicationHistoryRs(List<Change> items) {
}
