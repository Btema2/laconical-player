package com.laconical.player.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.ui.platform.LocalContext
import com.laconical.player.core.model.Track
import com.laconical.player.ui.AudioArtData
import com.laconical.player.ui.MainViewModel
import com.laconical.player.ui.toHsl
import kotlin.math.roundToInt
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween

private val QUEUE_ITEM_HEIGHT = 72.dp

/**
 * Full-screen queue sheet. Positioned and translated by [LibraryScreen] via [modifier].
 * The [progress] value (0f = closed, 1f = open) is reserved for future queue-body fades.
 *
 * Album art, title, artist, AND play/pause button are all INVISIBLE ghosts here —
 * [LibraryScreen]'s morphing overlay renders the real versions on top, lerping from
 * FullPlayer positions to these exact positions.
 *
 * Ghost art target position (root coords): left=20dp, top=statusBarPadding+20dp, size=56dp
 * Ghost title target position (root coords): left=88dp, top=statusBarPadding+26dp
 * Ghost artist target position (root coords): left=88dp, top=statusBarPadding+46dp
 * Ghost play button center (root coords): screenWidth-44dp, statusBarPadding+48dp
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

    // Interactive only once the sheet is mostly open. While invisible (pre-warm) or in the
    // first half of the open animation, every pointer node is stripped so touches pass through
    // to the FullPlayer beneath (the morph layer sits above it). The QueueSheet root Box has no
    // pointer node, so stripping the children below makes the whole subtree non-hit-testable.
    val interactive = progress > 0.5f

    // Instant-scroll to current track only on initial open (not on track changes while browsing).
    // Triggered at progress > 0.01f so the sheet is nearly transparent — user never sees the jump.
    // Using scrollToItem (not animateScrollToItem) avoids traversing intermediate items,
    // which would trigger Coil thumbnail loads for every song between 0 and currentIndex.
    var wasQueueOpen by remember { mutableStateOf(false) }
    LaunchedEffect(progress > 0.01f, currentIndex, queue.size) {
        val isOpen = progress > 0.01f
        if (isOpen && !wasQueueOpen && currentIndex >= 0 && queue.isNotEmpty()) {
            listState.scrollToItem(currentIndex.coerceIn(0, queue.lastIndex))
        }
        wasQueueOpen = isOpen
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
            // ── Draggable header area ──────────────────────────────────────────
            // Swipe down on the header dismisses the queue back to the full player.
            Column(
                modifier = Modifier
                    .then(
                        if (interactive) Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val tracker = VelocityTracker()
                                tracker.addPosition(down.uptimeMillis, down.position)
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) {
                                        val velocity = tracker.calculateVelocity()
                                        onDragEnd(velocity.y)
                                        break
                                    }
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    onDragDelta(change.positionChange().y)
                                    change.consume()
                                } while (true)
                            }
                        } else Modifier
                    )
            ) {
                // Borderless header row — all four elements are INVISIBLE ghosts.
                // LibraryScreen's morph overlay draws the real versions and handles taps.
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
                        Text(
                            text = currentTrack?.title ?: " ",
                            color = Color.Transparent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentTrack?.artist ?: " ",
                            color = Color.Transparent,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Ghost play/pause slot — invisible 48dp placeholder
                    Box(modifier = Modifier.size(48.dp))
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── "UP NEXT" label ───────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.10f)
                )
            }

            // ── Track list with drag-to-reorder ────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = WindowInsets.navigationBars
                    .add(WindowInsets(top = 4.dp, bottom = 4.dp))
                    .asPaddingValues()
            ) {
                itemsIndexed(queue, key = { _, track -> track.id }) { index, track ->
                    val isCurrentTrack = index == currentIndex
                    val isBefore = index < currentIndex

                    QueueTrackRow(
                        track = track,
                        index = index,
                        queueSize = queue.size,
                        interactive = interactive,
                        isCurrentTrack = isCurrentTrack,
                        isBefore = isBefore,
                        seekBarActiveColor = seekBarActiveColor,
                        itemHeightPx = itemHeightPx,
                        dragFromIndexState = dragFromIndexState,
                        dragOffsetYState = dragOffsetYState,
                        firstVisibleIndex = { listState.firstVisibleItemIndex },
                        onTrackClick = { viewModel.seekToQueueIndex(index) },
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
    interactive: Boolean,
    isCurrentTrack: Boolean,
    isBefore: Boolean,
    seekBarActiveColor: Color,
    itemHeightPx: Float,
    dragFromIndexState: MutableIntState,
    dragOffsetYState: MutableFloatState,
    firstVisibleIndex: () -> Int,
    onTrackClick: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val isDraggingThis = dragFromIndexState.intValue == index

    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDragDelta by rememberUpdatedState(onDragDelta)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)
    val latestOnDragCancel by rememberUpdatedState(onDragCancel)

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
                    // Clamp the visual displacement range to items already in the composition.
                    // Items above firstVisibleIndex are not yet composed; applying +itemHeightPx
                    // to them would trigger LazyColumn to compose new items mid-drag, causing a
                    // visible recomposition jump. The actual drop target (computed from dy) is
                    // unclamped, so the item still lands at the correct position.
                    val firstVisible = firstVisibleIndex()
                    val visTarget = if (from > target) target.coerceAtLeast(firstVisible) else target
                    translationY = when {
                        index == from -> dy
                        from < visTarget && index in (from + 1)..visTarget -> -itemHeightPx
                        from > visTarget && index in visTarget until from -> itemHeightPx
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
                .then(if (interactive) Modifier.clickable(onClick = onTrackClick) else Modifier)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center
            ) {
                // MusicNote sits BEHIND the art: visible while loading or when no
                // art exists; the opaque loaded image covers it. AsyncImage (not
                // SubcomposeAsyncImage) avoids a per-row subcomposition pass — that
                // subcomposition, multiplied across a screenful of rows composing on
                // frame 1 of the queue-open animation, was the jank/"warm-up" spike.
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFF555555)
                )
                val context = LocalContext.current
                AsyncImage(
                    model = remember(track.albumArtUri ?: track.mediaUri) {
                        ImageRequest.Builder(context)
                            .data(AudioArtData(track.mediaUri, track.albumArtUri))
                            .size(144, 144)
                            .build()
                    },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = if (isCurrentTrack) seekBarActiveColor
                            else if (isBefore) Color.White.copy(alpha = 0.5f)
                            else Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (isCurrentTrack) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = if (isBefore) Color.Gray.copy(alpha = 0.25f)
                            else Color.Gray.copy(alpha = 0.75f),
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
                    .then(
                        if (interactive) Modifier.pointerInput(track.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { latestOnDragStart() },
                                onDrag = { _, offset -> latestOnDragDelta(offset.y) },
                                onDragEnd = { latestOnDragEnd() },
                                onDragCancel = { latestOnDragCancel() }
                            )
                        } else Modifier
                    ),
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
