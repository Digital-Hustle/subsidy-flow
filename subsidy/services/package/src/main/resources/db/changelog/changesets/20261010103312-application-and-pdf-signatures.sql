--liquibase formatted sql

--changeset codex:20261010103312-application-and-pdf-signatures
-- требования: FR-01, FR-03, FR-05, FR-47; B1-1..B1-4
alter table packages add column application jsonb;
alter table packages add column requested_amount numeric(19,2) check (requested_amount > 0);
alter table packages add column application_revision integer not null default 0 check (application_revision >= 0);
create table application_changes (
    package_id uuid not null references packages(id),
    revision integer not null check (revision > 0),
    before_data jsonb,
    after_data jsonb not null,
    changed_by text not null,
    changed_at timestamptz not null,
    primary key (package_id, revision)
);
create function reject_application_history_mutation() returns trigger language plpgsql as $$
begin
    raise exception 'application history is append-only';
end;
$$;
create trigger application_history_append_only before update or delete on application_changes
    for each row execute function reject_application_history_mutation();
alter table file_versions add column signature_status text not null default 'UNKNOWN'
    check (signature_status in ('PRESENT', 'ABSENT', 'UNKNOWN'));
alter table file_versions add column signature_reason text default 'NOT_INSPECTED';
alter table file_versions add constraint signature_result_consistent check (
    (signature_status = 'UNKNOWN' and signature_reason is not null)
    or (signature_status <> 'UNKNOWN' and signature_reason is null)
);


--rollback alter table file_versions drop constraint signature_result_consistent, drop column signature_reason, drop column signature_status; drop table application_changes; drop function reject_application_history_mutation(); alter table packages drop column requested_amount, drop column application_revision, drop column application;
