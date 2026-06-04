package com.laconical.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

private val PillShape = RoundedCornerShape(50)

private data class NavItem(
    val label: String,
    val route: String,
    val icon: ImageVector,
)

private val navItems = listOf(
    NavItem("Tracks",    NavRoute.TRACKS,    Icons.Outlined.MusicNote),
    NavItem("Albums",    NavRoute.ALBUMS,    Icons.Outlined.Album),
    NavItem("Artists",   NavRoute.ARTISTS,   Icons.Outlined.Person),
    NavItem("Playlists", NavRoute.PLAYLISTS, Icons.Outlined.QueueMusic),
)

@Composable
fun LaconicalBottomNav(
    selectedRoute: String,
    onTabSelected: (String) -> Unit,
    dynamicColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val bgColor = LocalAppSurface.current
    val pillFill   = dynamicColor?.copy(alpha = 0.26f) ?: Color.White.copy(alpha = 0.12f)
    val pillBorder = dynamicColor?.copy(alpha = 0.18f) ?: Color.Transparent

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
                navItems.forEach { item ->
                    key(item.route) {
                        val isSelected = selectedRoute == item.route

                        val iconTint = if (isSelected) Color.White else Color.White.copy(alpha = 0.42f)

                        val yOffset by animateDpAsState(
                            targetValue = if (isSelected) (-2).dp else 0.dp,
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                            label = "iconOffset_${item.route}"
                        )

                        val pillWidth by animateDpAsState(
                            targetValue = if (isSelected) 76.dp else 0.dp,
                            animationSpec = tween(320, easing = FastOutSlowInEasing),
                            label = "pillWidth_${item.route}"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onTabSelected(item.route) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // Pill — expands from center (curtains effect)
                            Box(
                                modifier = Modifier
                                    .width(pillWidth)
                                    .height(42.dp)
                                    .clip(PillShape)
                                    .background(pillFill)
                                    .border(1.dp, pillBorder, PillShape)
                            )

                            // Icon + label on top of pill
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = iconTint,
                                    modifier = Modifier.offset(y = yOffset)
                                )
                                AnimatedVisibility(
                                    visible = isSelected,
                                    enter = fadeIn(tween(220)) + slideInVertically { it / 2 },
                                    exit  = fadeOut(tween(150)) + slideOutVertically { it / 2 }
                                ) {
                                    Text(
                                        text = item.label,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
