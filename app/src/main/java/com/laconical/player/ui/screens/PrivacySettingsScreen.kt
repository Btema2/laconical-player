package com.laconical.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.outlined.Masks
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laconical.player.ui.LocalAppSurface

@Composable
fun PrivacySettingsScreen(
    dominantColor: Color?,
    lyricsNetworkEnabled: Boolean,
    onLyricsNetworkEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val accent = settingsAccent(dominantColor)
    val level = remember(lyricsNetworkEnabled) {
        computePrivacyLevel(privacyTradeoffs(lyricsNetworkEnabled))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = statusBarPadding)
    ) {
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
                text = "Privacy",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Privacy meter (top of tab; general, not feature-specific) ──
        PrivacyMeterCard(level = level, accent = accent)

        Spacer(Modifier.height(16.dp))

        // ── Feature pills (online lyrics today; more later) ──────────
        SettingsSection(title = "Lyrics", icon = Icons.Filled.Lyrics, accent = accent) {
            SettingsToggleRow(
                icon = Icons.Filled.Cloud,
                label = "Online lyrics search",
                description = "When you open or refresh lyrics, missing tracks can be looked up on lrclib.net. Only title, artist, album, and duration are sent — never the audio. Off keeps lyrics fully offline.",
                checked = lyricsNetworkEnabled,
                onCheckedChange = onLyricsNetworkEnabledChange,
                accent = accent
            )
            Spacer(Modifier.height(12.dp))
            PrivacyBullet(
                text = "Local sources always run first (embedded tags, .lrc files, on-device cache)."
            )
            Spacer(Modifier.height(8.dp))
            PrivacyBullet(
                text = "Network is used only if this is on and you open or refresh lyrics — not when skipping tracks."
            )
            Spacer(Modifier.height(8.dp))
            PrivacyBullet(
                text = "Successful results can be saved on-device so the same track does not need another lookup."
            )
        }

        Spacer(Modifier.height(180.dp))
    }
}

@Composable
internal fun PrivacyMeterCard(
    level: PrivacyLevel,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LocalAppSurface.current)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Masks,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Privacy meter",
                    color = Color(0xFF999999),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${level.label} ${level.emoji}",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Meter bar — full width track, fill = privacy fraction
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(level.fraction.coerceIn(0.08f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent)
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Laconical stays private by default. Online features are opt-in — each one you enable lowers this meter a bit.",
            color = Color(0xFFAAAAAA),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun PrivacyBullet(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "•",
            color = Color(0xFF777777),
            fontSize = 13.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = text,
            color = Color(0xFF999999),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
