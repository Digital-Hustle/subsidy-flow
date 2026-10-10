/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Режим стенда (ADR-021): dev — тестовый вход в сборке, prod — без него. Ставится build arg образа caddy. */
  readonly VITE_APP_MODE?: "dev" | "prod";
}
