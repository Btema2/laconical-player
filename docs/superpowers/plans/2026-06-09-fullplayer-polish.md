# FullPlayer Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish the FullPlayer with fade+marquee text in all three player states, a wired 3-dots menu (no art morph), a wired like button with spring pop, and a faded album header label.

**Architecture:** `FadingMarqueeText` is a new standalone composable placed in `ui/components/`. `TrackMenuOverlay` gains a `skipArtMorph` flag. `FullPlayer` gains three new parameters wired in `LibraryScreen`. `QueueMorphLayer` (private in `LibraryScreen.kt`) replaces its two `Text` calls with `FadingMarqueeText`, gated on a `isStable` flag computed from the morph fractions.

**Tech Stack:** Kotlin, Jetpack Compose 2025.02.00 BOM (`basicMarquee`, `CompositingStrategy.Offscreen`, `BlendMode.DstIn`), Robolectric + `createComposeRule` for component tests.

---

## File Map

| Action | Path |
|--------|------|
| **Create** | `app/src/main/java/com/laconical/player/ui/components/FadingMarqueeText.kt` |
| **Create** | `app/src/test/java/com/laconical/player/ui/components/FadingMarqueeTextTest.kt` |
| **Modify** | `app/src/main/java/com/laconical/player/ui/components/TrackMenuOverlay.kt` |
| **Modify** | `app/src/main/java/com/laconical/player/ui/components/FullPlayer.kt` |
| **Modify** | `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt` |

---

## Task 1: `FadingMarqueeText` composable + test

**Files:**
- Create: `app/src/main/java/com/laconical/player/ui/components/FadingMarqueeText.kt`
- Create: `app/src/test/java/com/laconical/player/ui/components/FadingMarqueeTextTest.kt`

- [ ] **Step 1: Write the failing test**

  Create `app/src/test/java/com/laconical/player/ui/components/FadingMarqueeTextTest.kt`:

  ```kotlin
  package com.laconical.player.ui.components

  import android.app.Application
  import androidx.compose.ui.graphics.Color
  import androidx.compose.ui.test.assertIsDisplayed
  import androidx.compose.ui.test.junit4.createComposeRule
  import androidx.compose.ui.test.onNodeWithText
  import androidx.compose.ui.text.font.FontWeight
  import androidx.compose.ui.unit.sp
  import androidx.test.ext.junit.runners.AndroidJUnit4
  import org.junit.Rule
  import org.junit.Test
  import org.junit.runner.RunWith
  import org.robolectric.annotation.Config

  @RunWith(AndroidJUnit4::class)
  @Config(sdk = [33], application = Application::class)
  class FadingMarqueeTextTest {

      @get:Rule
      val composeTestRule = createComposeRule()

      @Test
      fun `renders text content`() {
          composeTestRule.setContent {
              FadingMarqueeText(
                  text = "Test Song Title",
                  color = Color.White,
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold,
                  isScrolling = false,
              )
          }
          composeTestRule.onNodeWithText("Test Song Title").assertIsDisplayed()
      }

      @Test
      fun `renders with scrolling enabled without crashing`() {
          composeTestRule.setContent {
              FadingMarqueeText(
                  text = "A Very Long Song Title That Should Overflow The Container Width",
                  color = Color.White,
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold,
                  isScrolling = true,
              )
          }
          composeTestRule.onNodeWithText(
              "A Very Long Song Title That Should Overflow The Container Width",
              substring = true,
          ).assertIsDisplayed()
      }
  }
  ```

- [ ] **Step 2: Run test to confirm it fails**

  ```bash
  ./gradlew :app:test --tests "com.laconical.player.ui.components.FadingMarqueeTextTest" 2>&1 | tail -20
  ```

  Expected: **FAILED** — `FadingMarqueeText` not yet defined.

