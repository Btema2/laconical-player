# Nav Slide Transitions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all fade-only NavHost transitions with directional slide animations and block pointer input during the 300 ms transition window.

**Architecture:** A new `NavTransitions.kt` file owns the pure transition logic (testable, no NavHost coupling). `LibraryScreen.kt` passes the 4 transition lambdas to `NavHost` and wraps it in a `Box` that overlays a pointer-consuming scrim whenever `navController.visibleEntries.size > 1`.

**Tech Stack:** Kotlin · Jetpack Compose · Navigation Compose 2.8.5 · JUnit 4

---

## File Map

| File | Action |
|------|--------|
| `app/src/test/java/com/laconical/player/ui/navigation/NavTransitionsTest.kt` | Create — JUnit 4 unit tests for `isForwardNavigation` |
| `app/src/main/java/com/laconical/player/ui/navigation/NavTransitions.kt` | Create — TAB_ORDER, `isForwardNavigation`, 4 transition functions |
| `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt` | Modify — imports, `visibleEntries` state, NavHost params, input-blocking Box |

---

## Task 1: Write failing test for `isForwardNavigation`

**Files:**
- Create: `app/src/test/java/com/laconical/player/ui/navigation/NavTransitionsTest.kt`

- [ ] **Step 1: Create the test file**

```kotlin
package com.laconical.player.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavTransitionsTest {

    @Test
    fun `tracks to albums is forward`() {
        assertTrue(isForwardNavigation("tracks", "albums"))
    }

    @Test
    fun `albums to tracks is backward`() {
        assertFalse(isForwardNavigation("albums", "tracks"))
    }

    @Test
    fun `tracks to artists skips over albums and is forward`() {
        assertTrue(isForwardNavigation("tracks", "artists"))
    }

    @Test
    fun `artists to albums is backward`() {
        assertFalse(isForwardNavigation("artists", "albums"))
    }

    @Test
    fun `playlists to tracks is backward`() {
        assertFalse(isForwardNavigation("playlists", "tracks"))
    }

    @Test
    fun `detail route is always forward`() {
        assertTrue(isForwardNavigation("tracks", "album_detail/Radiohead"))
    }

    @Test
    fun `null routes default to forward`() {
        assertTrue(isForwardNavigation(null, null))
    }
}
```

- [ ] **Step 2: Run the test — verify it fails to compile (RED)**

```bash
./gradlew :app:testDebugUnitTest --tests "com.laconical.player.ui.navigation.NavTransitionsTest" 2>&1 | tail -20
```

Expected: compile error — `Unresolved reference: isForwardNavigation`

---

## Task 2: Create `NavTransitions.kt`

**Files:**
- Create: `app/src/main/java/com/laconical/player/ui/navigation/NavTransitions.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.laconical.player.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

private const val SLIDE_DURATION_MS = 300

internal val TAB_ORDER = mapOf(
    NavRoute.TRACKS    to 0,
    NavRoute.ALBUMS    to 1,
    NavRoute.ARTISTS   to 2,
    NavRoute.PLAYLISTS to 3,
)

/**
 * True when navigating to a higher tab index (or to a detail screen).
 * Pure function — testable without Android deps.
 */
internal fun isForwardNavigation(fromRoute: String?, toRoute: String?): Boolean {
    val fi = TAB_ORDER[fromRoute]
    val ti = TAB_ORDER[toRoute]
    return if (fi != null && ti != null) ti > fi else true
}

fun navEnterTransition(from: NavBackStackEntry, to: NavBackStackEntry): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(SLIDE_DURATION_MS, easing = FastOutSlowInEasing),
        initialOffsetX = { if (isForwardNavigation(from.destination.route, to.destination.route)) it else -it }
    )

fun navExitTransition(from: NavBackStackEntry, to: NavBackStackEntry): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(SLIDE_DURATION_MS, easing = FastOutLinearInEasing),
        targetOffsetX = { if (isForwardNavigation(from.destination.route, to.destination.route)) -it else it }
    )

fun navPopEnterTransition(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(SLIDE_DURATION_MS, easing = FastOutSlowInEasing),
        initialOffsetX = { -it }
    )

fun navPopExitTransition(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(SLIDE_DURATION_MS, easing = FastOutLinearInEasing),
        targetOffsetX = { it }
    )
```

