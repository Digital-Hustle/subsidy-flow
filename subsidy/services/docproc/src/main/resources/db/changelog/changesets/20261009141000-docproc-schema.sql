--liquibase formatted sql

--changeset b2:20261009141000-docproc-schema
create table jobs (
    id              uuid primary key,
    file_id         uuid        not null unique,
    package_id      uuid        not null,
    version         integer     not null,
    mime            text        not null,
    stage           text        not null,
    attempts        integer     not null default 0,
    next_run_at     timestamptz not null,
    lease_until     timestamptz,
    worker_id       text,
    last_error_code text,
    created_at      timestamptz not null,
    updated_at      timestamptz not null
);
create index jobs_ready_idx on jobs (next_run_at) where stage not in ('PARSED', 'NEEDS_REVIEW', 'UNREADABLE', 'FAILED', 'REMOVED');

create table stage_outputs (
    file_id    uuid        not null,
    version    integer     not null,
    stage      text        not null,
    output     jsonb       not null,
    created_at timestamptz not null,
    primary key (file_id, version, stage)
);

create table stage_events (
    id         uuid primary key,
    file_id    uuid        not null,
    stage      text        not null,
    reason     text,
    at         timestamptz not null
);
create index stage_events_file_idx on stage_events (file_id);

create table documents (
    id           uuid primary key,
    file_id      uuid        not null,
    package_id   uuid        not null,
    file_version integer     not null,
    from_page    integer     not null,
    to_page      integer     not null,
    stage        text        not null,
    reason_code  text,
    removed      boolean     not null default false,
    pending_type   text,
    pending_reason text,
    pending_by     text,
    created_at   timestamptz not null,
    updated_at   timestamptz not null
);
create index documents_package_idx on documents (package_id) where not removed;
create index documents_file_idx on documents (file_id);

create table parse_results (
    id              uuid primary key,
    document_id     uuid          not null references documents (id),
    version         integer       not null,
    doc_type        text          not null,
    type_confidence numeric(4, 3) not null,
    evidence        text,
    source          text          not null check (source in ('MODEL', 'SPECIALIST')),
    fields          jsonb         not null,
    model           text,
    prompt_version  text,
    schema_version  text,
    reason          text,
    created_by      text,
    created_at      timestamptz   not null,
    unique (document_id, version)
);

create table llm_calls (
    id          uuid primary key,
    document_id text,
    stage       text        not null,
    provider    text        not null,
    model       text        not null,
    latency_ms  bigint      not null,
    tokens_in   integer,
    tokens_out  integer,
    schema_ok   boolean     not null,
    outcome     text        not null,
    at          timestamptz not null
);
--rollback drop table llm_calls; drop table parse_results; drop table documents; drop table stage_events; drop table stage_outputs; drop table jobs;
