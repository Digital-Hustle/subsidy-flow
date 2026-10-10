--liquibase formatted sql

--changeset kiri4444:20261010140000-visual-signature
-- требование: FR-33 (B2-2): видимая подпись на заявлении, гарантийном письме и согласии ПД (vision-модель docproc).
-- Новая версия каждой программы с правилом VISUAL_SIGNATURE (WARNING), если его ещё нет; ЭП в PDF тоже считается подписью.
with rule as (select '{"code": "VISUAL_SIGNATURE", "title": "Подпись видна на документе", "op": "VISUAL_MARK", "severity": "WARNING", "types": ["APPLICATION", "GUARANTEE_LETTER", "PD_CONSENT"], "field": "visualSignature", "acceptElectronic": true}'::jsonb as value),
latest as (select distinct on (code) * from programs order by code, version desc)
insert into programs (code, version, name, demo, params, checklist, created_by, created_at)
select latest.code, latest.version + 1, latest.name, latest.demo,
       jsonb_set(latest.params, '{rules}', coalesce(latest.params->'rules', '[]'::jsonb) || jsonb_build_array(rule.value)),
       latest.checklist, 'migration-B2-2', now()
from latest cross join rule
where not exists (select 1 from jsonb_array_elements(coalesce(latest.params->'rules', '[]'::jsonb)) existing
                  where existing->>'code' = 'VISUAL_SIGNATURE');

insert into recheck_requests (package_id, due_at, requested_at)
select package_id, now(), now() from package_view where status <> 'DECIDED'
on conflict (package_id) do update set due_at = excluded.due_at, requested_at = excluded.requested_at;

--rollback delete from programs where created_by = 'migration-B2-2';
