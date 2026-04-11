package com.laconical.player.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.laconical.player.core.model.Track
import com.laconical.player.ui.AudioArtData
import com.laconical.player.ui.MainViewModel
import com.laconical.player.ui.toHsl
import kotlin.math.roundToInt
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween

private val QUEUE_ITEM_HEIGHT = 72.dp

/**
 * Full-screen queue sheet. Positioned and translated by [LibraryScreen] via [modifier].
 * The [progress] value (0f = closed, 1f = open) is passed in so that:
 *  - The artist text fades in with progress
 *  - The play/pause button fades in with progress
 * Album art and title are INVISIBLE ghosts here — [LibraryScreen] renders the morphing
 * overlay on top, lerping from FullPlayer to these exact positions.
 *
 * Ghost art target position (root coords): left=20dp, top=statusBarPadding+20dp, size=56dp
 * Ghost title target position (root coords): left=88dp, top=statusBarPadding+26dp
 */
@Composable
fun QueueSheet(
    viewModel: MainViewModel,
    progress: Float,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: (velocityY: Float) -> Unit,
) {
    val queue by viewModel.queue.collectAsState()
    val currentIndex by viewModel.currentQueueIndex.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val dominantColor by viewModel.playingTrackDominantColor.collectAsState()

    val themeColor = dominantColor ?: Color(0xFF1E1E1E)
    val seekBarActiveColor = remember(themeColor) {
        val hsl = themeColor.toHsl()
        Color.hsl(hsl[0] * 360f, hsl[1].coerceIn(0.2f, 0.5f), 0.4f)
    }
    val bgColor = if (dominantColor != null) {
        val v = dominantColor!!
        Color(
            red = 0.04f * 0.92f + v.red * 0.08f,
            green = 0.04f * 0.92f + v.green * 0.08f,
            blue = 0.05f * 0.92f + v.blue * 0.08f,
            alpha = 1f
        )
    } else Color(0xFF0A0A0C)
    val animatedBg by animateColorAsState(bgColor, tween(800), label = "QueueBg")

    val density = LocalDensity.current
    val itemHeightPx = with(density) { QUEUE_ITEM_HEIGHT.toPx() }

    val dragFromIndexState = remember { mutableIntStateOf(-1) }
    val dragOffsetYState = remember { mutableFloatStateOf(0f) }

    val listState = rememberLazyListState()

    // Auto-scroll to current track when queue becomes visible
    LaunchedEffect(progress > 0.8f, currentIndex, queue.size) {
        if (progress > 0.8f && currentIndex >= 0 && queue.isNotEmpty()) {
            listState.animateScrollToItem(currentIndex.coerceIn(0, queue.lastIndex))
        }
    }

    // Intercept downward scroll at list top to drag the sheet down
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && !listState.canScrollBackward) {
                    onDragDelta(available.y)
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available.y > 250f) {
                    onDragEnd(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    BackHandler(enabled = progress > 0.5f) { onDismiss() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(animatedBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ── Borderless header row ──────────────────────────────────────────
            // Art and title are INVISIBLE ghosts (LibraryScreen morph overlay draws them).
            // Artist + play/pause fade in with progress.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 0.dp)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ghost art placeholder — 56dp, matches morph target
                Box(modifier = Modifier.size(56.dp))

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Ghost title text — invisible, holds layout space for morph overlay alignment
                    Text(
                        text = currentTrack?.title ?: " ",
                        color = Color.Transparent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Artist — fades in as queue opens
                    Text(
                        text = currentTrack?.artist ?: "",
                        color = Color(0xFFBBBBBB).copy(alpha = progress),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Play/pause — fades in as queue opens
                Box(modifier = Modifier.graphicsLayer { alpha = progress }) {
                    GlowIconButton(onClick = { viewModel.togglePlayPause() }) {
                        Crossfade(targetState = isPlaying, label = "QueuePlayPause") { playing ->
                            Icon(
                                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── "UP NEXT" header ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "UP NEXT",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "${queue.size} tracks",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close queue",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                thickness = 0.5.dp,
                color = Color.White.copy(alpha = 0.10f)
            )

            // ── Track list with drag-to-reorder ────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(queue, key = { _, track -> track.id }) { index, track ->
                    val isCurrentTrack = index == currentIndex
                    val isBefore = index < currentIndex

                    QueueTrackRow(
                        track = track,
                        index = index,
                        queueSize = queue.size,
                        isCurrentTrack = isCurrentTrack,
                        isBefore = isBefore,
                        seekBarActiveColor = seekBarActiveColor,
                        itemHeightPx = itemHeightPx,
                        dragFromIndexState = dragFromIndexState,
                        dragOffsetYState = dragOffsetYState,
                        onTrackClick = { viewModel.playTrack(track) },
                        onDragStart = {
                            dragFromIndexState.intValue = index
                            dragOffsetYState.floatValue = 0f
                        },
                        onDragDelta = { dy -> dragOffsetYState.floatValue += dy },
                        onDragEnd = {
                            val from = dragFromIndexState.intValue
                            val dy = dragOffsetYState.floatValue
                            if (from >= 0) {
                                val to = (from + (dy / itemHeightPx).roundToInt())
                                    .coerceIn(0, queue.lastIndex)
                                if (to != from) viewModel.moveQueueItem(from, to)
                            }
                            dragFromIndexState.intValue = -1
                            dragOffsetYState.floatValue = 0f
                        },
                        onDragCancel = {
                            dragFromIndexState.intValue = -1
                            dragOffsetYState.floatValue = 0f
                        }
                    )
                }
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────────
 *  Individual queue track row with drag-to-reorder support
 * ────────────────────────────────────────────────────────────────────── */

@Composable
private fun QueueTrackRow(
    track: Track,
    index: Int,
    queueSize: Int,
    isCurrentTrack: Boolean,
    isBefore: Boolean,
    seekBarActiveColor: Color,
    itemHeightPx: Float,
    dragFromIndexState: MutableIntState,
    dragOffsetYState: MutableFloatState,
    onTrackClick: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val isDraggingThis = dragFromIndexState.intValue == index

    val dragScale by animateFloatAsState(
        targetValue = if (isDraggingThis) 1.03f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "DragScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(QUEUE_ITEM_HEIGHT)
            .zIndex(if (isDraggingThis) 1f else 0f)
            .graphicsLayer {
                val from = dragFromIndexState.intValue
                val dy = dragOffsetYState.floatValue
                if (from >= 0) {
                    val target = (from + (dy / itemHeightPx).roundToInt()).coerceIn(0, queueSize - 1)
                    translationY = when {
                        index == from -> dy
                        from < target && index in (from + 1)..target -> -itemHeightPx
                        from > target && index in target until from -> itemHeightPx
                        else -> 0f
                    }
                    shadowElevation = if (index == from) 20f else 0f
                }
                scaleX = dragScale
                scaleY = dragScale
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    when {
                        isDraggingThis -> Color.White.copy(alpha = 0.09f)
                        isCurrentTrack -> seekBarActiveColor.copy(alpha = 0.14f)
                        else -> Color.Transparent
                    }
                )
                .clickable(onClick = onTrackClick)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = remember(track.mediaUri) { AudioArtData(track.mediaUri) },
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = when {
                        isCurrentTrack -> seekBarActiveColor
                        isBefore -> Color.Gray.copy(alpha = 0.65f)
                        else -> Color.White
                    },
                    fontSize = 14.sp,
                    fontWeight = if (isCurrentTrack) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = Color.Gray.copy(alpha = if (isBefore) 0.45f else 0.75f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isCurrentTrack) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = seekBarActiveColor.copy(alpha = 0.85f),
                    modifier = Modifier.size(17.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .pointerInput(track.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDrag = { _, offset -> onDragDelta(offset.y) },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragCancel() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Hold to reorder",
                    tint = Color.Gray.copy(alpha = if (isDraggingThis) 0.85f else 0.40f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
