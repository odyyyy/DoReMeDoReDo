package com.example.musicplayer.presentation.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.musicplayer.player.PlaybackRepeatMode
import com.example.musicplayer.presentation.components.ArtworkImage
import com.example.musicplayer.presentation.components.extractAudioFormat
import com.example.musicplayer.presentation.components.formatDuration
import kotlin.math.abs

/** Length of the linear interpolation between two published positions. */
private const val POSITION_ANIMATION_MS = 500

/**
 * How close a published position must be to the value the user seeked to before
 * the seek bar stops showing the scrubbed position.
 */
private const val SEEK_SETTLE_TOLERANCE_MS = 1_000L

/**
 * Now Playing: the full playback surface.
 *
 * Every control is forwarded to [PlayerViewModel] and therefore to the single
 * player owned by `PlaybackService`; the screen never holds a player of its own.
 * It reads two state streams: [PlayerUiState] for track, transport and mode
 * changes, and [PlaybackProgress] for the seek bar, which is collected inside the
 * seek-bar composable only (via the deferred [NowPlayingContent] `progress`
 * lambda) so position ticks do not recompose the whole screen.
 */
@Composable
fun NowPlayingScreen(
    onBackClicked: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenAlbum: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val progress = viewModel.progress.collectAsStateWithLifecycle()

    NowPlayingContent(
        state = state,
        // Deferred read: only the seek bar recomposes on a position tick.
        progress = { progress.value },
        onBackClicked = onBackClicked,
        onOpenQueue = onOpenQueue,
        onOpenAlbum = onOpenAlbum,
        onTogglePlayPause = viewModel::onTogglePlayPause,
        onNext = viewModel::onNext,
        onPrevious = viewModel::onPrevious,
        onToggleShuffle = viewModel::onToggleShuffle,
        onCycleRepeatMode = viewModel::onCycleRepeatMode,
        onSeekTo = viewModel::onSeekTo,
        modifier = modifier,
    )
}

/**
 * Stateless Now Playing layout.
 *
 * @param state Track, transport and mode state, or `null` when nothing is loaded.
 * @param progress Deferred seek-bar state; reading it inside the seek bar keeps
 *   position updates from recomposing the artwork and metadata.
 */
@Composable
fun NowPlayingContent(
    state: PlayerUiState?,
    progress: () -> PlaybackProgress?,
    onBackClicked: () -> Unit,
    onOpenQueue: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenAlbum: (String) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        NowPlayingBackground(state = state)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            NowPlayingHeader(onBackClicked = onBackClicked)

            Spacer(modifier = Modifier.height(8.dp))

            if (state == null) {
                EmptyPlayerContent()
                return@Column
            }

            val format = remember(state.trackUri, state.title) {
                extractAudioFormat(state.trackUri, state.title)
            }

            // Now Playing is the only surface that draws the cover large enough
            // to need the full-resolution variant; lists and the notification
            // keep using the small thumbnail. The fallback covers libraries
            // indexed before the full variant existed.
            val displayArtworkUri = state.fullArtworkUri ?: state.artworkUri

            // The artwork takes whatever vertical space is left, so a short
            // window shrinks the image instead of pushing controls off screen.
            // Horizontal swipes switch tracks, and a tap opens the album.
            val swipeThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
            var dragOffset by remember(state.trackUri) { mutableStateOf(0f) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(state.trackUri) {
                        detectHorizontalDragGestures(
                            onDragCancel = { dragOffset = 0f },
                            onDragEnd = {
                                val offset = dragOffset
                                dragOffset = 0f
                                when {
                                    offset <= -swipeThresholdPx -> onNext()
                                    offset >= swipeThresholdPx -> onPrevious()
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset = (dragOffset + dragAmount)
                                    .coerceIn(-swipeThresholdPx * 2, swipeThresholdPx * 2)
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                ArtworkImage(
                    artworkUri = displayArtworkUri,
                    title = state.title,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .graphicsLayer { translationX = dragOffset }
                        .clickable(
                            onClickLabel = "Open album",
                            onClick = { onOpenAlbum(state.albumGroupingKey) },
                        ),
                    cornerRadius = 14.dp,
                    textStyle = MaterialTheme.typography.displayMedium,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        TrackFormatBadge(format = format)
                    }

                    Text(
                        text = state.artistLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = state.albumLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(
                            onClickLabel = "Open album",
                            onClick = { onOpenAlbum(state.albumGroupingKey) },
                        ),
                    )
                }

                // Buffering is a silent indicator, never a caption that would
                // stay on screen after the user moves elsewhere in the track.
                if (state.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .semantics { contentDescription = "Buffering" },
                        strokeWidth = 2.dp,
                        color = colors.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            SeekBar(
                progress = progress,
                onSeekTo = onSeekTo,
            )

            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                )
            } else if (state.hasEnded) {
                Text(
                    text = "Playback finished",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            TransportControls(
                state = state,
                onTogglePlayPause = onTogglePlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeatMode = onCycleRepeatMode,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(onClick = onOpenQueue) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = "Queue",
                        tint = colors.primary,
                    )
                }
            }
        }
    }
}

/**
 * Ambient background derived from the current cover.
 *
 * The artwork is drawn blurred behind the whole screen, with a background
 * gradient on top so metadata and controls keep their contrast. On Android
 * versions where [blur] has no effect the partial alpha and the scrim still
 * leave a soft colour wash instead of a distracting sharp image.
 */
@Composable
private fun NowPlayingBackground(
    state: PlayerUiState?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val artworkUri = state?.artworkUri

    Box(modifier = modifier.fillMaxSize()) {
        if (!artworkUri.isNullOrBlank()) {
            AsyncImage(
                model = artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(64.dp)
                    .alpha(0.75f),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to colors.background.copy(alpha = 0.35f),
                            0.55f to colors.background.copy(alpha = 0.75f),
                            1f to colors.background,
                        ),
                    ),
                ),
        )
    }
}

