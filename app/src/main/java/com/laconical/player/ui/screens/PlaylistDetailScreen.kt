package com.laconical.player.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.SubcomposeAsyncImage
import com.laconical.player.core.model.Track
import com.laconical.player.ui.AudioArtData
import com.laconical.player.ui.components.PlaylistCoverMosaic
import com.laconical.player.ui.viewmodels.PlaylistDetailViewModel
import kotlin.math.roundToInt

private val DETAIL_ITEM_HEIGHT = 72.dp

@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    bottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    val playlist by viewModel.playlist.collectAsState()
    val tracks by viewModel.tracks.collectAsState()

    val dragFromIndexState = remember { mutableIntStateOf(-1) }
    val dragOffsetYState = remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val itemHeightPx = with(density) { DETAIL_ITEM_HEIGHT.toPx() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0C))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = playlist?.name ?: "",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp)
        ) {
            PlaylistCoverMosaic(
                tracks = tracks.take(4),
                size = 120.dp,
                cornerRadius = 12.dp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${tracks.size} tracks",
                fontSize = 13.sp,
                color = Color(0xFF888888)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { viewModel.playAll() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4338CA)),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play All")
                }
                OutlinedButton(
                    onClick = { viewModel.shuffleAll() },
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Shuffle")
                }
            }
        }

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No tracks yet. Add some from the Tracks tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF555555)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
            ) {
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    PlaylistDetailTrackRow(
                        track = track,
                        index = index,
                        trackCount = tracks.size,
                        itemHeightPx = itemHeightPx,
                        dragFromIndexState = dragFromIndexState,
                        dragOffsetYState = dragOffsetYState,
                        firstVisibleIndex = { listState.firstVisibleItemIndex },
                        onTrackClick = {},
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
                                    .coerceIn(0, tracks.lastIndex)
                                if (to != from) viewModel.moveTrack(from, to)
                            }
                            dragFromIndexState.intValue = -1
                            dragOffsetYState.floatValue = 0f
                        },
                        onDragCancel = {
                            dragFromIndexState.intValue = -1
                            dragOffsetYState.floatValue = 0f
                        },
                        onRemove = { viewModel.removeTrack(track.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailTrackRow(
    track: Track,
    index: Int,
    trackCount: Int,
    itemHeightPx: Float,
    dragFromIndexState: MutableIntState,
    dragOffsetYState: MutableFloatState,
    firstVisibleIndex: () -> Int,
    onTrackClick: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onRemove: () -> Unit
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
            .height(DETAIL_ITEM_HEIGHT)
            .zIndex(if (isDraggingThis) 1f else 0f)
            .graphicsLayer {
                val from = dragFromIndexState.intValue
                val dy = dragOffsetYState.floatValue
                if (from >= 0) {
                    val target = (from + (dy / itemHeightPx).roundToInt()).coerceIn(0, trackCount - 1)
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
                .background(if (isDraggingThis) Color.White.copy(alpha = 0.09f) else Color.Transparent)
                .clickable(onClick = onTrackClick)
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
                SubcomposeAsyncImage(
                    model = remember(track.mediaUri) { AudioArtData(track.mediaUri) },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFF555555)
                        )
                    }
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = Color.Gray.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove from playlist",
                    tint = Color(0xFF666666),
                    modifier = Modifier.size(18.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .pointerInput(track.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { latestOnDragStart() },
                            onDrag = { _, offset -> latestOnDragDelta(offset.y) },
                            onDragEnd = { latestOnDragEnd() },
                            onDragCancel = { latestOnDragCancel() }
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
