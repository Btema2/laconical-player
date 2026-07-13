package com.laconical.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.laconical.player.ui.toHsl

@Composable
fun LaconicalTopBar(
    isSearchOpen: Boolean,
    searchQuery: String,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    dominantColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current

    val expandProgress = remember { Animatable(0f) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
            expandProgress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        } else {
            expandProgress.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
        }
    }

    var topBarWidthPx by remember { mutableStateOf(0f) }

    // Arrow + text field fade in during last 35% of expand
    val innerAlpha = ((expandProgress.value - 0.65f) / 0.35f).coerceIn(0f, 1f)

    // Bar fill: dominant hue, heavily desaturated (gray with color hint), dark
    val barFillColor = remember(dominantColor) {
        dominantColor?.let {
            val hsl = it.toHsl()
            Color(android.graphics.Color.HSVToColor(floatArrayOf(hsl[0] * 360f, 0.18f, 0.22f)))
        } ?: Color(0xFF2A2A2A)
    }
    val barBorderColor = remember(dominantColor) {
        dominantColor?.let {
            val hsl = it.toHsl()
            Color(android.graphics.Color.HSVToColor(floatArrayOf(hsl[0] * 360f, 0.22f, 0.36f)))
        } ?: Color(0xFF3E3E3E)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = statusBarHeight + 4.dp)
            .height(56.dp)
            .onSizeChanged { topBarWidthPx = it.width.toFloat() }
    ) {
        // ── Title ───────────────────────────────────────────────────
        Text(
            text = "Laconical Library",
            fontFamily = FontFamily.Serif,
            fontSize = 28.sp,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
                .graphicsLayer {
                    alpha = lerp(1f, 0f, (expandProgress.value / 0.5f).coerceIn(0f, 1f))
                    translationX = lerp(0f, -24.dp.toPx(), expandProgress.value)
                }
        )

        // ── Right icons (Settings + Search) ─────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.graphicsLayer {
                    val p = (expandProgress.value / 0.55f).coerceIn(0f, 1f)
                    alpha = lerp(1f, 0f, p)
                    scaleX = lerp(1f, 0.7f, p)
                    scaleY = lerp(1f, 0.7f, p)
                }
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }

            IconButton(
                onClick = onSearchOpen,
                enabled = expandProgress.value < 0.4f,
                modifier = Modifier.graphicsLayer {
                    alpha = lerp(1f, 0f, (expandProgress.value / 0.4f).coerceIn(0f, 1f))
                }
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
            }
        }

        // ── Back arrow — outside and to the left of the bar ─────────
        IconButton(
            onClick = { onQueryChange(""); onSearchClose() },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer { alpha = innerAlpha }
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Close search",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // ── Expanding search bar ────────────────────────────────────
        // Always composed so focusRequester has a node; invisible when progress = 0.
        // Full width reserves 40dp (arrow) + 8dp (gap) + 8dp (right padding) = 56dp.
        val barAlpha = (expandProgress.value / 0.2f).coerceIn(0f, 1f)
        val barWidthDp = if (topBarWidthPx > 0f) {
            with(density) {
                lerp(36f, topBarWidthPx - 56.dp.toPx(), expandProgress.value).toDp()
            }
        } else 36.dp

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .graphicsLayer { alpha = barAlpha }
                .width(barWidthDp)
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .drawBehind {
                    drawRoundRect(
                        color = barFillColor,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())
                    )
                    drawRoundRect(
                        color = barBorderColor,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )
                }
                .padding(horizontal = 12.dp)
        ) {
            BasicTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = innerAlpha }
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Color.White),
                decorationBox = { inner ->
                    Box {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search tracks, albums…",
                                style = TextStyle(color = Color(0xFF888888), fontSize = 15.sp)
                            )
                        }
                        inner()
                    }
                }
            )
        }
    }
}
