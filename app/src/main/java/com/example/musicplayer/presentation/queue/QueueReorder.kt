package com.example.musicplayer.presentation.queue

import kotlin.math.roundToInt

/**
 * Computes the destination index of a drag-reorder gesture.
 *
 * The item started at [fromIndex] and has been dragged [offsetY] pixels
 * vertically, where [rowPitchPx] is the full vertical pitch of one queue row
 * (row height plus spacing). The result is clamped to `0..itemCount - 1`.
 */
internal fun queueDropTargetIndex(
    fromIndex: Int,
    offsetY: Float,
    rowPitchPx: Float,
    itemCount: Int,
): Int {
    if (itemCount <= 0 || rowPitchPx <= 0f) return fromIndex
    val shift = (offsetY / rowPitchPx).roundToInt()
    return (fromIndex + shift).coerceIn(0, itemCount - 1)
}
