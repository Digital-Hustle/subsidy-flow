--liquibase formatted sql

--changeset b1:20261009102400-outbox-and-inbox
create table outbox (
    id           uuid primary key,
    topic        text        not null,
    message_key  text        not null,
    payload      jsonb       not null,
    created_at   timestamptz not null,
    published_at timestamptz
);
create index outbox_unpublished_idx on outbox (created_at) where published_at is null;

create table processed_events (
    event_id     uuid primary key,
    processed_at timestamptz not null default now()
);
--rollback drop table processed_events; drop table outbox;
