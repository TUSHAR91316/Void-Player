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

    private const val CACHE_LIMIT = 256
    private val cache = object : LinkedHashMap<Long, CodecInfo>(CACHE_LIMIT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, CodecInfo>?): Boolean = size > CACHE_LIMIT
    }

    fun inspectSong(song: Song): CodecInfo {
        synchronized(cache) { cache[song.id]?.let { return it } }
        val info = inspectPlatformAudioMetadata(song) ?: estimateFromUri(song.uri)
        synchronized(cache) { cache[song.id] = info }
        return info
    }

    private fun estimateFromUri(uri: String): CodecInfo {
        val extension = uri.substringBefore('?').substringAfterLast('/', "").substringAfterLast('.', "").lowercase()
        val format = when (extension) {
            "flac" -> "FLAC"
            "wav" -> "WAV"
            "m4a", "aac" -> "AAC"
            "ogg" -> "OGG"
            "opus" -> "Opus"
            "mp3" -> "MP3"
            else -> "Unknown audio"
        }
        val lossless = format in setOf("FLAC", "WAV")
        return CodecInfo(
            formatName = "$format (estimated)",
            isLossless = lossless,
            isHiRes = false,
            badgeLabel = "$format · Estimated",
            sampleRateEstimate = "Unavailable (estimated)",
            bitrateEstimate = "Unavailable (estimated)"
        )
    }
}

expect fun inspectPlatformAudioMetadata(song: Song): CodecInfo?
