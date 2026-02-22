package com.laconical.player.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LaconicalBottomNav(
    modifier: Modifier = Modifier
) {
    var selectedItem by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF000000).copy(alpha = 0.8f)) // Reverted to correct background from first build
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp) // Reverted to 64dp
                    .padding(horizontal = 32.dp), // Added padding to bring icons closer to center
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val items = listOf(
                    Pair("Tracks", Icons.Outlined.MusicNote),
                    Pair("Albums", Icons.Outlined.Album),
                    Pair("Artists", Icons.Outlined.Person),
                    Pair("Playlists", Icons.Outlined.QueueMusic)
                )

                items.forEachIndexed { index, pair ->
                    val isSelected = selectedItem == index
                    val itemColor = if (isSelected) Color.White else Color(0xFF888888)
                    // Keep FontWeight consistent (Medium) to prevent jump/shift
                    val itemFontWeight = FontWeight.Medium

                    val yOffset by animateDpAsState(
                        targetValue = if (isSelected) (-4).dp else 0.dp,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        label = "iconOffsetAnim"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { selectedItem = index }
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = pair.second,
                            contentDescription = pair.first,
                            tint = itemColor,
                            modifier = Modifier.offset(y = yOffset)
                        )
                        Text(
                            text = pair.first,
                            color = itemColor,
                            fontWeight = itemFontWeight,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
