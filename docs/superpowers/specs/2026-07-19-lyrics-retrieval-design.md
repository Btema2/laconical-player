# Lyrics Retrieval — Design

**Date:** 2026-07-19 · **Branch:** `feature/lyrics`
**Credit:** architecture inspired by [PixelPlayerOSS](https://github.com/PixelPlayerHQ/PixelPlayerOSS) and [Namida](https://github.com/namidaco/namida) — chain layering, scored remote matching, instrumental-marker persistence. No code copied.

## Goal

Retrieve and display lyrics (synced LRC + plain) for local tracks. Privacy-first: all network access is opt-in, default OFF. UI intentionally basic — architecture is the deliverable.

## Retrieval chain

```
in-memory LRU(100) → Room (lyrics table) → local sources → LRCLIB API (opt-in)
                                            ├ embedded tags
                                            └ sibling .lrc
```

Decisions (approved 2026-07-19):

- **No JSON disk-cache layer** (Room already persists; the extra layer in PixelPlayer is legacy).
- **No Google-scraping fallback** — brittle, ToS-hostile, worst privacy profile.
- **Embedded tags:** hand-written pure-JVM ID3v2.2/2.3/2.4 USLT/SYLT parser in `core:model`
  (Media3's `Id3Decoder` does **not** parse USLT — MP3, the dominant format, needs it) plus
  Media3 `MetadataRetriever` for FLAC/OGG VorbisComment `LYRICS`/`UNSYNCEDLYRICS`.
- **Fetch policy:** track changes run the local chain only (`lyricsJob`, cancel-before-restart
  like waveform/color). LRCLIB fires solely from lyrics-sheet open or manual refresh, so
  skipping tracks never leaks listening history.
- **Source priority preference:** `EMBEDDED_FIRST` (default) / `LOCAL_FIRST` / `API_FIRST` —
  reorders only the fetch stage; memory/Room caches always win.
- **Persistence policy:** Found + Instrumental persisted to Room (raw LRC text, re-parsed on
  read); NotFound never persisted so tracks can gain lyrics later; NetworkError never cached.
- **LRCLIB matching:** `/api/get` exact (title+artist+album+duration) → `/api/search` scored
  (title/artist exact-vs-contains, duration tolerance ±5 s synced / ±15 s plain, synced bonus,
  acceptance threshold — never force a bad match) → cleaned-title-only search.
  Proper User-Agent; single-in-flight + 300 ms gap.

## Module structure

- `core:model/lyrics` — `Lyrics`/`LyricsLine`/`LyricsSource`, `LrcParser`, `Id3UsltParser`,
  `currentLineIndex` (all pure JVM, unit-tested).
- `core:data/lyrics` — sources, `LrcLibClient`+`LrcLibScorer`, `LyricsRepository[Impl]`,
  `LyricsSettingsStore` (DataStore `lyrics_settings`); `db/` gains `LyricsEntity`+`LyricsDao`,
  DB v1→v2 with the codebase's first explicit `Migration`.
- `core:media` — `Media3EmbeddedFormatLyricsExtractor` implements the `core:data` port
  `EmbeddedFormatLyricsExtractor`; new acyclic dep edge `core:media → core:data` keeps Media3
  out of the data layer. Media3 1.10 notes: instance `MetadataRetriever.Builder` API,
  `androidx.media3.extractor.metadata.vorbis.VorbisComment` (`.flac` variant removed).
- `app` — `LyricsUiState` + flows in `MainViewModel`; `LyricsSheet` overlay composed last in
  LibraryScreen's outer Box (TrackMenuOverlay pattern, **not** the morph system); Settings
  "Lyrics" card (first Switch in the codebase + priority chips).

## Known limitation

Sibling `.lrc` reads are best-effort on API 29+ (scoped storage blocks non-media files);
direct read works on API ≤ 28, MediaStore-Files fallback otherwise. Full support would need
MANAGE_EXTERNAL_STORAGE or an SAF folder grant — deliberately deferred.

## Verification

`:core:model:test`, `:core:data:test` (incl. `MigrationTestHelper` v1→v2), `:core:media:test`,
`assembleDebug`, `lint`; manual device pass per plan (embedded MP3 offline, .lrc pickup,
opt-in LRCLIB fetch, synced highlight, tap-to-seek, refresh, instrumental, disabled-hint).
