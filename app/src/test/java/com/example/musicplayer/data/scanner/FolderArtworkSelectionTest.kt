package com.example.musicplayer.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The folder-cover rule: one image is always the cover, several prefer a
 * conventional name (cover/folder/front/…) and otherwise fall back to the
 * first image alphabetically. Pure, so no document provider is needed.
 */
class FolderArtworkSelectionTest {

    @Test
    fun singleImage_isUsedAsCover() {
        val name = MusicScanner.chooseFolderArtworkName(
            listOf("01 - Track.mp3", "cover.png"),
        )

        assertEquals("cover.png", name)
    }

    @Test
    fun singleUnconventionallyNamedImage_isUsedAsCover() {
        val name = MusicScanner.chooseFolderArtworkName(
            listOf("track.flac", "Radiohead - Amnesiac.jpg"),
        )

        assertEquals("Radiohead - Amnesiac.jpg", name)
    }

    @Test
    fun conventionalName_isPreferredOverOtherImages() {
        val name = MusicScanner.chooseFolderArtworkName(
            listOf("back-cover.jpg", "Folder.JPG", "booklet-1.jpg", "track.mp3"),
        )

        assertEquals("Folder.JPG", name)
    }

    @Test
    fun coverNameWinsOverFolderName() {
        val name = MusicScanner.chooseFolderArtworkName(
            listOf("folder.jpg", "cover.jpg"),
        )

        assertEquals("cover.jpg", name)
    }

    @Test
    fun severalUnconventionalImages_useFirstAlphabetically() {
        val name = MusicScanner.chooseFolderArtworkName(
            listOf("art-1.jpg", "art-2.png", "track.flac"),
        )

        assertEquals("art-1.jpg", name)
    }

    @Test
    fun noImages_resolvesToNull() {
        val name = MusicScanner.chooseFolderArtworkName(
            listOf("track.mp3", "notes.txt"),
        )

        assertNull(name)
    }
}
