package com.laconical.player.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import com.laconical.player.BuildConfig
import com.laconical.player.R
import com.laconical.player.core.model.Track
import com.laconical.player.ui.LocalAppSurface
import com.laconical.player.ui.toHsl

private const val GITHUB_URL = "https://github.com/Btema2/Laconical-Player"

@Composable
fun SettingsScreen(
    allTracks: List<Track>,
    dominantColor: Color?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val accent = remember(dominantColor) {
        dominantColor?.let {
            val hsl = it.toHsl()
            Color(android.graphics.Color.HSVToColor(floatArrayOf(hsl[0] * 360f, 0.45f, 0.75f)))
        } ?: Color(0xFF9E9E9E)
    }

    val appIconBitmap = remember {
        ContextCompat.getDrawable(context, R.mipmap.ic_launcher)!!
            .toBitmap(width = 192, height = 192)
            .asImageBitmap()
    }

    val trackCount = allTracks.size
    val albumCount = remember(allTracks) { allTracks.map { it.album }.distinct().size }
    val artistCount = remember(allTracks) { allTracks.map { it.artist }.distinct().size }
    val totalDuration = remember(allTracks) {
        formatDuration(allTracks.sumOf { it.durationMs })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = statusBarPadding)
    ) {
        // ── Header ───────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 4.dp, end = 16.dp, bottom = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = "Settings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── About card ───────────────────────────────────────────
        SettingsSection(title = "About", icon = Icons.Filled.Info, accent = accent) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Image(
                    bitmap = appIconBitmap,
                    contentDescription = "Laconical Player icon",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Laconical Player",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    color = Color(0xFFAAAAAA),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Inspired by Namida",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsRow(
                icon = Icons.Filled.Info,
                label = "Developer",
                value = "Btema2"
            )
            SettingsRow(
                icon = Icons.Filled.Code,
                label = "GitHub",
                value = "See project code",
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, GITHUB_URL.toUri()))
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Statistics card ──────────────────────────────────────
        SettingsSection(title = "Statistics", icon = Icons.Filled.QueryStats, accent = accent) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip(label = "Tracks", value = trackCount.toString(), accent = accent, modifier = Modifier.weight(1f))
                StatChip(label = "Albums", value = albumCount.toString(), accent = accent, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip(label = "Artists", value = artistCount.toString(), accent = accent, modifier = Modifier.weight(1f))
                StatChip(label = "Total Duration", value = totalDuration, accent = accent, modifier = Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Kinda empty here... Return after update to (probably 🤓) see more!",
            color = Color(0xFF777777),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LocalAppSurface.current)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF999999), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = Color.White, fontSize = 14.sp)
            Text(text = value, color = Color(0xFF999999), fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(vertical = 10.dp, horizontal = 12.dp)
    ) {
        Text(text = value, color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = Color(0xFF999999), fontSize = 11.sp)
    }
}

private fun formatDuration(totalMs: Long): String {
    val totalMinutes = totalMs / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return "${hours}h ${minutes}min"
}
