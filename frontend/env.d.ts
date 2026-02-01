/// <reference types="vite/client" />

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
