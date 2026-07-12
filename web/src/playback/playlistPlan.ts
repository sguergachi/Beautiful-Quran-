import {
  audioUrl,
  basmalahAudioUrl,
  type Reciter,
  type SurahContent,
} from '../data/models'
import {
  BASMALAH_PLAYLIST_AYAH,
  surahOpensWithBasmalahPreface,
} from '../domain/Basmalah'

export interface PlaylistItem {
  ayah: number
  url: string
}

/** Pure ayah playlist construction, including the optional chapter preface. */
export function buildPlaylist(
  content: SurahContent,
  reciter: Reciter,
  startAyah = 1,
): PlaylistItem[] {
  const playlist: PlaylistItem[] = []
  if (startAyah <= 1 && surahOpensWithBasmalahPreface(content.surah.id)) {
    playlist.push({
      ayah: BASMALAH_PLAYLIST_AYAH,
      url: basmalahAudioUrl(reciter),
    })
  }

  const firstAyah = Math.max(1, startAyah)
  for (const ayah of content.ayahs) {
    if (ayah.number < firstAyah) continue
    playlist.push({
      ayah: ayah.number,
      url: audioUrl(reciter, content.surah.id, ayah.number),
    })
  }
  return playlist
}
