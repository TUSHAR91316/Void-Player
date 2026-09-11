package com.tushar.voidplayer.player

import com.tushar.voidplayer.model.Song
import kotlinx.coroutines.flow.StateFlow

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

    fun play(song: Song)
    fun playNext(song: Song)
    fun setPlaylist(songs: List<Song>)
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
    fun setBassBoostStrength(strength: Int)
    fun toggleNormalization()
    fun updateSongArt(songId: Long, art: ByteArray)
    fun setPlaybackSpeed(speed: Float)
    fun setVolume(volume: Float)

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
