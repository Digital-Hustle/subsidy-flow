--liquibase formatted sql

--changeset b3:20261009151600-conversations
create table conversations (
    id          uuid primary key,
    package_id  uuid        not null,
    user_id     text        not null,
    created_at  timestamptz not null
);
create unique index conversations_package_user_uq on conversations (package_id, user_id);

create table messages (
    id                uuid primary key,
    conversation_id   uuid        not null references conversations (id),
    role              text        not null check (role in ('USER', 'ASSISTANT')),
    content           text        not null,
    tool_calls        jsonb       not null default '[]'::jsonb,
    sources           jsonb       not null default '[]'::jsonb,
    validator_status  text,
    validator_details jsonb       not null default '[]'::jsonb,
    model             text,
    latency_ms        bigint,
    created_at        timestamptz not null
);
create index messages_conversation_idx on messages (conversation_id, created_at);

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
--rollback drop table llm_calls; drop table messages; drop table conversations;
