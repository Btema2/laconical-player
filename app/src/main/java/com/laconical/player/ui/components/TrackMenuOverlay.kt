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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.SubcomposeAsyncImage
import com.laconical.player.core.model.Track
import com.laconical.player.ui.AudioArtData
import kotlinx.coroutines.launch

/**
 * Full-screen overlay replacing the native DropdownMenu.
 *
 * The album art morphs from [artStartOffsetPx]/[artStartSizePx] (root-space coords of
 * the tapped row thumbnail) to the card header position using the same ghost-overlay
 * pattern as Mini→Full→Queue. The ghost Box in the card header is transparent; this
 * composable renders the real image on top.
 *
 * Must be placed in the outermost Box of LibraryScreen so offsets == root offsets.
 */
@Composable
fun TrackMenuOverlay(
    track: Track,
    artStartOffsetPx: Offset,
    artStartSizePx: Float,
    isFavorite: Boolean,
    dominantColor: Color?,
    onDismiss: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onViewAlbum: (() -> Unit)?,
    onViewArtist: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
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

    // Ghost target position measured on first composition frame.
    // Initialized to artStartOffsetPx so art renders at source even before measurement.
    var targetOffsetPx by remember { mutableStateOf(artStartOffsetPx) }
    val targetSizePx = with(density) { 64.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Scrim ─────────────────────────────────────────────────────────────
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

        // ── Menu card (centered) ───────────────────────────────────────────
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val headerBg = if (dominantColor != null) {
                Color(
                    red = (dominantColor.red * 0.3f + 0.05f).coerceIn(0f, 1f),
                    green = (dominantColor.green * 0.3f + 0.05f).coerceIn(0f, 1f),
                    blue = (dominantColor.blue * 0.3f + 0.07f).coerceIn(0f, 1f),
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
                    // Ghost art Box — transparent; morphing overlay draws on top
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x22FFFFFF))
                            .onGloballyPositioned { coords ->
                                targetOffsetPx = coords.positionInRoot()
                            },
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.artist,
                            color = Color(0xFFAAAAAA),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.album,
                            color = Color(0xFF666666),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                val menuBg = Color(0xFF12121A)

                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF2A2A35))

                MenuRow(
                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    label = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                    iconTint = if (isFavorite) Color(0xFFE84B7A) else Color.White,
                    background = menuBg,
                ) { onFavoriteToggle(); dismiss() }

                if (onViewAlbum != null) {
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF1E1E28))
                    MenuRow(
                        icon = Icons.Default.Album,
                        label = "Go to Album",
                        sublabel = track.album,
                        background = menuBg,
                    ) { onViewAlbum(); dismiss() }
                }

                if (onViewArtist != null) {
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF1E1E28))
                    MenuRow(
                        icon = Icons.Default.Person,
                        label = "Go to Artist",
                        sublabel = track.artist,
                        background = menuBg,
                    ) { onViewArtist(); dismiss() }
                }

                if (onAddToPlaylist != null) {
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF1E1E28))
                    MenuRow(
                        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                        label = "Add to Playlist",
                        background = menuBg,
                        bottomCorner = true,
                    ) { onAddToPlaylist(); dismiss() }
                }
            }
        }

        // ── Morphing art — rendered last so it draws above the card ──────────
        val artLeft = with(density) { lerp(artStartOffsetPx.x, targetOffsetPx.x, prog).toDp() }
        val artTop  = with(density) { lerp(artStartOffsetPx.y, targetOffsetPx.y, prog).toDp() }
        val artSize = with(density) { lerp(artStartSizePx, targetSizePx, prog).toDp() }
        val artCorner = lerp(10f, 14f, prog).dp

        Box(
            modifier = Modifier
                .offset(x = artLeft, y = artTop)
                .size(artSize)
                .clip(RoundedCornerShape(artCorner)),
        ) {
            SubcomposeAsyncImage(
                model = remember(track.mediaUri) { AudioArtData(track.mediaUri) },
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E1E1E)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFF555555),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    sublabel: String? = null,
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
            .padding(horizontal = 20.dp, vertical = if (sublabel != null) 12.dp else 18.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = label,
                color = iconTint,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            if (sublabel != null) {
                Text(
                    text = sublabel,
                    color = Color(0xFF666666),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
