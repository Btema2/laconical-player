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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp

@Composable
fun LaconicalTopBar(
    isSearchOpen: Boolean,
    searchQuery: String,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current

    val expandProgress = remember { Animatable(0f) }
    val focusRequester = remember { FocusRequester() }

    // Animate bar open/close
    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            focusRequester.requestFocus()
            expandProgress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        } else {
            expandProgress.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
        }
    }

    // Track search icon position in root coords so the bar can start from there
    var searchIconRootX by remember { mutableStateOf(0f) }
    var topBarWidthPx by remember { mutableStateOf(0f) }

    // Inner alpha for back arrow and placeholder: only fades in in last 35% of progress
    val innerAlpha = ((expandProgress.value - 0.65f) / 0.35f).coerceIn(0f, 1f)

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
            // Settings icon — fades + scales out
            IconButton(
                onClick = { /* TODO: Settings */ },
                modifier = Modifier.graphicsLayer {
                    val p = (expandProgress.value / 0.55f).coerceIn(0f, 1f)
                    alpha = lerp(1f, 0f, p)
                    scaleX = lerp(1f, 0.7f, p)
                    scaleY = lerp(1f, 0.7f, p)
                }
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }

            // Search icon — fades out quickly
            IconButton(
                onClick = onSearchOpen,
                enabled = expandProgress.value < 0.4f,
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        searchIconRootX = coords.positionInRoot().x
                    }
                    .graphicsLayer {
                        alpha = lerp(1f, 0f, (expandProgress.value / 0.4f).coerceIn(0f, 1f))
                    }
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
            }
        }

        // ── Expanding search bar ────────────────────────────────────
        // Grows from the search icon position to fill the bar minus 8dp left margin
        val barAlpha = (expandProgress.value / 0.2f).coerceIn(0f, 1f)
        if (expandProgress.value > 0.01f && topBarWidthPx > 0f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .graphicsLayer { alpha = barAlpha }
                    .width(
                        with(density) {
                            lerp(
                                start = 36f,
                                stop = topBarWidthPx - 8.dp.toPx(),
                                fraction = expandProgress.value
                            ).toDp()
                        }
                    )
                    .height(40.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .drawBehind {
                        drawRoundRect(
                            color = Color(0xFF1E1E28),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx())
                        )
                        drawRoundRect(
                            color = Color(0xFF3A3A4A),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                        )
                    }
                    .padding(horizontal = 4.dp)
            ) {
                // Back arrow
                IconButton(
                    onClick = {
                        onQueryChange("")
                        onSearchClose()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer { alpha = innerAlpha }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close search",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Text field
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
                                    style = TextStyle(color = Color(0xFF666666), fontSize = 15.sp)
                                )
                            }
                            inner()
                        }
                    }
                )
            }
        }
    }
}
