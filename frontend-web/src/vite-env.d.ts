/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** URL pública del backend en producción (ej. https://api.puntonubepos.com). Vacío en dev. */
  readonly VITE_API_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
