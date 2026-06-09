package com.laconical.player.ui.components

import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

@Composable
fun FadingMarqueeText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    isScrolling: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.7f to Color.Black,
                            1.0f to Color.Transparent,
                        ),
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            .then(
                if (isScrolling) Modifier.basicMarquee(
                    animationMode = MarqueeAnimationMode.Immediately,
                    initialDelayMillis = 3000,
                    repeatDelayMillis = 2500,
                    velocity = 80.dp,
                ) else Modifier
            ),
    )
}
