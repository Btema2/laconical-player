package com.laconical.player.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laconical.player.ui.LocalAppSurface
import com.laconical.player.ui.navigation.NavRoute

private data class NavItem(
    val label: String,
    val route: String,
    val icon: ImageVector,
)

@Composable
fun LaconicalBottomNav(
    selectedRoute: String,
    onTabSelected: (String) -> Unit,
    dynamicColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val bgColor = LocalAppSurface.current

    val iconBaseColor = if (dynamicColor != null) {
        Color(
            red   = (dynamicColor.red   * 0.3f + 0.7f).coerceIn(0f, 1f),
            green = (dynamicColor.green * 0.3f + 0.7f).coerceIn(0f, 1f),
            blue  = (dynamicColor.blue  * 0.3f + 0.7f).coerceIn(0f, 1f),
            alpha = 1f
        )
    } else Color.White

    val indicatorColor = dynamicColor ?: Color.White

    val items = listOf(
        NavItem("Tracks",    NavRoute.TRACKS,    Icons.Outlined.MusicNote),
        NavItem("Albums",    NavRoute.ALBUMS,    Icons.Outlined.Album),
        NavItem("Artists",   NavRoute.ARTISTS,   Icons.Outlined.Person),
        NavItem("Playlists", NavRoute.PLAYLISTS, Icons.Outlined.QueueMusic),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    key(item.route) {
                        val isSelected = selectedRoute == item.route

                        val itemColor = if (isSelected) iconBaseColor else Color(0xFF666666)

                        val yOffset by animateDpAsState(
                            targetValue = if (isSelected) (-4).dp else 0.dp,
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                            label = "iconOffsetAnim_${item.route}"
                        )

                        val indicatorAlpha by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0f,
                            animationSpec = tween(300),
                            label = "indicatorAlpha_${item.route}"
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onTabSelected(item.route) }
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = itemColor,
                                modifier = Modifier.offset(y = yOffset)
                            )
                            Text(
                                text = item.label,
                                color = itemColor,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .width(16.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(indicatorColor.copy(alpha = indicatorAlpha))
                            )
                        }
                    }
                }
            }
        }
    }
}
