package com.laconical.player.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.model.Track
import kotlinx.coroutines.launch

/**
 * Full-screen overlay for playlist context actions (Rename, Delete).
 *
 * Mirrors TrackMenuOverlay layout and morph mechanic:
 * - PlaylistCoverMosaic floats from [artStartOffsetPx]/[artStartSizePx] (root-space
 *   coords of the tapped row mosaic) to the card header ghost position.
 * - In-card ghost fades in as the floating mosaic fades out.
 *
 * Must be placed in the outermost Box of LibraryScreen so offsets == root offsets.
 */
@Composable
fun PlaylistMenuOverlay(
    playlist: Playlist,
    artTracks: List<Track>,
    artStartOffsetPx: Offset,
    artStartSizePx: Float,
    dominantColor: Color?,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }

    fun dismiss() {
        scope.launch {
            progress.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }

    BackHandler { dismiss() }

    val prog = progress.value

    // Ghost target: initialized to artStartOffsetPx so mosaic renders at source before measurement
    var targetOffsetPx by remember { mutableStateOf(artStartOffsetPx) }
    val targetSizePx = with(density) { 64.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Scrim ──────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (prog * 1.6f).coerceIn(0f, 1f) }
                .background(Color(0xCC000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { dismiss() },
                ),
        )

        // ── Menu card (centered) ────────────────────────────────────────────
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val headerBg = if (dominantColor != null) {
                Color(
                    red   = (dominantColor.red   * 0.3f + 0.05f).coerceIn(0f, 1f),
                    green = (dominantColor.green * 0.3f + 0.05f).coerceIn(0f, 1f),
                    blue  = (dominantColor.blue  * 0.3f + 0.07f).coerceIn(0f, 1f),
                    alpha = 1f,
                )
            } else Color(0xFF1A1A24)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .graphicsLayer {
                        alpha = prog
                        scaleX = lerp(0.92f, 1f, prog)
                        scaleY = lerp(0.92f, 1f, prog)
                        translationY = lerp(48f, 0f, prog)
                    }
                    .clip(RoundedCornerShape(20.dp)),
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBg)
                        .padding(16.dp),
                ) {
                    // In-card ghost mosaic — fades in as floating fades out
                    val inCardAlpha = (prog * 4f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x22FFFFFF))
                            .graphicsLayer { alpha = inCardAlpha }
                            .onGloballyPositioned { coords ->
                                targetOffsetPx = coords.positionInRoot()
                            },
                    ) {
                        PlaylistCoverMosaic(
                            tracks = artTracks,
                            size = 64.dp,
                            cornerRadius = 14.dp,
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val trackCount = artTracks.size
                        if (trackCount > 0) {
                            Text(
                                text = "$trackCount track${if (trackCount == 1) "" else "s"}",
                                color = Color(0xFF888888),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF2A2A35))

                val menuBg = Color(0xFF12121A)

                PlaylistMenuRow(
                    icon = Icons.Default.DriveFileRenameOutline,
                    label = "Rename",
                    iconTint = Color.White,
                    background = menuBg,
                    onClick = {
                        dismiss()
                        onRename()
                    },
                )
                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF1E1E28))
                PlaylistMenuRow(
                    icon = Icons.Default.Delete,
                    label = "Delete Playlist",
                    iconTint = Color(0xFFEF4444),
                    background = menuBg,
                    bottomCorner = true,
                    onClick = {
                        dismiss()
                        onDelete()
                    },
                )
            }
        }

        // ── Floating mosaic — drawn above card so it morphs in ───────────────
        val floatingAlpha = lerp(1f, 0f, (prog * 4f).coerceIn(0f, 1f))
        val artLeft   = with(density) { lerp(artStartOffsetPx.x, targetOffsetPx.x, prog).toDp() }
        val artTop    = with(density) { lerp(artStartOffsetPx.y, targetOffsetPx.y, prog).toDp() }
        val artSize   = with(density) { lerp(artStartSizePx, targetSizePx, prog).toDp() }
        val artCorner = lerp(10f, 14f, prog).dp

        Box(
            modifier = Modifier
                .offset(x = artLeft, y = artTop)
                .size(artSize)
                .clip(RoundedCornerShape(artCorner))
                .graphicsLayer { alpha = floatingAlpha },
        ) {
            PlaylistCoverMosaic(
                tracks = artTracks,
                size = artSize,
                cornerRadius = artCorner,
            )
        }
    }
}

@Composable
private fun PlaylistMenuRow(
    icon: ImageVector,
    label: String,
    iconTint: Color = Color.White,
    background: Color,
    bottomCorner: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = if (bottomCorner)
        RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
    else
        RoundedCornerShape(0.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White),
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = iconTint,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
