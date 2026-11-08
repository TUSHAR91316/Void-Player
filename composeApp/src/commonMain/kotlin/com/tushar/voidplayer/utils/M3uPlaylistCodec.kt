package com.tushar.voidplayer.utils

import com.tushar.voidplayer.model.Song

data class M3uEntry(
    val location: String,
    val title: String = "",
    val durationSeconds: Long? = null
)

/** UTF-8 M3U/M3U8 reader/writer shared by playlist import and export flows. */
object M3uPlaylistCodec {
    private val playlistExtension = Regex("(?i)\\.m3u8?$")
    private val audioExtension = Regex("(?i)\\.(mp3|flac|m4a|aac|ogg|opus|wav|wma|alac|aiff?)$")
    private val invalidFileNameChars = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    fun parse(text: String): List<M3uEntry> {
        val entries = mutableListOf<M3uEntry>()
        var nextTitle = ""
        var nextDuration: Long? = null
        text.removePrefix("﻿").lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val payload = line.substringAfter(':')
                    val comma = payload.indexOf(',')
                    if (comma >= 0) {
                        nextDuration = payload.substring(0, comma).toDoubleOrNull()
                            ?.takeIf { it >= 0.0 }?.toLong()
                        nextTitle = payload.substring(comma + 1).trim()
                    }
                }
                line.startsWith('#') -> Unit
                else -> {
                    entries += M3uEntry(line, nextTitle, nextDuration)
                    nextTitle = ""
                    nextDuration = null
                }
            }
        }
        return entries
    }

    fun encode(entries: Iterable<M3uEntry>): String = buildString {
        appendLine("#EXTM3U")
        entries.forEach { entry ->
            val duration = entry.durationSeconds?.coerceAtLeast(0L) ?: -1L
            appendLine("#EXTINF:${duration},${entry.title.replace('\n', ' ').replace('\r', ' ').trim()}")
            appendLine(entry.location.trim())
        }
    }

    fun encodeSongs(songs: Iterable<Song>): String = encode(songs.map { song ->
        M3uEntry(song.uri, song.title, (song.duration / 1000L).takeIf { it > 0L })
    })

    /**
     * Match playlist entries to library songs without reordering the playlist.
     *
     * Order of preference: exact URI, unique file name, unique `#EXTINF` title (also in the
     * common "Artist - Title" form), then a unique file base name equal to a song title.
     * Ambiguous matches are skipped rather than guessed.
     */
    fun resolve(entries: Iterable<M3uEntry>, songs: List<Song>): List<Song> {
        val byUri = songs.associateBy { it.uri }
        val byTitle = songs.groupBy { normalize(it.title) }
        val byArtistTitle = songs.groupBy { normalize("${it.artist} - ${it.title}") }
        val byFileName = songs.groupBy { fileNameOf(it.uri).lowercase() }
            .filterKeys { audioExtension.containsMatchIn(it) }
        return entries.mapNotNull { entry ->
            byUri[entry.location]?.let { return@mapNotNull it }
            val fileName = fileNameOf(entry.location).lowercase()
            byFileName[fileName]?.singleOrNull()?.let { return@mapNotNull it }
            if (entry.title.isNotBlank()) {
                val title = normalize(entry.title)
                (byTitle[title]?.singleOrNull() ?: byArtistTitle[title]?.singleOrNull())
                    ?.let { return@mapNotNull it }
            }
            val baseName = normalize(fileName.replace(audioExtension, ""))
            if (baseName.isBlank()) null
            else byTitle[baseName]?.singleOrNull() ?: byArtistTitle[baseName]?.singleOrNull()
        }
    }

    /** "Road Trip.m3u8" -> "Road Trip". Dots inside the name are kept. */
    fun playlistNameFromFileName(fileName: String): String =
        fileName.substringAfterLast('/').substringAfterLast('\\').replace(playlistExtension, "").trim()

    /** A safe export file name: "Mr. Brightside mix" -> "Mr. Brightside mix.m3u8". */
    fun exportFileName(playlistName: String): String {
        val base = playlistName.replace(playlistExtension, "").replace(invalidFileNameChars, "_").trim().trim('.')
        return "${base.ifBlank { "Playlist" }.take(100)}.m3u8"
    }

    /** Last path segment of a path, file URL or SAF document URI, percent-decoded. */
    internal fun fileNameOf(location: String): String {
        val decoded = percentDecode(location.substringBefore('?').substringBefore('#'))
        return decoded.substringAfterLast('/').substringAfterLast('\\').substringAfterLast(':')
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun percentDecode(value: String): String {
        if ('%' !in value) return value
        val bytes = ArrayList<Byte>(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            val high = if (char == '%' && index + 2 < value.length) value[index + 1].digitToIntOrNull(16) else null
            val low = if (high != null) value[index + 2].digitToIntOrNull(16) else null
            if (high != null && low != null) {
                bytes += ((high shl 4) or low).toByte()
                index += 3
            } else {
                char.toString().encodeToByteArray().forEach { bytes += it }
                index++
            }
        }
        return bytes.toByteArray().decodeToString()
    }
}
