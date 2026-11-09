package com.example.musicplayer.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

/**
 * One row of a track list: artwork tile displaying artwork cover, title, format badge,
 * artist, duration, and an overflow menu with queue actions.
 *
 * Tapping the row starts playback from that track. The loaded track is marked with the
 * accent colour plus a state icon, so it is identifiable without relying on colour alone.
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
    val format = remember(track.uri, track.title) {
        extractAudioFormat(track.uri, track.title)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(
            artworkUri = track.artworkUri,
            title = track.title,
            modifier = Modifier.size(44.dp),
            cornerRadius = 6.dp,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isCurrent) colors.primary else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    color = colors.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = format,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }

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
            if (isPlaying) {
                PlayingEqualizer(
                    color = colors.primary,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Pause,
                    contentDescription = "Paused",
                    tint = colors.primary,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp),
                )
            }
        }

        Text(
            text = remember(track.durationMs) { formatDuration(track.durationMs) },
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp),
        )

        if (onPlayNext != null || onAddToQueue != null) {
            TrackOverflowMenu(
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
            )
        }
    }
}

/** Compact overflow menu with per-track queue actions. */
@Composable
private fun TrackOverflowMenu(
    onPlayNext: (() -> Unit)?,
    onAddToQueue: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Icon(
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = "Track actions",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable { expanded = true }
                .padding(6.dp),
        )

        if (expanded) {
            DropdownMenu(
                expanded = true,
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
}

/** Formats milliseconds as `m:ss` (or `h:mm:ss`), `--:--` when unknown. */
internal fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "0:00"
    val totalSeconds = durationMs / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    val m1 = (minutes / 10).toString()
    val m2 = (minutes % 10).toString()
    val s1 = (seconds / 10).toString()
    val s2 = (seconds % 10).toString()
    return if (hours > 0L) {
        "$hours:$m1$m2:$s1$s2"
    } else {
        "$minutes:$s1$s2"
    }
}
