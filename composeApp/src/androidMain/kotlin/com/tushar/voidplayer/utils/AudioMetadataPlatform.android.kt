package com.tushar.voidplayer.utils

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.tushar.voidplayer.VoidPlayerApp
import com.tushar.voidplayer.model.Song

actual fun inspectPlatformAudioMetadata(song: Song): CodecInfo? {
    val context = try { VoidPlayerApp.instance } catch (_: Throwable) { return null }
    val extractor = MediaExtractor()
    val retriever = MediaMetadataRetriever()
    try {
        val uri = Uri.parse(song.uri)
        extractor.setDataSource(context, uri, null)
        var audioFormat: MediaFormat? = null
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioFormat = format
                break
            }
        }
        val format = audioFormat ?: return null
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        val sampleRate = format.readInt(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.readInt(MediaFormat.KEY_CHANNEL_COUNT)
        val bitrate = format.readInt(MediaFormat.KEY_BIT_RATE)
        retriever.setDataSource(context, uri)
        val bits = if (Build.VERSION.SDK_INT >= 31) {
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() ?: 0
        } else 0
        val formatName = mime.removePrefix("audio/").uppercase()
        val lossless = mime.contains("flac") || mime.contains("alac") || mime.contains("wav") || mime.contains("raw") || mime.contains("pcm")
        val isHiRes = bits >= 24 && sampleRate >= 88_200
        val badge = when {
            isHiRes -> "Hi-Res $formatName"
            bits > 0 && sampleRate > 0 -> "$formatName · ${bits}-bit / ${sampleRate / 1000.0} kHz"
            else -> "$formatName · Measured"
        }
        return CodecInfo(
            formatName = formatName,
            isLossless = lossless,
            isHiRes = isHiRes,
            badgeLabel = badge,
            sampleRateEstimate = if (sampleRate > 0) "${sampleRate} Hz measured${if (channels > 0) " · $channels ch" else ""}" else "Unavailable (estimated)",
            bitrateEstimate = if (bitrate > 0) "${bitrate / 1000} kbps measured" else "Unavailable (estimated)"
        )
    } catch (_: Throwable) {
        return null
    } finally {
        try { extractor.release() } catch (_: Throwable) { }
        try { retriever.release() } catch (_: Throwable) { }
    }
}

private fun MediaFormat.readInt(key: String): Int = try {
    if (containsKey(key)) getInteger(key) else 0
} catch (_: Throwable) { 0 }
