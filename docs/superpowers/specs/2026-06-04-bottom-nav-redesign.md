# Bottom Nav Redesign

**Date:** 2026-06-04
**File:** `app/.../ui/components/LaconicalBottomNav.kt`

## Summary

Redesign `LaconicalBottomNav` to be more compact and expressive:
- Unselected tabs show icon only (no label)
- Selected tab shows icon + animated text + animated pill background
- All icons/text pure white; unselected icons at 42% opacity
- Pill background uses `dynamicColor` at ~26% opacity, expands from center like curtains opening

## Current State

`LaconicalBottomNav` currently:
- Always shows icon + text + dot indicator under every tab
- Active tab: `iconBaseColor` (brightened dynamic color), lifts icon by 4dp, shows dot
- Inactive tab: `Color(0xFF666666)` (dark gray)
- No background pill

## Design Decisions

### Colors
| Element | Value |
|---|---|
| Selected icon + text | `Color.White` |
| Unselected icon | `Color.White.copy(alpha = 0.42f)` |
| Pill background | `dynamicColor.copy(alpha = 0.26f)` (fallback: `Color.White.copy(alpha = 0.12f)`) |
| Pill border | `dynamicColor.copy(alpha = 0.18f)` (1dp, `RoundedCornerShape(50)`) |

No more `iconBaseColor` tinting. All active elements = pure white.

### Icon lift
Selected icon offset: `(-2).dp` (reduced from current `(-4).dp`).

### Remove
- Text label for unselected tabs — gone entirely
- Dot indicator (`16dp × 3dp` bar at bottom) — replaced by pill

### Pill background
- Shape: `RoundedCornerShape(50)` (stadium / fully circular ends), `42.dp` tall
- Width: animated `Dp` from `0.dp` → `76.dp`
- Animation: `animateDpAsState`, `tween(320ms, FastOutSlowInEasing)`
- Positioned: `Alignment.Center` inside each tab's `Box`
- Expands symmetrically from center → curtains-open effect

### Text appearance animation
`AnimatedVisibility(visible = isSelected)`:
- Enter: `fadeIn(tween(220)) + slideInVertically { it / 2 }` (slides up from half its height)
- Exit: `fadeOut(tween(150)) + slideOutVertically { it / 2 }`

## Layout Structure (per tab)

```
Box(Modifier.weight(1f).clickable(...)) {
    // Layer 1 — pill (z=0, Alignment.Center)
    Box(width=pillWidth, height=42.dp, clip=Stadium, bg=pillColor, border=pillBorder)

    // Layer 2 — content (z=1, Alignment.Center)
    Column(horizontalAlignment=CenterHorizontally) {
        Icon(tint=iconColor, modifier=Modifier.offset(y=yOffset))
        AnimatedVisibility(visible=isSelected) {
            Text(label, color=White, fontSize=10.sp, fontWeight=SemiBold)
        }
    }
}
```

The outer `Row` stays `height = 64.dp`, `padding horizontal = 32.dp`.

## Animations Summary

| Property | From | To | Spec |
|---|---|---|---|
| Pill width | `0.dp` | `76.dp` | `tween(320, FastOutSlowIn)` |
| Icon yOffset | `0.dp` | `(-2).dp` | `tween(300, FastOutSlowIn)` |
| Text alpha | 0 | 1 | `fadeIn tween(220)` via `AnimatedVisibility` |
| Text y | +half-height | 0 | `slideInVertically { it/2 }` via `AnimatedVisibility` |

## Removals

- `indicatorAlpha` animated float — delete
- `iconBaseColor` computation — delete (no longer tinting icons)
- `indicatorColor` — delete
- Dot indicator `Box` (16dp × 3dp) — delete
- `Text` outside `AnimatedVisibility` — replace with `AnimatedVisibility`-wrapped `Text`

## Files Changed

- `LaconicalBottomNav.kt` — single file, full rewrite of composable internals

## Out of Scope

- Favorites tab (not present in current nav — no change)
- Nav bar height, inset padding, route list — unchanged
- `dynamicColor` propagation from `MainViewModel` — unchanged
