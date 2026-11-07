package com.example.musicplayer.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicScannerTest {

    private val scanner = MusicScanner(
        context = DummyContext(),
    )

    @Test
    fun isAudioCandidate_recognizesSupportedExtensions() {
        val supportedExtensions = listOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus")

        for (ext in supportedExtensions) {
            assertTrue(
                "File with extension .$ext should be recognized as an audio candidate",
                scanner.isAudioCandidateByNameAndMime("track.$ext", null),
            )
            assertTrue(
                "Uppercase extension .${ext.uppercase()} should be recognized",
                scanner.isAudioCandidateByNameAndMime("track.${ext.uppercase()}", null),
            )
        }
    }

    @Test
    fun isAudioCandidate_recognizesAudioMimeTypeWithoutExtension() {
        assertTrue(
            "File with audio/ mime type should be recognized even without valid extension",
            scanner.isAudioCandidateByNameAndMime("track.unknown", "audio/x-unknown"),
        )
    }

    @Test
    fun isAudioCandidate_rejectsNonAudioFiles() {
        val nonAudioFiles = listOf(
            "document.txt" to "text/plain",
            "image.jpg" to "image/jpeg",
            "archive.zip" to "application/zip",
            "script.sh" to null,
            "data.bin" to "application/octet-stream",
        )

        for ((name, mime) in nonAudioFiles) {
            assertFalse(
                "File '$name' with mime '$mime' should not be an audio candidate",
                scanner.isAudioCandidateByNameAndMime(name, mime),
            )
        }
    }

    @Test
    fun decodeCueText_readsUtf8WithBom() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val text = "FILE \"01 - Song.flac\" WAVE"

        assertEquals(text, scanner.decodeCueText(bom + text.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun decodeCueText_fallsBackToLatin1ForLegacySheets() {
        val text = "FILE \"café.flac\" WAVE"

        assertEquals(text, scanner.decodeCueText(text.toByteArray(Charsets.ISO_8859_1)))
    }

    private class DummyContext : android.content.ContextWrapper(null)
}
