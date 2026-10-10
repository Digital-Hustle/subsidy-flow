package ru.subsidy.docproc.classify;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Реестр видов документов — данные из doc-types/registry.json (вид = данные, не код).
 */
@Component
public class TypeRegistry {

    /** Вид вне перечня. */
    public static final String OTHER = "OTHER";
    private static final String RESOURCE = "/doc-types/registry.json";

    private final Map<String, DocType> types = new LinkedHashMap<>();

    /**
     * Загружает реестр.
     *
     * @param json Jackson
     * @throws UncheckedIOException реестр не читается
     */
    public TypeRegistry(ObjectMapper json) {
        try (InputStream in = TypeRegistry.class.getResourceAsStream(RESOURCE)) {
            List<DocType> list = json.readValue(in, new TypeReference<List<DocType>>() { });
            list.forEach(type -> types.put(type.code(), type));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * Все виды.
     *
     * @return виды в порядке реестра
     */
    public List<DocType> all() {
        return List.copyOf(types.values());
    }

    /**
     * Вид по коду.
     *
     * @param code код
     * @return вид
     */
    public Optional<DocType> find(String code) {
        return Optional.ofNullable(code == null ? null : types.get(code));
    }

    /**
     * Вид по коду или OTHER.
     *
     * @param code код
     * @return вид
     */
    public DocType orOther(String code) {
        return find(code).orElse(types.get(OTHER));
    }
}
