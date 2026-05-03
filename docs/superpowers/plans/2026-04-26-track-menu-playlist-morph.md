# Track Menu → Playlist Picker Morph Transition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the jarring dismiss+reopen between `TrackMenuOverlay` and `AddToPlaylistOverlay` with an in-place morph: thumbnail stays, body crossfades, card height animates, back from playlist picker returns to main menu.

**Architecture:** Merge both overlays into a single `TrackMenuOverlay` with an internal `TrackMenuMode` state machine (`MAIN | PLAYLIST`). `switchProgress: Animatable` drives the thumbnail art handoff and size lerp; `AnimatedContent(mode)` drives body and header text crossfades; `animateContentSize()` handles card height. `AddToPlaylistOverlay.kt` is deleted.

**Tech Stack:** Kotlin + Jetpack Compose — `Animatable`, `AnimatedContent`, `animateContentSize`, `BackHandler`, `graphicsLayer`, `lerp`, Coil 3 `SubcomposeAsyncImage`

---

## File Map

| File | Action | What changes |
|------|--------|-------------|
| `app/.../ui/components/TrackMenuOverlay.kt` | **Rewrite** | Absorbs playlist picker, adds `TrackMenuMode`, `switchProgress`, `AnimatedContent` bodies |
| `app/.../ui/components/AddToPlaylistOverlay.kt` | **Delete** | Functionality merged into `TrackMenuOverlay` |
| `app/.../ui/LibraryScreen.kt` | **Modify** | Remove `AddToPlaylistOverlay` block + `playlistPickerTrack` state; update `TrackMenuOverlay` call |
| `app/.../ui/components/TrackListItem.kt` | **Modify** | Remove dead `onAddToPlaylist` param |
| `app/.../ui/components/TrackContextMenu.kt` | **Delete** | Superseded by overlay; never called in current codebase |

---

## Task 1: Add `TrackMenuMode` enum and update `TrackMenuOverlay` signature

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/TrackMenuOverlay.kt`

- [ ] **Step 1: Read the current file**

Read `app/src/main/java/com/laconical/player/ui/components/TrackMenuOverlay.kt` in full before editing.

- [ ] **Step 2: Add `TrackMenuMode` enum at the top of the file (before the composable)**

Add this after the package declaration and imports:

```kotlin
enum class TrackMenuMode { MAIN, PLAYLIST }
```

- [ ] **Step 3: Update the composable signature**

Replace:
```kotlin
@Composable
fun TrackMenuOverlay(
    track: Track,
    artStartOffsetPx: Offset,
    artStartSizePx: Float,
    isFavorite: Boolean,
    dominantColor: Color?,
    onDismiss: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onViewAlbum: (() -> Unit)?,
    onViewArtist: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
)
```

With:
```kotlin
@Composable
fun TrackMenuOverlay(
    track: Track,
    artStartOffsetPx: Offset,
    artStartSizePx: Float,
    isFavorite: Boolean,
    dominantColor: Color?,
    playlists: List<Playlist>,
    artTracks: Map<Long, List<Track>>,
    onDismiss: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onViewAlbum: (() -> Unit)?,
    onViewArtist: (() -> Unit)?,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreateNewPlaylist: () -> Unit,
)
```

- [ ] **Step 4: Add required imports**

Add these imports (alongside existing ones):
```kotlin
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.animateContentSize
import androidx.compose.animation.core.animateContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Add
import com.laconical.player.core.data.db.entity.Playlist
```

Note: `animateContentSize` is in `androidx.compose.foundation.layout` — use `Modifier.animateContentSize(...)`.

- [ ] **Step 5: Verify the file compiles (expected: error on `onAddToPlaylist` usage inside the body — will fix in Task 2)**

```bash
cd /home/btema2/smart-things/code/laconical-player && ./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|warning:" | head -20
```

Expected: compile errors referencing `onAddToPlaylist` usage and `playlistPickerTrack` — that's fine, fixed in later tasks.

---

## Task 2: Add state machine, `switchProgress`, and updated `BackHandler`

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/TrackMenuOverlay.kt`

- [ ] **Step 1: Add `switchProgress` and `mode` state inside the composable body**

