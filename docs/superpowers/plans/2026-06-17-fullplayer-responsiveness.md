# FullPlayer Responsiveness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make FullPlayer adaptive across portrait Android phones (9:16–9:19) by computing art/control sizes from available screen space and replacing hardcoded morph positions with measured values.

**Architecture:** One computed `artSizeDp` (derived via `BoxWithConstraints`) drives album art size, control icon scaling, and the morph overlay's art position. A new `onAlbumArtPositioned` callback threads the measured position back to `QueueMorphLayer`, eliminating the fragile hardcoded formula. All four changes are spread across two files: `FullPlayer.kt` and `LibraryScreen.kt`.

**Tech Stack:** Kotlin · Jetpack Compose · `BoxWithConstraints` · `onGloballyPositioned`

## Global Constraints

- Portrait-only smartphones, minSdk 26, no tablet/landscape handling
- Compositor-only animations — `scale`, `alpha`, `offset` only, no layout-bound property animation
- Morph ghost contract preserved: album art `Spacer` in `FullPlayer` stays the invisible layout anchor; `QueueMorphLayer` renders the actual art on top
- No new dependencies

---

### Task 1: Parameterize `PlaybackControls` icon sizes

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/FullPlayer.kt` (lines 548–617)

**Interfaces:**
- Produces: `PlaybackControls(playButtonSize: Dp = 72.dp, skipIconSize: Dp = 48.dp, ...)` — Task 2 calls this with computed values

- [ ] **Step 1: Add `Dp` import**

In `FullPlayer.kt`, add to the import block (after `import androidx.compose.ui.unit.sp`):

```kotlin
import androidx.compose.ui.unit.Dp
```

- [ ] **Step 2: Update `PlaybackControls` signature**

Replace the current function signature (lines 549–558):

```kotlin
@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    themeColor: Color,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPrevPositioned: (Float, Float) -> Unit = { _, _ -> },
    onPlayPositioned: (Float, Float) -> Unit = { _, _ -> },
    onNextPositioned: (Float, Float) -> Unit = { _, _ -> },
) {
```

With:

```kotlin
@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    themeColor: Color,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    playButtonSize: Dp = 72.dp,
    skipIconSize: Dp = 48.dp,
    onPrevPositioned: (Float, Float) -> Unit = { _, _ -> },
    onPlayPositioned: (Float, Float) -> Unit = { _, _ -> },
    onNextPositioned: (Float, Float) -> Unit = { _, _ -> },
) {
```

- [ ] **Step 3: Use params inside `PlaybackControls` body**

Inside the function body, add a derived icon size for the play button (ratio matches original 42/72):

```kotlin
val playIconSize = playButtonSize * (42f / 72f)
```

Replace `.size(48.dp)` on both `SkipPrevious` and `SkipNext` icons with `.size(skipIconSize)`.

Replace `.size(72.dp)` on the play/pause `Box` with `.size(playButtonSize)`.

Replace `.size(42.dp)` on the play/pause `Icon` with `.size(playIconSize)`.

The skip icon call sites become:

```kotlin
Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(skipIconSize))
```

```kotlin
Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(skipIconSize))
```

The play button box becomes:

```kotlin
Box(
    modifier = Modifier
        .size(playButtonSize)
        .clip(CircleShape)
        .background(animatedButtonColor)
        .clickable(onClick = onTogglePlay)
        .onGloballyPositioned { coords ->
            val c = coords.positionInRoot()
            onPlayPositioned(c.x + coords.size.width / 2f, c.y + coords.size.height / 2f)
        },
    contentAlignment = Alignment.Center
) {
    Crossfade(targetState = isPlaying, label = "PlayPause") { playing ->
        Icon(
            imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (playing) "Pause" else "Play",
            tint = Color.White, modifier = Modifier.size(playIconSize)
        )
    }
}
```

- [ ] **Step 4: Verify build passes**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`. All existing call sites still compile because both new params have defaults.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/components/FullPlayer.kt
git commit -m "feat: parameterize PlaybackControls icon sizes with Dp defaults"
```

---

### Task 2: Adaptive layout in `FullPlayer` via `BoxWithConstraints`

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/FullPlayer.kt`

