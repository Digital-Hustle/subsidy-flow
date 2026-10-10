package ru.subsidy.rules.integrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ru.subsidy.rules.config.AbstractIntegrationTest;
import ru.subsidy.rules.engine.ProgramBaseline.Baseline;
import ru.subsidy.rules.jooq.Tables;
import ru.subsidy.rules.stats.EstimateBaselineRepository;

class EstimateBaselineIntegrationTest extends AbstractIntegrationTest {

    private static final String PROGRAM = "OUTLIER-IT";
    private static final String ESTIMATE = "COST_JUSTIFICATION";

    @Autowired
    private DSLContext dsl;

    @Autowired
    private EstimateBaselineRepository baseline;

    private UUID estimate(String program, String type, boolean removed, String items) {
        UUID packageId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(Tables.PACKAGE_VIEW).set(Tables.PACKAGE_VIEW.PACKAGE_ID, packageId)
                .set(Tables.PACKAGE_VIEW.PROGRAM_CODE, program).set(Tables.PACKAGE_VIEW.STATUS, "CHECKED")
                .set(Tables.PACKAGE_VIEW.CREATED_AT, now).set(Tables.PACKAGE_VIEW.UPDATED_AT, now).execute();
        dsl.insertInto(Tables.PACKAGE_DOCUMENTS).set(Tables.PACKAGE_DOCUMENTS.DOCUMENT_ID, UUID.randomUUID())
                .set(Tables.PACKAGE_DOCUMENTS.PACKAGE_ID, packageId)
                .set(Tables.PACKAGE_DOCUMENTS.FILE_ID, UUID.randomUUID())
                .set(Tables.PACKAGE_DOCUMENTS.FILE_VERSION, 1).set(Tables.PACKAGE_DOCUMENTS.FROM_PAGE, 1)
                .set(Tables.PACKAGE_DOCUMENTS.TO_PAGE, 1).set(Tables.PACKAGE_DOCUMENTS.TYPE, type)
                .set(Tables.PACKAGE_DOCUMENTS.STAGE, "PARSED").set(Tables.PACKAGE_DOCUMENTS.REMOVED, removed)
                .set(Tables.PACKAGE_DOCUMENTS.FIELDS, JSONB.valueOf("[{\"name\":\"items\",\"value\":" + items + "}]"))
                .set(Tables.PACKAGE_DOCUMENTS.UPDATED_AT, now).execute();
        return packageId;
    }

    @Test
    void aggregatesOtherPackagesOfProgramOnly() {
        final UUID current = estimate(PROGRAM, ESTIMATE, false, "[{\"amount\":\"999999.00\"}]");
        estimate(PROGRAM, ESTIMATE, false, "[{\"amount\":\"100.00\"},{\"amount\":\"300.00\"}]");
        estimate(PROGRAM, ESTIMATE, false, "[{\"amount\":\"200,00\"},{\"amount\":null},{\"amount\":\"н/д\"}]");
        estimate(PROGRAM, "INVOICE", false, "[{\"amount\":\"777777.00\"}]");
        estimate(PROGRAM, ESTIMATE, true, "[{\"amount\":\"777777.00\"}]");
        estimate("OTHER-PROGRAM", ESTIMATE, false, "[{\"amount\":\"777777.00\"}]");

        Baseline stats = baseline.estimateItems(PROGRAM, current);

        assertThat(stats.packages()).isEqualTo(2);
        assertThat(stats.items()).isEqualTo(3);
        assertThat(stats.mean()).isEqualByComparingTo(new BigDecimal("200"));
        assertThat(stats.stddev()).isBetween(new BigDecimal("81.64"), new BigDecimal("81.65"));
    }

    @Test
    void emptyProgram_zeroPackages() {
        Baseline stats = baseline.estimateItems("NO-SUCH-PROGRAM", UUID.randomUUID());
        assertThat(stats.packages()).isZero();
        assertThat(stats.mean()).isNull();
    }
}
