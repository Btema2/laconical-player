# Playlist Menu Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the native `DropdownMenu` in `PlaylistRow` with an animated full-screen overlay (`PlaylistMenuOverlay`) that visually matches `TrackMenuOverlay` — dark scrim, centered card, morphing mosaic art from row position.

**Architecture:** New `PlaylistMenuOverlay` composable in `ui/components/` uses the same ghost-overlay morph pattern as `TrackMenuOverlay` — a floating `PlaylistCoverMosaic` lerps from its row root-space position into the card header, then the in-card ghost fades in. State is lifted to `LibraryScreen` (outermost Box) so root-space offsets are correct. `PlaylistsScreen` loses its internal dropdown/dialogs and gains an `onMenuOpen` callback. `LibraryScreen` gains a `PlaylistsViewModel` reference (via `hiltViewModel()`) to handle rename/delete, and shows existing `PlaylistBottomSheet` + `AlertDialog` for those actions.

**Tech Stack:** Kotlin · Jetpack Compose · Hilt · `androidx.compose.animation` · `androidx.compose.ui.util.lerp`

---

### Task 1: Create `PlaylistMenuOverlay.kt`

**Files:**
- Create: `app/src/main/java/com/laconical/player/ui/components/PlaylistMenuOverlay.kt`

- [ ] **Step 1: Create the file with the full overlay implementation**

```kotlin
package com.laconical.player.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.model.Track
import kotlinx.coroutines.launch

/**
 * Full-screen overlay for playlist context actions (Rename, Delete).
 *
 * Mirrors TrackMenuOverlay layout and morph mechanic:
 * - PlaylistCoverMosaic floats from [artStartOffsetPx]/[artStartSizePx] (root-space
 *   coords of the tapped row mosaic) to the card header ghost position.
 * - In-card ghost fades in as the floating mosaic fades out.
 *
 * Must be placed in the outermost Box of LibraryScreen so offsets == root offsets.
 */
@Composable
fun PlaylistMenuOverlay(
    playlist: Playlist,
    artTracks: List<Track>,
    artStartOffsetPx: Offset,
    artStartSizePx: Float,
    dominantColor: Color?,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }

    fun dismiss() {
        scope.launch {
            progress.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }

    BackHandler { dismiss() }

    val prog = progress.value

    // Ghost target: initialized to artStartOffsetPx so mosaic renders at source before measurement
    var targetOffsetPx by remember { mutableStateOf(artStartOffsetPx) }
    val targetSizePx = with(density) { 64.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Scrim ──────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (prog * 1.6f).coerceIn(0f, 1f) }
                .background(Color(0xCC000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { dismiss() },
                ),
        )

        // ── Menu card (centered) ────────────────────────────────────────────
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val headerBg = if (dominantColor != null) {
                Color(
                    red   = (dominantColor.red   * 0.3f + 0.05f).coerceIn(0f, 1f),
                    green = (dominantColor.green * 0.3f + 0.05f).coerceIn(0f, 1f),
                    blue  = (dominantColor.blue  * 0.3f + 0.07f).coerceIn(0f, 1f),
                    alpha = 1f,
                )
            } else Color(0xFF1A1A24)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .graphicsLayer {
                        alpha = prog
                        scaleX = lerp(0.92f, 1f, prog)
                        scaleY = lerp(0.92f, 1f, prog)
                        translationY = lerp(48f, 0f, prog)
                    }
                    .clip(RoundedCornerShape(20.dp)),
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBg)
                        .padding(16.dp),
                ) {
                    // In-card ghost mosaic — fades in as floating fades out
                    val inCardAlpha = (prog * 4f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x22FFFFFF))
                            .graphicsLayer { alpha = inCardAlpha }
                            .onGloballyPositioned { coords ->
                                targetOffsetPx = coords.positionInRoot()
                            },
                    ) {
                        PlaylistCoverMosaic(
                            tracks = artTracks,
                            size = 64.dp,
                            cornerRadius = 14.dp,
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val trackCount = artTracks.size
                        if (trackCount > 0) {
                            Text(
                                text = "$trackCount track${if (trackCount == 1) "" else "s"}",
                                color = Color(0xFF888888),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF2A2A35))

                val menuBg = Color(0xFF12121A)

                PlaylistMenuRow(
                    icon = Icons.Default.DriveFileRenameOutline,
                    label = "Rename",
                    iconTint = Color.White,
                    background = menuBg,
                    onClick = {
                        dismiss()
                        onRename()
                    },
                )
                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF1E1E28))
                PlaylistMenuRow(
                    icon = Icons.Default.Delete,
                    label = "Delete Playlist",
                    iconTint = Color(0xFFEF4444),
                    background = menuBg,
                    bottomCorner = true,
                    onClick = {
                        dismiss()
                        onDelete()
                    },
                )
            }
        }

        // ── Floating mosaic — drawn above card so it morphs in ───────────────
        val floatingAlpha = lerp(1f, 0f, (prog * 4f).coerceIn(0f, 1f))
        val artLeft   = with(density) { lerp(artStartOffsetPx.x, targetOffsetPx.x, prog).toDp() }
        val artTop    = with(density) { lerp(artStartOffsetPx.y, targetOffsetPx.y, prog).toDp() }
        val artSize   = with(density) { lerp(artStartSizePx, targetSizePx, prog).toDp() }
        val artCorner = lerp(10f, 14f, prog).dp

        Box(
            modifier = Modifier
                .offset(x = artLeft, y = artTop)
                .size(artSize)
                .clip(RoundedCornerShape(artCorner))
                .graphicsLayer { alpha = floatingAlpha },
        ) {
            PlaylistCoverMosaic(
                tracks = artTracks,
                size = artSize,
                cornerRadius = artCorner,
            )
        }
    }
}

@Composable
private fun PlaylistMenuRow(
    icon: ImageVector,
    label: String,
    iconTint: Color = Color.White,
    background: Color,
    bottomCorner: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = if (bottomCorner)
        RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
    else
        RoundedCornerShape(0.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White),
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = iconTint,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
```

