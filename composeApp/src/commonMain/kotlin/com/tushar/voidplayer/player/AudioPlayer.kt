package com.tushar.voidplayer.player

import com.tushar.voidplayer.model.Song
import kotlinx.coroutines.flow.StateFlow

data class EqPreset(val id: String, val name: String, val bandsDb: List<Float>, val isBuiltIn: Boolean = false)

interface AudioPlayer {
    val isPlaying: StateFlow<Boolean>
    val currentPosition: StateFlow<Long>
    val currentSong: StateFlow<Song?>
    val error: StateFlow<String?>
    val isShuffle: StateFlow<Boolean>
    val repeatMode: StateFlow<RepeatMode>
    val equalizerBands: StateFlow<List<EqualizerBand>>
    val isNormalizationEnabled: StateFlow<Boolean>
    val playbackSpeed: StateFlow<Float>
    val currentQueue: StateFlow<List<Song>>
    val bassBoostStrength: StateFlow<Int>
    val currentEqPreset: StateFlow<String>
    val isAiDjFlowEnabled: StateFlow<Boolean>
    val customEqPresets: StateFlow<List<EqPreset>>

    fun play(song: Song)
    /** Replace the queue with the visible list and start the selected song from it. */
    fun playFromList(songs: List<Song>, startSong: Song) {
        setPlaylist(songs)
        play(startSong)
    }
    fun playNext(song: Song)
    fun addToQueue(song: Song)
    fun removeFromQueue(song: Song)
    fun moveQueueItem(fromIndex: Int, toIndex: Int)
    fun setPlaylist(songs: List<Song>)
    /** Restore the last queue as paused after the current library has loaded. */
    fun restoreLastPlayback(songs: List<Song>) {}
    fun pause()
    fun resume()
    fun next()
    fun previous()
    fun toggleShuffle()
    fun toggleRepeat()
    fun seekTo(position: Long)
    fun seekForward(millis: Long = 10000)
    fun seekBackward(millis: Long = 10000)
    fun cleanUp()
    fun setEqualizerBandLevel(bandIndex: Int, level: Int)
    fun resetEqualizer()
    fun applyEqPreset(presetName: String)
    fun saveCustomEqPreset(name: String)
    fun deleteCustomEqPreset(id: String)
    fun renameCustomEqPreset(id: String, name: String)
    fun exportCustomEqPresets(): String
    fun importCustomEqPresets(json: String): Boolean
    fun setBassBoostStrength(strength: Int)
    fun toggleNormalization()
    fun updateSongArt(songId: Long, art: ByteArray)
    /** Refresh in-memory library and session metadata after a successful tag edit. */
    fun updateSongMetadata(song: Song) {}
    fun setPlaybackSpeed(speed: Float)
    fun setVolume(volume: Float)

    /** When enabled, the track after the current one is chosen by AI DJ Smart Flow. */
    fun setAiDjFlowEnabled(enabled: Boolean)

    enum class RepeatMode {
        OFF, ONE, ALL
    }

    data class EqualizerBand(
        val frequency: Int,
        val level: Int,
        val minLevel: Int,
        val maxLevel: Int
    )
}
