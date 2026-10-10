--liquibase formatted sql

--changeset b1:20261009103000-package-schema
create table organizations (
    id         uuid primary key,
    inn        text        not null unique,
    ogrn       text,
    name       text,
    created_by text        not null,
    created_at timestamptz not null
);

create table packages (
    id               uuid primary key,
    org_id           uuid        not null references organizations (id),
    program_code     text        not null,
    status           text        not null check (status in ('COLLECTING', 'PROCESSING', 'CHECKED', 'DECIDED')),
    idempotency_key  text unique,
    protocol_status  text,
    completeness     integer,
    failed           integer     not null default 0,
    warnings         integer     not null default 0,
    needs_review     integer     not null default 0,
    decision         text check (decision in ('APPROVE', 'REJECT', 'RETURN')),
    decision_comment text,
    decided_by       text,
    decided_at       timestamptz,
    created_by       text        not null,
    created_at       timestamptz not null,
    updated_at       timestamptz not null
);
create index packages_queue_idx on packages (updated_at desc, id desc);
create index packages_org_idx on packages (org_id);

create table files (
    id              uuid primary key,
    package_id      uuid        not null references packages (id),
    current_version integer     not null,
    stage           text        not null,
    reason_code     text,
    duplicate_of    uuid references files (id),
    deleted_at      timestamptz,
    created_at      timestamptz not null,
    updated_at      timestamptz not null
);
create index files_package_idx on files (package_id) where deleted_at is null;

create table file_versions (
    file_id       uuid        not null references files (id),
    version       integer     not null,
    content_enc   bytea       not null,
    nonce         bytea       not null,
    sha256        text        not null,
    mime          text        not null,
    size_bytes    bigint      not null,
    pages         integer,
    original_name text        not null,
    created_by    text        not null,
    created_at    timestamptz not null,
    primary key (file_id, version)
);
create index file_versions_sha_idx on file_versions (sha256);

create table file_documents (
    document_id     uuid primary key,
    file_id         uuid        not null references files (id),
    file_version    integer     not null,
    from_page       integer     not null,
    to_page         integer     not null,
    doc_type        text,
    type_confidence numeric(4, 3),
    source          text,
    stage           text        not null,
    reason_code     text,
    updated_at      timestamptz not null
);
create index file_documents_file_idx on file_documents (file_id);

create table page_previews (
    file_id    uuid        not null,
    version    integer     not null,
    page       integer     not null,
    png        bytea       not null,
    created_at timestamptz not null,
    primary key (file_id, version, page),
    foreign key (file_id, version) references file_versions (file_id, version)
);

create table file_access_audit (
    id         uuid primary key,
    file_id    uuid        not null references files (id),
    version    integer     not null,
    user_id    text        not null,
    action     text        not null,
    at         timestamptz not null
);
--rollback drop table file_access_audit; drop table page_previews; drop table file_documents; drop table file_versions; drop table files; drop table packages; drop table organizations;
