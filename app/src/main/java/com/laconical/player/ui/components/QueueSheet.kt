package com.laconical.player.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.laconical.player.core.model.Track
import com.laconical.player.ui.AudioArtData
import com.laconical.player.ui.MainViewModel
import com.laconical.player.ui.toHsl
import kotlin.math.roundToInt

private val QUEUE_ITEM_HEIGHT = 72.dp

@Composable
fun QueueSheet(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
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
    val animatedBg by androidx.compose.animation.animateColorAsState(bgColor, tween(800), label = "QueueBg")

    val density = LocalDensity.current
    val itemHeightPx = with(density) { QUEUE_ITEM_HEIGHT.toPx() }

    // Drag state — stable MutableState objects so graphicsLayer reads them without triggering recomposition.
    val dragFromIndexState = remember { mutableIntStateOf(-1) }
    val dragOffsetYState = remember { mutableFloatStateOf(0f) }

    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex, queue.size) {
        if (currentIndex >= 0 && queue.isNotEmpty()) {
            listState.animateScrollToItem(currentIndex.coerceIn(0, queue.lastIndex))
        }
    }

    BackHandler { onDismiss() }

    // Full-screen scrim — tap outside the sheet to dismiss
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() }
    ) {
        // Sheet panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(animatedBg)
                // Consume clicks so they don't reach the scrim dismissal
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
        ) {
            // Drag indicator handle at the very top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.22f))
                )
            }

            // ── Mini-player clone ──────────────────────────────────────────
            QueueMiniPlayer(
                viewModel = viewModel,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                seekBarActiveColor = seekBarActiveColor,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ── "UP NEXT" header ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
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

            // ── Track list with drag-to-reorder ────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
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
 *  Mini-player clone — identical visual to MiniPlayer, only play/pause
 * ────────────────────────────────────────────────────────────────────── */

@Composable
private fun QueueMiniPlayer(
    viewModel: MainViewModel,
    currentTrack: Track?,
    isPlaying: Boolean,
    seekBarActiveColor: Color,
) {
    val progress by viewModel.progress.collectAsState()
    val vibeColor by viewModel.playingTrackDominantColor.collectAsState()

    val baseColor = if (vibeColor != null) {
        Color(
            red = vibeColor!!.red * 0.6f,
            green = vibeColor!!.green * 0.6f,
            blue = vibeColor!!.blue * 0.6f,
            alpha = 1f
        )
    } else Color(0xFF1E1E1E)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(75.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0D0D10))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        baseColor.copy(alpha = 0.5f),
                        baseColor.copy(alpha = 0.15f),
                        Color(0xF00D0D10)
                    )
                )
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art
            if (currentTrack != null) {
                AsyncImage(
                    model = remember(currentTrack.mediaUri) { AudioArtData(currentTrack.mediaUri) },
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E1E1E))
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTrack?.title ?: "—",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentTrack?.artist ?: "",
                    color = Color(0xFFBBBBBB),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Only play/pause — no skip buttons
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

        // Progress bar at the bottom — same as MiniPlayer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.BottomCenter)
                .background(Color(0x11FFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(baseColor.copy(alpha = 0.9f))
            )
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
    // Read once for zIndex — triggers recompose only on drag start/end (not during continuous drag)
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
            // zIndex brings the dragged item above items rendered after it in LazyColumn
            .zIndex(if (isDraggingThis) 1f else 0f)
            .graphicsLayer {
                // Read drag state here — only triggers layer redraws, not recomposition
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

            // "Now playing" speaker icon for the current track
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

            // Drag handle — long-press to start reordering
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
