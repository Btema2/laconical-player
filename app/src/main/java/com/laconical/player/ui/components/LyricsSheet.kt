package com.laconical.player.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Size
import com.laconical.player.core.model.Track
import com.laconical.player.core.model.lyrics.LyricsSource
import com.laconical.player.ui.AudioArtData
import com.laconical.player.ui.LyricsUiState
import com.laconical.player.ui.MainViewModel

/**
 * Lyrics layer: blurred album-art background, top-bar chrome (back-arrow / refresh / menu),
 * the big synced/plain lyrics text with a top/bottom fade, a compact loading/message popup
 * card, and the shuffle/repeat controls (fade in — they have no full-player position to morph
 * FROM, unlike prev/play/next which the overlay lerps in from the full player).
 *
 * Composed inside `QueueMorphLayer` in `LibraryScreen.kt`, BELOW the morph overlay — the same
 * relationship `QueueSheet` has to that overlay. The overlay is the one that draws and lerps
 * the shared elements (album art → thumbnail, title/artist → top-bar center, seek bar,
 * prev/play/next) on top of this layer, so opening lyrics morphs continuously instead of
 * teleporting. See CLAUDE.md → "Lyrics (feature/lyrics)" and the mini→full Animation Pitfalls
 * this composable deliberately does NOT touch — this is purely a `full → lyrics` stage-2
 * transition, analogous to `full → queue`.
 *
 * `progress` (0f..1f) is `lyricsProg` from `LibraryScreen`'s `lyricsAnimatable` — every fade in
 * this file rides that single driver so the whole layer's entrance/exit stays in lockstep with
 * the overlay's positional lerp.
 */
@Composable
fun LyricsSheet(
    viewModel: MainViewModel,
    currentTrack: Track,
    progress: Float,
    dominantColor: Color?,
    onDismiss: () -> Unit,
    onShowMenu: () -> Unit,
    topInset: Dp,
    bottomInset: Dp,
    shuffleCenterX: Dp,
    repeatCenterX: Dp,
    controlsCenterY: Dp,
    modifier: Modifier = Modifier,
) {
    if (progress < 0.001f) return

    val lyricsUiState by viewModel.lyricsUiState.collectAsState()
    val lyricsLineIndex by viewModel.currentLyricsLineIndex.collectAsState()
    val shuffleModeEnabled by viewModel.shuffleModeEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()

    val accent = dominantColor ?: Color(0xFF9E9EFF)
    val context = LocalContext.current
    val imageModel: ImageRequest = remember(currentTrack.mediaUri) {
        ImageRequest.Builder(context)
            .data(AudioArtData(currentTrack.mediaUri, currentTrack.albumArtUri))
            .size(Size.ORIGINAL)
            .transformations(DownscaleBlurTransformation())
            .build()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress }
            // Swallow taps so nothing beneath (track list, mini player ghosts) reacts while
            // this layer is even partially visible — same defensive pattern the old fullscreen
            // overlay used.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        // ── Blurred background ───────────────────────────────────────────
        SubcomposeAsyncImage(
            model = imageModel,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            error = { Box(Modifier.fillMaxSize().background(Color(0xFF141313))) }
        )
        // Dark scrim first (keeps body text contrast safe regardless of how bright the art
        // is), dominant-color wash on top for mood.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.92f),
                            Color.Black.copy(alpha = 0.88f),
                            Color.Black.copy(alpha = 0.95f),
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(accent.copy(alpha = 0.10f))
        )

        // ── Top bar chrome ───────────────────────────────────────────────
        // Title/artist are NOT drawn here — the morph overlay renders them on top, landing at
        // this same row's vertical position. This row only owns the back-arrow and the
        // refresh/menu cluster.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(48.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close lyrics",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::refreshLyrics) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh lyrics", tint = Color.White)
                }
                IconButton(onClick = onShowMenu) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "More", tint = Color.White)
                }
            }
        }

        // ── Lyrics content ───────────────────────────────────────────────
        Crossfade(
            targetState = lyricsUiState,
            animationSpec = tween(300),
            label = "LyricsContent",
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset, bottom = bottomInset)
        ) { state ->
            when (state) {
                LyricsUiState.Idle,
                LyricsUiState.LoadingLocal,
                LyricsUiState.LoadingNetwork -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingPopupCard(accent)
                }

                LyricsUiState.Instrumental -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MessagePopupCard(message = "Instrumental ♪", accent = accent)
                }

                LyricsUiState.NotFound -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MessagePopupCard(message = "No lyrics found for this track.", accent = accent)
                }

                LyricsUiState.NetworkDisabledHint -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MessagePopupCard(
                        message = "No local lyrics found.\nLook it up online via LRCLIB?",
                        accent = accent,
                        actionLabel = "Enable online lookup",
                        onAction = {
                            viewModel.setLyricsNetworkEnabled(true)
                            viewModel.refreshLyrics()
                        },
                    )
                }

                LyricsUiState.NetworkError -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MessagePopupCard(
                        message = "Couldn't reach LRCLIB.\nCheck your connection.",
                        accent = accent,
                        actionLabel = "Retry",
                        onAction = viewModel::refreshLyrics,
                    )
                }

                is LyricsUiState.Loaded -> Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = state.source.badgeLabel(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .background(accent.copy(alpha = 0.38f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                    if (state.lyrics.synced) {
                        SyncedLyrics(
                            lines = state.lyrics.lines.map { it.timestampMs to it.text },
                            currentLineIndex = lyricsLineIndex,
                            onLineTap = viewModel::seekToMs,
                        )
                    } else {
                        Text(
                            text = state.lyrics.plain.orEmpty(),
                            color = Color(0xFFDADADA),
                            fontSize = 18.sp,
                            lineHeight = 28.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 32.dp, vertical = 24.dp)
                        )
                    }
                }
            }
        }

        // ── Shuffle / repeat — fade in with the layer, no full-player counterpart to morph
        // from, so they don't ride the positional lerp the way prev/play/next do. ───────────
        Box(
            modifier = Modifier
                .offset(x = shuffleCenterX - 22.dp, y = controlsCenterY - 22.dp)
                .size(44.dp)
                .graphicsLayer { alpha = progress },
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = viewModel::toggleShuffle) {
                Icon(
                    imageVector = if (shuffleModeEnabled) Icons.Filled.Shuffle else Icons.Outlined.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (shuffleModeEnabled) accent else Color.White,
                )
            }
        }
        Box(
            modifier = Modifier
                .offset(x = repeatCenterX - 22.dp, y = controlsCenterY - 22.dp)
                .size(44.dp)
                .graphicsLayer { alpha = progress },
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = viewModel::cycleRepeatMode) {
                val repeatIcon = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne
                    Player.REPEAT_MODE_ALL -> Icons.Filled.Repeat
                    else -> Icons.Outlined.Repeat
                }
                Icon(
                    imageVector = repeatIcon,
                    contentDescription = "Repeat",
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) accent else Color.White,
                )
            }
        }
    }
}

