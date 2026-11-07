package com.example.musicplayer.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Small animated equalizer shown next to the track that is currently playing.
 *
 * Each bar oscillates at its own speed and phase, so the icon reads as a live
 * level meter instead of a static "sound" glyph. The animation is confined to
 * this composable, and only the single playing row composes it, so list
 * scrolling and other rows are unaffected.
 */
@Composable
fun PlayingEqualizer(
    color: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 4,
) {
    val transition = rememberInfiniteTransition(label = "playingEqualizer")
    val levels = List(barCount) { index ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 320 + index * 85,
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(index * 90),
            ),
            label = "equalizerBar$index",
        )
    }

    Canvas(
        modifier = modifier.semantics { contentDescription = "Playing" },
    ) {
        if (size.width <= 0f || size.height <= 0f || barCount <= 0) return@Canvas

        // Gaps are a fixed fraction of the bar width, so the meter scales with
        // the icon size the caller asks for.
        val gapFraction = 0.4f
        val barWidth = size.width / (barCount + (barCount - 1) * gapFraction)
        val gap = barWidth * gapFraction
        val corner = CornerRadius(barWidth / 2f, barWidth / 2f)

        // Reading the animation states here, in the draw phase, keeps every
        // frame from recomposing the composable.
        levels.forEachIndexed { index, levelState ->
            val barHeight = size.height * levelState.value.coerceIn(0.2f, 1f)
            drawRoundRect(
                color = color,
                topLeft = Offset(
                    x = index * (barWidth + gap),
                    y = (size.height - barHeight) / 2f,
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = corner,
            )
        }
    }
}