- [ ] **Step 2: Run the tests — verify GREEN**

```bash
./gradlew :app:testDebugUnitTest --tests "com.laconical.player.ui.navigation.NavTransitionsTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` — 7 tests pass

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/laconical/player/ui/navigation/NavTransitionsTest.kt \
        app/src/main/java/com/laconical/player/ui/navigation/NavTransitions.kt
git commit -m "feat: add NavTransitions — directional slide specs + unit tests"
```

---

## Task 3: Wire transitions into `LibraryScreen.kt`

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt`

### Step 1: Add missing imports

- [ ] Add these imports after the existing `import androidx.navigation.navArgument` line (around line 72):

```kotlin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.laconical.player.ui.navigation.navEnterTransition
import com.laconical.player.ui.navigation.navExitTransition
import com.laconical.player.ui.navigation.navPopEnterTransition
import com.laconical.player.ui.navigation.navPopExitTransition
```

### Step 2: Add `visibleEntries` state

- [ ] After the `val queueAnimatable = remember { Animatable(0f) }` line (line ~159), add:

```kotlin
val visibleEntries by navController.visibleEntries.collectAsState()
val isTransitioning = visibleEntries.size > 1
```

### Step 3: Replace the `NavHost` call with a Box-wrapped version

- [ ] Find this exact block (lines 300–304):

```kotlin
                    if (hasPermission) {
                        NavHost(
                            navController = navController,
                            startDestination = NavRoute.TRACKS,
                            modifier = Modifier.fillMaxSize()
                        ) {
```

Replace with:

```kotlin
                    if (hasPermission) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            NavHost(
                                navController = navController,
                                startDestination = NavRoute.TRACKS,
                                modifier = Modifier.fillMaxSize(),
                                enterTransition    = { navEnterTransition(initialState, targetState) },
                                exitTransition     = { navExitTransition(initialState, targetState) },
                                popEnterTransition = { navPopEnterTransition() },
                                popExitTransition  = { navPopExitTransition() },
                            ) {
```

### Step 4: Add the input-blocking overlay + close the Box

- [ ] Find this exact block (line ~465–466), the closing braces of NavHost and the `if (hasPermission)`:

```kotlin
                        }
                    } else {
```

Replace with:

```kotlin
                        }

                        if (isTransitioning) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                awaitPointerEvent(PointerEventPass.Initial)
                                                    .changes.forEach { it.consume() }
                                            }
                                        }
                                    }
                            )
                        }
                        }
                    } else {
```

> Note: the extra `}` on its own line closes the `Box(modifier = Modifier.fillMaxSize())` opened in Step 3.

---

## Task 4: Build verification and commit

- [ ] **Step 1: Build**

```bash
./gradlew assembleDebug 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`

If it fails, common causes:
- Missing import for `PointerEventPass` — verify the import line is present
- `navController.visibleEntries` not found — Navigation version must be ≥ 2.7.0 (project uses 2.8.5, so this is fine)
- Extra/missing `}` — check the Box wrapper has exactly one more `}` than before

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
git commit -m "feat: wire slide transitions + input blocker into NavHost"
```

---

## Manual Verification Checklist

After installing on device (`./gradlew installDebug`):

- [ ] Tracks → Albums: content slides in from **right**, Tracks slides out to **left**
- [ ] Albums → Tracks: content slides in from **left**, Albums slides out to **right**
- [ ] Tracks → Artists (skip Albums): slides **right** (forward, higher index)
- [ ] Playlists → Tracks: slides **left** (backward, lower index)
- [ ] Tap track row → album detail: pushes in from **right**
- [ ] Back from album detail: pops out to **right**, previous screen slides in from **left**
- [ ] Rapidly tap a disappearing screen during transition: no action fires (scrim blocks it)
- [ ] Mini↔full player morph: **unaffected** (separate system)
- [ ] QueueSheet: **unaffected** (separate system)
