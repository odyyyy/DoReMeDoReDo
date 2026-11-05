package com.example.musicplayer.presentation.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicplayer.domain.model.Track
import java.util.Locale

/**
 * One row of a track list: artwork tile, title, artist, duration, and an optional
 * overflow menu with the queue actions.
 *
 * Tapping the row starts playback from that track. The loaded track is marked
 * with the accent colour plus a state icon, so it is identifiable without relying
 * on colour alone.
 *
 * When [onPlayNext] or [onAddToQueue] is provided, the overflow menu exposes the
 * queue actions without giving up the whole-row tap target.
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
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkTile(
            title = track.title,
            modifier = Modifier.size(44.dp),
            cornerRadius = 6.dp,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isCurrent) colors.primary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isCurrent) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.Pause,
                contentDescription = if (isPlaying) "Playing" else "Paused",
                tint = colors.primary,
                modifier = Modifier.size(16.dp),
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
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
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Track actions",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (onPlayNext != null) {
                DropdownMenuItem(
                    text = { Text("Play next") },
                    leadingIcon = {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onPlayNext()
                    },
                )
            }

            if (onAddToQueue != null) {
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    leadingIcon = {
                        Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onAddToQueue()
                    },
                )
            }
        }
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