--liquibase formatted sql

--changeset rules:20261009144500-rules-schema
create table programs (
    code       text        not null,
    version    integer     not null,
    name       text        not null,
    demo       boolean     not null default false,
    params     jsonb       not null,
    checklist  jsonb       not null,
    created_by text        not null,
    created_at timestamptz not null,
    primary key (code, version)
);

create table package_view (
    package_id   uuid primary key,
    org_id       uuid,
    inn          text,
    program_code text        not null,
    status       text        not null,
    created_by   text,
    created_at   timestamptz not null,
    updated_at   timestamptz not null
);

create table package_files (
    file_id     uuid primary key,
    package_id  uuid        not null,
    version     integer     not null,
    stage       text        not null,
    reason_code text,
    removed     boolean     not null default false,
    updated_at  timestamptz not null
);
create index package_files_package_idx on package_files (package_id);

create table package_documents (
    document_id     uuid primary key,
    package_id      uuid          not null,
    file_id         uuid          not null,
    file_version    integer       not null,
    from_page       integer       not null,
    to_page         integer       not null,
    type            text,
    type_confidence numeric(4, 3),
    source          text,
    stage           text          not null,
    reason_code     text,
    result_version  integer,
    fields          jsonb         not null default '[]'::jsonb,
    removed         boolean       not null default false,
    updated_at      timestamptz   not null
);
create index package_documents_package_idx on package_documents (package_id);

create table field_overrides (
    id          uuid primary key,
    package_id  uuid        not null,
    document_id uuid        not null,
    field       text        not null,
    value       text,
    reason      text,
    created_by  text        not null,
    created_at  timestamptz not null
);
create index field_overrides_document_idx on field_overrides (document_id, field, created_at);

create table recheck_requests (
    package_id   uuid primary key,
    due_at       timestamptz not null,
    requested_at timestamptz not null
);

create table protocols (
    id                uuid primary key,
    package_id        uuid        not null,
    version           integer     not null,
    status            text        not null,
    program_code      text        not null,
    program_version   integer     not null,
    rules_version     text        not null,
    completeness_pct  integer     not null,
    failed            integer     not null,
    warnings          integer     not null,
    needs_review      integer     not null,
    completeness      jsonb       not null,
    facts             jsonb       not null,
    created_at        timestamptz not null,
    unique (package_id, version)
);

create table rule_results (
    id           uuid primary key,
    protocol_id  uuid        not null references protocols (id),
    package_id   uuid        not null,
    program_code text        not null,
    position     integer     not null,
    rule         text        not null,
    status       text        not null,
    message      text,
    evidence     jsonb       not null default '[]'::jsonb,
    expected     text,
    actual       text,
    created_at   timestamptz not null
);
create index rule_results_protocol_idx on rule_results (protocol_id);
create index rule_results_rule_idx on rule_results (rule, status, created_at);

create table org_history (
    id         uuid primary key,
    inn        text        not null,
    at         timestamptz not null,
    actor      text        not null,
    action     text        not null,
    package_id uuid,
    details    jsonb       not null default '{}'::jsonb
);
create index org_history_inn_idx on org_history (inn, at);
--rollback drop table org_history; drop table rule_results; drop table protocols; drop table recheck_requests; drop table field_overrides; drop table package_documents; drop table package_files; drop table package_view; drop table programs;

--changeset rules:20261009144500-org-history-append-only splitStatements:false
create function org_history_append_only() returns trigger as $$
begin
    raise exception 'org_history is append-only';
end;
$$ language plpgsql;
create trigger org_history_no_update before update or delete on org_history
    for each row execute function org_history_append_only();
--rollback drop trigger org_history_no_update on org_history; drop function org_history_append_only();

--changeset rules:20261009144500-protocols-immutable splitStatements:false
create function protocols_immutable() returns trigger as $$
begin
    raise exception 'protocols are immutable';
end;
$$ language plpgsql;
create trigger protocols_no_update before update or delete on protocols
    for each row execute function protocols_immutable();
create trigger rule_results_no_update before update or delete on rule_results
    for each row execute function protocols_immutable();
--rollback drop trigger rule_results_no_update on rule_results; drop trigger protocols_no_update on protocols; drop function protocols_immutable();