Inside `TrackMenuOverlay`, after the existing `val progress = remember { Animatable(0f) }` line, add:

```kotlin
val switchProgress = remember { Animatable(0f) }
var mode by remember { mutableStateOf(TrackMenuMode.MAIN) }
```

Add `import androidx.compose.runtime.getValue` and `import androidx.compose.runtime.setValue` if not already present.

- [ ] **Step 2: Update the `dismiss()` local function to only work from MAIN mode**

The existing `dismiss()` stays unchanged — it animates `progress` to 0 then calls `onDismiss()`. No change needed.

- [ ] **Step 3: Replace the `BackHandler` with a mode-aware chain**

Replace:
```kotlin
BackHandler { dismiss() }
```

With:
```kotlin
BackHandler {
    if (mode == TrackMenuMode.PLAYLIST) {
        mode = TrackMenuMode.MAIN
        scope.launch { switchProgress.animateTo(0f, tween(250, easing = FastOutSlowInEasing)) }
    } else {
        dismiss()
    }
}
```

---

## Task 3: Implement thumbnail handoff (floating art fade-out + in-card art fade-in)

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/TrackMenuOverlay.kt`

- [ ] **Step 1: Replace the ghost Box in the header with a real `SubcomposeAsyncImage`**

The current header has a transparent ghost Box:
```kotlin
Box(
    modifier = Modifier
        .size(64.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(Color(0x22FFFFFF))
        .onGloballyPositioned { coords ->
            targetOffsetPx = coords.positionInRoot()
        },
)
```

Replace it with:
```kotlin
val sp = switchProgress.value
val inCardSize = lerp(64f, 56f, sp).dp
val inCardCorner = lerp(14f, 12f, sp).dp
val inCardAlpha = (sp * 4f).coerceIn(0f, 1f)

Box(
    modifier = Modifier
        .size(inCardSize)
        .clip(RoundedCornerShape(inCardCorner))
        .background(Color(0x22FFFFFF))
        .graphicsLayer { alpha = inCardAlpha }
        .onGloballyPositioned { coords ->
            targetOffsetPx = coords.positionInRoot()
        },
    contentAlignment = Alignment.Center,
) {
    SubcomposeAsyncImage(
        model = remember(track.mediaUri) { AudioArtData(track.mediaUri) },
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        error = {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color(0xFF555555),
            )
        },
    )
}
```

- [ ] **Step 2: Fade out the floating art overlay as `switchProgress` rises**

Find the existing floating art `Box` near the bottom of `TrackMenuOverlay` (the one using `offset(x = artLeft, y = artTop)`). Add a `graphicsLayer` to fade it out:

```kotlin
val floatingArtAlpha = lerp(1f, 0f, (switchProgress.value * 4f).coerceIn(0f, 1f))

Box(
    modifier = Modifier
        .offset(x = artLeft, y = artTop)
        .size(artSize)
        .clip(RoundedCornerShape(artCorner))
        .graphicsLayer { alpha = floatingArtAlpha },
) {
    // existing SubcomposeAsyncImage unchanged
}
```

---

## Task 4: Wrap menu body in `AnimatedContent` and add `animateContentSize` to the card

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/TrackMenuOverlay.kt`

- [ ] **Step 1: Add `animateContentSize` to the card `Column`**

Find the outer card `Column` modifier chain:
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .graphicsLayer { ... }
        .clip(RoundedCornerShape(20.dp)),
)
```

Add `animateContentSize` before `.clip`:
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .graphicsLayer { ... }
        .animateContentSize(animationSpec = tween(280, easing = FastOutSlowInEasing))
        .clip(RoundedCornerShape(20.dp)),
)
```

- [ ] **Step 2: Extract the main menu rows into a private composable `MainMenuBody`**

Add a new private composable at the bottom of the file (before `MenuRow`):

