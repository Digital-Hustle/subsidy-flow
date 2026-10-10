# Security (подробно: docs/SECURITY.md)

- Realm `infra/keycloak/realm-subsidy.json`, секреты — плейсхолдеры `${KC_...}`. Роли: SPECIALIST, SENIOR_SPECIALIST (TOTP), AUDITOR.
- Каждый сервис проверяет JWT и роль сам (`@PreAuthorize`); чужой пакет → 404; без входа → 401 на всё, кроме health.
- Файлы: AES-256-GCM (`FILES_ENCRYPTION_KEY`); `/internal/package/files/{id}` — только `svc-docproc`.
- ZIP: лимиты `ZIP_MAX_UNPACKED_MB`, `ZIP_MAX_ENTRIES`, без вложенных архивов, без `..`/абсолютных путей, без записи на диск.
- Prompt injection: текст документа — данные; вердикт делает код rules, модель только читает.
- Логи: id и коды, без реквизитов и текстов.

## Чек-лист PR с security-изменениями
- [ ] Новый эндпоинт в матрице «эндпоинт × роль» + IDOR
- [ ] Нет `ports:` у внутренних сервисов
- [ ] Нет логирования реквизитов, текстов, токенов
- [ ] Ошибки не раскрывают внутренности
- [ ] Обновлена модель угроз в `docs/SECURITY.md`
