# Bottom Nav Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign `LaconicalBottomNav` so unselected tabs show icon-only (dimmed white), and selected tab shows a white icon + animated label + curtains-expanding pill background tinted with the dominant album color.

**Architecture:** Single-file change to `LaconicalBottomNav.kt`. Replace the per-tab `Column` layout with a `Box`-per-tab layout so the animated pill sits behind the icon+label column. `AnimatedVisibility` controls label entry/exit; `animateDpAsState` drives icon lift and pill width. Two new JVM tests (Robolectric + Compose) verify label visibility behavior.

**Tech Stack:** Kotlin · Jetpack Compose · `animateDpAsState` · `AnimatedVisibility` · Robolectric 4.13 · `ui-test-junit4`

---

## File Map

| Action | Path |
|--------|------|
| Modify | `app/build.gradle.kts` |
| Modify | `app/src/main/java/com/laconical/player/ui/components/LaconicalBottomNav.kt` |
| Create | `app/src/test/java/com/laconical/player/ui/components/LaconicalBottomNavTest.kt` |

---

### Task 1: Enable Robolectric + Compose testing in `:app` and write failing tests

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/test/java/com/laconical/player/ui/components/LaconicalBottomNavTest.kt`

- [ ] **Step 1: Add test dependencies and `testOptions` to `app/build.gradle.kts`**

In `app/build.gradle.kts`, inside the `android { }` block, add after the `compileOptions { }` block:

```kotlin
testOptions {
    unitTests {
        isIncludeAndroidResources = true
    }
}
```

And in the `dependencies { }` block, add after the existing `testImplementation("junit:junit:4.13.2")` line:

```kotlin
testImplementation(libs.robolectric)
testImplementation("androidx.compose.ui:ui-test-junit4")
```

- [ ] **Step 2: Create the test file**

Create `app/src/test/java/com/laconical/player/ui/components/LaconicalBottomNavTest.kt`:

```kotlin
package com.laconical.player.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.laconical.player.ui.navigation.NavRoute
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class LaconicalBottomNavTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `selected tab shows label`() {
        composeTestRule.setContent {
            LaconicalBottomNav(
                selectedRoute = NavRoute.ALBUMS,
                onTabSelected = {}
            )
        }
        composeTestRule.onNodeWithText("Albums").assertIsDisplayed()
    }

    @Test
    fun `unselected tabs show no label`() {
        composeTestRule.setContent {
            LaconicalBottomNav(
                selectedRoute = NavRoute.ALBUMS,
                onTabSelected = {}
            )
        }
        composeTestRule.onNodeWithText("Tracks").assertDoesNotExist()
        composeTestRule.onNodeWithText("Artists").assertDoesNotExist()
        composeTestRule.onNodeWithText("Playlists").assertDoesNotExist()
    }

    @Test
    fun `tapping tab fires callback with that route`() {
        var tapped = ""
        composeTestRule.setContent {
            LaconicalBottomNav(
                selectedRoute = NavRoute.ALBUMS,
                onTabSelected = { tapped = it }
            )
        }
        composeTestRule.onNodeWithText("Albums").performClick()
        assertEquals(NavRoute.ALBUMS, tapped)
    }
}
```

- [ ] **Step 3: Run tests — expect FAIL (label logic doesn't match yet / existing code always shows labels)**

```bash
./gradlew :app:test --tests "com.laconical.player.ui.components.LaconicalBottomNavTest" --info 2>&1 | tail -30
```

Expected: `unselected tabs show no label` fails because current code always renders `Text` for every tab.

---

### Task 2: Rewrite `LaconicalBottomNav.kt`

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/LaconicalBottomNav.kt`

- [ ] **Step 1: Replace the file with the new implementation**

Full replacement for `app/src/main/java/com/laconical/player/ui/components/LaconicalBottomNav.kt`:

```kotlin
package com.laconical.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

private val PillShape = RoundedCornerShape(50)

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
    val pillFill   = dynamicColor?.copy(alpha = 0.26f) ?: Color.White.copy(alpha = 0.12f)
    val pillBorder = dynamicColor?.copy(alpha = 0.18f) ?: Color.Transparent

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

                        val iconTint = if (isSelected) Color.White else Color.White.copy(alpha = 0.42f)

                        val yOffset by animateDpAsState(
                            targetValue = if (isSelected) (-2).dp else 0.dp,
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                            label = "iconOffset_${item.route}"
                        )

                        val pillWidth by animateDpAsState(
                            targetValue = if (isSelected) 76.dp else 0.dp,
                            animationSpec = tween(320, easing = FastOutSlowInEasing),
                            label = "pillWidth_${item.route}"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onTabSelected(item.route) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // Pill — expands from center (curtains effect)
                            Box(
                                modifier = Modifier
                                    .width(pillWidth)
                                    .height(42.dp)
                                    .clip(PillShape)
                                    .background(pillFill)
                                    .border(1.dp, pillBorder, PillShape)
                            )

                            // Icon + label on top of pill
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = iconTint,
                                    modifier = Modifier.offset(y = yOffset)
                                )
                                AnimatedVisibility(
                                    visible = isSelected,
                                    enter = fadeIn(tween(220)) + slideInVertically { it / 2 },
                                    exit  = fadeOut(tween(150)) + slideOutVertically { it / 2 }
                                ) {
                                    Text(
                                        text = item.label,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build — expect success**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

---

### Task 3: Run tests, verify, commit

- [ ] **Step 1: Run the nav bar tests**

```bash
./gradlew :app:test --tests "com.laconical.player.ui.components.LaconicalBottomNavTest" --info 2>&1 | tail -30
```

Expected: all 3 tests pass — `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run full app test suite to check for regressions**

```bash
./gradlew :app:test 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, no failures.

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts \
        app/src/main/java/com/laconical/player/ui/components/LaconicalBottomNav.kt \
        app/src/test/java/com/laconical/player/ui/components/LaconicalBottomNavTest.kt
git commit -m "feat: redesign bottom nav — pill animation, icon-only unselected tabs"
```

---

## Visual Verification Checklist

After installing on device (`./gradlew installDebug`):

- [ ] Unselected tabs: icon only, white at ~42% opacity (visibly dimmer than selected)
- [ ] Selected tab: pure white icon lifted 2dp, label fades+slides in below icon
- [ ] Pill: appears behind selected tab, expands from narrow line to full width (~76dp)
- [ ] Tapping another tab: old pill shrinks away, new pill expands; text fades out/in
- [ ] With album art playing: pill tinted with dominant color, not plain white
- [ ] Without any track: pill falls back to dim white tint
