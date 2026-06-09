# Progressive Track Loading — Design Spec

**Date:** 2026-06-09  
**Status:** Approved

## Problem

On first launch, the system permission dialog appears. After the user grants media access, the
app shows 0 songs until the user closes and reopens the app. Root cause: `MainViewModel.init`
calls `loadTracks()` at startup before permission is granted, so `_allTracks` stays empty.
When the launcher callback sets `hasPermission = true` it never re-triggers loading.

## Goals

1. Show songs immediately after permission is granted — no restart required.
2. Tracks appear progressively as MediaStore is indexed (not all at once) with a fluid
   fade + slide-up cascade animation per item.

## Out of Scope

- Showing a progress bar / percentage counter.
- Any changes to Albums, Artists, Playlists screens (they derive from `_allTracks` already).

---

## Section 1 — Bug Fix

**File:** `app/.../ui/LibraryScreen.kt`

Add one line to the permission launcher callback:

```kotlin
val launcher = rememberLauncherForActivityResult(...) { isGranted ->
    hasPermission = isGranted
    if (isGranted) viewModel.loadTracks()   // triggers reload after grant
}
```

`loadTracks()` is already safe to call multiple times. The `init` block continues to call it
at startup for the normal (permission already granted) code path.

---

## Section 2 — Progressive Flow Loading

### `MediaRepository` interface

Add `getTracksFlow()` alongside the existing `getTracks()`. Keep `getTracks()` — it is used
by existing tests and avoids a breaking interface change.

```kotlin
interface MediaRepository {
    suspend fun getTracks(): List<Track>
    fun getTracksFlow(batchSize: Int = 25): Flow<List<Track>>
}
```

`getTracksFlow()` emits a **growing snapshot** on each emission (full list so far, not a delta).
This matches the `StateFlow<List<Track>>` pattern in `MainViewModel` — consumers replace, not merge.

### `LocalMediaRepositoryImpl`

Walk the same MediaStore cursor as `getTracks()`, emitting after every `batchSize` rows plus
a final emit for the remainder:

```kotlin
override fun getTracksFlow(batchSize: Int): Flow<List<Track>> = flow {
    val tracks = mutableListOf<Track>()
    // identical projection / selection / sortOrder as getTracks()
    context.contentResolver.query(...)?.use { cursor ->
        // identical column index setup
        while (cursor.moveToNext()) {
            tracks.add(/* read row */)
            if (tracks.size % batchSize == 0) emit(tracks.toList())
        }
    }
    if (tracks.isNotEmpty()) emit(tracks.toList())
}.flowOn(Dispatchers.IO)
```

### `MainViewModel.loadTracks()`

Replace the one-shot `repository.getTracks()` call with flow collection. Housekeeping
(`purgeStaleTrackIds`, `restorePlaybackSession`, `startSessionPersistence`) runs only after
the final batch — same behaviour as today, just deferred:

```kotlin
fun loadTracks() {
    viewModelScope.launch {
        _isLoadingTracks.value = true
        repository.getTracksFlow().collect { batch ->
            _allTracks.value = batch
        }
        _isLoadingTracks.value = false
        val liveIds = _allTracks.value.map { it.id }.toSet()
        if (liveIds.isNotEmpty()) userDataRepository.purgeStaleTrackIds(liveIds)
        restorePlaybackSession(_allTracks.value)
        startSessionPersistence()
    }
    startAmplitudeTicker()
    startAutoAdvanceCollector()
}
```

Add to `MainViewModel`:

```kotlin
private val _isLoadingTracks = MutableStateFlow(false)
val isLoadingTracks: StateFlow<Boolean> = _isLoadingTracks.asStateFlow()
```

---

## Section 3 — Track Appearance Animation

### Existing infrastructure

`staggeredEntrance(index)` already provides fade + slide-up (25 ms stagger, capped at 8
items). No new animation primitives are needed.

### Gap: stagger cap breaks batch-load cascade

During initial load, items at index > 8 get 0 ms delay, causing batches to land as a single
wall rather than a cascade. Fix: add a `isLoadingIn` mode that applies full per-item stagger
with a modulo so delay stays reasonable at any index.

### `StaggeredEntrance.kt` change

```kotlin
fun Modifier.staggeredEntrance(index: Int, isLoadingIn: Boolean = false): Modifier
```

Behaviour:
- `isLoadingIn = false` (default): existing two-speed behaviour unchanged — no regression
  for scroll-in items.
- `isLoadingIn = true`: stagger delay = `(index % 25) * 25 ms`; duration = 150 ms always.
  Modulo keeps max delay at 600 ms regardless of total track count.

### Wiring in `LibraryScreen`

Collect `isLoadingTracks` from the ViewModel and pass to the track list:

```kotlin
val isLoadingTracks by viewModel.isLoadingTracks.collectAsState()
// ...
.staggeredEntrance(index, isLoadingIn = isLoadingTracks)
```

---

## Data Flow Summary

```
Permission granted
  → launcher callback sets hasPermission = true
  → viewModel.loadTracks() called
      → _isLoadingTracks = true
      → getTracksFlow() walks MediaStore cursor
          → emit batch 1 (25 tracks) → _allTracks updated → LazyColumn adds items → staggeredEntrance fires
          → emit batch 2 (50 tracks) → new items appended → staggeredEntrance fires on new items
          → ...
          → emit final batch
      → _isLoadingTracks = false
      → purgeStaleTrackIds, restorePlaybackSession, startSessionPersistence
```

---

## Files Changed

| File | Change |
|------|--------|
| `core/data/.../MediaRepository.kt` | Add `getTracksFlow()` to interface |
| `core/data/.../LocalMediaRepositoryImpl.kt` | Implement `getTracksFlow()` |
| `app/.../ui/MainViewModel.kt` | Use flow in `loadTracks()`; add `_isLoadingTracks` |
| `app/.../ui/LibraryScreen.kt` | Call `loadTracks()` in launcher callback; pass `isLoadingIn` |
| `app/.../ui/components/StaggeredEntrance.kt` | Add `isLoadingIn` parameter |

## Testing

- Grant permission on fresh install → tracks appear in waves without restart.
- Re-open app with permission already granted → normal load, no animation regression.
- Fast permission grant on a device with many tracks (500+) → stagger stays smooth (modulo keeps max delay ≤ 600 ms).
- Existing `staggeredEntrance` scroll behaviour unchanged (default `isLoadingIn = false`).