- [ ] **Step 3: Create `FadingMarqueeText.kt`**

  Create `app/src/main/java/com/laconical/player/ui/components/FadingMarqueeText.kt`:

  ```kotlin
  package com.laconical.player.ui.components

  import androidx.compose.foundation.MarqueeAnimationMode
  import androidx.compose.foundation.basicMarquee
  import androidx.compose.material3.Text
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.draw.drawWithContent
  import androidx.compose.ui.graphics.*
  import androidx.compose.ui.text.font.FontWeight
  import androidx.compose.ui.text.style.TextOverflow
  import androidx.compose.ui.unit.TextUnit
  import androidx.compose.ui.unit.dp

  @Composable
  fun FadingMarqueeText(
      text: String,
      color: Color,
      fontSize: TextUnit,
      fontWeight: FontWeight,
      isScrolling: Boolean,
      modifier: Modifier = Modifier,
  ) {
      Text(
          text = text,
          color = color,
          fontSize = fontSize,
          fontWeight = fontWeight,
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Clip,
          modifier = modifier
              .then(
                  if (isScrolling) Modifier.basicMarquee(
                      animationMode = MarqueeAnimationMode.Immediately,
                      initialDelayMillis = 3000,
                      repeatDelayMillis = 2500,
                      velocity = 80.dp,
                  ) else Modifier
              )
              .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
              .drawWithContent {
                  drawContent()
                  drawRect(
                      brush = Brush.horizontalGradient(
                          colorStops = arrayOf(
                              0.7f to Color.Black,
                              1.0f to Color.Transparent,
                          ),
                      ),
                      blendMode = BlendMode.DstIn,
                  )
              },
      )
  }
  ```

- [ ] **Step 4: Run tests to confirm they pass**

  ```bash
  ./gradlew :app:test --tests "com.laconical.player.ui.components.FadingMarqueeTextTest" 2>&1 | tail -20
  ```

  Expected: **PASSED** (2 tests).

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/laconical/player/ui/components/FadingMarqueeText.kt \
          app/src/test/java/com/laconical/player/ui/components/FadingMarqueeTextTest.kt
  git commit -m "feat: add FadingMarqueeText composable with right-side fade and conditional marquee"
  ```

---

## Task 2: `TrackMenuOverlay` — `skipArtMorph` parameter

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/TrackMenuOverlay.kt`

- [ ] **Step 1: Add `skipArtMorph` parameter**

  In `TrackMenuOverlay.kt`, add the parameter after `dominantColor: Color?`:

  ```kotlin
  // Before:
  dominantColor: Color?,
  playlists: List<Playlist>,

  // After:
  dominantColor: Color?,
  skipArtMorph: Boolean = false,
  playlists: List<Playlist>,
  ```

- [ ] **Step 2: Update in-card art alpha**

  Find this block (~line 183–193):

  ```kotlin
  val sp = switchProgress.value
  val inCardSize = lerp(64f, 56f, sp).dp
  val inCardCorner = lerp(14f, 12f, sp).dp
  val inCardAlpha = (sp * 4f).coerceIn(0f, 1f)
  ```

  Replace with:

  ```kotlin
  val sp = switchProgress.value
  val inCardSize = lerp(64f, 56f, sp).dp
  val inCardCorner = lerp(14f, 12f, sp).dp
  val inCardAlpha = when {
      skipArtMorph -> (prog * 2f).coerceIn(0f, 1f)
      else -> (sp * 4f).coerceIn(0f, 1f)
  }
  ```

- [ ] **Step 3: Update floating art alpha**

  Find this line (~line 334):

  ```kotlin
  val floatingArtAlpha = lerp(1f, 0f, (switchProgress.value * 4f).coerceIn(0f, 1f))
  ```

  Replace with:

  ```kotlin
  val floatingArtAlpha = when {
      skipArtMorph -> 0f
      else -> lerp(1f, 0f, (switchProgress.value * 4f).coerceIn(0f, 1f))
  }
  ```

