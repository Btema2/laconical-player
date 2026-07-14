package com.laconical.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.laconical.player.ui.SortLabel
import com.laconical.player.ui.toHsl

/**
 * Horizontal row of sort-option FilterChips, styled to match the Tracks-page
 * sort row. Chips scroll in a weighted LazyRow; an optional [trailing]
 * composable (e.g. [ShuffleFab]) is pinned to the right, outside the scroll.
 */
@Composable
fun <T : SortLabel> SortChipRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    dominantColor: Color?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(options, key = { it.toString() }) { order ->
                FilterChip(
                    selected = selected == order,
                    onClick = { onSelect(order) },
                    label = { Text(order.label, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = (dominantColor ?: Color(0xFF404040)).copy(alpha = 0.35f),
                        selectedLabelColor = Color.White,
                        containerColor = Color.Transparent,
                        labelColor = Color(0xFF888888)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected == order,
                        borderColor = Color(0xFF444444),
                        selectedBorderColor = Color.Transparent
                    )
                )
            }
        }
        if (trailing != null) {
            Box(modifier = Modifier.padding(end = 16.dp)) {
                trailing()
            }
        }
    }
}

/**
 * Large brightened-dominant-color shuffle circle, 1.5x the height of a
 * FilterChip (~32.dp chip height -> 48.dp). Uses the same HSL-brighten
 * formula as the FullPlayer play/pause button.
 */
@Composable
fun ShuffleFab(
    dominantColor: Color?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColor = dominantColor ?: Color(0xFF1E1E1E)
    val bgColor = remember(themeColor) {
        val hsl = themeColor.toHsl()
        Color.hsl(hue = hsl[0] * 360f, saturation = hsl[1].coerceIn(0.2f, 0.5f), lightness = 0.4f)
    }
    val animatedColor by animateColorAsState(targetValue = bgColor, animationSpec = tween(800), label = "ShuffleFabColor")

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(animatedColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Shuffle,
            contentDescription = "Shuffle all",
            tint = Color.White
        )
    }
}