**Interfaces:**
- Consumes: `PlaybackControls(playButtonSize, skipIconSize)` from Task 1
- Produces: `FullPlayer(onAlbumArtPositioned: (x: Float, y: Float, sizePx: Float) -> Unit = { _, _, _ -> })` — Task 3 wires this callback

- [ ] **Step 1: Add `onAlbumArtPositioned` parameter to `FullPlayer`**

In the `FullPlayer` composable signature (around line 51–64), add the new callback after `onShowMenu`:

```kotlin
@Composable
fun FullPlayer(
    viewModel: MainViewModel,
    expandedFraction: Float,
    onCollapse: () -> Unit,
    onTitlePositioned: (Float) -> Unit = {},
    onArtistPositioned: (Float, Float) -> Unit = { _, _ -> },
    onPlayControlsPositioned: (prevX: Float, prevY: Float, playX: Float, playY: Float, nextX: Float, nextY: Float) -> Unit = { _, _, _, _, _, _ -> },
    onAlbumArtPositioned: (x: Float, y: Float, sizePx: Float) -> Unit = { _, _, _ -> },
    onShowQueue: () -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onShowMenu: () -> Unit = {},
) {
```

- [ ] **Step 2: Replace `Column` with `BoxWithConstraints` → `Column`**

The current outer `Box` contains a `Column` with `statusBarsPadding()`. Replace that `Column(...)` block — keeping the outer `Box` untouched — with:

```kotlin
BoxWithConstraints(
    modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .padding(horizontal = 24.dp, vertical = 16.dp)
) {
    val artSizeDp = minOf(maxWidth * 0.95f, maxHeight * 0.42f)
    val controlScale = (artSizeDp.value / 280f).coerceIn(0.85f, 1.15f)
    val playButtonSize = 72.dp * controlScale
    val skipIconSize = 48.dp * controlScale

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── all existing children go here, with two targeted changes below ──
    }
}
```

The Column modifier changes from:
```kotlin
modifier = Modifier
    .fillMaxSize()
    .statusBarsPadding()
    .padding(horizontal = 24.dp, vertical = 16.dp),
```
to simply:
```kotlin
modifier = Modifier.fillMaxSize(),
```

- [ ] **Step 3: Replace the fixed spacer above album art**

Find (line 184):
```kotlin
Spacer(modifier = Modifier.height(64.dp))
```

Replace with:
```kotlin
Spacer(modifier = Modifier.weight(0.08f))
```

- [ ] **Step 4: Replace album art Spacer modifier and add position callback**

Find the album art `Spacer` (lines 189–207). Replace its modifier chain:

```kotlin
// OLD
Spacer(
    modifier = Modifier
        .fillMaxWidth(0.95f)
        .aspectRatio(1f)
        .pointerInput(Unit) {
            val threshold = 80.dp.toPx()
            var totalDragY = 0f
            detectDragGestures(
                onDragStart = { totalDragY = 0f },
                onDrag = { _, dragAmount -> totalDragY += dragAmount.y },
                onDragEnd = {
                    when {
                        totalDragY < -threshold -> onShowQueue()
                        totalDragY > threshold -> onCollapse()
                    }
                }
            )
        }
)

// NEW
Spacer(
    modifier = Modifier
        .size(artSizeDp)
        .onGloballyPositioned { coords ->
            val pos = coords.positionInRoot()
            onAlbumArtPositioned(pos.x, pos.y, coords.size.width.toFloat())
        }
        .pointerInput(Unit) {
            val threshold = 80.dp.toPx()
            var totalDragY = 0f
            detectDragGestures(
                onDragStart = { totalDragY = 0f },
                onDrag = { _, dragAmount -> totalDragY += dragAmount.y },
                onDragEnd = {
                    when {
                        totalDragY < -threshold -> onShowQueue()
                        totalDragY > threshold -> onCollapse()
                    }
                }
            )
        }
)
```

