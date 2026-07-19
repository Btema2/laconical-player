package com.laconical.player.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laconical.player.core.model.lyrics.LyricsSource
import com.laconical.player.ui.LyricsUiState

/**
 * Full-screen lyrics overlay. Deliberately basic — exercises every lyrics feature
 * (synced highlight + auto-scroll, tap-to-seek, plain fallback, source badge, refresh,
 * instrumental / not-found / network states); visual polish comes later.
 *
 * Composed last in LibraryScreen's outer Box (TrackMenuOverlay pattern) — never part
 * of the morph system.
 */
@Composable
fun LyricsSheet(
    uiState: LyricsUiState,
    currentLineIndex: Int,
    dominantColor: Color?,
    onLineTap: (Long) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)
    val accent = dominantColor ?: Color(0xFF9E9EFF)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xF70C0C10))
            // Swallow taps so nothing beneath the overlay reacts.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close lyrics", tint = Color.White)
                }
                Text("LYRICS", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                (uiState as? LyricsUiState.Loaded)?.let { loaded ->
                    Text(
                        text = loaded.source.badgeLabel(),
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(accent.copy(alpha = 0.15f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh lyrics", tint = Color.White)
                }
            }

            when (uiState) {
                LyricsUiState.Idle,
                LyricsUiState.LoadingLocal,
                LyricsUiState.LoadingNetwork -> CenteredBox {
                    CircularProgressIndicator(color = accent)
                }

                LyricsUiState.Instrumental -> CenteredMessage("Instrumental ♪")

                LyricsUiState.NotFound -> CenteredMessage("No lyrics found")

                LyricsUiState.NetworkDisabledHint -> CenteredMessage(
                    "No local lyrics found.\nEnable online lookup (LRCLIB) in Settings → Lyrics."
                )

                LyricsUiState.NetworkError -> CenteredMessage(
                    "Couldn't reach LRCLIB.\nCheck your connection and tap refresh."
                )

                is LyricsUiState.Loaded -> {
                    if (uiState.lyrics.synced) {
                        SyncedLyrics(
                            lines = uiState.lyrics.lines.map { it.timestampMs to it.text },
                            currentLineIndex = currentLineIndex,
                            accent = accent,
                            onLineTap = onLineTap
                        )
                    } else {
                        Text(
                            text = uiState.lyrics.plain.orEmpty(),
                            color = Color(0xFFDADADA),
                            fontSize = 16.sp,
                            lineHeight = 26.sp,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncedLyrics(
    lines: List<Pair<Long?, String>>,
    currentLineIndex: Int,
    accent: Color,
    onLineTap: (Long) -> Unit,
) {
    val listState = rememberLazyListState()

    // Gentle follow-scroll, keeping the active line in the upper third. Slow cadence
    // (once per lyric line) — not the drag-reorder case CLAUDE.md warns about.
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0) {
            listState.animateScrollToItem((currentLineIndex - 3).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(lines) { index, (timestampMs, text) ->
            val isCurrent = index == currentLineIndex
            Text(
                text = text.ifBlank { "· · ·" },
                color = if (isCurrent) accent else Color(0xFF8A8A8A),
                fontSize = if (isCurrent) 19.sp else 16.sp,
                lineHeight = 26.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = timestampMs != null) { timestampMs?.let(onLineTap) }
                    .padding(horizontal = 24.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun CenteredMessage(message: String) {
    CenteredBox {
        Text(
            text = message,
            color = Color(0xFF9A9A9A),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

private fun LyricsSource.badgeLabel(): String = when (this) {
    LyricsSource.EMBEDDED -> "Embedded"
    LyricsSource.LOCAL_LRC -> "Local file"
    LyricsSource.LRCLIB -> "LRCLIB"
}
