package com.laconical.player.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import coil3.compose.SubcomposeAsyncImage
import com.laconical.player.core.model.Track
import com.laconical.player.core.model.TrackAudioDetails
import com.laconical.player.ui.AudioArtData

enum class SongInfoTab { BASIC, ADVANCED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongInfoBottomSheet(
    track: Track,
    details: TrackAudioDetails?,
    dominantColor: Color?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val primaryAccent = dominantColor ?: Color(0xFF9D8EFF)
    val containerBg = MaterialTheme.colorScheme.surfaceContainer

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = containerBg,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33FFFFFF)),
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
                                tint = Color.White,
                            )
                        },
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        color = Color(0xFFCCCCCC),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                    )
                }
            }

            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = primaryAccent,
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = primaryAccent,
                        )
                    }
                },
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Text(
                            text = "Basic Info",
                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTabIndex == 0) primaryAccent else Color(0xFFBBBBCC),
                        )
                    },
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Text(
                            text = "Advanced Technical",
                            fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTabIndex == 1) primaryAccent else Color(0xFFBBBBCC),
                        )
                    },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedContent(
                targetState = selectedTabIndex,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "info_tabs",
            ) { tabIdx ->
                when (tabIdx) {
                    0 -> BasicInfoTab(track = track, details = details, context = context, accentColor = primaryAccent)
                    1 -> AdvancedInfoTab(details = details, accentColor = primaryAccent)
                }
            }
        }
    }
}

@Composable
private fun BasicInfoTab(
    track: Track,
    details: TrackAudioDetails?,
    context: Context,
    accentColor: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        InfoSectionCard(title = "Track Metadata") {
            InfoRow(label = "Title", value = track.title)
            InfoRow(label = "Artist", value = track.artist)
            InfoRow(label = "Album", value = track.album)
            details?.albumArtist?.let { InfoRow(label = "Album Artist", value = it) }
            details?.genre?.let { InfoRow(label = "Genre", value = it) }
            details?.year?.let { InfoRow(label = "Year", value = it) }
            if (track.trackNumber > 0) {
                InfoRow(label = "Track #", value = track.trackNumber.toString())
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        InfoSectionCard(title = "File Information") {
            InfoRow(
                label = "File Path",
                value = details?.filePath ?: track.mediaUri,
                trailingAction = {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Path",
                        tint = accentColor,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                copyToClipboard(context, "File Path", details?.filePath ?: track.mediaUri)
                            },
                    )
                },
            )
            InfoRow(label = "File Size", value = details?.fileSizeFormatted ?: "Loading...")
            InfoRow(label = "Container / MIME", value = details?.mimeType ?: "Loading...")
            InfoRow(label = "Date Modified", value = details?.dateAddedFormatted ?: "Loading...")
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = {
                val metadataText = buildFullMetadataText(track, details)
                copyToClipboard(context, "Full Metadata", metadataText)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = accentColor,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Copy Full Metadata",
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun buildFullMetadataText(track: Track, details: TrackAudioDetails?): String {
    return buildString {
        appendLine("--- Track Metadata ---")
        appendLine("Title: ${track.title}")
        appendLine("Artist: ${track.artist}")
        appendLine("Album: ${track.album}")
        details?.albumArtist?.let { appendLine("Album Artist: $it") }
        details?.genre?.let { appendLine("Genre: $it") }
        details?.year?.let { appendLine("Year: $it") }
        if (track.trackNumber > 0) {
            appendLine("Track #: ${track.trackNumber}")
        }
        appendLine()
        appendLine("--- File Information ---")
        appendLine("File Path: ${details?.filePath ?: track.mediaUri}")
        details?.fileSizeFormatted?.let { appendLine("File Size: $it") }
        details?.mimeType?.let { appendLine("Container / MIME: $it") }
        details?.dateAddedFormatted?.let { appendLine("Date Modified: $it") }
        if (details != null) {
            appendLine()
            appendLine("--- Audio Specs ---")
            details.codec?.let { appendLine("Codec: $it") }
            details.bitrateKbps?.let { appendLine("Bitrate: $it kbps") }
            details.sampleRateHz?.let { appendLine("Sample Rate: ${it / 1000.0} kHz") }
            details.bitDepthBits?.let { appendLine("Bit Depth: $it-bit") }
            details.channels?.let { appendLine("Channels: $it") }
        }
    }.trimEnd()
}

@Composable
private fun AdvancedInfoTab(
    details: TrackAudioDetails?,
    accentColor: Color,
) {
    if (details == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = accentColor)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        InfoSectionCard(title = "Audio Technical Specs") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SpecBadge(label = "Bitrate", value = details.bitrateKbps?.let { "$it kbps" } ?: "N/A", accentColor = accentColor)
                SpecBadge(label = "Sample Rate", value = details.sampleRateHz?.let { "${it / 1000.0} kHz" } ?: "N/A", accentColor = accentColor)
                SpecBadge(label = "Bit Depth", value = details.bitDepthBits?.let { "$it-bit" } ?: "N/A", accentColor = accentColor)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SpecBadge(label = "Codec", value = details.codec ?: "Audio", accentColor = accentColor)
                SpecBadge(label = "Channels", value = details.channels ?: "Stereo", accentColor = accentColor)
            }
        }
    }
}

@Composable
private fun InfoSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            color = Color(0xFFDDDDFF),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Text(
            text = label,
            color = Color(0xFFCCCCCC),
            fontSize = 13.sp,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailingAction?.invoke()
    }
}

@Composable
private fun SpecBadge(label: String, value: String, accentColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accentColor.copy(alpha = 0.25f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text = label, color = Color(0xFFDDDDDD), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(3.dp))
        Text(text = value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}
