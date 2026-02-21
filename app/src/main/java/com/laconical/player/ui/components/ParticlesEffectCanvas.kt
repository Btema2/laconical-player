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
import kotlin.math.sin
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
    modifier: Modifier = Modifier
) {
    var time by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                time = it
            }
        }
    }

    val density = LocalDensity.current.density
    val originX = with(LocalDensity.current) { 42.dp.toPx() }
    val particles = remember {
        List(12) {
            Particle(
                x = originX,
                y = 0f, // will be properly initialized in draw loop once size is known
                angle = Random.nextFloat() * (2f * Math.PI.toFloat()),
                speed = (Random.nextFloat() * 200f + 50f) * density,
                baseAlpha = Random.nextFloat() * 0.6f, // 0 to 0.6f
                radius = (Random.nextFloat() * 3f + 2f) * density, // 2dp to 5dp
                life = Random.nextFloat(),
                maxLife = 1f
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val dt = 0.016f // Roughly 60fps delta
        val originY = size.height / 2f
        
        particles.forEach { p ->
            // Initialize uninitialized Y coordinates
            if (p.y == 0f && p.life < 1f) {
                p.y = originY
            }

            p.x += kotlin.math.cos(p.angle) * p.speed * dt
            p.y += kotlin.math.sin(p.angle) * p.speed * dt
            p.life -= dt * 0.5f

            if (p.life <= 0) {
                p.x = originX
                p.y = originY
                p.angle = Random.nextFloat() * (2f * Math.PI.toFloat())
                p.life = p.maxLife
            }

            val alpha = p.baseAlpha * p.life
            drawCircle(
                color = color.copy(alpha = alpha.coerceIn(0f, 1f)),
                radius = p.radius,
                center = Offset(p.x, p.y)
            )
        }
    }
}
