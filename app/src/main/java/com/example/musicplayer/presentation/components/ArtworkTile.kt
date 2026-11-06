package com.example.musicplayer.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Neutral artwork tile: a dark gradient square with a hairline gold edge displaying
 * the audio format (e.g. "MP3", "FLAC", "OGG").
 */
@Composable
fun ArtworkTile(
    format: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    textStyle: TextStyle = MaterialTheme.typography.labelSmall,
) {
    val colors = MaterialTheme.colorScheme
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val brush = remember(colors.surfaceContainerHigh, colors.surfaceContainerLow) {
        Brush.verticalGradient(
            listOf(colors.surfaceContainerHigh, colors.surfaceContainerLow),
        )
    }
    val borderColor = remember(colors.primary) { colors.primary.copy(alpha = 0.22f) }
    val textColor = remember(colors.primary) { colors.primary.copy(alpha = 0.85f) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
            .border(width = 1.dp, color = borderColor, shape = shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = format,
            style = textStyle,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1,
        )
    }
}
