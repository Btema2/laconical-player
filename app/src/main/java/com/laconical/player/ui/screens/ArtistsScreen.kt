package com.laconical.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.laconical.player.ui.components.staggeredEntrance
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
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
import com.laconical.player.ui.ArtistSortOrder
import com.laconical.player.ui.AudioArtData
import com.laconical.player.ui.components.SortChipRow
import com.laconical.player.ui.viewmodels.ArtistsViewModel

private fun List<com.laconical.player.ui.viewmodels.Artist>.applySort(order: ArtistSortOrder) = when (order) {
    ArtistSortOrder.NAME -> sortedBy { it.name.lowercase() }
    ArtistSortOrder.TRACKS -> sortedByDescending { it.trackCount }
    ArtistSortOrder.ALBUMS -> sortedByDescending { it.albumCount }
}

@Composable
fun ArtistsScreen(
    onArtistClick: (String) -> Unit,
    dominantColor: Color? = null,
    modifier: Modifier = Modifier,
    viewModel: ArtistsViewModel = hiltViewModel()
) {
    val allArtists by viewModel.artists.collectAsState()
    var sortOrder by remember { mutableStateOf(ArtistSortOrder.NAME) }
    val artists = remember(allArtists, sortOrder) { allArtists.applySort(sortOrder) }
    val listState = rememberLazyListState()
    LaunchedEffect(sortOrder) { listState.scrollToItem(0) }
    val context = LocalContext.current

    if (allArtists.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No artists found", style = MaterialTheme.typography.bodyLarge, color = Color.White)
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        SortChipRow(
            options = ArtistSortOrder.entries.toList(),
            selected = sortOrder,
            onSelect = { sortOrder = it },
            dominantColor = dominantColor
        )
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(artists, key = { _, artist -> artist.name }) { index, artist ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .staggeredEntrance(index)
                    .fillMaxWidth()
                    .clickable { onArtistClick(artist.name) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val imageModel = remember(artist.representativeTrackUri) {
                    AudioArtData(artist.representativeTrackUri)
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1E1E)),
                    contentAlignment = Alignment.Center
                ) {
                    SubcomposeAsyncImage(
                        model = imageModel,
                        imageLoader = SingletonImageLoader.get(context),
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape),
                        error = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFF555555),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artist.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append("${artist.trackCount} tracks")
                            if (artist.albumCount > 1) append(" · ${artist.albumCount} albums")
                        },
                        fontSize = 13.sp,
                        color = Color(0xFFAAAAAA)
                    )
                }
            }
        }
        }
    }
}
