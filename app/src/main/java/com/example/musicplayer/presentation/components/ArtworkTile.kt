package com.example.musicplayer.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Neutral artwork tile: a muted accent square carrying the initial of [title].
 *
 * Artwork extraction and caching arrive with the artwork pipeline; until an
 * `artworkUri` is actually populated, every player surface shows this tile so
 * missing artwork never leaves a hole in the layout.
 */
@Composable
fun ArtworkTile(
    title: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            style = textStyle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}