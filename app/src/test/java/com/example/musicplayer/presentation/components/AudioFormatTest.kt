package com.example.musicplayer.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioFormatTest {

    @Test
    fun extractAudioFormat_extractsExtensionFromUri() {
        assertEquals("MP3", extractAudioFormat("content://music/song.mp3"))
        assertEquals("FLAC", extractAudioFormat("file:///sdcard/music/album/track.flac"))
        assertEquals("OGG", extractAudioFormat("content://com.android.providers.media/document/audio%3A123.ogg"))
        assertEquals("WAV", extractAudioFormat("content://tree/primary:Music/track.wav?query=1"))
        assertEquals("M4A", extractAudioFormat("file:///storage/emulated/0/Music/song.m4a"))
        assertEquals("OPUS", extractAudioFormat("file:///storage/emulated/0/Music/song.opus"))
        assertEquals("AAC", extractAudioFormat("file:///storage/emulated/0/Music/song.aac"))
    }

    @Test
    fun extractAudioFormat_fallsBackToTitleOrAudio() {
        assertEquals("FLAC", extractAudioFormat("content://opaque/uri", "MySong.flac"))
        assertEquals("AUDIO", extractAudioFormat("content://opaque/uri", "MySongNoExt"))
    }

    @Test
    fun formatDuration_formatsCorrectlyWithoutStringFormat() {
        assertEquals("--:--", formatDuration(0L))
        assertEquals("3:00", formatDuration(180_000L))
        assertEquals("1:05", formatDuration(65_000L))
        assertEquals("1:01:05", formatDuration(3_665_000L))
    }
}
