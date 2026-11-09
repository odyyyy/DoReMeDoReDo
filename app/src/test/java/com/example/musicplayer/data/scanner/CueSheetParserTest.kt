package com.example.musicplayer.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CueSheetParserTest {

    @Test
    fun `parses a multi-file sheet into one track per file`() {
        val sheet = CueSheetParser.parse(
            """
            REM GENRE "Jazz"
            REM DISCNUMBER 2
            PERFORMER "Miles Davis"
            TITLE "Kind of Blue"
            FILE "01 - So What.flac" WAVE
              TRACK 01 AUDIO
                TITLE "So What"
                INDEX 01 00:00:00
            FILE "02 - Freddie Freeloader.flac" WAVE
              TRACK 02 AUDIO
                TITLE "Freddie Freeloader"
                INDEX 01 00:00:00
            """.trimIndent(),
        )

        assertEquals("Kind of Blue", sheet.albumTitle)
        assertEquals("Miles Davis", sheet.performer)
        assertEquals(2, sheet.discNumber)
        assertEquals(
            listOf("01 - So What.flac", "02 - Freddie Freeloader.flac"),
            sheet.files.map { it.fileName },
        )
        assertEquals(listOf(1, 2), sheet.files.map { it.tracks.single().number })
    }

    @Test
    fun `parses a single-file sheet with several tracks`() {
        val sheet = CueSheetParser.parse(
            """
            FILE "album.flac" WAVE
              TRACK 01 AUDIO
                TITLE "First"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "Second"
                INDEX 01 03:20:00
            """.trimIndent(),
        )

        val file = sheet.files.single()
        assertEquals("album.flac", file.fileName)
        assertEquals(listOf(1, 2), file.tracks.map { it.number })
        assertEquals(listOf("First", "Second"), file.tracks.map { it.title })
    }

    @Test
    fun `ignores malformed lines instead of failing the sheet`() {
        val sheet = CueSheetParser.parse(
            """
            TRACK not-a-number AUDIO
            FILE "one.flac" WAVE
              TRACK 03 AUDIO
              TRACK nope AUDIO
            """.trimIndent(),
        )

        assertEquals(1, sheet.files.size)
        assertEquals(listOf(3), sheet.files.single().tracks.map { it.number })
        assertNull(sheet.discNumber)
    }

    @Test
    fun `normalizes file names for matching`() {
        assertEquals("01 - song.flac", CueSheetParser.normalizeFileName("  Dir/01 - Song.flac "))
        assertEquals(
            "01 - song.flac",
            CueSheetParser.normalizeFileName("dir\\01 - Song.flac"),
        )
    }

    @Test
    fun `parses INDEX 01 start offsets into milliseconds`() {
        val sheet = CueSheetParser.parse(
            """
            FILE "album.flac" WAVE
              TRACK 01 AUDIO
                TITLE "First"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "Second"
                INDEX 01 03:20:00
              TRACK 03 AUDIO
                TITLE "Third"
                INDEX 01 06:40:25
            """.trimIndent(),
        )

        val tracks = sheet.files.single().tracks
        assertEquals(listOf(0L, 200_000L, 400_333L), tracks.map { it.startOffsetMs })
        assertEquals(listOf("First", "Second", "Third"), tracks.map { it.title })
    }

    @Test
    fun `parseTimestampMs converts frames to milliseconds`() {
        assertEquals(0L, CueSheetParser.parseTimestampMs("00:00:00"))
        assertEquals(200_000L, CueSheetParser.parseTimestampMs("03:20:00"))
        assertEquals(60_333L, CueSheetParser.parseTimestampMs("01:00:25"))
        assertEquals(0L, CueSheetParser.parseTimestampMs("garbage"))
        assertEquals(0L, CueSheetParser.parseTimestampMs("00:00"))
    }

    @Test
    fun `captures sheet level title and performer for album identity`() {
        val sheet = CueSheetParser.parse(
            """
            PERFORMER "Miles Davis"
            TITLE "Kind of Blue"
            FILE "album.flac" WAVE
              TRACK 01 AUDIO
                TITLE "So What"
                INDEX 01 00:00:00
            """.trimIndent(),
        )

        assertEquals("Kind of Blue", sheet.albumTitle)
        assertEquals("Miles Davis", sheet.performer)
    }
}
