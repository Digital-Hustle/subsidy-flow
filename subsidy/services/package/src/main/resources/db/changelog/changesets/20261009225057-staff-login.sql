--liquibase formatted sql

--changeset subsidy-agent:20261009225057-staff-login
-- требование: FR-60 (ADR-023) — сотрудники, вход по коду из письма, сессии
create table staff_users (
    id           uuid primary key,
    name         text        not null,
    email        text        not null,
    role         text        not null check (role in ('SPECIALIST', 'SENIOR_SPECIALIST', 'AUDITOR')),
    status       text        not null check (status in ('ACTIVE', 'INVITED', 'DISABLED')),
    demo         boolean     not null default false,
    created_at   timestamptz not null,
    last_seen_at timestamptz
);
create unique index staff_users_email_uq on staff_users (lower(email));

-- один активный код на адрес; строка заводится и для неизвестного адреса (code_hash null) — ответ не раскрывает, есть ли сотрудник
create table staff_login_codes (
    email      text primary key,
    code_hash  text,
    salt       text        not null,
    attempts   int         not null default 0,
    expires_at timestamptz not null,
    resend_at  timestamptz not null
);

create table staff_sessions (
    id           uuid primary key,
    staff_id     uuid        not null references staff_users (id),
    refresh_hash text        not null unique,
    created_at   timestamptz not null,
    expires_at   timestamptz not null,
    revoked_at   timestamptz
);
create index staff_sessions_staff_idx on staff_sessions (staff_id);
--rollback drop table staff_sessions; drop table staff_login_codes; drop table staff_users;
