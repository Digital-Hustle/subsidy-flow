# Общий почтовый ящик команды

Проект шлёт письма с одного адреса:

**no-reply@digital-hustle.ru**

Ящик используется для входа по коду, приглашений сотрудников и уведомлений.

## Переменные

| Переменная | Значение | Зачем |
|---|---|---|
| `MAIL_FROM` | `no-reply@digital-hustle.ru` | адрес отправителя |
| `SMTP_HOST` | хост провайдера | SMTP-сервер |
| `SMTP_PORT` | `465` (SSL) или `587` (STARTTLS) | порт |
| `SMTP_USERNAME` | `no-reply@digital-hustle.ru` | логин |
| `SMTP_PASSWORD` | пароль ящика | **секрет, в git не кладём** |
| `SMTP_PROTOCOL` | `smtps` для 465, `smtp` для 587 | протокол |
| `SMTP_STARTTLS` | `false` для 465, `true` для 587 | STARTTLS |

## Где лежит

Файл `mail.env` (формат `KEY=value`) — рядом со стендом, в git его нет (`.gitignore`). Пример: `subsidy/infra/mail.env.example`.

- Локально: `cp subsidy/infra/mail.env.example subsidy/infra/mail.env` и заполнить.
- На сервере стендов: файл `/root/shared/mail.env`, стенд подключает его через `MAIL_ENV_FILE=/root/shared/mail.env` в своём `infra/.env`.

## Как подключает кейс

В `docker-compose.yml` сервиса, который шлёт письма:

```yaml
    env_file:
      - path: ${MAIL_ENV_FILE:-./mail.env}
        required: false
```

`required: false` — стенд поднимается и без почты. Значения в секции `environment` перебивают `env_file`, поэтому `MAIL_FROM`/`SMTP_*` там не дублируем.

## Если SMTP ещё не настроен

Кейс должен работать и без почты: в демо-режиме код входа возвращается в ответе API и показывается на странице входа (как в песочнице), отправка письма при пустом `SMTP_HOST` только пишет предупреждение в лог. В боевом режиме без SMTP вход по коду невозможен — это ожидаемо.

## Оформление письма

HTML + текстовая версия, фирменные цвета, крупный код, срок действия, подпись «Если вы не запрашивали вход — просто игнорируйте письмо». Пример разметки — `subsidy/services/package/src/main/resources/mail/layout.html`.
