/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Absolute WebSocket URL of the API when the host can't proxy /ws (e.g. Vercel). */
  readonly VITE_WS_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
