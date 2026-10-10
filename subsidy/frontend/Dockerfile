# Сборка фронта и раздача статики через Caddy (infra/caddy/Caddyfile монтируется в compose).
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx vite build

FROM caddy:2.10-alpine
COPY --from=build /app/dist /srv
