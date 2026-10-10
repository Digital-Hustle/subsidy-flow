package ru.subsidy.rules.api.controller.impl;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import ru.subsidy.common.security.CurrentUser;
import ru.subsidy.rules.api.controller.ProtocolController;
import ru.subsidy.rules.api.dto.AcceptedRs;
import ru.subsidy.rules.api.dto.FieldRq;
import ru.subsidy.rules.api.dto.Items;
import ru.subsidy.rules.api.dto.RebuildRs;
import ru.subsidy.rules.facts.FieldOverrideService;
import ru.subsidy.rules.projection.ProjectionRebuild;
import ru.subsidy.rules.projection.ProjectionService;
import ru.subsidy.rules.protocol.ProtocolExporter;
import ru.subsidy.rules.protocol.ProtocolQueryService;
import ru.subsidy.rules.protocol.ProtocolView;

/**
 * Реализация {@link ProtocolController}.
 */
@RestController
public class ProtocolControllerImpl implements ProtocolController {

    private static final String RECHECK_SCHEDULED = "RECHECK_SCHEDULED";
    private static final String READERS = "hasAnyRole('SPECIALIST','SENIOR_SPECIALIST','AUDITOR')";
    private static final Map<String, String> TYPES = Map.of(
            ProtocolExporter.DOCX, "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ProtocolExporter.XLSX, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            ProtocolExporter.PDF, MediaType.APPLICATION_PDF_VALUE);

    private final ProtocolQueryService protocols;
    private final FieldOverrideService overrides;
    private final ProtocolExporter exporter;
    private final ProjectionRebuild rebuild;

    /**
     * Конструктор.
     *
     * @param protocols протоколы
     * @param overrides правки полей
     * @param exporter выгрузка
     * @param rebuild пересборка проекции
     */
    public ProtocolControllerImpl(ProtocolQueryService protocols, FieldOverrideService overrides,
                                  ProtocolExporter exporter, ProjectionRebuild rebuild) {
        this.protocols = protocols;
        this.overrides = overrides;
        this.exporter = exporter;
        this.rebuild = rebuild;
    }

    @Override
    @PreAuthorize(READERS)
    public ProtocolView protocol(UUID packageId) {
        return protocols.latest(packageId);
    }

    @Override
    @PreAuthorize(READERS)
    public Items<ProtocolView.Summary> versions(UUID packageId) {
        return new Items<>(protocols.versions(packageId));
    }

    @Override
    @PreAuthorize(READERS)
    public ProtocolView version(UUID packageId, int version) {
        return protocols.version(packageId, version);
    }

    @Override
    @PreAuthorize("hasAnyRole('SPECIALIST','SENIOR_SPECIALIST')")
    public AcceptedRs editField(UUID packageId, FieldRq request, CurrentUser user) {
        overrides.override(packageId, request.documentId(), request.field(), request.value(), request.reason(),
                user.userId());
        return new AcceptedRs(packageId, RECHECK_SCHEDULED);
    }

    @Override
    @PreAuthorize(READERS)
    public ResponseEntity<byte[]> export(UUID packageId, String format) {
        ProtocolView protocol = protocols.latest(packageId);
        byte[] body = exporter.export(protocol, format);
        String name = "protocol-" + packageId + "-v" + protocol.version() + "." + format;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(TYPES.get(format)))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build()
                        .toString())
                .body(body);
    }

    @Override
    @PreAuthorize("hasRole('SENIOR_SPECIALIST')")
    public RebuildRs rebuildProjection(UUID packageId) {
        ProjectionService.Reconciled done = rebuild.rebuild(packageId);
        return new RebuildRs(packageId, done.documents(), done.updated(), done.removed(), RECHECK_SCHEDULED);
    }
}