- [ ] **Step 2: Verify the file compiles in isolation**

```bash
cd /home/btema2/smart-things/code/Laconical-Player
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|warning:|BUILD"
```

Expected: `BUILD SUCCESSFUL` or only deprecation warnings — no errors referencing `PlaylistMenuOverlay.kt`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/components/PlaylistMenuOverlay.kt
git commit -m "feat: add PlaylistMenuOverlay with morphing mosaic and animated card"
```

---

### Task 2: Update `PlaylistsScreen` — remove dropdown, add `onMenuOpen` callback

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/screens/PlaylistsScreen.kt`

The goal: `PlaylistsScreen` no longer owns the context menu. It gains `onMenuOpen`, passes it down to `PlaylistRow`, and `PlaylistRow` no longer has a `DropdownMenu`, `onRename`, or `onDelete`.

- [ ] **Step 1: Rewrite `PlaylistsScreen.kt`**

Replace the entire file content with:

```kotlin
package com.laconical.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.model.Track
import com.laconical.player.ui.components.PlaylistCoverMosaic
import com.laconical.player.ui.components.staggeredEntrance
import com.laconical.player.ui.viewmodels.PlaylistsViewModel

@Composable
fun PlaylistsScreen(
    onFavoritesClick: () -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onCreatePlaylist: () -> Unit,
    onMenuOpen: (playlist: Playlist, artOffsetPx: Offset, artSizePx: Float) -> Unit,
    bottomPadding: Dp = 0.dp,
    dominantColor: Color? = null,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel()
) {
    val accentColor = if (dominantColor != null) {
        Color(
            red   = (dominantColor.red   * 0.3f + 0.7f).coerceIn(0f, 1f),
            green = (dominantColor.green * 0.3f + 0.7f).coerceIn(0f, 1f),
            blue  = (dominantColor.blue  * 0.3f + 0.7f).coerceIn(0f, 1f),
            alpha = 1f
        )
    } else Color.White
    val playlists by viewModel.playlists.collectAsState()
    val artMap by viewModel.playlistArtTracks.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + bottomPadding + 80.dp
            )
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFavoritesClick() }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color(0xFFE84B7A),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Favorites",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF666666)
                    )
                }
            }

            item {
                NewPlaylistRow(
                    accentColor = accentColor,
                    onClick = onCreatePlaylist
                )
            }

            if (playlists.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No playlists yet. Tap 'Create playlist' to add one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF555555)
                        )
                    }
                }
            } else {
                itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
                    Box(modifier = Modifier.staggeredEntrance(index)) {
                        PlaylistRow(
                            playlist = playlist,
                            artTracks = artMap[playlist.id] ?: emptyList(),
                            onClick = { onPlaylistClick(playlist.id) },
                            onMenuOpen = { offset, size -> onMenuOpen(playlist, offset, size) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NewPlaylistRow(
    accentColor: Color,
    onClick: () -> Unit
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(Color(0xFF2A2A2A))
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Light,
                        color = accentColor
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "New Playlist",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = accentColor
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(Color(0xFF2A2A2A))
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    artTracks: List<Track>,
    onClick: () -> Unit,
    onMenuOpen: (artOffsetPx: Offset, artSizePx: Float) -> Unit,
) {
    val density = LocalDensity.current
    var mosaicOffsetPx by remember { mutableStateOf(Offset.Zero) }
    val mosaicSizePx = with(density) { 52.dp.toPx() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        PlaylistCoverMosaic(
            tracks = artTracks,
            size = 52.dp,
            modifier = Modifier.onGloballyPositioned { coords ->
                mosaicOffsetPx = coords.positionInRoot()
            }
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1
            )
        }
        IconButton(onClick = { onMenuOpen(mosaicOffsetPx, mosaicSizePx) }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Playlist options",
                tint = Color(0xFF888888)
            )
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
cd /home/btema2/smart-things/code/Laconical-Player
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|warning:|BUILD"
```

