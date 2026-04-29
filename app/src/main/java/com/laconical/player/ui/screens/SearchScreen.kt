package com.laconical.player.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.SubcomposeAsyncImage
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.model.Track
import com.laconical.player.ui.AudioArtData
import com.laconical.player.ui.components.PlaylistCoverMosaic
import com.laconical.player.ui.components.TrackListItem
import com.laconical.player.ui.components.staggeredEntrance
import com.laconical.player.ui.deriveBarColor

enum class SearchFilter(val label: String) {
    ALL("All"),
    TRACKS("Tracks"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    PLAYLISTS("Playlists")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    tracks: List<Track>,
    searchedAlbums: List<Track>,
    searchedArtists: List<Track>,
    searchedPlaylists: List<Playlist>,
    playlistArtTracks: Map<Long, List<Track>>,
    dominantColor: Color?,
    currentTrack: Track?,
    isPlaying: Boolean,
    favoriteIds: Set<Long>,
    onNavigateBack: () -> Unit,
    onTrackClick: (List<Track>, Int) -> Unit,
    onFavoriteToggle: (Long) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(SearchFilter.ALL) }
    val focusRequester = remember { FocusRequester() }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val context = LocalContext.current
    val loader = SingletonImageLoader.get(context)

    val containerColor by animateColorAsState(
        targetValue = dominantColor.deriveBarColor(),
        animationSpec = tween(400),
        label = "SearchBarColor"
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(modifier = modifier.fillMaxSize()) {

        // ── Header ────────────────────────────────────────────────────────
        Column(modifier = Modifier.background(containerColor)) {
            Spacer(modifier = Modifier.height(statusBarPadding + 4.dp))

            // Search bar row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    onNavigateBack()
                    onSearchQueryChange("")
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                val interactionSource = remember { MutableInteractionSource() }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = {
                        onSearchQueryChange(it)
                        selectedFilter = SearchFilter.ALL
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color.White),
                    interactionSource = interactionSource,
                    decorationBox = { innerTextField ->
                        TextFieldDefaults.DecorationBox(
                            value = searchQuery,
                            innerTextField = innerTextField,
                            enabled = true,
                            singleLine = true,
                            visualTransformation = VisualTransformation.None,
                            interactionSource = interactionSource,
                            placeholder = {
                                Text(
                                    text = "Search",
                                    style = TextStyle(
                                        color = Color.Gray.copy(alpha = 0.6f),
                                        fontSize = 16.sp
                                    )
                                )
                            },
                            trailingIcon = {
                                AnimatedVisibility(
                                    visible = searchQuery.isNotEmpty(),
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    IconButton(onClick = {
                                        onSearchQueryChange("")
                                        selectedFilter = SearchFilter.ALL
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear search",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            container = {
                                Box(
                                    Modifier.border(
                                        1.dp,
                                        Color.Gray.copy(alpha = 0.4f),
                                        RoundedCornerShape(24.dp)
                                    )
                                )
                            }
                        )
                    }
                )
            }

            // Filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SearchFilter.entries) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                filter.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = (dominantColor ?: Color(0xFF404040))
                                .copy(alpha = 0.35f),
                            selectedLabelColor = Color.White,
                            containerColor = Color.Transparent,
                            labelColor = Color(0xFF888888)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedFilter == filter,
                            borderColor = Color(0xFF444444),
                            selectedBorderColor = Color.Transparent
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // ── Results ───────────────────────────────────────────────────────
        if (searchQuery.isNotBlank()) {
            when (selectedFilter) {
                SearchFilter.ALL -> AllResultsContent(
                    tracks = tracks,
                    searchedAlbums = searchedAlbums,
                    searchedArtists = searchedArtists,
                    searchedPlaylists = searchedPlaylists,
                    playlistArtTracks = playlistArtTracks,
                    dominantColor = dominantColor,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    favoriteIds = favoriteIds,
                    loader = loader,
                    onTrackClick = onTrackClick,
                    onFavoriteToggle = onFavoriteToggle,
                    onAlbumClick = onAlbumClick,
                    onArtistClick = onArtistClick,
                    onPlaylistClick = onPlaylistClick,
                    onViewAllTracks = { selectedFilter = SearchFilter.TRACKS },
                    onViewAllArtists = { selectedFilter = SearchFilter.ARTISTS },
                    onViewAllAlbums = { selectedFilter = SearchFilter.ALBUMS },
                    onViewAllPlaylists = { selectedFilter = SearchFilter.PLAYLISTS }
                )
                SearchFilter.TRACKS -> TracksOnlyContent(
                    tracks = tracks,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    favoriteIds = favoriteIds,
                    onTrackClick = onTrackClick,
                    onFavoriteToggle = onFavoriteToggle
                )
                SearchFilter.ARTISTS -> ArtistsOnlyContent(
                    artists = searchedArtists,
                    loader = loader,
                    onArtistClick = onArtistClick
                )
                SearchFilter.ALBUMS -> AlbumsOnlyContent(
                    albums = searchedAlbums,
                    loader = loader,
                    onAlbumClick = onAlbumClick
                )
                SearchFilter.PLAYLISTS -> PlaylistsOnlyContent(
                    playlists = searchedPlaylists,
                    playlistArtTracks = playlistArtTracks,
                    onPlaylistClick = onPlaylistClick
                )
            }
        }
    }
}

// ── "All" grouped view ────────────────────────────────────────────────────────

@Composable
private fun AllResultsContent(
    tracks: List<Track>,
    searchedAlbums: List<Track>,
    searchedArtists: List<Track>,
    searchedPlaylists: List<Playlist>,
    playlistArtTracks: Map<Long, List<Track>>,
    dominantColor: Color?,
    currentTrack: Track?,
    isPlaying: Boolean,
    favoriteIds: Set<Long>,
    loader: ImageLoader,
    onTrackClick: (List<Track>, Int) -> Unit,
    onFavoriteToggle: (Long) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onViewAllTracks: () -> Unit,
    onViewAllArtists: () -> Unit,
    onViewAllAlbums: () -> Unit,
    onViewAllPlaylists: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (searchedAlbums.isNotEmpty()) {
            item(key = "albums_header") {
                SectionHeader("Albums", searchedAlbums.size, dominantColor, onViewAllAlbums)
            }
            item(key = "albums_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(searchedAlbums, key = { it.album }) { track ->
                        AlbumCard(track = track, loader = loader, onClick = onAlbumClick)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (searchedArtists.isNotEmpty()) {
            item(key = "artists_header") {
                SectionHeader("Artists", searchedArtists.size, dominantColor, onViewAllArtists)
            }
            item(key = "artists_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(searchedArtists, key = { it.artist }) { track ->
                        ArtistCard(track = track, loader = loader, onClick = onArtistClick)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (tracks.isNotEmpty()) {
            item(key = "tracks_header") {
                SectionHeader("Tracks", tracks.size, dominantColor, onViewAllTracks)
            }
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                TrackListItem(
                    track = track,
                    isActiveTrack = currentTrack?.id == track.id,
                    isPlaybackActive = isPlaying,
                    isFavorite = favoriteIds.contains(track.id),
                    onFavoriteToggle = { onFavoriteToggle(track.id) },
                    onClick = { onTrackClick(tracks, index) },
                    modifier = Modifier.staggeredEntrance(index)
                )
            }
        }

        if (searchedPlaylists.isNotEmpty()) {
            item(key = "playlists_header") {
                SectionHeader("Playlists", searchedPlaylists.size, dominantColor, onViewAllPlaylists)
            }
            items(searchedPlaylists, key = { it.id }) { playlist ->
                SearchPlaylistRow(
                    playlist = playlist,
                    artTracks = playlistArtTracks[playlist.id] ?: emptyList(),
                    onClick = { onPlaylistClick(playlist.id) }
                )
            }
        }
    }
}

// ── Single-category views ─────────────────────────────────────────────────────

@Composable
private fun TracksOnlyContent(
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    favoriteIds: Set<Long>,
    onTrackClick: (List<Track>, Int) -> Unit,
    onFavoriteToggle: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            TrackListItem(
                track = track,
                isActiveTrack = currentTrack?.id == track.id,
                isPlaybackActive = isPlaying,
                isFavorite = favoriteIds.contains(track.id),
                onFavoriteToggle = { onFavoriteToggle(track.id) },
                onClick = { onTrackClick(tracks, index) },
                modifier = Modifier.staggeredEntrance(index)
            )
        }
    }
}

@Composable
private fun ArtistsOnlyContent(
    artists: List<Track>,
    loader: ImageLoader,
    onArtistClick: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        itemsIndexed(artists, key = { _, track -> track.artist }) { index, track ->
            ArtistCard(
                track = track,
                loader = loader,
                onClick = onArtistClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredEntrance(index)
            )
        }
    }
}

@Composable
private fun AlbumsOnlyContent(
    albums: List<Track>,
    loader: ImageLoader,
    onAlbumClick: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        itemsIndexed(albums, key = { _, track -> track.album }) { index, track ->
            AlbumCard(
                track = track,
                loader = loader,
                onClick = onAlbumClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredEntrance(index)
            )
        }
    }
}

@Composable
private fun PlaylistsOnlyContent(
    playlists: List<Playlist>,
    playlistArtTracks: Map<Long, List<Track>>,
    onPlaylistClick: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
            SearchPlaylistRow(
                playlist = playlist,
                artTracks = playlistArtTracks[playlist.id] ?: emptyList(),
                onClick = { onPlaylistClick(playlist.id) },
                modifier = Modifier.staggeredEntrance(index)
            )
        }
    }
}

