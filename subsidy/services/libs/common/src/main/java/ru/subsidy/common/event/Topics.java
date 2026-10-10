package ru.subsidy.common.event;

/**
 * Топики Kafka (см. docs/ARCHITECTURE.md «Схема событий»).
 * Топик: &lt;владелец&gt;.&lt;агрегат&gt;.v&lt;N&gt;, ключ — id агрегата, partitions 3.
 */
public final class Topics {

    public static final String PACKAGE_PACKAGE_V1 = "package.package.v1";
    public static final String PACKAGE_FILE_V1 = "package.file.v1";
    public static final String DOCPROC_DOCUMENT_V1 = "docproc.document.v1";
    public static final String RULES_PACKAGE_V1 = "rules.package.v1";

    private Topics() {}
}
