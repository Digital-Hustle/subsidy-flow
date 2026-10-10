package ru.subsidy.assistant.api.controller.impl;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import ru.subsidy.assistant.api.controller.AskController;
import ru.subsidy.assistant.api.dto.AskRq;
import ru.subsidy.assistant.api.dto.AskRs;
import ru.subsidy.assistant.api.dto.Items;
import ru.subsidy.assistant.api.dto.MessageRs;
import ru.subsidy.assistant.api.dto.SuggestionsRs;
import ru.subsidy.assistant.chat.AskService;
import ru.subsidy.assistant.chat.SuggestionService;
import ru.subsidy.common.security.CurrentUser;

/**
 * Реализация {@link AskController}.
 */
@RestController
public class AskControllerImpl implements AskController {

    private static final String READERS = "hasAnyRole('SPECIALIST','SENIOR_SPECIALIST','AUDITOR')";

    private final AskService asks;
    private final SuggestionService suggestions;

    /**
     * Конструктор.
     *
     * @param asks вопросы
     * @param suggestions подсказки
     */
    public AskControllerImpl(AskService asks, SuggestionService suggestions) {
        this.asks = asks;
        this.suggestions = suggestions;
    }

    @Override
    @PreAuthorize(READERS)
    public AskRs ask(UUID packageId, AskRq request, CurrentUser user) {
        return AskRs.of(asks.ask(packageId, user.userId(), request.question().strip()));
    }

    @Override
    @PreAuthorize(READERS)
    public Items<MessageRs> messages(UUID packageId, CurrentUser user) {
        return new Items<>(asks.history(packageId, user.userId()).stream().map(MessageRs::of).toList());
    }

    @Override
    @PreAuthorize(READERS)
    public SuggestionsRs suggestions(UUID packageId) {
        return new SuggestionsRs(suggestions.suggest(packageId));
    }
}