// ── Shared small composables ──────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    dominantColor: Color?,
    onViewAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$title • $count",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        TextButton(
            onClick = onViewAll,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.textButtonColors(
                contentColor = (dominantColor ?: Color(0xFF888888)).copy(alpha = 0.9f)
            )
        ) {
            Text("View All", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun AlbumCard(
    track: Track,
    loader: ImageLoader,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier.width(130.dp)
) {
    Column(
        modifier = modifier.clickable { onClick(track.album) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SubcomposeAsyncImage(
            model = AudioArtData(track.mediaUri),
            imageLoader = loader,
            contentDescription = track.album,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E1E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFF555555),
                        modifier = Modifier.fillMaxSize(0.4f)
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = track.album,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun ArtistCard(
    track: Track,
    loader: ImageLoader,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier.width(90.dp)
) {
    Column(
        modifier = modifier.clickable { onClick(track.artist) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SubcomposeAsyncImage(
            model = AudioArtData(track.mediaUri),
            imageLoader = loader,
            contentDescription = track.artist,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E1E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFF555555),
                        modifier = Modifier.fillMaxSize(0.4f)
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = track.artist,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun SearchPlaylistRow(
    playlist: Playlist,
    artTracks: List<Track>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        PlaylistCoverMosaic(tracks = artTracks, size = 52.dp)
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = playlist.name,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