- [ ] **Step 5: Pass scaled sizes to the ghost `PlaybackControls`**

Find the invisible ghost `PlaybackControls` call inside `Box(modifier = Modifier.graphicsLayer { alpha = 0f })`. Add `playButtonSize` and `skipIconSize`:

```kotlin
Box(modifier = Modifier.graphicsLayer { alpha = 0f }) {
    PlaybackControls(
        isPlaying = isPlaying,
        themeColor = themeColor,
        onTogglePlay = {},
        onPrevious = {},
        onNext = {},
        playButtonSize = playButtonSize,
        skipIconSize = skipIconSize,
        onPrevPositioned  = { x, y -> onPlayControlsPositioned(x, y, Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE) },
        onPlayPositioned  = { x, y -> onPlayControlsPositioned(Float.MIN_VALUE, Float.MIN_VALUE, x, y, Float.MIN_VALUE, Float.MIN_VALUE) },
        onNextPositioned  = { x, y -> onPlayControlsPositioned(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE, x, y) },
    )
}
```

- [ ] **Step 6: Verify build passes**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/components/FullPlayer.kt
git commit -m "feat: adaptive FullPlayer layout via BoxWithConstraints"
```

---

### Task 3: Update `QueueMorphLayer` — add params and replace hardcoded formula

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt` (function `QueueMorphLayer`, line 950+)

**Interfaces:**
- Produces: `QueueMorphLayer(..., fullArtTopPx: Float = -1f, fullArtLeftPx: Float = -1f, fullArtSizePx: Float = -1f, scope)` — Task 4 passes real values; defaults keep existing call site compiling

- [ ] **Step 1: Add three params with defaults to `QueueMorphLayer` signature**

Find the function signature (line 950–967). Add three params before `scope`, with `-1f` defaults so the existing call site compiles unchanged until Task 4:

```kotlin
private fun QueueMorphLayer(
    queueAnimatable: Animatable<Float, AnimationVector1D>,
    viewModel: MainViewModel,
    currentTrack: Track,
    expandedFraction: Float,
    collapsedSheetRootYPx: Float,
    sheetRootYPx: Float,
    fullTitleTopPx: Float,
    fullArtistLeftPx: Float,
    fullArtistTopPx: Float,
    fullPrevCenterXPx: Float,
    fullPrevCenterYPx: Float,
    fullPlayCenterXPx: Float,
    fullPlayCenterYPx: Float,
    fullNextCenterXPx: Float,
    fullNextCenterYPx: Float,
    fullArtTopPx: Float = -1f,
    fullArtLeftPx: Float = -1f,
    fullArtSizePx: Float = -1f,
    scope: CoroutineScope,
) {
```

- [ ] **Step 2: Replace hardcoded art position formula**

Find (lines 1055–1057):

```kotlin
val fullArtSizeDp = (screenWidthDp - 48.dp) * 0.95f
val fullArtLeftDp = (screenWidthDp - fullArtSizeDp) / 2f
val fullArtTopDp  = statusBarPadding + 16.dp + 48.dp + 64.dp
```

Replace with:

```kotlin
val fullArtSizeDp = if (fullArtSizePx >= 0f) with(density) { fullArtSizePx.toDp() }
                    else (screenWidthDp - 48.dp) * 0.95f
val fullArtLeftDp = if (fullArtLeftPx >= 0f) with(density) { fullArtLeftPx.toDp() }
                    else (screenWidthDp - fullArtSizeDp) / 2f
val fullArtTopDp  = if (fullArtTopPx >= 0f) with(density) { (fullArtTopPx - sheetRootYPx).toDp() }
                    else statusBarPadding + 16.dp + 48.dp + 64.dp
```

- [ ] **Step 3: Verify build passes**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`. The existing `QueueMorphLayer(...)` call site still compiles because the new params have defaults.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
git commit -m "feat: replace hardcoded morph art position with measured-value params"
```

