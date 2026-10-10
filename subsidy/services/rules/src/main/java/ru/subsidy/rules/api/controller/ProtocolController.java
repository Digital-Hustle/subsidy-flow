package ru.subsidy.rules.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import jakarta.validation.Valid;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.rules.api.dto.AcceptedRs;
import ru.subsidy.rules.api.dto.FieldRq;
import ru.subsidy.rules.api.dto.Items;
import ru.subsidy.rules.api.dto.RebuildRs;
import ru.subsidy.rules.protocol.ProtocolView;

/**
 * Протокол проверки пакета, версии, правки полей, выгрузка.
 */
@RequestMapping("/api/v1/packages/{packageId}")
public interface ProtocolController {

    /**
     * Последний протокол.
     *
     * @param packageId пакет
     * @return протокол (409 NOT_CHECKED_YET, пока идёт разбор)
     */
    @GetMapping("/protocol")
    ProtocolView protocol(@PathVariable UUID packageId);

    /**
     * Версии протокола.
     *
     * @param packageId пакет
     * @return версии
     */
    @GetMapping("/protocols")
    Items<ProtocolView.Summary> versions(@PathVariable UUID packageId);

    /**
     * Конкретная версия.
     *
     * @param packageId пакет
     * @param version версия
     * @return протокол
     */
    @GetMapping("/protocols/{version}")
    ProtocolView version(@PathVariable UUID packageId, @PathVariable int version);

    /**
     * Правка значения поля → перепроверка → новая версия.
     *
     * @param packageId пакет
     * @param request поле и значение
     * @param user специалист
     * @return принято
     */
    @PutMapping("/fields")
    @ResponseStatus(HttpStatus.ACCEPTED)
    AcceptedRs editField(@PathVariable UUID packageId, @Valid @RequestBody FieldRq request, CurrentUser user);

    /**
     * Выгрузка протокола.
     *
     * @param packageId пакет
     * @param format docx, xlsx, pdf
     * @return файл
     */
    @GetMapping("/protocol/export")
    ResponseEntity<byte[]> export(@PathVariable UUID packageId,
                                  @RequestParam(defaultValue = "pdf") String format);

    /**
     * Пересборка проекции документов пакета из docproc (RUL-18) и перепроверка. Только SENIOR_SPECIALIST.
     *
     * @param packageId пакет
     * @return итог (404 PACKAGE_NOT_FOUND, 503 DEPENDENCY_UNAVAILABLE)
     */
    @PostMapping("/projection/rebuild")
    @ResponseStatus(HttpStatus.ACCEPTED)
    RebuildRs rebuildProjection(@PathVariable UUID packageId);
}
