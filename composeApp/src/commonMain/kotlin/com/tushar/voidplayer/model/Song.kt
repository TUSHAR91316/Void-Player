package com.tushar.voidplayer.model

/**
 * Represents a single audio track.
 *
 * Identity is based solely on [id] and [uri] so that the same physical file
 * is always considered equal regardless of metadata changes (e.g. favourites toggle,
 * cover-art lazy-load). The [LazyColumn] key uses [id] which is stable per track.
 *
 * For SAF-loaded files, [id] is derived from the URI string hash (guaranteed positive).
 * For MediaStore-loaded files, [id] is the MediaStore row ID (always positive).
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String = "Unknown Album",
    val duration: Long,
    val uri: String,
    val coverArt: ByteArray? = null,
    val isFavorite: Boolean = false,
    val genre: String = "",
    val bpm: Int = 0,
    val acousticEnergy: Float = 0.5f,
    val dominantMood: String = ""
) {
    // Equality and hash are based on identity (id + uri) only.
    // Metadata fields like isFavorite are intentionally excluded so that
    // LazyColumn item animations and playlist lookups remain stable.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Song) return false
        if (id != other.id) return false
        if (uri != other.uri) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + uri.hashCode()
        return result
    }
}
