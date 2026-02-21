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
import kotlin.math.sin
import kotlin.random.Random

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
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

    val particles = remember {
        List(20) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                vx = Random.nextFloat() * 0.5f + 0.1f, // Drift right
                vy = (Random.nextFloat() - 0.5f) * 0.2f, // Slight vertical drift
                baseAlpha = Random.nextFloat() * 0.5f + 0.1f,
                radius = Random.nextFloat() * 4f + 2f,
                life = Random.nextFloat(),
                maxLife = 1f
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val dt = 0.016f // Roughly 60fps delta
        
        particles.forEach { p ->
            p.x += p.vx * dt * size.width * 0.1f
            p.y += (p.vy + sin(time.toFloat() / 1000000000f + p.x) * 0.05f) * dt * size.height * 0.1f
            p.life -= dt * 0.2f

            if (p.life <= 0 || p.x > size.width) {
                p.x = 0f
                p.y = Random.nextFloat() * size.height
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