- [ ] **Step 4: Build to confirm no compile errors**

  ```bash
  ./gradlew :app:assembleDebug 2>&1 | grep -E "error:|BUILD"
  ```

  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/laconical/player/ui/components/TrackMenuOverlay.kt
  git commit -m "feat: add skipArtMorph param to TrackMenuOverlay"
  ```

---

## Task 3: `FullPlayer` — new params + like animation + album header fade

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/FullPlayer.kt`

- [ ] **Step 1: Add new parameters to `FullPlayer`**

  Add three parameters after `onShowQueue`:

  ```kotlin
  // Before:
  onShowQueue: () -> Unit = {},

  // After:
  onShowQueue: () -> Unit = {},
  isFavorite: Boolean = false,
  onToggleFavorite: () -> Unit = {},
  onShowMenu: () -> Unit = {},
  ```

- [ ] **Step 2: Wire the 3-dots button**

  Find (~line 151):

  ```kotlin
  IconButton(onClick = { }) {
      Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = "More", tint = Color.White)
  }
  ```

  Replace with:

  ```kotlin
  IconButton(onClick = onShowMenu) {
      Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = "More", tint = Color.White)
  }
  ```

- [ ] **Step 3: Add album header fade**

  Find the album `Text` in the top bar (~line 140):

  ```kotlin
  Text(
      text = track.album.uppercase(),
      color = Color.Gray,
      fontSize = 12.sp,
      fontWeight = FontWeight.Normal,
      letterSpacing = 1.sp,
      maxLines = 1,
      modifier = Modifier.weight(1f),
      textAlign = TextAlign.Center
  )
  ```

  Replace with:

  ```kotlin
  Text(
      text = track.album.uppercase(),
      color = Color.Gray,
      fontSize = 12.sp,
      fontWeight = FontWeight.Normal,
      letterSpacing = 1.sp,
      maxLines = 1,
      textAlign = TextAlign.Center,
      modifier = Modifier
          .weight(1f)
          .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
          .drawWithContent {
              drawContent()
              drawRect(
                  brush = Brush.horizontalGradient(
                      colorStops = arrayOf(
                          0.7f to Color.Black,
                          1.0f to Color.Transparent,
                      ),
                  ),
                  blendMode = BlendMode.DstIn,
              )
          },
  )
  ```

- [ ] **Step 4: Add like button animation state**

  Inside the `FullPlayer` composable body, after the existing `val particleColor = ...` block (~line 103), add:

  ```kotlin
  var likePressed by remember { mutableStateOf(false) }
  val likeScale by animateFloatAsState(
      targetValue = if (likePressed) 1.4f else 1f,
      animationSpec = spring(dampingRatio = 0.3f, stiffness = 600f),
      label = "LikeScale",
  )
  LaunchedEffect(likePressed) {
      if (likePressed) { delay(50); likePressed = false }
  }
  ```

- [ ] **Step 5: Wire like button with icon, color, and pop**

  Find the like `IconButton` in the Track Info Row (~line 219):

  ```kotlin
  IconButton(onClick = { }) {
      Icon(
          imageVector = Icons.Outlined.FavoriteBorder,
          contentDescription = "Like",
          tint = Color.White,
          modifier = Modifier.size(28.dp)
      )
  }
  ```

  Replace with:

  ```kotlin
  IconButton(onClick = { likePressed = true; onToggleFavorite() }) {
      Icon(
          imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
          contentDescription = "Like",
          tint = if (isFavorite) Color(0xFFE84B7A) else Color.White,
          modifier = Modifier
              .size(28.dp)
              .graphicsLayer { scaleX = likeScale; scaleY = likeScale },
      )
  }
  ```

- [ ] **Step 6: Add missing imports to `FullPlayer.kt`**

  Add these imports at the top of the file alongside existing imports:

  ```kotlin
  import androidx.compose.ui.draw.drawWithContent
  import androidx.compose.ui.graphics.BlendMode
  import androidx.compose.ui.graphics.Brush
  import androidx.compose.ui.graphics.CompositingStrategy
  ```

  (Note: `Icons.Filled.Favorite` is already covered by the existing `import androidx.compose.material.icons.filled.*`. `spring`, `animateFloatAsState`, `LaunchedEffect`, `mutableStateOf` are already imported.)

