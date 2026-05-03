# Color System Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace near-black dynamic-color system with dark-gray base (`#141313` bg / `#212121` surface) that tints subtly with dominant album art color, with top bar matching background and bottom nav brighter than background.

**Architecture:** Two `compositionLocalOf` Color values (`LocalAppBackground`, `LocalAppSurface`) computed + animated in `LibraryScreen`, provided via `CompositionLocalProvider` at root. All tab screens and chrome components read from locals — no prop drilling. Material You disabled so system wallpaper does not override.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, `animateColorAsState`, `compositionLocalOf`

---

## File Map

| File | Change |
|------|--------|
| `core/designsystem/.../Theme.kt` | `dynamicColor = false`, update `DarkColorScheme` base colors |
| `app/.../ui/ColorUtils.kt` | Add `LocalAppBackground` + `LocalAppSurface`; delete `deriveBarColor()` |
| `app/.../ui/LibraryScreen.kt` | Two animated colors, `CompositionLocalProvider` wrap, replace 8× `MaterialTheme.colorScheme.background`, drop `dominantColor` arg from TopBar |
| `app/.../ui/components/LaconicalTopBar.kt` | Drop `dominantColor` param; read `LocalAppBackground.current` |
| `app/.../ui/components/LaconicalBottomNav.kt` | Read `LocalAppSurface.current`; drop radial gradient; add indicator pill |

---

## Task 1: Disable Material You — Theme.kt

**Files:**
- Modify: `core/designsystem/src/main/kotlin/com/laconical/player/core/designsystem/Theme.kt`

- [ ] **Step 1: Update Theme.kt**

Replace the entire file content with:

```kotlin
package com.laconical.player.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    background = Color(0xFF141313),
    surface = Color(0xFF212121),
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun LaconicalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add core/designsystem/src/main/kotlin/com/laconical/player/core/designsystem/Theme.kt
git commit -m "feat: disable Material You, set dark base colors in DarkColorScheme"
```

---

## Task 2: Add CompositionLocals — ColorUtils.kt

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/ColorUtils.kt`

- [ ] **Step 1: Update ColorUtils.kt**

Replace the entire file:

```kotlin
package com.laconical.player.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

internal fun Color.toHsl(): FloatArray {
    val hsl = FloatArray(3)
    val r = red; val g = green; val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    hsl[2] = (max + min) / 2
    if (max == min) {
        hsl[0] = 0f; hsl[1] = 0f
    } else {
        val d = max - min
        hsl[1] = if (hsl[2] > 0.5f) d / (2f - max - min) else d / (max + min)
        when (max) {
            r -> hsl[0] = (g - b) / d + (if (g < b) 6f else 0f)
            g -> hsl[0] = (b - r) / d + 2f
            b -> hsl[0] = (r - g) / d + 4f
        }
        hsl[0] /= 6f
    }
    return hsl
}

val LocalAppBackground = compositionLocalOf { Color(0xFF141313) }
val LocalAppSurface    = compositionLocalOf { Color(0xFF212121) }
```

Note: `deriveBarColor()` is intentionally removed — it is replaced by `LocalAppBackground` in `LaconicalTopBar`.

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug
```

Expected: build error on `deriveBarColor` call sites (LaconicalTopBar.kt). This is expected — fixed in Task 4.

- [ ] **Step 3: Commit (even with expected compile errors is fine as draft; alternatively skip until Task 4)**

If you prefer green builds at every step, skip the commit here and do it together with Task 3 (which rewrites LaconicalTopBar and removes the only call site of `deriveBarColor`). Otherwise:

```bash
git add app/src/main/java/com/laconical/player/ui/ColorUtils.kt
git commit -m "feat: add LocalAppBackground + LocalAppSurface; remove deriveBarColor"
```

---

