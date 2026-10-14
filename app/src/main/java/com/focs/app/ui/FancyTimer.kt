package com.focs.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

private val RingColors = listOf(
    Color(0xFF7F5AF0), // violet
    Color(0xFFFF4FD8), // pink
    Color(0xFFFF8A3D), // orange
    Color(0xFFFFD166), // gold
    Color(0xFF2CB67D), // mint
    Color(0xFF3DA9FC), // sky
    Color(0xFF7F5AF0),
)

/** Deep, slowly drifting night-sky gradient behind the focus timer. */
@Composable
fun FocusBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val drift by rememberInfiniteTransition(label = "backdrop").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF1B1035), Color(0xFF0B1A2E)),
                    start = Offset(0f, 2000f * drift),
                    end = Offset(1400f, 2000f * (1 - drift) + 600f),
                )
            ),
        content = content,
    )
}

/**
 * A glowing ring that empties as focus time runs out. The rainbow gradient slowly spins,
 * a bright dot marks the leading edge and pulses once a second.
 */
@Composable
fun FancyTimer(remainingMs: Long, totalMs: Long, modifier: Modifier = Modifier, size: Dp = 300.dp) {
    val transition = rememberInfiniteTransition(label = "timer")
    val spin by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(9_000, easing = LinearEasing)), label = "spin",
    )
    val pulse by transition.animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(1_000), RepeatMode.Reverse), label = "pulse",
    )
    val progress = (remainingMs / totalMs.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val inset = stroke * 1.6f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val sweep = 360f * progress
            val brush = Brush.sweepGradient(RingColors, center)

            // Faint full track.
            drawArc(Color.White.copy(alpha = 0.07f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))

            // Spinning gradient: rotate the canvas, counter-rotate the arc so it stays anchored at 12 o'clock.
            rotate(spin) {
                drawArc(
                    brush, -90f - spin, sweep, false, topLeft, arcSize,
                    alpha = 0.22f * pulse, style = Stroke(stroke * 2.4f, cap = StrokeCap.Round),
                )
                drawArc(
                    brush, -90f - spin, sweep, false, topLeft, arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }

            // Leading-edge dot.
            val radius = arcSize.width / 2
            val angle = Math.toRadians((-90f + sweep).toDouble())
            val dot = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
            drawCircle(Color.White.copy(alpha = 0.35f * pulse), stroke * 1.3f, dot)
            drawCircle(Color.White, stroke * 0.55f, dot)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatClock(remainingMs),
                color = Color.White,
                fontSize = 60.sp,
                fontWeight = FontWeight.Thin,
                letterSpacing = 2.sp,
            )
            Text(
                "FOCUS",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 8.sp,
            )
        }
    }
}

fun formatClock(ms: Long): String {
    val s = (ms + 999) / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
