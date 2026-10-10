--liquibase formatted sql

--changeset codex:20261010103312-application-revisions
-- требования: FR-01, FR-47
alter table package_view add column application jsonb;
alter table package_view add column application_revision integer not null default 0 check (application_revision >= 0);


--rollback alter table package_view drop column application_revision, drop column application;
