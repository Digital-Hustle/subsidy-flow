package ru.subsidy.assistant.api.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;
import ru.subsidy.assistant.api.dto.AskRq;
import ru.subsidy.assistant.api.dto.AskRs;
import ru.subsidy.assistant.api.dto.Items;
import ru.subsidy.assistant.api.dto.MessageRs;
import ru.subsidy.assistant.api.dto.SuggestionsRs;
import ru.subsidy.common.security.CurrentUser;

/**
 * Вопросы помощнику по пакету (B-06). Только чтение: помощник ничего не меняет в пакете.
 */
@RequestMapping("/api/v1/packages/{packageId}")
public interface AskController {

    /**
     * Задать вопрос.
     *
     * @param packageId пакет
     * @param request вопрос
     * @param user пользователь
     * @return ответ, источники и статус валидатора
     */
    @PostMapping("/ask")
    AskRs ask(@PathVariable UUID packageId, @Valid @RequestBody AskRq request, CurrentUser user);

    /**
     * История диалога текущего пользователя по пакету.
     *
     * @param packageId пакет
     * @param user пользователь
     * @return сообщения
     */
    @GetMapping("/messages")
    Items<MessageRs> messages(@PathVariable UUID packageId, CurrentUser user);

    /**
     * Подсказки вопросов по результатам проверки.
     *
     * @param packageId пакет
     * @return вопросы
     */
    @GetMapping("/suggestions")
    SuggestionsRs suggestions(@PathVariable UUID packageId);
}
