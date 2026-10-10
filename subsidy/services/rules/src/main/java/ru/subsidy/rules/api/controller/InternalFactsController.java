package ru.subsidy.rules.api.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import ru.subsidy.rules.api.dto.FactsRs;

/**
 * Факты и протокол пакета для assistant (service account).
 */
public interface InternalFactsController {

    /**
     * Факты пакета.
     *
     * @param packageId пакет
     * @return факты и протокол
     */
    @GetMapping("/internal/rules/packages/{packageId}/facts")
    FactsRs facts(@PathVariable UUID packageId);
}
