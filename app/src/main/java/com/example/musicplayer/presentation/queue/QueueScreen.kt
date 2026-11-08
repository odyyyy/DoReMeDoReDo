package com.example.musicplayer.presentation.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicplayer.player.PlaybackQueue
import com.example.musicplayer.player.PlaybackTrack
import com.example.musicplayer.presentation.components.ArtworkImage
import com.example.musicplayer.presentation.components.MessageStatus
import com.example.musicplayer.presentation.components.PlayingEqualizer
import com.example.musicplayer.presentation.components.extractAudioFormat
import com.example.musicplayer.presentation.components.formatDuration
import androidx.compose.material3.Surface

/** Fixed row and spacing geometry so drag reordering can convert pixels to rows. */
private val QueueRowHeight = 68.dp
private val QueueRowSpacing = 6.dp

/**
 * Queue screen: the player's playlist in order, with the current item
 * highlighted.
 *
 * Tapping a row jumps playback to that item. Long-press a row to drag it to a new
 * position; the reorder is written back to Media3 on drop, and each row can be
 * removed. The list scrolls to the current item when the screen opens.
 */
@Composable
fun QueueScreen(
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val shuffleModeEnabled by viewModel.shuffleModeEnabled.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var initialScrollDone by remember { mutableStateOf(false) }

    // Bring the playing item into view once, when the queue first arrives.
    LaunchedEffect(queue.currentIndex, queue.size) {
        if (!initialScrollDone && queue.currentIndex >= 0 && queue.items.isNotEmpty()) {
            listState.scrollToItem(queue.currentIndex)
            initialScrollDone = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        QueueTopBar(
            itemCount = queue.size,
            onBackClicked = onBackClicked,
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (queue.isEmpty) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                MessageStatus(
                    title = "Queue is Empty",
                    message = "Add tracks with \"Play next\" or \"Add to queue\".",
                    actionLabel = "Back to Library",
                    onAction = onBackClicked,
                )
            }
        } else {
            Text(
                text = if (shuffleModeEnabled) {
                    "Shuffle is on; turn it off to reorder"
                } else {
                    "Long-press a track to reorder"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(10.dp))

            QueueList(
                queue = queue,
                listState = listState,
                isPlaying = isPlaying,
                canReorder = !shuffleModeEnabled,
                onSelect = viewModel::onSelect,
                onMove = viewModel::onMove,
                onRemove = viewModel::onRemove,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QueueTopBar(
    itemCount: Int,
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClicked) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = colors.onSurface,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Queue",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
            )

            Text(
                text = when (itemCount) {
                    0 -> "Nothing queued"
                    1 -> "1 track"
                    else -> "$itemCount tracks"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QueueList(
    queue: PlaybackQueue,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isPlaying: Boolean,
    canReorder: Boolean,
    onSelect: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = queue.items
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val rowPitchPx = with(LocalDensity.current) { (QueueRowHeight + QueueRowSpacing).toPx() }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(QueueRowSpacing),
    ) {
        // No key: the same track may legitimately appear twice in the queue.
        // The content type still lets Compose reuse row compositions by slot.
        itemsIndexed(
            items = items,
            contentType = { _, _ -> "queueItem" },
        ) { index, item ->
            val isDragging = index == draggingIndex
            val handleSelect = remember(index, onSelect) { { onSelect(index) } }
            val handleRemove = remember(index, onRemove) { { onRemove(index) } }
            QueueRow(
                item = item,
                isCurrent = index == queue.currentIndex,
                isPlaying = isPlaying,
                isDragging = isDragging,
                onSelect = handleSelect,
                onRemove = handleRemove,
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffsetY else 0f
                    }
                    .pointerInput(items.size, index, canReorder) {
                        if (!canReorder) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingIndex = index
                                dragOffsetY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetY += dragAmount.y
                            },
                            onDragEnd = {
                                val from = draggingIndex
                                val offset = dragOffsetY
                                draggingIndex = -1
                                dragOffsetY = 0f
                                if (from in items.indices) {
                                    val to = queueDropTargetIndex(from, offset, rowPitchPx, items.size)
                                    if (to != from) onMove(from, to)
                                }
                            },
                            onDragCancel = {
                                draggingIndex = -1
                                dragOffsetY = 0f
                            },
                        )
                    },
            )
        }
    }
}

@Composable
private fun QueueRow(
    item: PlaybackTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isDragging: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(QueueRowHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isCurrent -> colors.primary.copy(alpha = 0.12f)
                    isDragging -> colors.surfaceContainerHigh
                    else -> colors.surfaceContainer
                },
            )
            .clickable(onClickLabel = "Play this track", onClick = onSelect)
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.DragHandle,
            contentDescription = "Reorder handle",
            tint = colors.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )

        Spacer(modifier = Modifier.width(6.dp))

        val format = remember(item.uri, item.title) {
            extractAudioFormat(item.uri, item.title)
        }

        ArtworkImage(
            artworkUri = item.artworkUri,
            title = item.title,
            modifier = Modifier.size(40.dp),
            cornerRadius = 6.dp,
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = item.title,
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

            if (!item.artist.isNullOrBlank()) {
                Text(
                    text = item.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isCurrent) {
            if (isPlaying) {
                PlayingEqualizer(
                    color = colors.primary,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "Paused",
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp),
                )
            }

            Spacer(modifier = Modifier.width(10.dp))
        }

        Text(
            text = formatDuration(item.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )

        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remove from queue",
                tint = colors.onSurfaceVariant,
            )
        }
    }
}