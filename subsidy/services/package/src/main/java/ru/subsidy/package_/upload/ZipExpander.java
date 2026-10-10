package ru.subsidy.package_.upload;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ru.subsidy.package_.upload.UploadService.IncomingFile;

/**
 * Безопасная распаковка ZIP в памяти: лимиты на число файлов и реально прочитанные байты
 * (а не заявленный в заголовке размер), запрет path traversal, вложенных архивов и шифрования.
 * На диск ничего не пишется.
 */
@Component
public class ZipExpander {

    private static final long BYTES_IN_MB = 1_048_576L;
    private static final int BUFFER = 8192;
    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};
    private static final Pattern UNSAFE_PATH = Pattern.compile("(^[/\\\\])|(^[A-Za-z]:)|(^|[/\\\\])\\.\\.([/\\\\]|$)");
    private static final List<String> ARCHIVE_EXTENSIONS = List.of(".zip", ".rar", ".7z", ".tar", ".gz");

    private final long maxUnpackedBytes;
    private final long maxUnpackedMb;
    private final int maxEntries;

    /**
     * Конструктор.
     *
     * @param maxUnpackedMb ZIP_MAX_UNPACKED_MB
     * @param maxEntries ZIP_MAX_ENTRIES
     */
    public ZipExpander(@Value("${ZIP_MAX_UNPACKED_MB:300}") long maxUnpackedMb,
                       @Value("${ZIP_MAX_ENTRIES:100}") int maxEntries) {
        this.maxUnpackedMb = maxUnpackedMb;
        this.maxUnpackedBytes = maxUnpackedMb * BYTES_IN_MB;
        this.maxEntries = maxEntries;
    }

    /**
     * Распаковывает архив целиком или отклоняет его целиком.
     *
     * @param archive байты ZIP
     * @return файлы из архива (без папок и служебных файлов)
     * @throws ZipRejectedException если архив небезопасен, пуст или повреждён
     */
    public List<IncomingFile> expand(byte[] archive) {
        List<IncomingFile> result = new ArrayList<>();
        long budget = maxUnpackedBytes;
        try (ZipArchiveInputStream zip = new ZipArchiveInputStream(new ByteArrayInputStream(archive),
                StandardCharsets.UTF_8.name(), true, true)) {
            ZipArchiveEntry entry = zip.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory() && !ignored(entry.getName())) {
                    checkEntry(zip, entry, result.size() + 1);
                    byte[] content = read(zip, budget);
                    budget -= content.length;
                    if (isArchive(entry.getName(), content)) {
                        throw new ZipRejectedException("вложенный архив");
                    }
                    result.add(new IncomingFile(baseName(entry.getName()), content));
                }
                entry = zip.getNextEntry();
            }
        } catch (IOException | IllegalArgumentException failure) {
            throw new ZipRejectedException("архив повреждён");
        }
        if (result.isEmpty()) {
            throw new ZipRejectedException("архив пустой");
        }
        return result;
    }

    private void checkEntry(ZipArchiveInputStream zip, ZipArchiveEntry entry, int ordinal) {
        if (ordinal > maxEntries) {
            throw new ZipRejectedException("больше " + maxEntries + " файлов");
        }
        if (UNSAFE_PATH.matcher(entry.getName()).find()) {
            throw new ZipRejectedException("небезопасный путь внутри архива");
        }
        if (entry.getGeneralPurposeBit().usesEncryption() || !zip.canReadEntryData(entry)) {
            throw new ZipRejectedException("файл в архиве зашифрован или сжат неподдерживаемым методом");
        }
    }

    private byte[] read(ZipArchiveInputStream zip, long budget) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER];
        long left = budget;
        int read = zip.read(buffer);
        while (read != -1) {
            if (read > left) {
                throw new ZipRejectedException("распакованный размер больше " + maxUnpackedMb + " МБ");
            }
            out.write(buffer, 0, read);
            left -= read;
            read = zip.read(buffer);
        }
        return out.toByteArray();
    }

    private static boolean ignored(String name) {
        String base = baseName(name);
        return name.startsWith("__MACOSX/") || base.startsWith(".") || "Thumbs.db".equalsIgnoreCase(base);
    }

    private static boolean isArchive(String name, byte[] content) {
        String lower = name.toLowerCase(Locale.ROOT);
        boolean byName = ARCHIVE_EXTENSIONS.stream().anyMatch(lower::endsWith);
        boolean byMagic = content.length >= ZIP_MAGIC.length;
        for (int pos = 0; byMagic && pos < ZIP_MAGIC.length; pos++) {
            byMagic = content[pos] == ZIP_MAGIC[pos];
        }
        return byName || byMagic && !looksLikeOffice(lower);
    }

    private static boolean looksLikeOffice(String lowerName) {
        return lowerName.endsWith(".docx") || lowerName.endsWith(".xlsx");
    }

    private static String baseName(String name) {
        String normalized = name.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }
}