@Composable
private fun SyncedLyrics(
    lines: List<Pair<Long?, String>>,
    currentLineIndex: Int,
    onLineTap: (Long) -> Unit,
) {
    val listState = rememberLazyListState()

    // Same gentle follow-scroll cadence as before: once per lyric line, keeping the active
    // line near the top of the visible window so more upcoming lines are readable below it.
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0) {
            listState.animateScrollToItem((currentLineIndex - 1).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            // Top/bottom fade — the "text at top and bottom is nicely faded away" effect.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.12f to Color.Black,
                            0.85f to Color.Black,
                            1f to Color.Transparent,
                        ),
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
        contentPadding = PaddingValues(vertical = 56.dp, horizontal = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        itemsIndexed(lines) { index, (timestampMs, text) ->
            val isCurrent = index == currentLineIndex

            val textColor by animateColorAsState(
                targetValue = if (isCurrent) Color.White else Color(0xFF8F8F94),
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "lineColor"
            )
            val fontSizeFloat by animateFloatAsState(
                targetValue = if (isCurrent) 27f else 22f,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "lineFontSize"
            )
            // Animate alpha only — target the same White the highlight was already using so
            // the fade never dips toward black/muddy the way lerping through Color.Transparent
            // (0,0,0,0) would.
            val highlightColor by animateColorAsState(
                targetValue = if (isCurrent) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0f),
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "lineHighlight"
            )

            Text(
                text = text.ifBlank { "· · ·" },
                color = textColor,
                fontSize = fontSizeFloat.sp,
                lineHeight = 32.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                style = LocalTextStyle.current.copy(textMotion = TextMotion.Animated),
                modifier = Modifier
                    .fillMaxWidth()
                    // The active line's larger font can push a short line into wrapping onto an
                    // extra row — animateContentSize smooths that height change (and the
                    // resulting reflow of every line below it) instead of an instant snap.
                    //
                    // clip() sits OUTSIDE animateContentSize (not inside, despite the usual
                    // "animateContentSize before size modifiers" rule — clip doesn't impose a
                    // size, so that rule doesn't apply to it). animateContentSize clips its own
                    // bounds with a plain RECTANGLE by default while growing/shrinking, to mask
                    // the child's natural (already full-size) content underneath. A clip()
                    // placed INSIDE it shapes that full-size child, not the animated reveal
                    // window — so mid-animation you see the rectangular reveal cut straight
                    // through the rounded shape (sharp edge at the growing bottom). Wrapping
                    // clip() outside applies the rounded shape to the live ANIMATED bounds every
                    // frame instead, so the growth itself stays rounded throughout.
                    .clip(RoundedCornerShape(14.dp))
                    .animateContentSize(animationSpec = tween(80, easing = FastOutSlowInEasing))
                    .background(highlightColor)
                    .clickable(enabled = timestampMs != null) { timestampMs?.let(onLineTap) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun LoadingPopupCard(accent: Color) {
    PopupCard {
        val infinite = rememberInfiniteTransition(label = "LyricsLoading")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            repeat(4) { i ->
                val heightFrac by infinite.animateFloat(
                    initialValue = 0.28f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(480 + i * 85, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bar$i"
                )
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(28.dp * heightFrac)
                        .clip(RoundedCornerShape(3.dp))
                        .background(accent)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Finding lyrics…", color = Color(0xFFCCCCCC), fontSize = 13.sp)
    }
}

@Composable
private fun MessagePopupCard(
    message: String,
    accent: Color,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    PopupCard {
        Text(
            text = message,
            color = Color(0xFFCCCCCC),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onAction) {
                Text(actionLabel, color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PopupCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xCC181820))
            .padding(horizontal = 28.dp, vertical = 24.dp),
        content = content,
    )
}

private fun LyricsSource.badgeLabel(): String = when (this) {
    LyricsSource.EMBEDDED -> "Embedded"
    LyricsSource.LOCAL_LRC -> "Local file"
    LyricsSource.LRCLIB -> "LRCLIB"
}