Expected: Compile errors referencing `PlaylistsScreen` call site in `LibraryScreen.kt` (missing `onMenuOpen` param) — that's correct, Task 3 fixes it. No errors inside `PlaylistsScreen.kt` itself.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/screens/PlaylistsScreen.kt
git commit -m "refactor: remove DropdownMenu from PlaylistRow, add onMenuOpen callback"
```

---

### Task 3: Update `LibraryScreen` — wire overlay state, `PlaylistsViewModel`, and dialogs

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt`

Four changes:
1. Import `PlaylistMenuOverlay` and `PlaylistsViewModel`
2. Add three state vars for the playlist context menu (after line 191, alongside the track menu state)
3. Obtain `PlaylistsViewModel` via `hiltViewModel()` inside `LibraryScreen`
4. Pass `onMenuOpen` to `PlaylistsScreen` call site
5. Render `PlaylistMenuOverlay` + rename/delete dialogs in the outermost Box (after the track menu overlay block)

- [ ] **Step 1: Add imports**

In `LibraryScreen.kt`, find the imports block. Add these two imports (alongside the existing `TrackMenuOverlay` import at line 58):

```kotlin
import com.laconical.player.ui.components.PlaylistMenuOverlay
import com.laconical.player.ui.viewmodels.PlaylistsViewModel
import com.laconical.player.ui.components.PlaylistBottomSheet
```

Note: `PlaylistBottomSheet` import may already exist — only add if missing. Check with:
```bash
grep "PlaylistBottomSheet\|PlaylistMenuOverlay\|PlaylistsViewModel" \
  app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
```

- [ ] **Step 2: Add `PlaylistsViewModel` and context menu state**

Locate this block in `LibraryScreen` (around line 119–121):

```kotlin
@Composable
fun LibraryScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
```

Add `PlaylistsViewModel` as a second parameter with default `hiltViewModel()`:

```kotlin
@Composable
fun LibraryScreen(
    viewModel: MainViewModel = hiltViewModel(),
    playlistsViewModel: PlaylistsViewModel = hiltViewModel(),
) {
```

Then locate the track menu state block (around line 188–191):

```kotlin
    var contextMenuTrack by remember { mutableStateOf<Track?>(null) }
    var contextMenuArtOffset by remember { mutableStateOf(Offset.Zero) }
    var contextMenuArtSize by remember { mutableFloatStateOf(0f) }
```

Add playlist context menu state immediately after:

```kotlin
    var contextMenuPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var contextMenuPlaylistArtOffset by remember { mutableStateOf(Offset.Zero) }
    var contextMenuPlaylistArtSize by remember { mutableFloatStateOf(0f) }
    var showRenamePlaylist by remember { mutableStateOf(false) }
    var showDeletePlaylist by remember { mutableStateOf(false) }
```

- [ ] **Step 3: Pass `onMenuOpen` to `PlaylistsScreen` call site**

Find the `PlaylistsScreen(` composable call (around line 549):

```kotlin
                                        PlaylistsScreen(
                                            onFavoritesClick = {
                                                navController.navigate(NavRoute.FAVORITES)
                                            },
                                            onPlaylistClick = { playlistId ->
                                                navController.navigate(NavRoute.playlistDetailRoute(playlistId))
                                            },
                                            onCreatePlaylist = { showCreateFromPlaylistsTab = true },
                                            bottomPadding = trackListBottomPadding,
                                            dominantColor = playingTrackDominantColor
                                        )
```

