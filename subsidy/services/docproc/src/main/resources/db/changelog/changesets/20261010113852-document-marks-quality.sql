--liquibase formatted sql

--changeset kiri4444:20261010113852-document-marks-quality
-- требование: FR-33 (B2-2/B2-4): признаки подписи/печати и качество страниц в результате разбора
alter table parse_results add column marks jsonb;
comment on column parse_results.marks is 'Визуальные признаки документа: signature/stamp {status: DETECTED|NOT_DETECTED|UNDETERMINED, source, confidence} (B2-2); не заявляет криптографическую действительность ЭП';

alter table parse_results add column page_quality jsonb;
comment on column parse_results.page_quality is 'Метрики качества страниц документа из ocr: blur/brightness/glare/cutOff/meanConf (B2-3/B2-4)';

--rollback alter table parse_results drop column marks, drop column page_quality;