```kotlin
@Composable
private fun MainMenuBody(
    isFavorite: Boolean,
    onViewAlbum: (() -> Unit)?,
    onViewArtist: (() -> Unit)?,
    track: Track,
    menuBg: Color,
    onFavoriteClick: () -> Unit,
    onViewAlbumClick: () -> Unit,
    onViewArtistClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
) {
    MenuRow(
        icon = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
        label = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
        iconTint = if (isFavorite) Color(0xFFE84B7A) else Color.White,
        background = menuBg,
        onClick = onFavoriteClick,
    )
    if (onViewAlbum != null) {
        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF1E1E28))
        MenuRow(
            icon = Icons.Default.Album,
            label = "Go to Album",
            sublabel = track.album,
            background = menuBg,
            onClick = onViewAlbumClick,
        )
    }
    if (onViewArtist != null) {
        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF1E1E28))
        MenuRow(
            icon = Icons.Default.Person,
            label = "Go to Artist",
            sublabel = track.artist,
            background = menuBg,
            onClick = onViewArtistClick,
        )
    }
    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF1E1E28))
    MenuRow(
        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
        label = "Add to Playlist",
        background = menuBg,
        bottomCorner = true,
        onClick = onAddToPlaylistClick,
    )
}
```

- [ ] **Step 3: Add a private `PlaylistPickerBody` composable**

Add after `MainMenuBody`:

```kotlin
@Composable
private fun PlaylistPickerBody(
    playlists: List<Playlist>,
    artTracks: Map<Long, List<Track>>,
    menuBg: Color,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreateNew: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .background(menuBg)
            .heightIn(max = 280.dp),
    ) {
        if (playlists.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No playlists yet",
                        color = Color(0xFF555555),
                        fontSize = 14.sp,
                    )
                }
            }
        } else {
            itemsIndexed(playlists, key = { _, p -> p.id }) { index, playlist ->
                if (index > 0) {
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF1E1E28))
                }
                PlaylistPickerRow(
                    playlist = playlist,
                    artTracks = artTracks[playlist.id] ?: emptyList(),
                    background = menuBg,
                    onClick = { onSelectPlaylist(playlist) },
                )
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF1E1E28))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(menuBg)
            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White),
                onClick = onCreateNew,
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = Color(0xFF7C6FE0),
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = "New Playlist",
            color = Color(0xFF7C6FE0),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
```

Also add a `PlaylistPickerRow` private composable (moved from `AddToPlaylistOverlay.kt`):

```kotlin
@Composable
private fun PlaylistPickerRow(
    playlist: Playlist,
    artTracks: List<Track>,
    background: Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White),
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        PlaylistCoverMosaic(
            tracks = artTracks,
            size = 48.dp,
            cornerRadius = 10.dp,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = playlist.name,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
```

- [ ] **Step 4: Replace the existing menu rows in the card body with `AnimatedContent(mode)`**

Find the existing body content inside the card `Column` — everything after the header `Row` and first `HorizontalDivider`. Replace the three `MenuRow` blocks with:

```kotlin
HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF2A2A35))

AnimatedContent(
    targetState = mode,
    transitionSpec = {
        fadeIn(tween(200, delayMillis = 100)) togetherWith fadeOut(tween(100))
    },
    label = "menu_body",
) { currentMode ->
    val menuBg = Color(0xFF12121A)
    when (currentMode) {
        TrackMenuMode.MAIN -> MainMenuBody(
            isFavorite = isFavorite,
            onViewAlbum = onViewAlbum,
            onViewArtist = onViewArtist,
            track = track,
            menuBg = menuBg,
            onFavoriteClick = { onFavoriteToggle(); dismiss() },
            onViewAlbumClick = { onViewAlbum?.invoke(); dismiss() },
            onViewArtistClick = { onViewArtist?.invoke(); dismiss() },
            onAddToPlaylistClick = {
                mode = TrackMenuMode.PLAYLIST
                scope.launch { switchProgress.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }
            },
        )
        TrackMenuMode.PLAYLIST -> PlaylistPickerBody(
            playlists = playlists,
            artTracks = artTracks,
            menuBg = menuBg,
            onSelectPlaylist = { playlist ->
                onSelectPlaylist(playlist)
                dismiss()
            },
            onCreateNew = {
                onCreateNewPlaylist()
                dismiss()
            },
        )
    }
}
```

---

