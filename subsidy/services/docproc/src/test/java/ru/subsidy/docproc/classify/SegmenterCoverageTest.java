package ru.subsidy.docproc.classify;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class SegmenterCoverageTest {

    private static Segment seg(int first, int last, String type) {
        return new Segment(first, last, type, new BigDecimal("0.900"), null);
    }

    private static void assertCovers(List<Segment> result, int pages) {
        int next = 1;
        for (Segment segment : result) {
            assertThat(segment.fromPage()).isEqualTo(next);
            assertThat(segment.toPage()).isGreaterThanOrEqualTo(segment.fromPage());
            next = segment.toPage() + 1;
        }
        assertThat(next - 1).isEqualTo(pages);
    }

    @Test
    void validSegments_keptAsIs() {
        List<Segment> result = Classifier.cover(List.of(seg(1, 2, "INVOICE"), seg(3, 5, "CONTRACT")), 5);
        assertThat(result).extracting(Segment::type).containsExactly("INVOICE", "CONTRACT");
        assertCovers(result, 5);
    }

    @Test
    void gapsOverlapsAndOrder_repaired() {
        List<Segment> result = Classifier.cover(List.of(seg(4, 9, "PAYMENT_ORDER"), seg(2, 3, "INVOICE"),
                seg(3, 4, "CONTRACT")), 6);
        assertCovers(result, 6);
    }

    @Test
    void empty_becomesSingleOther() {
        List<Segment> result = Classifier.cover(List.of(), 3);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().type()).isEqualTo(TypeRegistry.OTHER);
        assertCovers(result, 3);
    }
}