- [ ] **Step 7: Build to confirm no compile errors**

  ```bash
  ./gradlew :app:assembleDebug 2>&1 | grep -E "error:|BUILD"
  ```

  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

  ```bash
  git add app/src/main/java/com/laconical/player/ui/components/FullPlayer.kt
  git commit -m "feat: wire FullPlayer menu/like buttons; add like pop animation and album header fade"
  ```

---

## Task 4: `LibraryScreen` — wire FullPlayer + QueueMorphLayer fade

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt`

- [ ] **Step 1: Add `FadingMarqueeText` import**

  Add alongside the other component imports (~line 58):

  ```kotlin
  import com.laconical.player.ui.components.FadingMarqueeText
  ```

- [ ] **Step 2: Add `isMenuFromFullPlayer` state**

  After `var contextMenuArtSize by remember { mutableFloatStateOf(0f) }` (~line 194):

  ```kotlin
  var isMenuFromFullPlayer by remember { mutableStateOf(false) }
  ```

- [ ] **Step 3: Wire new parameters in the `FullPlayer(...)` call**

  The current `FullPlayer(...)` call (~line 346) ends with `onShowQueue = { ... }`. Add the three new named arguments after it:

  ```kotlin
  onShowQueue = {
      scope.launch {
          queueAnimatable.animateTo(
              1f,
              tween(QUEUE_ANIM_MS, easing = FastOutSlowInEasing)
          )
      }
  },
  isFavorite = favoriteIds.contains(currentTrack?.id),
  onToggleFavorite = { currentTrack?.let { viewModel.toggleFavorite(it.id) } },
  onShowMenu = {
      contextMenuTrack = currentTrack
      isMenuFromFullPlayer = true
  },
  ```

- [ ] **Step 4: Update `TrackMenuOverlay(...)` call**

  The current call (~line 763) has `onDismiss = { contextMenuTrack = null }`. Replace the entire `TrackMenuOverlay(...)` block with:

  ```kotlin
  contextMenuTrack?.let { track ->
      TrackMenuOverlay(
          track = track,
          artStartOffsetPx = contextMenuArtOffset,
          artStartSizePx = contextMenuArtSize,
          skipArtMorph = isMenuFromFullPlayer,
          isFavorite = favoriteIds.contains(track.id),
          dominantColor = playingTrackDominantColor,
          playlists = playlists,
          artTracks = playlistArtTracks,
          onDismiss = {
              contextMenuTrack = null
              isMenuFromFullPlayer = false
          },
          onFavoriteToggle = { viewModel.toggleFavorite(track.id) },
          onViewAlbum = {
              val fromFullPlayer = isMenuFromFullPlayer
              contextMenuTrack = null
              isMenuFromFullPlayer = false
              if (fromFullPlayer) {
                  scope.launch { scaffoldState.bottomSheetState.partialExpand() }
              }
              navController.navigate(NavRoute.albumDetailRoute(track.album))
          },
          onViewArtist = {
              val fromFullPlayer = isMenuFromFullPlayer
              contextMenuTrack = null
              isMenuFromFullPlayer = false
              if (fromFullPlayer) {
                  scope.launch { scaffoldState.bottomSheetState.partialExpand() }
              }
              navController.navigate(NavRoute.artistDetailRoute(track.artist))
          },
          onSelectPlaylist = { playlist ->
              viewModel.addTrackToPlaylist(track.id, playlist.id)
              playlistToastData = Pair(track.title, playlist.name)
              contextMenuTrack = null
              isMenuFromFullPlayer = false
          },
          onCreateNewPlaylist = { originOffset ->
              newPlaylistOriginOffset = originOffset
              pendingNewPlaylistTrack = track
              showCreateForPicker = true
          },
      )
  }
  ```

- [ ] **Step 5: Add `isStable` to `QueueMorphLayer` body**

  Inside `QueueMorphLayer`, right after the existing `val queueProg = queueAnimatable.value` line (~line 877), add:

  ```kotlin
  val isStable = (expandedFraction < 0.05f && queueProg < 0.05f) ||
                 (expandedFraction > 0.95f && queueProg < 0.05f) ||
                 (queueProg > 0.95f)
  ```

- [ ] **Step 6: Replace title `Text` with `FadingMarqueeText`**

  Find the title `Text` in `QueueMorphLayer` (~line 992):

  ```kotlin
  Text(
      text = currentTrack.title,
      color = Color.White,
      fontSize = finalTitleSize,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
      modifier = Modifier
          .offset(x = finalTitleLeft, y = finalTitleTop)
          .widthIn(max = finalTitleMaxWidthDp)
  )
  ```

  Replace with:

  ```kotlin
  FadingMarqueeText(
      text = currentTrack.title,
      color = Color.White,
      fontSize = finalTitleSize,
      fontWeight = FontWeight.Bold,
      isScrolling = isStable,
      modifier = Modifier
          .offset(x = finalTitleLeft, y = finalTitleTop)
          .widthIn(max = finalTitleMaxWidthDp),
  )
  ```

- [ ] **Step 7: Replace artist `Text` with `FadingMarqueeText`**

  Find the artist `Text` in `QueueMorphLayer` (~line 1018):

  ```kotlin
  Text(
      text = currentTrack.artist,
      color = Color(0xFFBBBBBB),
      fontSize = finalArtistSize,
      fontWeight = FontWeight.Medium,
      maxLines = 1,
      overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
      modifier = Modifier
          .offset(x = finalArtistLeft, y = finalArtistTop)
          .widthIn(max = finalTitleMaxWidthDp)
  )
  ```

  Replace with:

  ```kotlin
  FadingMarqueeText(
      text = currentTrack.artist,
      color = Color(0xFFBBBBBB),
      fontSize = finalArtistSize,
      fontWeight = FontWeight.Medium,
      isScrolling = isStable,
      modifier = Modifier
          .offset(x = finalArtistLeft, y = finalArtistTop)
          .widthIn(max = finalTitleMaxWidthDp),
  )
  ```

- [ ] **Step 8: Build and run all app tests**

  ```bash
  ./gradlew :app:assembleDebug :app:test 2>&1 | grep -E "error:|PASSED|FAILED|BUILD"
  ```

  Expected: `BUILD SUCCESSFUL`, all tests **PASSED**.

- [ ] **Step 9: Commit**

  ```bash
  git add app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
  git commit -m "feat: wire FullPlayer menu/like in LibraryScreen; apply FadingMarqueeText in QueueMorphLayer"
  ```

---

## Manual Verification Checklist

After all tasks are committed, install on device and verify:

- [ ] **Like button** — tap ♡ → icon fills pink with a spring pop; tap again → reverts to outline with same pop
- [ ] **3-dots from FullPlayer** — opens `TrackMenuOverlay` with no art flying in from the album art; card appears with scale+fade animation only
- [ ] **3-dots → Go to Album** — player collapses, navigates to album screen
- [ ] **3-dots → Go to Artist** — player collapses, navigates to artist screen
- [ ] **3-dots from track list** — art morph still animates normally (unaffected)
- [ ] **Marquee (long title)** — on a track with a long title: text idles for ~3 s, scrolls right-to-left, pauses ~2.5 s, loops
- [ ] **Marquee gates on stability** — during mini↔full transition, no scrolling; marquee starts only when fully at rest
- [ ] **Marquee in queue header** — same behaviour in the queue sheet header
- [ ] **Fade on short text** — short title/artist: no visible fade artifact (gradient starts at 70% width, invisible unless text reaches it)
- [ ] **Album header fade** — a very long album name fades at the right edge in the FullPlayer top bar
