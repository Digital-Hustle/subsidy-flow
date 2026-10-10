#!/bin/sh
# Одна БД и одна роль на сервис (docs/ARCHITECTURE.md): сервисы не видят чужие таблицы.
set -eu
for svc in package docproc rules assistant keycloak; do
  upper=$(echo "$svc" | tr '[:lower:]' '[:upper:]')
  [ "$svc" = keycloak ] && var=KC_DB_PASSWORD || var="${upper}_DB_PASSWORD"
  pass=$(printenv "$var")
  psql -v ON_ERROR_STOP=1 -U postgres <<SQL
CREATE ROLE $svc LOGIN PASSWORD '$pass';
CREATE DATABASE $svc OWNER $svc;
REVOKE ALL ON DATABASE $svc FROM PUBLIC;
SQL
done