## Task 3: Fix TopBar — LaconicalTopBar.kt

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/LaconicalTopBar.kt`

- [ ] **Step 1: Rewrite LaconicalTopBar.kt**

Replace the entire file:

```kotlin
package com.laconical.player.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laconical.player.ui.LocalAppBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaconicalTopBar(
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val containerColor = LocalAppBackground.current

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = containerColor
        ),
        title = {
            Text(
                text = "Laconical Library",
                fontFamily = FontFamily.Serif,
                fontSize = 28.sp,
                color = Color.White
            )
        },
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { /* TODO: Settings */ }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }
            }
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.padding(top = statusBarHeight + 4.dp)
    )
}
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug
```

Expected: compile error in `LibraryScreen.kt` — `dominantColor` argument no longer exists on `LaconicalTopBar`. Fix in next step.

- [ ] **Step 3: Remove dominantColor arg from LaconicalTopBar call in LibraryScreen.kt**

Find this block (around line 332–337):

```kotlin
LaconicalTopBar(
    dominantColor = playingTrackDominantColor,
    onSearchClick = { navController.navigate(NavRoute.SEARCH) }
)
```

Replace with:

```kotlin
LaconicalTopBar(
    onSearchClick = { navController.navigate(NavRoute.SEARCH) }
)
```

- [ ] **Step 4: Build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/components/LaconicalTopBar.kt \
        app/src/main/java/com/laconical/player/ui/LibraryScreen.kt \
        app/src/main/java/com/laconical/player/ui/ColorUtils.kt
git commit -m "feat: top bar reads LocalAppBackground; remove deriveBarColor"
```

---

## Task 4: Two animated colors + CompositionLocalProvider — LibraryScreen.kt

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt`

- [ ] **Step 1: Add imports at top of LibraryScreen.kt**

Find the existing import block. Add these two lines if not already present:

```kotlin
import androidx.compose.runtime.CompositionLocalProvider
import com.laconical.player.ui.LocalAppBackground
import com.laconical.player.ui.LocalAppSurface
```

- [ ] **Step 2: Replace single animated color with two animated colors**

Find this block (around lines 142–158):

```kotlin
val targetColor = if (playingTrackDominantColor != null) {
    val vibe = playingTrackDominantColor!!
    Color(
        red = (0.04f * 0.92f) + (vibe.red * 0.08f),
        green = (0.04f * 0.92f) + (vibe.green * 0.08f),
        blue = (0.05f * 0.92f) + (vibe.blue * 0.08f),
        alpha = 1f
    )
} else {
    Color(0xFF0A0A0C)
}

val animatedColor by animateColorAsState(
    targetValue = targetColor,
    animationSpec = tween(1000),
    label = "BgColorAnim"
)
```

Replace with:

```kotlin
val targetBgColor = if (playingTrackDominantColor != null) {
    val d = playingTrackDominantColor!!
    Color(
        red   = 0.0784f * 0.93f + d.red   * 0.07f,
        green = 0.0745f * 0.93f + d.green * 0.07f,
        blue  = 0.0745f * 0.93f + d.blue  * 0.07f,
        alpha = 1f
    )
} else Color(0xFF141313)

val targetSurfaceColor = if (playingTrackDominantColor != null) {
    val d = playingTrackDominantColor!!
    Color(
        red   = 0.1294f * 0.94f + d.red   * 0.06f,
        green = 0.1294f * 0.94f + d.green * 0.06f,
        blue  = 0.1294f * 0.94f + d.blue  * 0.06f,
        alpha = 1f
    )
} else Color(0xFF212121)

val animatedBgColor by animateColorAsState(
    targetValue = targetBgColor,
    animationSpec = tween(1000),
    label = "BgColorAnim"
)

val animatedSurfaceColor by animateColorAsState(
    targetValue = targetSurfaceColor,
    animationSpec = tween(1000),
    label = "SurfaceColorAnim"
)
```

- [ ] **Step 3: Wrap root Box with CompositionLocalProvider and update Surface color**

Find this block (around lines 266–270):

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    Surface(
        color = animatedColor,
        modifier = Modifier.fillMaxSize()
    ) {
```

Replace with:

```kotlin
CompositionLocalProvider(
    LocalAppBackground provides animatedBgColor,
    LocalAppSurface    provides animatedSurfaceColor
) {
Box(modifier = Modifier.fillMaxSize()) {
    Surface(
        color = animatedBgColor,
        modifier = Modifier.fillMaxSize()
    ) {
```

Then scroll to the very bottom of the `LibraryScreen` composable function body. The last `}` before any private composable definitions closes the outermost `Box`. Add one more `}` after it for `CompositionLocalProvider`. The tail of the composable should read:

```kotlin
        // ... last morph overlay / QueueMorphLayer call
    } // closes outermost Box(modifier = Modifier.fillMaxSize())
} // closes CompositionLocalProvider — ADD THIS LINE
```