/** Small format pill (MP3, FLAC, OGG ...) shown next to the album line. */
@Composable
private fun TrackFormatBadge(
    format: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        color = colors.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier,
    ) {
        Text(
            text = format,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = colors.primary,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun NowPlayingHeader(
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClicked) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "Now Playing",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Seek bar with elapsed/total time.
 *
 * The published position arrives twice a second, so the thumb is interpolated
 * linearly between two published values while playing; that interpolation is
 * confined to this composable. While the user scrubs — and until the player
 * reports a position close to the seek target — the scrubbed position is shown
 * instead, so the thumb never jumps back after a seek.
 */
@Composable
private fun SeekBar(
    progress: () -> PlaybackProgress?,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val current = progress()
    val durationMs = current?.durationMs ?: 0L
    val isSeekable = current?.isSeekable == true

    var scrubbedMs by remember(current?.trackUri) { mutableStateOf<Long?>(null) }

    // Drop the scrubbed position once playback reports the seeked position.
    LaunchedEffect(current?.positionMs, current?.trackUri) {
        val target = scrubbedMs ?: return@LaunchedEffect
        val reported = current?.positionMs ?: return@LaunchedEffect
        if (abs(reported - target) <= SEEK_SETTLE_TOLERANCE_MS) scrubbedMs = null
    }

    val targetFraction = when {
        scrubbedMs != null -> seekPositionFraction(scrubbedMs ?: 0L, durationMs)
        current != null -> current.fraction
        else -> 0f
    }
    val isPlaying = current?.isPlaying == true
    val fraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = if (isPlaying && scrubbedMs == null) {
            tween(durationMillis = POSITION_ANIMATION_MS, easing = LinearEasing)
        } else {
            snap()
        },
        label = "playbackPosition",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { value -> scrubbedMs = positionForFraction(value, durationMs) },
            onValueChangeFinished = { scrubbedMs?.let(onSeekTo) },
            enabled = isSeekable,
            colors = SliderDefaults.colors(
                thumbColor = colors.primary,
                activeTrackColor = colors.primary,
                inactiveTrackColor = colors.surfaceContainerHigh,
                disabledThumbColor = colors.onSurfaceVariant,
                disabledActiveTrackColor = colors.surfaceContainerHigh,
                disabledInactiveTrackColor = colors.surfaceContainerHigh,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Playback position" },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(positionForFraction(fraction, durationMs)),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )

            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransportControls(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeButton(
            icon = Icons.Rounded.Shuffle,
            label = "Shuffle",
            stateDescription = if (state.shuffleModeEnabled) "On" else "Off",
            isActive = state.shuffleModeEnabled,
            onClick = onToggleShuffle,
        )

        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous track",
                tint = colors.onSurface,
            )
        }

        FilledIconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier.size(64.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ),
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(32.dp),
            )
        }

        IconButton(
            onClick = onNext,
            enabled = state.hasNext,
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next track",
                tint = if (state.hasNext) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }

        ModeButton(
            icon = if (state.repeatMode == PlaybackRepeatMode.ONE) {
                Icons.Rounded.RepeatOne
            } else {
                Icons.Rounded.Repeat
            },
            label = "Repeat",
            stateDescription = when (state.repeatMode) {
                PlaybackRepeatMode.OFF -> "Off"
                PlaybackRepeatMode.ALL -> "All"
                PlaybackRepeatMode.ONE -> "One"
            },
            isActive = state.repeatMode != PlaybackRepeatMode.OFF,
            onClick = onCycleRepeatMode,
        )
    }
}

/** Toggle button whose active state is exposed to accessibility services. */
@Composable
private fun ModeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    stateDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    IconButton(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = label
            this.stateDescription = stateDescription
        },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) colors.primary else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyPlayerContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Nothing is playing",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Pick a track from the library to start listening.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}