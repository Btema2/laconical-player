# Create Playlist Dialog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the plain `ModalBottomSheet` used for playlist creation with a polished floating dialog that matches `TrackMenuOverlay`'s visual language — dark rounded card, scrim, spring entry animation, and seamless back-navigation that returns to the playlist picker.

**Architecture:** A new `CreatePlaylistDialog` composable owns its own full-screen `Box` overlay (scrim + card) with `Animatable`-driven entry/exit. It is placed in LibraryScreen's root `Box` at higher z-order than `TrackMenuOverlay`, so it layers correctly. `PlaylistsScreen` delegates upward via a new `onCreatePlaylist` callback instead of owning the overlay itself.

**Tech Stack:** Kotlin, Jetpack Compose, `androidx.compose.animation.core.Animatable`, `BackHandler`, `FocusRequester`, `BoxWithConstraints`, Material3 `OutlinedTextField`/`Button`

---

## File Map

| Action | File |
|--------|------|
| **Create** | `app/src/main/java/com/laconical/player/ui/components/CreatePlaylistDialog.kt` |
| **Modify** | `app/src/main/java/com/laconical/player/ui/components/TrackMenuOverlay.kt` |
| **Modify** | `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt` |
| **Modify** | `app/src/main/java/com/laconical/player/ui/screens/PlaylistsScreen.kt` |

---

## Task 1: Create `CreatePlaylistDialog` — static layout, no animation

Build the component's visual structure. Animation comes in Task 2.

**Files:**
- Create: `app/src/main/java/com/laconical/player/ui/components/CreatePlaylistDialog.kt`

- [ ] **Step 1: Create the file with the static composable**

```kotlin
package com.laconical.player.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CreatePlaylistDialog(
    originOffset: Offset?,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var text by remember { mutableStateOf(TextFieldValue("", TextRange.Zero)) }
    val nameIsValid = text.text.trim().isNotEmpty()

    BackHandler { onBack() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        // Card — upper-center
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = maxHeight * 0.15f, horizontal = 24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A24))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QueueMusic,
                        contentDescription = null,
                        tint = Color(0xFF7C6FE0),
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Create new playlist",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF2A2A35))

                // Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF12121A))
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("Playlist name", color = Color(0xFF555555)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (nameIsValid) onConfirm(text.text.trim())
                        }),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = Color(0xFF888888))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { if (nameIsValid) onConfirm(text.text.trim()) },
                            enabled = nameIsValid,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF7C6FE0),
                                disabledContainerColor = Color(0xFF7C6FE0).copy(alpha = 0.4f),
                            ),
                        ) {
                            Text("Create", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build — verify it compiles**

```bash
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` (may have deprecation warnings — that's fine, errors are not).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/components/CreatePlaylistDialog.kt
git commit -m "feat: add CreatePlaylistDialog static layout"
```

---

## Task 2: Add entry/exit animation to `CreatePlaylistDialog`

Wire `Animatable(0f)` to drive `graphicsLayer` transforms. Entry differs based on whether `originOffset` is provided.

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/CreatePlaylistDialog.kt`

- [ ] **Step 1: Add animation imports and state**

Add these imports at the top of the file (after the existing imports):

```kotlin
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
```

- [ ] **Step 2: Add `progress` Animatable and animate-then-focus LaunchedEffect**

Replace the existing state block and `LaunchedEffect` at the top of `CreatePlaylistDialog`:

```kotlin
val scope = rememberCoroutineScope()
val focusRequester = remember { FocusRequester() }
var text by remember { mutableStateOf(TextFieldValue("", TextRange.Zero)) }
val nameIsValid = text.text.trim().isNotEmpty()
val progress = remember { Animatable(0f) }
val density = LocalDensity.current

BackHandler { onBack() }

LaunchedEffect(Unit) {
    progress.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
    focusRequester.requestFocus()
}
```

- [ ] **Step 3: Add dismiss helper that runs exit animation before calling callback**

Add this inside the composable body, before `BoxWithConstraints`:

```kotlin
fun animatedDismiss(callback: () -> Unit) {
    scope.launch {
        progress.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
        callback()
    }
}
```

- [ ] **Step 4: Wire exit animation to scrim tap, Cancel, and Back**

Replace the three callback references:

```kotlin
BackHandler { animatedDismiss(onBack) }
// ...scrim clickable:
onClick = { animatedDismiss(onDismiss) }
// ...Cancel TextButton:
TextButton(onClick = { animatedDismiss(onDismiss) })
```

**Note:** `onConfirm` fires immediately (no exit delay) — leave that unchanged.

- [ ] **Step 5: Apply `graphicsLayer` to the card `Column`**

The card needs to know its center Y so morph-from-origin can translate correctly. We measure it with `onGloballyPositioned`. Replace the card `Column`'s modifier chain:

```kotlin
var cardCenterYPx by remember { mutableStateOf(0f) }
val prog = progress.value

