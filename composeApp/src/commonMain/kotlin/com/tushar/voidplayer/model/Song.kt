package com.tushar.voidplayer.model

/**
 * Represents a single audio track.
 *
 * Equality is the generated data-class equality over every field, so a copy with a
 * toggled [isFavorite], lazily loaded [coverArt] or refined [duration] is a *different*
 * value. This matters: Compose `mutableStateOf` and `MutableStateFlow` drop writes that
 * are equal to the current value, so identity-only equality silently swallowed those
 * updates. [coverArt] is compared by reference, which is cheap and correct because new
 * artwork always arrives as a new array.
 *
 * To ask "is this the same physical file?", use [isSameTrack]. The [LazyColumn] key uses
 * [id], which is stable per track.
 *
 * For SAF-loaded files, [id] is a negative 64-bit SHA-256-derived URI identifier.
 * For MediaStore-loaded files, [id] is the positive MediaStore row ID.
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
    val musicalKey: String = "",
    val acousticEnergy: Float = 0.5f,
    val dominantMood: String = ""
) {
    /** True when both values refer to the same underlying file, regardless of metadata. */
    fun isSameTrack(other: Song?): Boolean = other != null && id == other.id && uri == other.uri
}
