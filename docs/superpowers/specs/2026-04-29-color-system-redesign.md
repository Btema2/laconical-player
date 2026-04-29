# Color System Redesign — Laconical Player

**Date:** 2026-04-29  
**Status:** Approved

## Goal

Replace near-black dynamic-color system with a controlled dark-gray base that tints subtly with the currently playing track's dominant color. Top bar matches background. Bottom nav is consistently brighter than background. All tab screens share one animated background color.

---

## Color Tokens

| Token | Default (no track) | Role |
|-------|--------------------|------|
| `AppBackground` | `#141313` | App background, top bar, all tab screens |
| `AppSurface` | `#212121` | Bottom nav, elevated UI elements |

---

## Tinting Formula

When a track is playing, dominant album art color blends into each base:

```kotlin
// AppBackground tint (7% dominant)
Color(
    red   = 0.0784f * 0.93f + dominant.red   * 0.07f,
    green = 0.0745f * 0.93f + dominant.green * 0.07f,
    blue  = 0.0745f * 0.93f + dominant.blue  * 0.07f,
    alpha = 1f
)

// AppSurface tint (6% dominant)
Color(
    red   = 0.1294f * 0.94f + dominant.red   * 0.06f,
    green = 0.1294f * 0.94f + dominant.green * 0.06f,
    blue  = 0.1294f * 0.94f + dominant.blue  * 0.06f,
    alpha = 1f
)
```

Both animated with `animateColorAsState(tween(1000ms))`. Surface always stays lighter than background — permanent elevation contrast.

---

## Architecture: CompositionLocal

Two locals defined in `ColorUtils.kt`:

```kotlin
val LocalAppBackground = staticCompositionLocalOf { Color(0xFF141313) }
val LocalAppSurface    = staticCompositionLocalOf { Color(0xFF212121) }
```

`LibraryScreen` computes both animated colors and provides them at root:

```kotlin
CompositionLocalProvider(
    LocalAppBackground provides animatedBgColor,
    LocalAppSurface    provides animatedSurfaceColor
) {
    BottomSheetScaffold(...) { ... }
}
```

No prop drilling. All children read animated values automatically.

---

## Files Changed

### `Theme.kt`
- Set `dynamicColor = false` — disables Material You override so custom colors apply.

### `ColorUtils.kt`
- Add `LocalAppBackground` and `LocalAppSurface` vals.
- Replace existing single `targetColor` tinting block with two-color block (bg + surface).
- Remove `deriveBarColor()` — no longer used.

### `LibraryScreen.kt`
- Replace existing `targetColor` / `animatedColor` with two animated colors: `animatedBgColor`, `animatedSurfaceColor`.
- Wrap root content in `CompositionLocalProvider`.
- Replace all `MaterialTheme.colorScheme.background` usages in tab route boxes with `LocalAppBackground.current`.
- Remove `dominantColor =` argument from `LaconicalTopBar` call.

### `LaconicalTopBar.kt`
- Drop `dominantColor: Color?` param and `deriveBarColor()` usage.
- Read `LocalAppBackground.current` for `containerColor` and `scrolledContainerColor`.

### `LaconicalBottomNav.kt`
- Drop `Color(0xFF0D0D10)` fallback and radial gradient hack.
- Read `LocalAppSurface.current` as flat background base.
- Keep `dynamicColor` param for dominant color — used only for icon tinting and indicator.
- Selected tab indicator: small `3dp` tall × `16dp` wide rounded pill below icon, colored with dominant color (or white fallback), fades in/out via `animateFloatAsState` on selected state.

---

## Invariants

- `AppSurface` is always visually lighter than `AppBackground` — guaranteed by higher base value.
- `deriveBarColor()` deleted — top bar color is always identical to background.
- Material You disabled — no system wallpaper color bleeds in.
- Animation duration: 1000ms tween on both colors — slow enough to feel organic.
