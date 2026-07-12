export interface MediaSessionActions {
  play: () => void
  pause: () => void
  previous: () => void
  next: () => void
}

export interface MediaSessionTrack {
  title: string
  artist: string
  album: string
}

type SessionPort = Pick<MediaSession, 'metadata' | 'setActionHandler'>
type MetadataFactory = (track: MediaSessionTrack) => MediaMetadata

export function browserMediaSession(): MediaSession | null {
  if (typeof navigator === 'undefined' || !('mediaSession' in navigator)) return null
  return navigator.mediaSession
}

/** Owns lock-screen metadata and OS transport handlers for the web player. */
export class MediaSessionBridge {
  constructor(
    actions: MediaSessionActions,
    private readonly session: SessionPort | null = browserMediaSession(),
    private readonly createMetadata: MetadataFactory = (track) => new MediaMetadata(track),
  ) {
    if (!session) return
    session.setActionHandler('play', actions.play)
    session.setActionHandler('pause', actions.pause)
    session.setActionHandler('previoustrack', actions.previous)
    session.setActionHandler('nexttrack', actions.next)
  }

  update(track: MediaSessionTrack): void {
    if (!this.session) return
    this.session.metadata = this.createMetadata(track)
  }
}
