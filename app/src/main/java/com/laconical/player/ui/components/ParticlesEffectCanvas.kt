package com.laconical.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.random.Random

data class Particle(
    var x: Float,
    var y: Float,
    var angle: Float,
    var speed: Float,
    var baseAlpha: Float,
    var radius: Float,
    var life: Float,
    val maxLife: Float
)

@Composable
fun ParticlesEffectCanvas(
    color: Color,
    isPlaybackActive: Boolean,
    modifier: Modifier = Modifier
) {
    // Reading `time` inside the Canvas block is what drives recomposition each frame.
    var time by remember { mutableLongStateOf(0L) }
    var lastTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameNanos ->
                lastTime = time
                time = frameNanos
            }
        }
    }

    val density = LocalDensity.current.density
    val originX = with(LocalDensity.current) { 42.dp.toPx() }

    val particles = remember {
        List(24) {
            Particle(
                x = originX,
                y = -1f, // sentinel: will be initialized to center on first draw
                angle = Random.nextFloat() * (2f * Math.PI.toFloat()),
                speed = (Random.nextFloat() * 70f + 40f) * density,
                baseAlpha = Random.nextFloat() * 0.5f + 0.2f,
                radius = (Random.nextFloat() * 2.5f + 1.5f) * density,
                life = Random.nextFloat(),
                maxLife = 1f
            )
        }
    }

    // Reading `time` here is critical — it makes Compose recompose this Canvas every frame.
    Canvas(modifier = modifier.fillMaxSize()) {
        val currentTime = time  // subscribe to state; triggers recomposition each frame
        val dt = if (lastTime == 0L) 0.016f
                 else ((currentTime - lastTime) / 1_000_000_000f).coerceIn(0f, 0.05f)

        val originY = size.height / 2f

        particles.forEach { p ->
            // Initialize Y on first draw when layout size is known
            if (p.y < 0f) {
                p.y = originY
            }

            p.x += kotlin.math.cos(p.angle) * p.speed * dt
            p.y += kotlin.math.sin(p.angle) * p.speed * dt
            p.life -= dt * 0.8f

            if (p.life <= 0f) {
                if (isPlaybackActive) {
                    // Detect if the particle was "waiting" for playback to resume.
                    // If so, randomize its starting life and position to avoid all 
                    // particles starting in sync (which causes a pulsing effect).
                    val wasWaiting = p.life < -0.05f
                    p.life = if (wasWaiting) Random.nextFloat() * p.maxLife else p.maxLife
                    p.angle = Random.nextFloat() * (2f * Math.PI.toFloat())
                    
                    val age = p.maxLife - p.life
                    p.x = originX + kotlin.math.cos(p.angle) * p.speed * age
                    p.y = originY + kotlin.math.sin(p.angle) * p.speed * age
                }
            }

            val alpha = (p.baseAlpha * p.life).coerceIn(0f, 1f)
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = p.radius,
                center = Offset(p.x, p.y)
            )
        }
    }
}
