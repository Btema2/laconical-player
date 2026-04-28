# Fast List Animations — Design Spec

**Date:** 2026-04-28  
**Status:** Approved

## Problem

Items in lazy lists (Tracks, Albums, Artists, Playlists) use `staggeredEntrance` to fade + slide into view. The stagger delay is computed as `min(index, STAGGER_CAP) * STAGGER_MS`. With `STAGGER_CAP=12` and `STAGGER_MS=30`, any item at index ≥ 12 waits **360ms** before its 180ms animation starts — **540ms total** of invisible content on fast scroll.

## Goal

Scroll-in items (index > STAGGER_CAP) appear within **90ms**. Initial-load items (index 0..STAGGER_CAP) keep their cascade but complete faster.

## Scope

Single file: `app/src/main/java/com/laconical/player/ui/components/StaggeredEntrance.kt`  
No call-site changes. All four screens (Tracks, Albums, Artists, Playlists) benefit automatically.

## Design

### Two-speed mode

Items are split into two classes at composition time:

- **Initial batch** (`index ≤ STAGGER_CAP`): cascading stagger delay, standard animation duration.
- **Scroll-in** (`index > STAGGER_CAP`): zero delay, fast animation duration.

### Constants

| Constant | Old | New |
|---|---|---|
| `STAGGER_MS` | 30 | 25 |
| `STAGGER_CAP` | 12 | 8 |
| `ANIM_DURATION_MS` | 180 | 150 |
| `SCROLL_IN_DURATION_MS` | — | 90 (new) |
| Start offset | 20dp | 16dp |

### Logic

```kotlin
val isInitialBatch = index <= STAGGER_CAP
val staggerDelay = if (isInitialBatch) index.toLong() * STAGGER_MS else 0L
val duration = if (isInitialBatch) ANIM_DURATION_MS else SCROLL_IN_DURATION_MS
```

### Result timeline

| Item type | Delay | Animation | Total visible |
|---|---|---|---|
| Initial item 0 | 0ms | 150ms | 150ms |
| Initial item 8 | 200ms | 150ms | 350ms |
| Scroll-in item (any) | 0ms | 90ms | **90ms** |

Previous worst case: 540ms. New worst case: 350ms (initial), 90ms (scroll).

## Trade-offs

- Tiny behavioral discontinuity at the STAGGER_CAP boundary: item 8 cascades, item 9 snaps in at 90ms. Imperceptible in practice — both never enter the viewport simultaneously.
- Approach C (viewport-relative stagger) would be more theoretically correct but requires threading `LazyListState` to all four usage sites for no visible UX gain.

## Testing

- Manual: scroll fast through a large track/album/artist list — no visible empty space.
- Manual: switch to a tab for the first time — initial cascade still present and feels snappy.
- No automated tests needed (pure animation timing, no logic change).
