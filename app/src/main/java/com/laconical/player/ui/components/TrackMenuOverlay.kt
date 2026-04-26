package com.laconical.player.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.model.Track
import com.laconical.player.ui.AudioArtData
import kotlinx.coroutines.launch

enum class TrackMenuMode { MAIN, PLAYLIST }

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
    playlists: List<Playlist>,
    artTracks: Map<Long, List<Track>>,
    onDismiss: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onViewAlbum: (() -> Unit)?,
    onViewArtist: (() -> Unit)?,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreateNewPlaylist: () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val switchProgress = remember { Animatable(0f) }
    var mode by remember { mutableStateOf(TrackMenuMode.MAIN) }

    fun dismiss() {
        scope.launch {
            progress.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }

    BackHandler {
        if (mode == TrackMenuMode.PLAYLIST) {
            mode = TrackMenuMode.MAIN
            scope.launch { switchProgress.animateTo(0f, tween(250, easing = FastOutSlowInEasing)) }
        } else {
            dismiss()
        }
    }

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
                    .animateContentSize(animationSpec = tween(280, easing = FastOutSlowInEasing))
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
                    // In-card art — fades in as switchProgress rises
                    val sp = switchProgress.value
                    val inCardSize = lerp(64f, 56f, sp).dp
                    val inCardCorner = lerp(14f, 12f, sp).dp
                    val inCardAlpha = (sp * 4f).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .size(inCardSize)
                            .clip(RoundedCornerShape(inCardCorner))
                            .background(Color(0x22FFFFFF))
                            .graphicsLayer { alpha = inCardAlpha }
                            .onGloballyPositioned { coords ->
                                targetOffsetPx = coords.positionInRoot()
                            },
                        contentAlignment = Alignment.Center,
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
                                    tint = Color(0xFF555555),
                                )
                            },
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Header text — crossfades between track info and "ADD TO PLAYLIST" label
                    AnimatedContent(
                        targetState = mode,
                        transitionSpec = {
                            fadeIn(tween(200, delayMillis = 80)) togetherWith fadeOut(tween(80))
                        },
                        label = "header_text",
                        modifier = Modifier.weight(1f),
                    ) { currentMode ->
                        when (currentMode) {
                            TrackMenuMode.MAIN -> Column {
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
                            TrackMenuMode.PLAYLIST -> Column {
                                Text(
                                    text = "ADD TO PLAYLIST",
                                    color = Color(0xFF888888),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.5.sp,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = track.title,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = track.artist,
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF2A2A35))

                AnimatedContent(
                    targetState = mode,
                    transitionSpec = {
                        fadeIn(tween(200, delayMillis = 100)) togetherWith fadeOut(tween(100))
                    },
                    label = "menu_body",
                ) { currentMode ->
                    val menuBg = Color(0xFF12121A)
                    when (currentMode) {
                        TrackMenuMode.MAIN -> MainMenuBody(
                            isFavorite = isFavorite,
                            onViewAlbum = onViewAlbum,
                            onViewArtist = onViewArtist,
                            track = track,
                            menuBg = menuBg,
                            onFavoriteClick = { onFavoriteToggle(); dismiss() },
                            onViewAlbumClick = { onViewAlbum?.invoke(); dismiss() },
                            onViewArtistClick = { onViewArtist?.invoke(); dismiss() },
                            onAddToPlaylistClick = {
                                mode = TrackMenuMode.PLAYLIST
                                scope.launch {
                                    switchProgress.animateTo(
                                        1f,
                                        tween(280, easing = FastOutSlowInEasing),
                                    )
                                }
                            },
                        )
                        TrackMenuMode.PLAYLIST -> PlaylistPickerBody(
                            playlists = playlists,
                            artTracks = artTracks,
                            menuBg = menuBg,
                            onSelectPlaylist = { playlist ->
                                onSelectPlaylist(playlist)
                                dismiss()
                            },
                            onCreateNew = {
                                onCreateNewPlaylist()
                                dismiss()
                            },
                        )
                    }
                }
            }
        }

        // ── Morphing art — rendered last so it draws above the card ──────────
        val artLeft = with(density) { lerp(artStartOffsetPx.x, targetOffsetPx.x, prog).toDp() }
        val artTop  = with(density) { lerp(artStartOffsetPx.y, targetOffsetPx.y, prog).toDp() }
        val artSize = with(density) { lerp(artStartSizePx, targetSizePx, prog).toDp() }
        val artCorner = lerp(10f, 14f, prog).dp
        val floatingArtAlpha = lerp(1f, 0f, (switchProgress.value * 4f).coerceIn(0f, 1f))

        Box(
            modifier = Modifier
                .offset(x = artLeft, y = artTop)
                .size(artSize)
                .clip(RoundedCornerShape(artCorner))
                .graphicsLayer { alpha = floatingArtAlpha },
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
private fun MainMenuBody(
    isFavorite: Boolean,
    onViewAlbum: (() -> Unit)?,
    onViewArtist: (() -> Unit)?,
    track: Track,
    menuBg: Color,
    onFavoriteClick: () -> Unit,
    onViewAlbumClick: () -> Unit,
    onViewArtistClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
) {
    MenuRow(
        icon = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
        label = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
        iconTint = if (isFavorite) Color(0xFFE84B7A) else Color.White,
        background = menuBg,
        onClick = onFavoriteClick,
    )
    if (onViewAlbum != null) {
        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF1E1E28))
        MenuRow(
            icon = Icons.Default.Album,
            label = "Go to Album",
            sublabel = track.album,
            background = menuBg,
            onClick = onViewAlbumClick,
        )
    }
    if (onViewArtist != null) {
        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF1E1E28))
        MenuRow(
            icon = Icons.Default.Person,
            label = "Go to Artist",
            sublabel = track.artist,
            background = menuBg,
            onClick = onViewArtistClick,
        )
    }
    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF1E1E28))
    MenuRow(
        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
        label = "Add to Playlist",
        background = menuBg,
        bottomCorner = true,
        onClick = onAddToPlaylistClick,
    )
}

@Composable
private fun PlaylistPickerBody(
    playlists: List<Playlist>,
    artTracks: Map<Long, List<Track>>,
    menuBg: Color,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreateNew: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .background(menuBg)
            .heightIn(max = 280.dp),
    ) {
        if (playlists.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No playlists yet",
                        color = Color(0xFF555555),
                        fontSize = 14.sp,
                    )
                }
            }
        } else {
            itemsIndexed(playlists, key = { _, p -> p.id }) { index, playlist ->
                if (index > 0) {
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF1E1E28))
                }
                PlaylistPickerRow(
                    playlist = playlist,
                    artTracks = artTracks[playlist.id] ?: emptyList(),
                    background = menuBg,
                    onClick = { onSelectPlaylist(playlist) },
                )
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF1E1E28))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(menuBg)
            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White),
                onClick = onCreateNew,
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = Color(0xFF7C6FE0),
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = "New Playlist",
            color = Color(0xFF7C6FE0),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PlaylistPickerRow(
    playlist: Playlist,
    artTracks: List<Track>,
    background: Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White),
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        PlaylistCoverMosaic(
            tracks = artTracks,
            size = 48.dp,
            cornerRadius = 10.dp,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = playlist.name,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
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