## Task 5: Crossfade the header text

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/TrackMenuOverlay.kt`

- [ ] **Step 1: Find the header text `Column` inside the card header `Row`**

It currently looks like:
```kotlin
Column(modifier = Modifier.weight(1f)) {
    Text(text = track.title, ...)
    Spacer(...)
    Text(text = track.artist, ...)
    Spacer(...)
    Text(text = track.album, ...)
}
```

- [ ] **Step 2: Wrap with `AnimatedContent(mode)`**

Replace the text `Column` with:

```kotlin
AnimatedContent(
    targetState = mode,
    transitionSpec = {
        fadeIn(tween(200, delayMillis = 80)) togetherWith fadeOut(tween(80))
    },
    label = "header_text",
    modifier = Modifier.weight(1f),
) { currentMode ->
    when (currentMode) {
        TrackMenuMode.MAIN -> Column {
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                color = Color(0xFFAAAAAA),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.album,
                color = Color(0xFF666666),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TrackMenuMode.PLAYLIST -> Column {
            Text(
                text = "ADD TO PLAYLIST",
                color = Color(0xFF888888),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                color = Color(0xFFAAAAAA),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
```

- [ ] **Step 3: Verify the file compiles**

```bash
cd /home/btema2/smart-things/code/laconical-player && ./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:" | head -20
```

Expected: errors only from `LibraryScreen.kt` referencing the old signature — not from `TrackMenuOverlay.kt` itself.

---

## Task 6: Update `LibraryScreen.kt`

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt`

- [ ] **Step 1: Read the relevant section of LibraryScreen**

Read lines 140–150 (state vars) and lines 553–615 (overlay section).

- [ ] **Step 2: Remove `playlistPickerTrack` state variable**

Remove line:
```kotlin
var playlistPickerTrack by remember { mutableStateOf<Track?>(null) }
```

Keep `pendingNewPlaylistTrack` and `showCreateForPicker` — still needed for the create-playlist sheet.

- [ ] **Step 3: Remove the `AddToPlaylistOverlay` import and block**

Remove import:
```kotlin
import com.laconical.player.ui.components.AddToPlaylistOverlay
```

Remove the entire `AddToPlaylistOverlay` block (lines ~570–588):
```kotlin
// ── Add to playlist overlay ────────────────────────────────────────
playlistPickerTrack?.let { track ->
    AddToPlaylistOverlay(
        ...
    )
}
```

- [ ] **Step 4: Update the `TrackMenuOverlay` call with new parameters**

Replace:
```kotlin
contextMenuTrack?.let { track ->
    TrackMenuOverlay(
        track = track,
        artStartOffsetPx = contextMenuArtOffset,
        artStartSizePx = contextMenuArtSize,
        isFavorite = favoriteIds.contains(track.id),
        dominantColor = playingTrackDominantColor,
        onDismiss = { contextMenuTrack = null },
        onFavoriteToggle = { viewModel.toggleFavorite(track.id) },
        onViewAlbum = {
            contextMenuTrack = null
            navController.navigate(NavRoute.albumDetailRoute(track.album))
        },
        onViewArtist = {
            contextMenuTrack = null
            navController.navigate(NavRoute.artistDetailRoute(track.artist))
        },
        onAddToPlaylist = {
            contextMenuTrack = null
            playlistPickerTrack = track
        },
    )
}
```

With:
```kotlin
contextMenuTrack?.let { track ->
    TrackMenuOverlay(
        track = track,
        artStartOffsetPx = contextMenuArtOffset,
        artStartSizePx = contextMenuArtSize,
        isFavorite = favoriteIds.contains(track.id),
        dominantColor = playingTrackDominantColor,
        playlists = playlists,
        artTracks = playlistArtTracks,
        onDismiss = { contextMenuTrack = null },
        onFavoriteToggle = { viewModel.toggleFavorite(track.id) },
        onViewAlbum = {
            contextMenuTrack = null
            navController.navigate(NavRoute.albumDetailRoute(track.album))
        },
        onViewArtist = {
            contextMenuTrack = null
            navController.navigate(NavRoute.artistDetailRoute(track.artist))
        },
        onSelectPlaylist = { playlist ->
            viewModel.addTrackToPlaylist(track.id, playlist.id)
            contextMenuTrack = null
        },
        onCreateNewPlaylist = {
            pendingNewPlaylistTrack = track
            contextMenuTrack = null
            showCreateForPicker = true
        },
    )
}
```

- [ ] **Step 5: Remove the dead `onAddToPlaylist` callback on the `TrackListItem` in the tracks list**

Find (around line 361):
```kotlin
onAddToPlaylist = {
    playlistPickerTrack = track
},
```

Remove this parameter entirely from the `TrackListItem(...)` call.

- [ ] **Step 6: Verify full build**

```bash
cd /home/btema2/smart-things/code/laconical-player && ./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:" | head -20
```

Expected: no errors.

---

## Task 7: Delete dead files

**Files:**
- Delete: `app/src/main/java/com/laconical/player/ui/components/AddToPlaylistOverlay.kt`
- Delete: `app/src/main/java/com/laconical/player/ui/components/TrackContextMenu.kt`

- [ ] **Step 1: Verify `TrackContextMenu` is not imported anywhere**

```bash
grep -r "TrackContextMenu\|import.*TrackContextMenu" /home/btema2/smart-things/code/laconical-player/app/src/main/java/
```

Expected: zero results (or only the file itself).

- [ ] **Step 2: Verify `AddToPlaylistOverlay` is not imported anywhere**

```bash
grep -r "AddToPlaylistOverlay\|import.*AddToPlaylistOverlay" /home/btema2/smart-things/code/laconical-player/app/src/main/java/
```

Expected: zero results.

- [ ] **Step 3: Delete both files**

```bash
rm /home/btema2/smart-things/code/laconical-player/app/src/main/java/com/laconical/player/ui/components/AddToPlaylistOverlay.kt
rm /home/btema2/smart-things/code/laconical-player/app/src/main/java/com/laconical/player/ui/components/TrackContextMenu.kt
```

---

## Task 8: Remove dead `onAddToPlaylist` param from `TrackListItem`

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/TrackListItem.kt`

- [ ] **Step 1: Remove the parameter from the function signature**

Find:
```kotlin
onAddToPlaylist: (() -> Unit)? = null,
```

Remove this line from `TrackListItem`'s parameter list.

- [ ] **Step 2: Verify no callers pass `onAddToPlaylist`**

```bash
grep -rn "onAddToPlaylist" /home/btema2/smart-things/code/laconical-player/app/src/main/java/
```

Expected: zero results.

- [ ] **Step 3: Full build and lint**

```bash
cd /home/btema2/smart-things/code/laconical-player && ./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:" | head -20
```

Expected: no errors.

---

## Task 9: Build, verify, commit

- [ ] **Step 1: Full debug build**

```bash
cd /home/btema2/smart-things/code/laconical-player && ./gradlew assembleDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Manual verification checklist**

Install on device/emulator (`./gradlew installDebug`) and verify:

1. Tap 3-dots on any track → overlay opens with morphing thumbnail ✓
2. Tap "Add to Playlist" → card morphs in-place: thumbnail stays, text crossfades to "ADD TO PLAYLIST" label, rows fade out, playlist list fades in, card height animates ✓
3. While in playlist mode, press system Back → reverse morph back to main menu rows ✓
4. Press Back again → overlay dismisses ✓
5. Tap scrim → overlay dismisses ✓
6. Select a playlist → track added, overlay closes ✓
7. Tap "New Playlist" → overlay closes, create-playlist sheet opens ✓
8. Verify other menu items still work: Favorites toggle, Go to Album, Go to Artist ✓

- [ ] **Step 3: Commit**

```bash
cd /home/btema2/smart-things/code/laconical-player
git add app/src/main/java/com/laconical/player/ui/components/TrackMenuOverlay.kt \
        app/src/main/java/com/laconical/player/ui/components/TrackListItem.kt \
        app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
git rm app/src/main/java/com/laconical/player/ui/components/AddToPlaylistOverlay.kt \
       app/src/main/java/com/laconical/player/ui/components/TrackContextMenu.kt
git commit -m "feat: morph track menu → playlist picker in-place instead of dismiss+reopen"
```
