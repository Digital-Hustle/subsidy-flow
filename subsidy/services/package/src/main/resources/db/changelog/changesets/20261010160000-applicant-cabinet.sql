--liquibase formatted sql

--changeset b1:20261010160000-applicant-cabinet
-- требования: FR-01, FR-02, FR-05 (кабинет заявителя, docs/API.md «Кабинет заявителя»)
-- ИНН заявитель вводит в анкете — до этого у черновика нет организации
alter table packages alter column org_id drop not null;
alter table files add column slot_type text;
create table applicants (
    package_id      uuid primary key references packages (id),
    key_hash        text,
    resume_hash     text,
    email           text,
    phone           text,
    resume_failures integer     not null default 0,
    state           text        not null check (state in ('DRAFT', 'SUBMITTED', 'NEEDS_FIX', 'DELETED')),
    consent_at      timestamptz not null,
    submitted_at    timestamptz,
    deleted_at      timestamptz,
    created_at      timestamptz not null
);
create unique index applicants_key_idx on applicants (key_hash) where key_hash is not null;
create index applicants_email_idx on applicants (lower(email)) where deleted_at is null;
create index applicants_phone_idx on applicants (phone) where deleted_at is null;
--rollback drop table applicants; alter table files drop column slot_type; alter table packages alter column org_id set not null;
