package com.example.musicplayer.presentation.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicplayer.domain.model.TrackSort

/**
 * Header shared by the Tracks and Albums tabs: the tab title, a quiet subtitle,
 * and the queue shortcut with its current size.
 */
@Composable
fun LibraryTopBar(
    title: String,
    subtitle: String,
    queueSize: Int,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // The badge is a sibling of the IconButton instead of its content: the
        // button clips its children to a circle, which cut off three-digit
        // queue counts. Drawing the pill outside the button keeps any number
        // (e.g. "305") fully visible.
        Box {
            IconButton(onClick = onOpenQueue) {
                Icon(
                    imageVector = Icons.Rounded.QueueMusic,
                    contentDescription = if (queueSize > 0) {
                        "Queue, $queueSize tracks"
                    } else {
                        "Queue"
                    },
                    tint = colors.onSurface,
                )
            }

            if (queueSize > 0) {
                Surface(
                    color = colors.primary,
                    contentColor = colors.onPrimary,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-2).dp),
                ) {
                    Text(
                        text = queueSize.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/**
 * Search field with an icon clear action plus a sort menu that shows the active
 * order and an icon per option.
 */
@Composable
fun SearchAndSortBar(
    query: String,
    onQueryChange: (String) -> Unit,
    sort: TrackSort,
    onSortChange: (TrackSort) -> Unit,
    modifier: Modifier = Modifier,
    showSort: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            placeholder = {
                Text(
                    text = "Search title, artist, album",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search",
                            tint = colors.onSurfaceVariant,
                        )
                    }
                }
            },
        )

        if (showSort) {
            SortMenu(sort = sort, onSortChange = onSortChange)
        }
    }
}

@Composable
private fun SortMenu(
    sort: TrackSort,
    onSortChange: (TrackSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Box(modifier = modifier) {
        FilledTonalIconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Rounded.Sort,
                contentDescription = "Sort: ${sort.label}",
                tint = colors.primary,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            TrackSort.entries.forEach { option ->
                val isSelected = option == sort
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            color = if (isSelected) colors.primary else colors.onSurface,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = option.icon(),
                            contentDescription = null,
                            tint = if (isSelected) colors.primary else colors.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = colors.primary,
                            )
                        }
                    },
                    onClick = {
                        onSortChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Icon shown next to each sort option. */
internal fun TrackSort.icon(): ImageVector = when (this) {
    TrackSort.TITLE -> Icons.Rounded.SortByAlpha
    TrackSort.ARTIST -> Icons.Rounded.Person
    TrackSort.ALBUM -> Icons.Rounded.Album
    TrackSort.DURATION -> Icons.Rounded.Timer
    TrackSort.DATE_ADDED -> Icons.Rounded.CalendarToday
    TrackSort.SHUFFLE -> Icons.Rounded.Shuffle
}