Column(
    modifier = Modifier
        .fillMaxWidth()
        .onGloballyPositioned { coords ->
            cardCenterYPx = coords.positionInRoot().y + coords.size.height / 2f
        }
        .graphicsLayer {
            alpha = prog
            val (scaleVal, transYVal) = if (originOffset != null) {
                // Morph from origin row: scale 0.82→1, translateY from row→card center
                val targetY = with(density) { (originOffset.y - cardCenterYPx) }
                Pair(lerp(0.82f, 1f, prog), lerp(targetY, 0f, prog))
            } else {
                // Simple drop-in: scale 0.88→1, translateY -16dp→0
                val startY = with(density) { -16.dp.toPx() }
                Pair(lerp(0.88f, 1f, prog), lerp(startY, 0f, prog))
            }
            scaleX = scaleVal
            scaleY = scaleVal
            translationY = transYVal
        }
        .clip(RoundedCornerShape(20.dp)),
) {
```

- [ ] **Step 6: Add missing imports**

```kotlin
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.util.lerp
```

- [ ] **Step 7: Also animate the scrim alpha**

Wrap the scrim `Box` in a `graphicsLayer` so it fades with entry/exit:

```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .graphicsLayer { alpha = (progress.value * 1.6f).coerceIn(0f, 1f) }
        .background(Color(0xCC000000))
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { animatedDismiss(onDismiss) },
        ),
)
```

- [ ] **Step 8: Build — verify compiles**

```bash
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/components/CreatePlaylistDialog.kt
git commit -m "feat: add entry/exit animation to CreatePlaylistDialog"
```

---

## Task 3: Update `TrackMenuOverlay` — pass origin offset via `onCreateNewPlaylist`

Change `onCreateNewPlaylist` signature to deliver the "New Playlist" row's screen position to the caller.

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/TrackMenuOverlay.kt`

- [ ] **Step 1: Change the `onCreateNewPlaylist` parameter type in `TrackMenuOverlay`**

In `TrackMenuOverlay.kt`, find the function signature (line ~98) and change:

```kotlin
// Before
onCreateNewPlaylist: () -> Unit,

// After
onCreateNewPlaylist: (originOffset: Offset) -> Unit,
```

- [ ] **Step 2: Pass `onCreateNew` down to `PlaylistPickerBody`**

Find the call to `PlaylistPickerBody` inside the `AnimatedContent` block (~line 311). Change:

```kotlin
// Before
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

// After
TrackMenuMode.PLAYLIST -> PlaylistPickerBody(
    playlists = playlists,
    artTracks = artTracks,
    menuBg = menuBg,
    onSelectPlaylist = { playlist ->
        onSelectPlaylist(playlist)
        dismiss()
    },
    onCreateNew = { originOffset ->
        onCreateNewPlaylist(originOffset)
        // Do NOT call dismiss() — overlay stays alive behind CreatePlaylistDialog
    },
)
```

- [ ] **Step 3: Update `PlaylistPickerBody` signature to accept and pass offset**

Find `PlaylistPickerBody` private function (~line 419). Change its `onCreateNew` parameter:

```kotlin
// Before
private fun PlaylistPickerBody(
    playlists: List<Playlist>,
    artTracks: Map<Long, List<Track>>,
    menuBg: Color,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreateNew: () -> Unit,
)

// After
private fun PlaylistPickerBody(
    playlists: List<Playlist>,
    artTracks: Map<Long, List<Track>>,
    menuBg: Color,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreateNew: (originOffset: Offset) -> Unit,
)
```

- [ ] **Step 4: Measure the "New Playlist" row position and pass it to `onCreateNew`**

In `PlaylistPickerBody`, find the "New Playlist" `Row` at the bottom (~line 463). Add `onGloballyPositioned` state and wire it:

```kotlin
// Add at top of PlaylistPickerBody composable body:
var rowOffset by remember { mutableStateOf(Offset.Zero) }

// Then modify the Row's modifier:
Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
        .fillMaxWidth()
        .background(menuBg)
        .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
        .onGloballyPositioned { coords -> rowOffset = coords.positionInRoot() }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = Color.White),
            onClick = { onCreateNew(rowOffset) },
        )
        .padding(horizontal = 20.dp, vertical = 18.dp),
) {
```

