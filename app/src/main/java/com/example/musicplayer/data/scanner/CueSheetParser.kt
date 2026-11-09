package com.example.musicplayer.data.scanner

import java.util.Locale

/**
 * One `TRACK` entry of a cue sheet.
 *
 * @property number Track number declared by the sheet (1-based).
 * @property title Track title declared by the sheet, when present.
 * @property startOffsetMs Start position of the track within the parent audio
 *   file, in milliseconds, parsed from the sheet's `INDEX 01` entry.
 */
data class CueTrack(
    val number: Int,
    val title: String? = null,
    val startOffsetMs: Long = 0,
)

/**
 * One `FILE` entry of a cue sheet and the tracks it contains.
 *
 * A file with several tracks is a single-file album (or one side of a vinyl
 * rip); the app cannot split such a file into separate playable tracks.
 * [MusicScanner] therefore only uses cue entries whose `FILE` block holds
 * exactly one track as an ordering hint.
 */
data class CueFile(
    val fileName: String,
    val tracks: List<CueTrack> = emptyList(),
)

/**
 * Parsed cue sheet. Only the fields that help order existing audio files are
 * retained.
 */
data class CueSheet(
    val files: List<CueFile> = emptyList(),
    val albumTitle: String? = null,
    val performer: String? = null,
    val discNumber: Int? = null,
)

/**
 * Minimal, dependency-free parser for CUE sheets.
 *
 * Cue sheets are plain text and are widely used to describe the order of the
 * tracks of an album rip. The parser intentionally ignores everything the app
 * does not need (indexes, flags, genres, dates) and only extracts `FILE`,
 * `TRACK`, `TITLE`, `PERFORMER`, and `REM DISCNUMBER`.
 */
object CueSheetParser {

    /**
     * Parses [text]. Malformed lines are ignored instead of failing the whole
     * sheet, because a partially readable cue still provides useful order.
     */
    fun parse(text: String): CueSheet {
        val files = mutableListOf<MutableCueFile>()
        var currentFile: MutableCueFile? = null
        var currentTrack: MutableCueTrack? = null
        var albumTitle: String? = null
        var performer: String? = null
        var discNumber: Int? = null

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue

            val keyword = line.substringBefore(' ').uppercase(Locale.ROOT)
            val rest = line.substringAfter(' ', "").trim()

            when (keyword) {
                "FILE" -> {
                    val fileName = parseValue(rest) ?: continue
                    val file = MutableCueFile(fileName.trim())
                    files += file
                    currentFile = file
                    currentTrack = null
                }

                "TRACK" -> {
                    val file = currentFile ?: continue
                    val number = rest.substringBefore(' ').toIntOrNull()
                        ?.takeIf { it > 0 } ?: continue
                    val track = MutableCueTrack(number)
                    file.tracks += track
                    currentTrack = track
                }

                "TITLE" -> {
                    val title = parseValue(rest) ?: continue
                    if (currentTrack != null) currentTrack.title = title
                    else if (currentFile == null) albumTitle = title
                }

                "PERFORMER" -> {
                    val value = parseValue(rest) ?: continue
                    if (currentTrack == null && currentFile == null) performer = value
                }

                "REM" -> {
                    val parts = rest.split(WHITESPACE, limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].uppercase(Locale.ROOT)
                        if (key == "DISCNUMBER" || key == "DISC") {
                            discNumber = parts[1].trim().toIntOrNull()?.takeIf { it > 0 }
                        }
                    }
                }

                "INDEX" -> {
                    val parts = rest.split(WHITESPACE, limit = 2)
                    if (parts.size == 2 && currentTrack != null) {
                        val indexNumber = parts[0].uppercase(Locale.ROOT)
                        if (indexNumber == "01") {
                            currentTrack.startOffsetMs = parseTimestampMs(parts[1])
                        }
                    }
                }
            }
        }

        return CueSheet(
            files = files.map { file ->
                CueFile(
                    fileName = file.fileName,
                    tracks = file.tracks.map { track ->
                CueTrack(track.number, track.title, track.startOffsetMs)
            },
                )
            },
            albumTitle = albumTitle,
            performer = performer,
            discNumber = discNumber,
        )
    }

    /**
     * Normalizes a cue `FILE` name so it can be matched against the names that
     * a document provider reports. Cue sheets sometimes include a relative
     * path; only the base name is significant.
     */
    fun normalizeFileName(name: String): String =
        name.substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
            .lowercase(Locale.ROOT)

    /**
     * Returns the unquoted value of [rest], or the bare token when the value is
     * not quoted. An unterminated quote consumes the rest of the line.
     */
    private fun parseValue(rest: String): String? {
        if (rest.isEmpty()) return null
        val start = rest.indexOf('"')
        if (start < 0) return rest.substringBefore(' ').trim().ifEmpty { null }

        val closing = rest.indexOf('"', startIndex = start + 1)
        val value = if (closing < 0) {
            rest.substring(start + 1)
        } else {
            rest.substring(start + 1, closing)
        }
        return value.trim().ifEmpty { null }
    }

    private val WHITESPACE = Regex("\\s+")

    /**
     * Parses a cue timestamp `MM:SS:FF` (frames are 1/75 of a second) into
     * milliseconds. Returns `0` when the value is missing or malformed.
     */
    fun parseTimestampMs(timestamp: String): Long {
        val parts = timestamp.split(":")
        if (parts.size != 3) return 0
        val minutes = parts[0].toIntOrNull() ?: 0
        val seconds = parts[1].toIntOrNull() ?: 0
        val frames = parts[2].toIntOrNull() ?: 0
        return ((minutes.toLong() * 60 + seconds) * 1000) + (frames * 1000 / 75)
    }

    private class MutableCueFile(val fileName: String) {
        val tracks = mutableListOf<MutableCueTrack>()
    }

    private class MutableCueTrack(val number: Int) {
        var title: String? = null
        var startOffsetMs: Long = 0
    }
}
