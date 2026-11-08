package com.tushar.voidplayer.utils

import com.tushar.voidplayer.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AudioMetadataUtilsTest {

    @Test
    fun estimatesFlacAsLossless() {
        val song = Song(101L, "Lossless Song", "Artist", duration = 180_000L, uri = "content://media/audio/song.flac")
        val info = AudioMetadataUtils.inspectSong(song)
        assertTrue(info.isLossless)
        assertTrue(info.formatName.contains("FLAC", ignoreCase = true))
    }

    @Test
    fun estimatesWavAsLossless() {
        val song = Song(102L, "Wav Song", "Artist", duration = 180_000L, uri = "content://media/audio/sample.wav")
        val info = AudioMetadataUtils.inspectSong(song)
        assertTrue(info.isLossless)
        assertTrue(info.formatName.contains("WAV", ignoreCase = true))
    }

    @Test
    fun estimatesLossyFormatsCorrectly() {
        val formats = mapOf(
            "song.mp3" to "MP3",
            "song.m4a" to "AAC",
            "song.aac" to "AAC",
            "song.ogg" to "OGG",
            "song.opus" to "Opus"
        )
        for ((file, expectedFormat) in formats) {
            val song = Song(file.hashCode().toLong(), "Title", "Artist", duration = 120_000L, uri = "file:///music/$file")
            val info = AudioMetadataUtils.inspectSong(song)
            assertFalse(info.isLossless, "$file should be lossy")
            assertTrue(info.formatName.contains(expectedFormat, ignoreCase = true), "Expected format $expectedFormat for $file")
        }
    }

    @Test
    fun handlesUnknownExtensionGracefully() {
        val song = Song(103L, "Mystery", "Artist", duration = 60_000L, uri = "file:///music/audiofile.unknownext")
        val info = AudioMetadataUtils.inspectSong(song)
        assertFalse(info.isLossless)
        assertTrue(info.formatName.contains("Unknown", ignoreCase = true))
    }

    @Test
    fun handlesUriWithQueryParamsAndNoExtension() {
        val song = Song(104L, "Stream", "Artist", duration = 60_000L, uri = "https://example.com/stream?id=123")
        val info = AudioMetadataUtils.inspectSong(song)
        assertFalse(info.isLossless)
        assertTrue(info.formatName.contains("Unknown", ignoreCase = true))
    }

    @Test
    fun returnsCachedCodecInfoOnSubsequentLookups() {
        val song = Song(105L, "Cached", "Artist", duration = 120_000L, uri = "file:///music/cached.mp3")
        val first = AudioMetadataUtils.inspectSong(song)
        val second = AudioMetadataUtils.inspectSong(song)
        assertSame(first, second, "AudioMetadataUtils must return identical cached instance for the same song ID")
    }
}

