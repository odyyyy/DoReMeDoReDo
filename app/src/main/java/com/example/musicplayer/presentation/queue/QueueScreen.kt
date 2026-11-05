package com.example.musicplayer.presentation.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicplayer.player.PlaybackQueue
import com.example.musicplayer.player.PlaybackTrack
import com.example.musicplayer.presentation.components.formatDuration

/** Fixed row and spacing geometry so drag reordering can convert pixels to rows. */
private val QueueRowHeight = 64.dp
private val QueueRowSpacing = 4.dp

/**
 * Queue screen: the player's playlist in order, with the current item
 * highlighted. Long-press a row to drag it to a new position; the reorder is
 * written back to Media3 on drop. Each row can also be removed.
 */
@Composable
fun QueueScreen(
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        QueueTopBar(
            itemCount = queue.size,
            onBackClicked = onBackClicked,
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (queue.isEmpty) {
            EmptyQueueContent(
                onBackClicked = onBackClicked,
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                text = "Long-press a track to reorder",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            QueueList(
                queue = queue,
                onMove = { from, to -> viewModel.onMove(from, to) },
                onRemove = { index -> viewModel.onRemove(index) },
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
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClicked) {
            Text(
                text = "←",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Queue",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = when (itemCount) {
                    0 -> "Nothing queued"
                    1 -> "1 track"
                    else -> "$itemCount tracks"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QueueList(
    queue: PlaybackQueue,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = queue.items
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val rowPitchPx = with(LocalDensity.current) { (QueueRowHeight + QueueRowSpacing).toPx() }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(QueueRowSpacing),
    ) {
        itemsIndexed(items) { index, item ->
            val isDragging = index == draggingIndex
            QueueRow(
                item = item,
                isCurrent = index == queue.currentIndex,
                isDragging = isDragging,
                onRemove = { onRemove(index) },
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffsetY else 0f
                    }
                    .pointerInput(items.size, index) {
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
    isDragging: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(QueueRowHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    isDragging -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
            )
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "⠿",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (!item.artist.isNullOrBlank()) {
                Text(
                    text = item.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Text(
            text = formatDuration(item.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        IconButton(onClick = onRemove) {
            Text(
                text = "✕",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyQueueContent(
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Queue is Empty",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Add tracks with \"Play next\" or \"Add to queue\".",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(onClick = onBackClicked) {
            Text("Back to Library")
        }
    }
}