- [ ] **Step 5: Build — verify compiles**

```bash
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` — if there's a type mismatch on the `onCreateNewPlaylist` call in `LibraryScreen.kt`, that's expected and will be fixed in Task 4.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/components/TrackMenuOverlay.kt
git commit -m "feat: pass origin offset from TrackMenuOverlay new playlist row"
```

---

## Task 4: Update `LibraryScreen` — wire new state, fix overlay stack

Fix the broken `onCreateNewPlaylist` call site, add `newPlaylistOriginOffset` state, add `showCreateFromPlaylistsTab` state, keep `contextMenuTrack` alive, render `CreatePlaylistDialog` above `TrackMenuOverlay`, and pass `onCreatePlaylist` callback down to `PlaylistsScreen`.

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt`

- [ ] **Step 1: Add new state variables after the existing `showCreateForPicker` line (~line 181)**

```kotlin
var showCreateForPicker by remember { mutableStateOf(false) }
// ADD these two lines:
var newPlaylistOriginOffset by remember { mutableStateOf(Offset.Zero) }
var showCreateFromPlaylistsTab by remember { mutableStateOf(false) }
```

- [ ] **Step 2: Add `CreatePlaylistDialog` import**

Add to the import block:

```kotlin
import com.laconical.player.ui.components.CreatePlaylistDialog
```

- [ ] **Step 3: Fix `onCreateNewPlaylist` wiring (~line 780)**

```kotlin
// Before
onCreateNewPlaylist = {
    pendingNewPlaylistTrack = track
    contextMenuTrack = null
    showCreateForPicker = true
},

// After
onCreateNewPlaylist = { originOffset ->
    newPlaylistOriginOffset = originOffset
    pendingNewPlaylistTrack = track
    // contextMenuTrack intentionally NOT cleared — overlay stays alive
    showCreateForPicker = true
},
```

- [ ] **Step 4: Replace the `showCreateForPicker` block (~line 738) with `CreatePlaylistDialog`**

```kotlin
// Before — remove this block entirely:
if (showCreateForPicker) {
    PlaylistBottomSheet(
        title = "New Playlist",
        onDismiss = {
            pendingNewPlaylistTrack = null
            showCreateForPicker = false
        },
        onConfirm = { name ->
            pendingNewPlaylistTrack?.let { t ->
                viewModel.createPlaylistAndAdd(name, t.id)
            }
            pendingNewPlaylistTrack = null
            showCreateForPicker = false
        }
    )
}

// After — replace with:
if (showCreateForPicker) {
    CreatePlaylistDialog(
        originOffset = newPlaylistOriginOffset.takeIf { contextMenuTrack != null },
        onDismiss = {
            showCreateForPicker = false
            contextMenuTrack = null
        },
        onBack = {
            showCreateForPicker = false
            // contextMenuTrack stays — reveals TrackMenuOverlay in PLAYLIST mode
        },
        onConfirm = { name ->
            pendingNewPlaylistTrack?.let { t ->
                viewModel.createPlaylistAndAdd(name, t.id)
            }
            pendingNewPlaylistTrack = null
            showCreateForPicker = false
            contextMenuTrack = null
        },
    )
}
```

- [ ] **Step 5: Add `showCreateFromPlaylistsTab` dialog block after the picker block**

Place this immediately after the `showCreateForPicker` block:

```kotlin
if (showCreateFromPlaylistsTab) {
    CreatePlaylistDialog(
        originOffset = null,
        onDismiss = { showCreateFromPlaylistsTab = false },
        onBack = { showCreateFromPlaylistsTab = false },
        onConfirm = { name ->
            viewModel.createPlaylist(name)
            showCreateFromPlaylistsTab = false
        },
    )
}
```

- [ ] **Step 6: Pass `onCreatePlaylist` callback to `PlaylistsScreen` (~line 547)**

```kotlin
// Before
PlaylistsScreen(
    onFavoritesClick = {
        navController.navigate(NavRoute.FAVORITES)
    },
    onPlaylistClick = { playlistId ->
        navController.navigate(NavRoute.playlistDetailRoute(playlistId))
    },
    bottomPadding = trackListBottomPadding,
    dominantColor = playingTrackDominantColor
)

// After
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

- [ ] **Step 7: Build — verify compiles (may fail until Task 5 updates PlaylistsScreen)**

```bash
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: compile error about `onCreatePlaylist` not being a parameter of `PlaylistsScreen` — that's fine. Continue to Task 5.

- [ ] **Step 8: Commit (even if build fails — partial commit is fine here)**

```bash
git add app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
git commit -m "feat: wire CreatePlaylistDialog in LibraryScreen, keep overlay alive on back"
```

