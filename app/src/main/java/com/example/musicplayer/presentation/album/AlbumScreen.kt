package com.example.musicplayer.presentation.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicplayer.domain.model.Album
import com.example.musicplayer.presentation.components.ArtworkImage
import com.example.musicplayer.presentation.components.MessageStatus
import com.example.musicplayer.presentation.components.PlaybackErrorBanner
import com.example.musicplayer.presentation.components.TrackItem
import com.example.musicplayer.presentation.components.formatDuration
import com.example.musicplayer.presentation.library.PlaybackHighlight

/**
 * Album Details screen displaying an album's artwork, metadata, play action, and
 * its track list.
 *
 * Selecting any track starts playback with this album's track list as the queue.
 */
@Composable
fun AlbumScreen(
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackHighlight by viewModel.playbackHighlight.collectAsStateWithLifecycle()
    val playbackError by viewModel.playbackError.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        TopBar(onBackClicked = onBackClicked)

        Spacer(modifier = Modifier.height(8.dp))

        if (playbackError != null) {
            PlaybackErrorBanner(
                message = playbackError.orEmpty(),
                onDismiss = viewModel::dismissPlaybackError,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        when (val state = uiState) {
            is AlbumUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
            }

            is AlbumUiState.NotFound -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                MessageStatus(
                    title = "Album Not Found",
                    message = "This album is no longer in the library.",
                    actionLabel = "Back to Library",
                    onAction = onBackClicked,
                    isError = true,
                )
            }

            is AlbumUiState.Ready -> {
                AlbumHeader(
                    album = state.album,
                    totalDurationMs = state.totalDurationMs,
                    onPlayClicked = { viewModel.playAlbum() },
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(
                        items = state.tracks,
                        key = { it.uri },
                        contentType = { "albumTrack" },
                    ) { track ->
                        val isCurrent = playbackHighlight?.trackUri == track.uri
                        val isPlaying = isCurrent && playbackHighlight?.isPlaying == true
                        val onClick = remember(track) { { viewModel.onTrackSelected(track) } }
                        val onPlayNextAction = remember(track) { { viewModel.onPlayNext(track) } }
                        val onAddToQueueAction = remember(track) { { viewModel.onAddToQueue(track) } }
                        TrackItem(
                            track = track,
                            isCurrent = isCurrent,
                            isPlaying = isPlaying,
                            onClick = onClick,
                            onPlayNext = onPlayNextAction,
                            onAddToQueue = onAddToQueueAction,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
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
            text = "Album",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AlbumHeader(
    album: Album,
    totalDurationMs: Long,
    onPlayClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(
            artworkUri = album.artworkUri,
            title = album.title,
            modifier = Modifier.size(104.dp),
            cornerRadius = 12.dp,
            textStyle = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${album.trackCount} tracks · ${formatDuration(totalDurationMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary.copy(alpha = 0.85f),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onPlayClicked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text("Play Album")
            }
        }
    }
}