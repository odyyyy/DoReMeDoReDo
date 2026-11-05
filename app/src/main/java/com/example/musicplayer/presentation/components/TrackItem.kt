package com.example.musicplayer.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicplayer.domain.model.Track
import java.util.Locale

/**
 * One row of the Tracks list: artwork placeholder, title, artist, duration.
 *
 * Tapping the row starts playback from that track. The currently loaded track
 * is highlighted; [isPlaying] distinguishes "playing" from "paused".
 *
 * When [onPlayNext] or [onAddToQueue] is provided, an overflow menu exposes the
 * queue actions without giving up the whole-row tap target.
 *
 * Artwork: real artwork renders only once artwork caching (Task 19) starts
 * populating `Track.artworkUri`. Until then a neutral placeholder tile is
 * shown, so the row never depends on missing images.
 */
@Composable
fun TrackItem(
    track: Track,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit = {},
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkPlaceholder(title = track.title)

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isCurrent) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isPlaying) "▶" else "❚❚",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (onPlayNext != null || onAddToQueue != null) {
            TrackOverflowMenu(
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
            )
        }
    }
}

/** Compact overflow menu with the per-track queue actions. */
@Composable
private fun TrackOverflowMenu(
    onPlayNext: (() -> Unit)?,
    onAddToQueue: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Text(
                text = "⋮",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (onPlayNext != null) {
                DropdownMenuItem(
                    text = { Text("Play next") },
                    onClick = {
                        expanded = false
                        onPlayNext()
                    },
                )
            }

            if (onAddToQueue != null) {
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    onClick = {
                        expanded = false
                        onAddToQueue()
                    },
                )
            }
        }
    }
}

/**
 * Minimal artwork stand-in: a muted tile carrying the track's initial.
 */
@Composable
private fun ArtworkPlaceholder(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Formats milliseconds as `m:ss` (or `h:mm:ss`), `--:--` when unknown. */
internal fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "--:--"
    val totalSeconds = durationMs / 1_000L
    val hours = totalSeconds / 3_600L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, (totalSeconds % 3_600L) / 60L, totalSeconds % 60L)
    } else {
        String.format(Locale.US, "%d:%02d", totalSeconds / 60L, totalSeconds % 60L)
    }
}