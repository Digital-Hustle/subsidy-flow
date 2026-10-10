package ru.subsidy.gateway.limit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Счётчики «не больше N за минуту» в памяти (данных не хранит; при рестарте обнуляются — это допустимо).
 */
final class FixedWindows {

    static final long WINDOW_MS = 60_000;
    private static final int MAX_KEYS = 10_000;

    private final Map<String, long[]> windows = new ConcurrentHashMap<>();

    /**
     * Засчитывает запрос.
     *
     * @param key   пользователь и вид лимита
     * @param limit запросов в окне
     * @param nowMs текущее время
     * @return 0 — можно; иначе сколько миллисекунд ждать
     */
    long acquire(String key, int limit, long nowMs) {
        long[] retry = {0};
        windows.compute(key, (ignored, window) -> {
            if (window == null || nowMs - window[0] >= WINDOW_MS) {
                return new long[] {nowMs, 1};
            }
            if (window[1] >= limit) {
                retry[0] = window[0] + WINDOW_MS - nowMs;
            } else {
                window[1]++;
            }
            return window;
        });
        if (windows.size() > MAX_KEYS) {
            windows.values().removeIf(window -> nowMs - window[0] >= WINDOW_MS);
        }
        return retry[0];
    }
}
