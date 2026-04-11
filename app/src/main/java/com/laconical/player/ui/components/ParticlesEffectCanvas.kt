package com.laconical.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
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
    // Updated each frame inside LaunchedEffect to drive Canvas recomposition.
    var frameTime by remember { mutableLongStateOf(0L) }
    // Set via onSizeChanged so LaunchedEffect knows the canvas bounds for mutation.
    var canvasHeight by remember { mutableFloatStateOf(0f) }

    val density = LocalDensity.current.density
    val originX = with(LocalDensity.current) { 42.dp.toPx() }

    val particles = remember {
        List(24) {
            Particle(
                x = originX,
                y = -1f, // sentinel: initialized to center on first frame
                angle = Random.nextFloat() * (2f * Math.PI.toFloat()),
                speed = (Random.nextFloat() * 70f + 40f) * density,
                baseAlpha = Random.nextFloat() * 0.5f + 0.2f,
                radius = (Random.nextFloat() * 2.5f + 1.5f) * density,
                life = Random.nextFloat(),
                maxLife = 1f
            )
        }
    }

    // All particle state mutation happens here, outside the draw phase.
    // Canvas is a pure reader of particle positions below.
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos -> frameTime = nanos }
            val dt = if (lastNanos == 0L) 0.016f
                     else ((frameTime - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastNanos = frameTime

            val originY = canvasHeight / 2f
            if (originY <= 0f) continue // canvas not yet laid out

            particles.forEach { p ->
                if (p.y < 0f) p.y = originY

                p.x += kotlin.math.cos(p.angle) * p.speed * dt
                p.y += kotlin.math.sin(p.angle) * p.speed * dt
                p.life -= dt * 0.8f

                if (p.life <= 0f && isPlaybackActive) {
                    val wasWaiting = p.life < -0.05f
                    p.life = if (wasWaiting) Random.nextFloat() * p.maxLife else p.maxLife
                    p.angle = Random.nextFloat() * (2f * Math.PI.toFloat())
                    val age = p.maxLife - p.life
                    p.x = originX + kotlin.math.cos(p.angle) * p.speed * age
                    p.y = originY + kotlin.math.sin(p.angle) * p.speed * age
                }
            }
        }
    }

    // Pure draw — reads particle state, no mutations.
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasHeight = it.height.toFloat() }
    ) {
        if (frameTime == 0L) return@Canvas // not yet started
        particles.forEach { p ->
            val alpha = (p.baseAlpha * p.life).coerceIn(0f, 1f)
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = p.radius,
                center = Offset(p.x, p.y)
            )
        }
    }
}
