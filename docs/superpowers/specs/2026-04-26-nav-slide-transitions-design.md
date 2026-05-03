# Nav Slide Transitions Design

**Date:** 2026-04-26
**Branch:** bugfixes

## Problem

All route changes use Compose Navigation's default fade. Fading screens remain tappable until fully transparent, causing ghost taps that corrupt app state.

## Goals

1. Replace fades with directional slide transitions.
2. Tab switches slide left/right based on tab order.
3. Detail drill-downs always push from right; back always returns to left.
4. Block all pointer input during any active transition (300ms window).

## Out of Scope

- Mini→full player morph (separate system, unrelated).
- QueueSheet transition (separate system).

---

## Architecture

### New File: `ui/navigation/NavTransitions.kt`

Single responsibility: produce `EnterTransition` / `ExitTransition` values from two `NavBackStackEntry` references.

```kotlin
val TAB_ORDER = mapOf(
    NavRoute.TRACKS   to 0,
    NavRoute.ALBUMS   to 1,
    NavRoute.ARTISTS  to 2,
    NavRoute.PLAYLISTS to 3,
)

private fun isForward(from: NavBackStackEntry, to: NavBackStackEntry): Boolean {
    val fi = TAB_ORDER[from.destination.route]
    val ti = TAB_ORDER[to.destination.route]
    return if (fi != null && ti != null) ti > fi else true
}

fun navEnterTransition(from: NavBackStackEntry, to: NavBackStackEntry): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        initialOffsetX = { if (isForward(from, to)) it else -it }
    )

fun navExitTransition(from: NavBackStackEntry, to: NavBackStackEntry): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(300, easing = FastOutLinearInEasing),
        targetOffsetX = { if (isForward(from, to)) -it else it }
    )

// Pop = going back = always left
fun navPopEnterTransition(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        initialOffsetX = { -it }
    )

fun navPopExitTransition(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(300, easing = FastOutLinearInEasing),
        targetOffsetX = { it }
    )
```

### Modified: `LibraryScreen.kt`

Two changes only:

**1. Pass transition lambdas to NavHost:**

```kotlin
NavHost(
    navController = navController,
    startDestination = NavRoute.TRACKS,
    modifier = Modifier.fillMaxSize(),
    enterTransition    = { navEnterTransition(initialState, targetState) },
    exitTransition     = { navExitTransition(initialState, targetState) },
    popEnterTransition = { navPopEnterTransition() },
    popExitTransition  = { navPopExitTransition() },
)
```

**2. Wrap NavHost in a Box with input-blocking overlay:**

```kotlin
val visibleEntries by navController.visibleEntries.collectAsState()
val isTransitioning = visibleEntries.size > 1

Box(modifier = Modifier.fillMaxSize()) {
    NavHost(...)

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
```

---

## Transition Direction Table

| Navigation action              | Enter from | Exit to |
|-------------------------------|------------|---------|
| Tab: Tracks → Albums (forward) | right      | left    |
| Tab: Albums → Tracks (backward)| left       | right   |
| Drill into detail              | right      | left    |
| Back from detail               | left       | right   |

Tab switches use `navigate()` with `popUpTo(TRACKS)` → triggers `enterTransition` + `exitTransition`, not pop variants. Back button uses pop variants.

---

## Animation Spec

| Property | Value |
|----------|-------|
| Duration | 300ms |
| Enter easing | `FastOutSlowInEasing` |
| Exit easing | `FastOutLinearInEasing` |
| Offset | Full screen width (`{ it }`) |

---

## Files Changed

| File | Change |
|------|--------|
| `ui/navigation/NavTransitions.kt` | New — transition spec functions |
| `ui/LibraryScreen.kt` | Add 4 lambdas to NavHost + input-blocking overlay |

No changes to any screen composable, ViewModel, or navigation graph structure.
