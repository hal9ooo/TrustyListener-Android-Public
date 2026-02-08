package com.trustylistener.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.PI
import kotlin.math.sin

/**
 * Animated audio visualizer component
 */
@Composable
fun AudioVisualizer(
    audioLevel: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 32,
    color: Color = Color.Cyan
) {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")

    // Animated bars that react to audio level
    val animatedBars = List(barCount) { index ->
        val delay = index * 50
        infiniteTransition.animateFloat(
            initialValue = 0.1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 600 + (index * 20),
                    easing = FastOutSlowInEasing,
                    delayMillis = delay
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$index"
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val barWidth = size.width / barCount
        val maxBarHeight = size.height * 0.8f

        animatedBars.forEachIndexed { index, animatedValue ->
            // Combine animation with actual audio level
            val heightMultiplier = if (isActive) {
                (animatedValue.value * 0.5f + audioLevel * 0.5f).coerceIn(0.1f, 1f)
            } else {
                0.1f
            }

            val barHeight = maxBarHeight * heightMultiplier
            val x = index * barWidth + barWidth / 2
            val y = (size.height - barHeight) / 2

            // Draw mirrored bars (top and bottom)
            drawBar(
                x = x,
                y = size.height / 2,
                width = barWidth * 0.6f,
                height = barHeight / 2,
                color = color,
                alpha = 0.3f + heightMultiplier * 0.7f
            )

            drawBar(
                x = x,
                y = size.height / 2 - barHeight / 2,
                width = barWidth * 0.6f,
                height = barHeight / 2,
                color = color,
                alpha = 0.5f + heightMultiplier * 0.5f
            )
        }

        // Center line
        drawLine(
            color = color.copy(alpha = 0.3f),
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 2f
        )
    }
}

private fun DrawScope.drawBar(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    color: Color,
    alpha: Float
) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                color.copy(alpha = alpha),
                color.copy(alpha = alpha * 0.3f)
            ),
            startY = y,
            endY = y + height
        ),
        topLeft = Offset(x - width / 2, y),
        size = Size(width, height)
    )
}

/**
 * Circular audio level indicator
 */
@Composable
fun CircularAudioIndicator(
    audioLevel: Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Green
) {
    val animatedLevel by animateFloatAsState(
        targetValue = audioLevel,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "audio_level"
    )

    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension / 10
        val radius = (size.minDimension - strokeWidth) / 2

        // Background circle
        drawCircle(
            color = Color.Gray.copy(alpha = 0.3f),
            radius = radius,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )

        // Progress arc
        val sweepAngle = 360f * animatedLevel
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )
    }
}

@Preview
@Composable
fun AudioVisualizerPreview() {
    AudioVisualizer(
        audioLevel = 0.5f,
        isActive = true,
        modifier = Modifier.fillMaxSize()
    )
}
