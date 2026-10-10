--liquibase formatted sql

--changeset rules:20261009144600-demo-program context:demo,test
insert into programs (code, version, name, demo, params, checklist, created_by, created_at)
values ('DEMO-2026', 1, 'Демо-программа субсидирования 2026 (демо)', true,
        '{"rate": "0.50", "maxAmount": "3000000.00", "egrulMaxAgeDays": 30, "taxMaxAgeDays": 30, "costFrom": "2026-01-01", "costTo": "2026-12-31", "okvedAllowed": ["10.*", "25.*", "62.01"], "reimbursePaidOnly": true}'::jsonb,
        '[{"item": "APPLICATION", "title": "Заявление на предоставление субсидии", "kind": "REQUIRED", "types": ["APPLICATION"], "single": true}, {"item": "EGRUL", "title": "Выписка из ЕГРЮЛ/ЕГРИП", "kind": "REQUIRED", "types": ["EGRUL_EXTRACT"], "single": true}, {"item": "TAX", "title": "Справка ФНС об отсутствии задолженности", "kind": "REQUIRED", "types": ["TAX_CERTIFICATE"], "single": true}, {"item": "FINANCE", "title": "Бухгалтерская (финансовая) отчётность", "kind": "REQUIRED", "types": ["FIN_STATEMENTS"], "single": false}, {"item": "ESTIMATE", "title": "Смета затрат", "kind": "REQUIRED", "types": ["COST_ESTIMATE"], "single": true}, {"item": "COST_PROOF", "title": "Подтверждение затрат: договор или счёт", "kind": "ONE_OF", "types": ["CONTRACT", "INVOICE"], "single": false}, {"item": "PAYMENTS", "title": "Платёжные поручения об оплате затрат", "kind": "CONDITIONAL", "types": ["PAYMENT_ORDER"], "condition": "reimbursePaidOnly", "conditionText": "программа возмещает только оплаченные затраты", "single": false}]'::jsonb,
        'system', now());
--rollback delete from programs where code = 'DEMO-2026';
