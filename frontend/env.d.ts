/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_PRIVACY_CONTACT_EMAIL?: string
  readonly VITE_PUSH_ENABLED?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

// YouTube iframe API types
interface Window {
  YT: typeof YT
  onYouTubeIframeAPIReady: () => void
}

declare namespace YT {
  class Player {
    constructor(element: HTMLElement | string, options: PlayerOptions)
    getCurrentTime(): number
    getDuration(): number
    destroy(): void
  }

  interface PlayerOptions {
    events?: {
      onReady?: (event: PlayerEvent) => void
      onStateChange?: (event: PlayerEvent) => void
    }
  }

  interface PlayerEvent {
    target: Player
  }
}
