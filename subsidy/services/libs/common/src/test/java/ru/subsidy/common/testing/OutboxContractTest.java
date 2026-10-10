package ru.subsidy.common.testing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OutboxContractTest {

    private static final String FILE = "11111111-1111-7111-8111-111111111111";
    private static final String PKG = "22222222-2222-7222-8222-222222222222";

    private static String envelope(String type, String payload) {
        return "{\"eventId\":\"33333333-3333-7333-8333-333333333333\",\"type\":\"" + type
                + "\",\"occurredAt\":\"2026-10-09T10:00:00Z\",\"aggregateId\":\"" + FILE
                + "\",\"traceparent\":null,\"payload\":" + payload + "}";
    }

    @Test
    void validEventPasses() {
        String payload = "{\"fileId\":\"" + FILE + "\",\"packageId\":\"" + PKG
                + "\",\"version\":1,\"stage\":\"QUEUED\"}";
        assertThat(OutboxContract.check("docproc.document.v1", envelope("StageChanged", payload))).isEmpty();
    }

    @Test
    void unknownFieldMissingFieldAndUnknownTypeAreReported() {
        String extra = "{\"fileId\":\"" + FILE + "\",\"packageId\":\"" + PKG + "\",\"version\":1,"
                + "\"stage\":\"QUEUED\",\"secret\":1}";
        assertThat(OutboxContract.check("docproc.document.v1", envelope("StageChanged", extra))).isNotEmpty();
        String missing = "{\"fileId\":\"" + FILE + "\",\"version\":1,\"stage\":\"QUEUED\"}";
        assertThat(OutboxContract.check("docproc.document.v1", envelope("StageChanged", missing))).isNotEmpty();
        assertThat(OutboxContract.check("docproc.document.v1", envelope("Unknown", "{}")))
                .anyMatch(error -> error.contains("нет схемы"));
    }
}
