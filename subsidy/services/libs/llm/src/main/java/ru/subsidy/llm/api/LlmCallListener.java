package ru.subsidy.llm.api;

/**
 * SPI журнала вызовов: реализует сервис-потребитель (docproc пишет в llm_calls).
 */
public interface LlmCallListener {

    /**
     * Вызывается после каждого обращения к провайдеру.
     *
     * @param call запись
     */
    void onCall(LlmCallRecord call);
}
