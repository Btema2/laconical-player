package com.laconical.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.asImage
import com.laconical.player.core.model.Track
import com.laconical.player.ui.AudioAlbumArtFetcher
import com.laconical.player.ui.AudioAlbumArtKeyer
import com.laconical.player.ui.AudioArtData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

@Composable
fun TrackListItem(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var vibeColor by remember { mutableStateOf(Color(0xFF888888)) }
    val context = LocalContext.current

    LaunchedEffect(track.mediaUri, track.dataPath, isPlaying) {
        val loadTarget = if (!track.dataPath.isNullOrEmpty()) track.dataPath else track.mediaUri
        if (isPlaying && !loadTarget.isNullOrEmpty()) {
            withContext(Dispatchers.Default) {
                val imageLoader = coil3.ImageLoader.Builder(context)
                    .components {
                        add(AudioAlbumArtFetcher.Factory())
                        add(AudioAlbumArtKeyer())
                    }
                    .build()
                val request = ImageRequest.Builder(context)
                    .data(AudioArtData(loadTarget!!))
                    .size(100)
                    .build()

                when (val result = imageLoader.execute(request)) {
                    is SuccessResult -> {
                        val bitmap = (result.image as? coil3.BitmapImage)?.bitmap
                        bitmap?.let { bmp ->
                            try {
                                Palette.from(bmp).generate().dominantSwatch?.let { swatch ->
                                    vibeColor = Color(swatch.rgb)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = vibeColor, bounded = true),
                onClick = onClick
            )
    ) {
        if (isPlaying) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            vibeColor.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        startX = 0f,
                        endX = size.width
                    )
                )
            }
            ParticlesEffectCanvas(
                color = vibeColor,
                modifier = Modifier.matchParentSize()
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                if (isPlaying) {
                    val paint = remember {
                        Paint().apply {
                            color = vibeColor.copy(alpha = 0.6f)
                            asFrameworkPaint().maskFilter = android.graphics.BlurMaskFilter(40f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                        }
                    }
                    Canvas(modifier = Modifier.size(52.dp)) {
                        drawIntoCanvas { canvas ->
                            val rect = androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)
                            val cornerSize = 12.dp.toPx()
                            canvas.drawRoundRect(rect.left, rect.top, rect.right, rect.bottom, cornerSize, cornerSize, paint)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E1E)),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    val loader = remember(context) {
                        coil3.ImageLoader.Builder(context)
                            .components { 
                                add(AudioAlbumArtFetcher.Factory()) 
                                add(AudioAlbumArtKeyer())
                            }
                            .build()
                    }
                    var imageModel by remember(track.id) {
                        val target = if (!track.dataPath.isNullOrEmpty()) track.dataPath!! else track.mediaUri
                        mutableStateOf<Any?>(AudioArtData(target))
                    }
                    SubcomposeAsyncImage(
                        model = imageModel,
                        imageLoader = loader,
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        error = {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = "Music Placeholder",
                                tint = Color(0xFF555555)
                            )
                        },
                        loading = {
                            // Empty box
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                val titleColor = remember(vibeColor) {
                    val luminance = androidx.core.graphics.ColorUtils.calculateLuminance(vibeColor.toArgb())
                    if (luminance < 0.2) {
                        val hsl = FloatArray(3)
                        androidx.core.graphics.ColorUtils.colorToHSL(vibeColor.toArgb(), hsl)
                        hsl[2] = hsl[2].coerceAtLeast(0.4f)
                        Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
                    } else {
                        vibeColor
                    }
                }

                Text(
                    text = track.title,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isPlaying) FontWeight.ExtraBold else FontWeight.Normal,
                    color = if (isPlaying) titleColor else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = track.artist,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFFAAAAAA)
                )
            }

            IconButton(onClick = { /* TODO: Track menu */ }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = Color(0xFF777777)
                )
            }
        }
    }
}
