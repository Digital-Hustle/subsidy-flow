package ru.subsidy.docproc.classify;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Промпты из resources/prompts (версия — в имени файла).
 */
public final class Prompts {

    /** Версия промпта сегментации. */
    public static final String SEGMENT = "segment.v1";
    /** Версия промпта извлечения. */
    public static final String EXTRACT = "extract.v1";

    private Prompts() {
    }

    /**
     * Текст промпта.
     *
     * @param name имя без .md
     * @return текст
     * @throws UncheckedIOException промпта нет
     */
    public static String load(String name) {
        try (InputStream in = Prompts.class.getResourceAsStream("/prompts/" + name + ".md")) {
            if (in == null) {
                throw new IOException("нет промпта " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
