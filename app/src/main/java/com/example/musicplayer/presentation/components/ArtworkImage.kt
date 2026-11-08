package com.example.musicplayer.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
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
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh

    // A corrupt or missing artwork file must fall back to the neutral tile
    // instead of leaving an empty square.
    var failed by remember(artworkUri) { mutableStateOf(false) }

    if (artworkUri.isNullOrBlank() || failed) {
        ArtworkTile(
            title = title,
            modifier = modifier,
            cornerRadius = cornerRadius,
            textStyle = textStyle,
        )
    } else {
        // Deliberately no placeholder composable behind the image: list rows
        // would draw the gradient, border and initial under every cover, adding
        // overdraw and extra layout work while a large library scrolls. A solid
        // surface colour covers the short loading window instead.
        AsyncImage(
            model = artworkUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = remember(artworkUri) {
                { _: AsyncImagePainter.State.Error -> failed = true }
            },
            modifier = modifier
                .clip(shape)
                .background(backgroundColor),
        )
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