- [ ] **Step 4: Replace all MaterialTheme.colorScheme.background usages**

There are 8 occurrences in the NavHost route composables. Replace every instance of:

```kotlin
.background(MaterialTheme.colorScheme.background)
```

with:

```kotlin
.background(LocalAppBackground.current)
```

Locations (approximate lines): 361, 429, 446, 460, 477, 491, 507, 561.

Run a search to confirm all are caught:

```bash
grep -n "MaterialTheme.colorScheme.background" \
  app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
```

Expected: no results after replacement.

- [ ] **Step 5: Build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
git commit -m "feat: provide LocalAppBackground/Surface; replace MaterialTheme.colorScheme.background in all tabs"
```

---

## Task 5: Bottom nav — LocalAppSurface + indicator pill — LaconicalBottomNav.kt

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/LaconicalBottomNav.kt`

- [ ] **Step 1: Rewrite LaconicalBottomNav.kt**

Replace the entire file:

```kotlin
package com.laconical.player.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laconical.player.ui.LocalAppSurface
import com.laconical.player.ui.navigation.NavRoute

private data class NavItem(
    val label: String,
    val route: String,
    val icon: ImageVector,
)

@Composable
fun LaconicalBottomNav(
    selectedRoute: String,
    onTabSelected: (String) -> Unit,
    dynamicColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val bgColor = LocalAppSurface.current

    val iconBaseColor = if (dynamicColor != null) {
        Color(
            red   = (dynamicColor.red   * 0.3f + 0.7f).coerceIn(0f, 1f),
            green = (dynamicColor.green * 0.3f + 0.7f).coerceIn(0f, 1f),
            blue  = (dynamicColor.blue  * 0.3f + 0.7f).coerceIn(0f, 1f),
            alpha = 1f
        )
    } else Color.White

    val indicatorColor = dynamicColor ?: Color.White

    val items = listOf(
        NavItem("Tracks",    NavRoute.TRACKS,    Icons.Outlined.MusicNote),
        NavItem("Albums",    NavRoute.ALBUMS,    Icons.Outlined.Album),
        NavItem("Artists",   NavRoute.ARTISTS,   Icons.Outlined.Person),
        NavItem("Playlists", NavRoute.PLAYLISTS, Icons.Outlined.QueueMusic),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    key(item.route) {
                        val isSelected = selectedRoute == item.route

                        val itemColor = if (isSelected) iconBaseColor else Color(0xFF666666)

                        val yOffset by animateDpAsState(
                            targetValue = if (isSelected) (-4).dp else 0.dp,
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                            label = "iconOffsetAnim_${item.route}"
                        )

                        val indicatorAlpha by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0f,
                            animationSpec = tween(300),
                            label = "indicatorAlpha_${item.route}"
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onTabSelected(item.route) }
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = itemColor,
                                modifier = Modifier.offset(y = yOffset)
                            )
                            Text(
                                text = item.label,
                                color = itemColor,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .width(16.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(indicatorColor.copy(alpha = indicatorAlpha))
                            )
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/components/LaconicalBottomNav.kt
git commit -m "feat: bottom nav reads LocalAppSurface; add indicator pill; remove radial gradient"
```

---

## Task 6: Final verification

- [ ] **Step 1: Clean build**

```bash
./gradlew clean assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Verify no stale references**

```bash
grep -rn "deriveBarColor\|MaterialTheme.colorScheme.background\|0xFF0D0D10\|0xFF0A0A0C\|dynamicColor = true" \
  app/src/main/java/com/laconical/player/ui/
```

Expected: no results (or only results inside QueueMorphLayer which intentionally keeps its own dark colors).

- [ ] **Step 3: Install and verify visually**

```bash
./gradlew installDebug
```

Check:
- App opens: background is dark gray (not near-black)
- Top bar background matches screen background exactly (no separate darker strip)
- Bottom nav is visibly brighter than background
- Play a track: all surfaces shift slowly toward dominant color over ~1 second
- Switch tabs: background color is consistent across Tracks / Albums / Artists / Playlists
- Selected tab: small colored pill appears under icon

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "feat: complete color system redesign — dark gray base, CompositionLocal tinting, unified surfaces"
```