---

## Task 5: Update `PlaylistsScreen` — remove internal create sheet, accept callback

Remove `showCreateSheet` state and `PlaylistBottomSheet` create usage. Accept `onCreatePlaylist` param and wire `NewPlaylistRow` to it.

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/screens/PlaylistsScreen.kt`

- [ ] **Step 1: Add `onCreatePlaylist` to the function signature**

```kotlin
// Before
@Composable
fun PlaylistsScreen(
    onFavoritesClick: () -> Unit,
    onPlaylistClick: (Long) -> Unit,
    bottomPadding: Dp = 0.dp,
    dominantColor: Color? = null,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel()
)

// After
@Composable
fun PlaylistsScreen(
    onFavoritesClick: () -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onCreatePlaylist: () -> Unit,
    bottomPadding: Dp = 0.dp,
    dominantColor: Color? = null,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel()
)
```

- [ ] **Step 2: Remove `showCreateSheet` state variable**

Find and delete this line (~line 75):

```kotlin
var showCreateSheet by remember { mutableStateOf(false) }
```

- [ ] **Step 3: Wire `NewPlaylistRow` to use `onCreatePlaylist` instead of `showCreateSheet`**

```kotlin
// Before
item {
    NewPlaylistRow(
        accentColor = accentColor,
        onClick = { showCreateSheet = true }
    )
}

// After
item {
    NewPlaylistRow(
        accentColor = accentColor,
        onClick = onCreatePlaylist
    )
}
```

- [ ] **Step 4: Remove the `PlaylistBottomSheet` create block**

Find and delete this entire block (~line 165):

```kotlin
if (showCreateSheet) {
    PlaylistBottomSheet(
        title = "New Playlist",
        onDismiss = { showCreateSheet = false },
        onConfirm = { name ->
            viewModel.createPlaylist(name)
            showCreateSheet = false
        }
    )
}
```

- [ ] **Step 5: Remove unused `PlaylistBottomSheet` import if now unused**

Check if `PlaylistBottomSheet` is still referenced (it may still be used for rename). If import is unused:

```bash
grep -n "PlaylistBottomSheet" app/src/main/java/com/laconical/player/ui/screens/PlaylistsScreen.kt
```

If the only reference was the create block (now deleted) and the rename block still exists, the import stays. If zero references remain, remove the import line.

- [ ] **Step 6: Build — verify full project compiles cleanly**

```bash
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/screens/PlaylistsScreen.kt
git commit -m "feat: delegate playlist creation from PlaylistsScreen to LibraryScreen overlay"
```

---

## Task 6: Manual verification

No automated UI tests exist for this flow. Verify the three user journeys manually on a device or emulator.

**Files:** None modified.

- [ ] **Step 1: Install on device/emulator**

```bash
./gradlew installDebug
```

- [ ] **Step 2: Verify flow — from Playlists tab**

1. Open app → tap **Playlists** tab
2. Tap **New Playlist** row
3. Confirm: dialog appears at top ~15% of screen with `QueueMusic` icon + "Create new playlist" title
4. Confirm: keyboard auto-appears after entry animation
5. Confirm: **Create** button is disabled (greyed) with empty field
6. Type a name → **Create** button becomes active
7. Tap **Create** → dialog dismisses, new playlist appears in list
8. Repeat, tap **Cancel** → dialog dismisses, no playlist created
9. Repeat, tap outside (scrim) → dialog dismisses
10. Repeat, use system back → dialog dismisses

- [ ] **Step 3: Verify flow — from track 3-dot menu → Add to Playlist → New Playlist**

1. Long-press / 3-dot any track → **Add to Playlist**
2. Tap **New Playlist** at bottom of picker
3. Confirm: `CreatePlaylistDialog` appears with morph animation from the row position
4. Confirm: `TrackMenuOverlay` is still visible underneath (can see its scrim slightly darker)
5. Type a name → **Create** → both overlays dismiss, playlist created with track added
6. Repeat step 1-2 → tap **Cancel** → both overlays dismiss
7. Repeat step 1-2 → tap scrim → both overlays dismiss
8. Repeat step 1-2 → use system back → **only** `CreatePlaylistDialog` closes, playlist picker is still visible
9. Confirm: pressing back again from playlist picker closes `TrackMenuOverlay` entirely

- [ ] **Step 4: Commit**

```bash
git add .  # nothing to stage — just mark verification done
git commit --allow-empty -m "chore: verify CreatePlaylistDialog flows manually"
```

(Or skip the empty commit and fold the note into the next real commit.)
