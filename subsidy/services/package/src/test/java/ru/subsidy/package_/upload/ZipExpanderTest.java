package ru.subsidy.package_.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import ru.subsidy.package_.upload.UploadService.IncomingFile;

class ZipExpanderTest {

    private static final int MAX_ENTRIES = 3;
    private static final int BOMB_BYTES = 2 * 1_048_576;
    private static final byte[] DATA = "data".getBytes(StandardCharsets.UTF_8);

    private final ZipExpander expander = new ZipExpander(1, MAX_ENTRIES);

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    @Test
    void expandsFiles_skipsFoldersAndJunk() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("docs/", new byte[0]);
        entries.put("docs/выписка.pdf", DATA);
        entries.put("__MACOSX/docs/._x", DATA);
        entries.put(".DS_Store", DATA);
        entries.put("смета.xlsx", DATA);
        List<IncomingFile> files = expander.expand(zip(entries));
        assertThat(files).extracting(IncomingFile::name).containsExactly("выписка.pdf", "смета.xlsx");
        assertThat(files.get(0).content()).isEqualTo(DATA);
    }

    @Test
    void pathTraversal_rejected() throws IOException {
        byte[] archive = zip(Map.of("../../etc/passwd.pdf", DATA));
        assertThatThrownBy(() -> expander.expand(archive)).isInstanceOf(ZipRejectedException.class)
                .hasMessageContaining("путь");
    }

    @Test
    void nestedArchive_rejected() throws IOException {
        byte[] inner = zip(Map.of("a.pdf", DATA));
        assertThatThrownBy(() -> expander.expand(zip(Map.of("inner.bin", inner))))
                .isInstanceOf(ZipRejectedException.class).hasMessageContaining("вложенный");
    }

    @Test
    void tooManyEntries_rejected() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int idx = 0; idx <= MAX_ENTRIES; idx++) {
            entries.put("f" + idx + ".pdf", DATA);
        }
        byte[] archive = zip(entries);
        assertThatThrownBy(() -> expander.expand(archive)).isInstanceOf(ZipRejectedException.class);
    }

    @Test
    void zipBomb_stoppedByRealBytes() throws IOException {
        byte[] bomb = zip(Map.of("zeros.pdf", new byte[BOMB_BYTES]));
        assertThat(bomb.length).isLessThan(BOMB_BYTES / 100);
        assertThatThrownBy(() -> expander.expand(bomb)).isInstanceOf(ZipRejectedException.class)
                .hasMessageContaining("МБ");
    }

    @Test
    void garbageOrEmpty_rejected() throws IOException {
        assertThatThrownBy(() -> expander.expand(DATA)).isInstanceOf(ZipRejectedException.class);
        byte[] empty = zip(Map.of());
        assertThatThrownBy(() -> expander.expand(empty)).isInstanceOf(ZipRejectedException.class);
    }
}
