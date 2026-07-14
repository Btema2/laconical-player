package com.laconical.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.laconical.player.ui.components.staggeredEntrance
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.SingletonImageLoader
import coil3.compose.SubcomposeAsyncImage
import com.laconical.player.ui.AlbumSortOrder
import com.laconical.player.ui.AudioArtData
import com.laconical.player.ui.components.SortChipRow
import com.laconical.player.ui.viewmodels.AlbumsViewModel

private fun List<com.laconical.player.ui.viewmodels.Album>.applySort(order: AlbumSortOrder) = when (order) {
    AlbumSortOrder.NAME -> sortedBy { it.name.lowercase() }
    AlbumSortOrder.TRACKS -> sortedByDescending { it.trackCount }
    AlbumSortOrder.ARTIST -> sortedBy { it.artistName.lowercase() }
}

@Composable
fun AlbumsScreen(
    onAlbumClick: (String) -> Unit,
    dominantColor: Color? = null,
    modifier: Modifier = Modifier,
    viewModel: AlbumsViewModel = hiltViewModel()
) {
    val allAlbums by viewModel.albums.collectAsState()
    var sortOrder by remember { mutableStateOf(AlbumSortOrder.NAME) }
    val albums = remember(allAlbums, sortOrder) { allAlbums.applySort(sortOrder) }
    val gridState = rememberLazyGridState()
    LaunchedEffect(sortOrder) { gridState.scrollToItem(0) }
    val context = LocalContext.current

    if (allAlbums.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No albums found", style = MaterialTheme.typography.bodyLarge, color = Color.White)
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        SortChipRow(
            options = AlbumSortOrder.entries.toList(),
            selected = sortOrder,
            onSelect = { sortOrder = it },
            dominantColor = dominantColor
        )
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(albums, key = { _, album -> album.name }) { index, album ->
            Column(
                modifier = Modifier
                    .staggeredEntrance(index)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onAlbumClick(album.name) }
                    .padding(bottom = 4.dp)
            ) {
                val imageModel = remember(album.representativeTrackUri) {
                    AudioArtData(album.representativeTrackUri)
                }
                SubcomposeAsyncImage(
                    model = imageModel,
                    imageLoader = SingletonImageLoader.get(context),
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp)),
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
                Spacer(Modifier.height(6.dp))
                Text(
                    text = album.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Text(
                    text = "${album.artistName} · ${album.trackCount} tracks",
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFFAAAAAA),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
        }
    }
}
