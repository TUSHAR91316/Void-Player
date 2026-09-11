package com.tushar.voidplayer.utils

import com.tushar.voidplayer.model.Song

data class CodecInfo(
    val formatName: String,
    val isLossless: Boolean,
    val isHiRes: Boolean,
    val badgeLabel: String,
    val sampleRateEstimate: String,
    val bitrateEstimate: String
)

object AudioMetadataUtils {

    fun inspectSong(song: Song): CodecInfo {
        val uri = song.uri.lowercase()
        val title = song.title.lowercase()

        return when {
            uri.endsWith(".flac") || title.contains("flac") -> CodecInfo(
                formatName = "FLAC (Free Lossless Audio Codec)",
                isLossless = true,
                isHiRes = true,
                badgeLabel = "Hi-Res FLAC",
                sampleRateEstimate = "Up to 24-bit / 96.0 kHz Lossless",
                bitrateEstimate = "Variable Bitrate (VBR Lossless)"
            )
            uri.endsWith(".wav") || title.contains("wav") -> CodecInfo(
                formatName = "WAV (Linear PCM Audio)",
                isLossless = true,
                isHiRes = true,
                badgeLabel = "Hi-Res WAV",
                sampleRateEstimate = "16/24-bit / 44.1+ kHz PCM",
                bitrateEstimate = "1411.2+ kbps Uncompressed"
            )
            uri.endsWith(".m4a") || uri.endsWith(".aac") -> CodecInfo(
                formatName = "AAC / M4A (Advanced Audio Coding)",
                isLossless = false,
                isHiRes = false,
                badgeLabel = "AAC HD",
                sampleRateEstimate = "44.1 / 48.0 kHz Stereo",
                bitrateEstimate = "128 - 320 kbps VBR"
            )
            uri.endsWith(".ogg") || uri.endsWith(".opus") -> CodecInfo(
                formatName = "OGG / Opus Audio",
                isLossless = false,
                isHiRes = false,
                badgeLabel = "OPUS HD",
                sampleRateEstimate = "48.0 kHz Dynamic Stereo",
                bitrateEstimate = "64 - 256 kbps Adaptive"
            )
            else -> CodecInfo(
                formatName = "MPEG-1 Audio Layer III (MP3)",
                isLossless = false,
                isHiRes = false,
                badgeLabel = "MP3 Audio",
                sampleRateEstimate = "44.1 / 48.0 kHz Stereo",
                bitrateEstimate = "128 - 320 kbps (CBR / VBR)"
            )
        }
    }
}
