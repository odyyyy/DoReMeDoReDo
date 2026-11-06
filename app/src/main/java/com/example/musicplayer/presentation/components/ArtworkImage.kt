package com.example.musicplayer.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.net.URLDecoder

/**
 * Artwork for one track or album: the cached image cover from the artwork pipeline,
 * falling back to the neutral [ArtworkTile] displaying the title initial when
 * missing or unreadable.
 */
@Composable
fun ArtworkImage(
    artworkUri: String?,
    title: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    if (artworkUri.isNullOrBlank()) {
        ArtworkTile(
            title = title,
            modifier = modifier,
            cornerRadius = cornerRadius,
            textStyle = textStyle,
        )
    } else {
        Box(modifier = modifier) {
            ArtworkTile(
                title = title,
                modifier = Modifier.matchParentSize(),
                cornerRadius = cornerRadius,
                textStyle = textStyle,
            )

            AsyncImage(
                model = artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape),
            )
        }
    }
}

/**
 * Extracts a concise audio format label (e.g. "MP3", "FLAC", "OGG") from a URI or title.
 */
internal fun extractAudioFormat(uriString: String, title: String? = null): String {
    val decoded = runCatching { URLDecoder.decode(uriString, "UTF-8") }.getOrDefault(uriString)
    val uriExt = decoded.substringBefore('?').substringAfterLast('.', "").trim().lowercase()
    if (uriExt in SUPPORTED_FORMATS) return uriExt.uppercase()

    if (title != null) {
        val titleExt = title.substringAfterLast('.', "").trim().lowercase()
        if (titleExt in SUPPORTED_FORMATS) return titleExt.uppercase()
    }

    val lowerUri = decoded.lowercase()
    for (fmt in SUPPORTED_FORMAT_LIST) {
        if (lowerUri.contains(".$fmt")) return fmt.uppercase()
    }

    return "AUDIO"
}

private val SUPPORTED_FORMATS = setOf(
    "mp3", "flac", "ogg", "wav", "m4a", "aac", "opus", "wma", "alac", "aiff"
)
private val SUPPORTED_FORMAT_LIST = listOf(
    "flac", "mp3", "opus", "ogg", "m4a", "aac", "wav", "wma"
)