Replace with:

```kotlin
                                        PlaylistsScreen(
                                            onFavoritesClick = {
                                                navController.navigate(NavRoute.FAVORITES)
                                            },
                                            onPlaylistClick = { playlistId ->
                                                navController.navigate(NavRoute.playlistDetailRoute(playlistId))
                                            },
                                            onCreatePlaylist = { showCreateFromPlaylistsTab = true },
                                            onMenuOpen = { playlist, offset, size ->
                                                contextMenuPlaylist = playlist
                                                contextMenuPlaylistArtOffset = offset
                                                contextMenuPlaylistArtSize = size
                                            },
                                            bottomPadding = trackListBottomPadding,
                                            dominantColor = playingTrackDominantColor
                                        )
```

- [ ] **Step 4: Add `PlaylistMenuOverlay` and dialogs in the outermost Box**

Locate this comment block (around line 773):

```kotlin
        }
        if (showCreateForPicker) {
```

Insert the playlist overlay and dialogs immediately before `if (showCreateForPicker)`:

```kotlin
        // ── Playlist context menu overlay ───────────────────────────────────
        contextMenuPlaylist?.let { playlist ->
            PlaylistMenuOverlay(
                playlist = playlist,
                artTracks = playlistArtTracks[playlist.id] ?: emptyList(),
                artStartOffsetPx = contextMenuPlaylistArtOffset,
                artStartSizePx = contextMenuPlaylistArtSize,
                dominantColor = playingTrackDominantColor,
                onDismiss = { contextMenuPlaylist = null },
                onRename = { showRenamePlaylist = true },
                onDelete = { showDeletePlaylist = true },
            )
        }
        if (showRenamePlaylist) {
            contextMenuPlaylist?.let { target ->
                PlaylistBottomSheet(
                    title = "Rename Playlist",
                    initialName = target.name,
                    onDismiss = {
                        showRenamePlaylist = false
                        contextMenuPlaylist = null
                    },
                    onConfirm = { name ->
                        playlistsViewModel.renamePlaylist(target.id, name)
                        showRenamePlaylist = false
                        contextMenuPlaylist = null
                    }
                )
            }
        }
        if (showDeletePlaylist) {
            contextMenuPlaylist?.let { target ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {
                        showDeletePlaylist = false
                        contextMenuPlaylist = null
                    },
                    title = { Text("Delete \"${target.name}\"?") },
                    text = { Text("This will permanently delete the playlist and remove all its tracks. Your music files are not affected.") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            playlistsViewModel.deletePlaylist(target.id)
                            showDeletePlaylist = false
                            contextMenuPlaylist = null
                        }) {
                            Text("Delete", color = Color(0xFFEF4444))
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            showDeletePlaylist = false
                            contextMenuPlaylist = null
                        }) { Text("Cancel") }
                    }
                )
            }
        }
```

- [ ] **Step 5: Build and verify**

```bash
cd /home/btema2/smart-things/code/Laconical-Player
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` — no errors. Deprecation warnings are fine.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
git commit -m "feat: wire PlaylistMenuOverlay into LibraryScreen with rename/delete dialogs"
```

---

### Task 4: Full build + install verification

**Files:** No code changes — verification only.

- [ ] **Step 1: Clean build**

```bash
cd /home/btema2/smart-things/code/Laconical-Player
./gradlew clean assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Install on device (if connected)**

```bash
./gradlew installDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL` — app installed.

- [ ] **Step 3: Manual smoke test checklist**

1. Navigate to Playlists tab
2. Tap the 3-dot on any playlist row
3. Verify: dark scrim appears, centered card slides up + scales in, mosaic morphs from row into card header
4. Verify: Rename and Delete rows visible with correct icon colors (white / red)
5. Tap Rename → verify `PlaylistBottomSheet` appears with current name pre-filled
6. Confirm rename → verify playlist name updates in list
7. Reopen menu, tap Delete → verify `AlertDialog` appears
8. Cancel delete → verify playlist still exists
9. Reopen menu, tap Delete → confirm → verify playlist removed from list
10. Tap outside the card → verify overlay dismisses with reverse animation
11. Press system Back while overlay is open → verify overlay dismisses

- [ ] **Step 4: Final commit if any fixes applied**

```bash
git add -p
git commit -m "fix: <describe any fix from smoke test>"
```

If no fixes needed, skip this step.