---

### Task 4: Wire album art measurement in `LibraryScreen`

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt`

**Interfaces:**
- Consumes: `FullPlayer(onAlbumArtPositioned)` from Task 2
- Consumes: `QueueMorphLayer(fullArtTopPx, fullArtLeftPx, fullArtSizePx)` from Task 3

- [ ] **Step 1: Declare three new state variables**

In `LibraryScreen`, near the existing ghost state vars (around line 260–268, after `fullNextCenterYPx`), add:

```kotlin
var fullArtTopPx  by remember { mutableFloatStateOf(-1f) }
var fullArtLeftPx by remember { mutableFloatStateOf(-1f) }
var fullArtSizePx by remember { mutableFloatStateOf(-1f) }
```

- [ ] **Step 2: Wire `onAlbumArtPositioned` in the `FullPlayer` call**

In the `FullPlayer(...)` call (around line 355), add after `onPlayControlsPositioned`:

```kotlin
onAlbumArtPositioned = { x, y, sizePx ->
    fullArtLeftPx = x
    fullArtTopPx  = y
    fullArtSizePx = sizePx
},
```

- [ ] **Step 3: Extend `allGhostsReady` gate**

Find (line 759–761):

```kotlin
val allGhostsReady = sheetRootYPx >= 0f &&
    fullTitleTopPx >= 0f && fullArtistTopPx >= 0f &&
    fullPrevCenterYPx >= 0f && fullPlayCenterYPx >= 0f && fullNextCenterYPx >= 0f
```

Replace with:

```kotlin
val allGhostsReady = sheetRootYPx >= 0f &&
    fullTitleTopPx >= 0f && fullArtistTopPx >= 0f &&
    fullPrevCenterYPx >= 0f && fullPlayCenterYPx >= 0f && fullNextCenterYPx >= 0f &&
    fullArtSizePx >= 0f
```

- [ ] **Step 4: Extend `overlayActive` check**

Find (around line 387–389):

```kotlin
val overlayActive = currentTrack != null && sheetRootYPx >= 0f &&
    fullTitleTopPx >= 0f && fullArtistTopPx >= 0f &&
    fullPrevCenterYPx >= 0f && fullPlayCenterYPx >= 0f && fullNextCenterYPx >= 0f
```

Replace with:

```kotlin
val overlayActive = currentTrack != null && sheetRootYPx >= 0f &&
    fullTitleTopPx >= 0f && fullArtistTopPx >= 0f &&
    fullPrevCenterYPx >= 0f && fullPlayCenterYPx >= 0f && fullNextCenterYPx >= 0f &&
    fullArtSizePx >= 0f
```

- [ ] **Step 5: Pass the three vars into `QueueMorphLayer`**

In the `QueueMorphLayer(...)` call (around line 763–780), add after `fullNextCenterYPx`:

```kotlin
fullArtTopPx  = fullArtTopPx,
fullArtLeftPx = fullArtLeftPx,
fullArtSizePx = fullArtSizePx,
```

- [ ] **Step 6: Verify build and lint pass**

```bash
./gradlew assembleDebug && ./gradlew lint
```

Expected: `BUILD SUCCESSFUL` for both.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
git commit -m "feat: wire album art position measurement into morph overlay"
```

---

## Post-Implementation Verification

After all four tasks complete, verify the full feature end-to-end:

- [ ] `./gradlew assembleDebug` — clean build
- [ ] `./gradlew lint` — no new warnings
- [ ] Install on a device or emulator: `./gradlew installDebug`
- [ ] Open a track, expand FullPlayer — confirm art, controls, and spacing look proportional
- [ ] On a small emulator (e.g. Pixel 3a, 5.6", ~360×780dp): album art ≤ screen width, controls visible without scrolling
- [ ] Morph transition (mini → full) — art slides from mini strip to correct full-player position with no snap/glitch
- [ ] Morph transition (full → queue) — art slides from full-player to queue header correctly
- [ ] Collapse player — no visual artifacts
