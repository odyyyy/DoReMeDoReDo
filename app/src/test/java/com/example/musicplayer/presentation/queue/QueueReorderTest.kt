package com.example.musicplayer.presentation.queue

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure checks of the drag-reorder index math. A row pitch of 68 px (64 px row +
 * 4 px spacing) stands in for the on-screen geometry.
 */
class QueueReorderTest {

    private val pitch = 68f

    @Test
    fun noDrag_keepsOriginalIndex() {
        assertEquals(2, queueDropTargetIndex(fromIndex = 2, offsetY = 0f, rowPitchPx = pitch, itemCount = 5))
    }

    @Test
    fun draggingDownOneRow_targetsNextIndex() {
        assertEquals(3, queueDropTargetIndex(fromIndex = 2, offsetY = pitch, rowPitchPx = pitch, itemCount = 5))
    }

    @Test
    fun draggingUpTwoRows_targetsEarlierIndex() {
        assertEquals(1, queueDropTargetIndex(fromIndex = 3, offsetY = -2 * pitch, rowPitchPx = pitch, itemCount = 5))
    }

    @Test
    fun smallDrag_roundsToNearestRow() {
        assertEquals(2, queueDropTargetIndex(fromIndex = 2, offsetY = pitch * 0.4f, rowPitchPx = pitch, itemCount = 5))
        assertEquals(3, queueDropTargetIndex(fromIndex = 2, offsetY = pitch * 0.6f, rowPitchPx = pitch, itemCount = 5))
    }

    @Test
    fun dragPastTop_clampsToFirstIndex() {
        assertEquals(0, queueDropTargetIndex(fromIndex = 1, offsetY = -10 * pitch, rowPitchPx = pitch, itemCount = 5))
    }

    @Test
    fun dragPastBottom_clampsToLastIndex() {
        assertEquals(4, queueDropTargetIndex(fromIndex = 3, offsetY = 10 * pitch, rowPitchPx = pitch, itemCount = 5))
    }

    @Test
    fun invalidGeometry_keepsOriginalIndex() {
        assertEquals(1, queueDropTargetIndex(fromIndex = 1, offsetY = pitch, rowPitchPx = 0f, itemCount = 5))
        assertEquals(1, queueDropTargetIndex(fromIndex = 1, offsetY = pitch, rowPitchPx = pitch, itemCount = 0))
    }
}
