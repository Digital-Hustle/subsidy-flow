--liquibase formatted sql

--changeset rules:20261010150000-requisite-checksums
-- требование: FR-42: контрольные разряды ИНН (10/12) и ОГРН/ОГРНИП (13/15) во всех документах.
-- WARNING, а не FAILED (ADR-025): в открытом наборе реквизиты синтетические; формат по-прежнему проверяет INN_FORMAT.
with additions as (select value, pos from jsonb_array_elements('[{"code": "INN_CHECKSUM", "title": "Контрольные цифры ИНН сходятся", "op": "PATTERN", "severity": "WARNING", "types": ["*"], "field": "inn", "normalize": "digits", "pattern": "\\d{10}|\\d{12}", "checksum": "inn", "hint": "контрольные цифры ИНН не сходятся — проверьте распознавание и реквизит в оригинале"}, {"code": "OGRN_CHECKSUM", "title": "Контрольная цифра ОГРН сходится", "op": "PATTERN", "severity": "WARNING", "types": ["*"], "field": "ogrn", "normalize": "digits", "pattern": "\\d{13}|\\d{15}", "checksum": "ogrn", "hint": "ОГРН — 13 цифр, ОГРНИП — 15; контрольная цифра не сходится — проверьте реквизит в оригинале"}]'::jsonb) with ordinality rules(value, pos)),
latest as (select distinct on (code) * from programs order by code, version desc)
insert into programs (code, version, name, demo, params, checklist, created_by, created_at)
select latest.code, latest.version + 1, latest.name, latest.demo,
       jsonb_set(latest.params, '{rules}', coalesce(latest.params->'rules', '[]'::jsonb) || coalesce((
           select jsonb_agg(additions.value order by additions.pos) from additions
           where not exists (select 1 from jsonb_array_elements(coalesce(latest.params->'rules', '[]'::jsonb)) existing
                             where existing->>'code' = additions.value->>'code')), '[]'::jsonb)),
       latest.checklist, 'migration-FR-42', now()
from latest
where exists (select 1 from additions where not exists (
    select 1 from jsonb_array_elements(coalesce(latest.params->'rules', '[]'::jsonb)) existing
    where existing->>'code' = additions.value->>'code'));

insert into recheck_requests (package_id, due_at, requested_at)
select package_id, now(), now() from package_view where status <> 'DECIDED'
on conflict (package_id) do update set due_at = excluded.due_at, requested_at = excluded.requested_at;

--rollback delete from programs where created_by = 'migration-FR-42';
