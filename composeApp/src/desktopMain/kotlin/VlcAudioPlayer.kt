import com.tushar.voidplayer.model.Song
import com.tushar.voidplayer.player.AudioPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.Equalizer
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent

class VlcAudioPlayer : AudioPlayer {

    private val audioComponent = AudioPlayerComponent()
    private var factory = MediaPlayerFactory("--no-video")
    private var player = factory.mediaPlayers().newMediaPlayer()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    override val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    override val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(AudioPlayer.RepeatMode.OFF)
    override val repeatMode: StateFlow<AudioPlayer.RepeatMode> = _repeatMode.asStateFlow()

    private val _equalizerBands = MutableStateFlow<List<AudioPlayer.EqualizerBand>>(emptyList())
    override val equalizerBands: StateFlow<List<AudioPlayer.EqualizerBand>> = _equalizerBands.asStateFlow()

    private val _isNormalizationEnabled = MutableStateFlow(false)
    override val isNormalizationEnabled: StateFlow<Boolean> = _isNormalizationEnabled.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    override val currentQueue: StateFlow<List<Song>> = _currentQueue.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // Internal State
    private var vlcEqualizer: Equalizer? = null
    private var originalQueue = listOf<Song>()
    private var currentIndex = -1

    init {
        setupEqualizer()
        setupEventListeners()
    }

    private fun setupEventListeners() {
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                _isPlaying.value = true
                _error.value = null
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                _isPlaying.value = false
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                _isPlaying.value = false
                _currentPosition.value = 0L
            }

            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                _currentPosition.value = newTime
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                when (_repeatMode.value) {
                    AudioPlayer.RepeatMode.ONE -> {
                        _currentSong.value?.let { play(it) }
                    }
                    AudioPlayer.RepeatMode.ALL, AudioPlayer.RepeatMode.OFF -> {
                        next()
                    }
                }
            }

            override fun error(mediaPlayer: MediaPlayer) {
                _isPlaying.value = false
                _error.value = "VLC encountered an error playing this media."
            }
        })
    }

    private fun setupEqualizer() {
        val factory = audioComponent.mediaPlayerFactory()
        val eq = factory.equalizer().newEqualizer()

        if (eq != null) {
            vlcEqualizer = eq
            player.audio().setEqualizer(eq)

            // Map VLC's native equalizer bands to the Compose state
            val bands = mutableListOf<AudioPlayer.EqualizerBand>()
            val amps = eq.amps()

            for (i in 0 until eq.bandCount()) {
                // Note: Exact frequency metadata isn't directly exposed by basic VLCJ eq amps,
                // so we use standard 10-band ISO frequencies as a fallback approximation.
                val frequencies = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
                if (i < frequencies.size && i < amps.size) {
                    bands.add(
                        AudioPlayer.EqualizerBand(
                            frequency = frequencies[i],
                            level = (amps[i] * 100).toInt(), // Convert VLC 5.0f to 500
                            minLevel = -2000,                // -20.0 dB
                            maxLevel = 2000                  // +20.0 dB
                        )
                    )
                }
            }
            _equalizerBands.value = bands
        }
    }

    override fun play(song: Song) {
        _currentSong.value = song
        player.media().play(song.uri)
    }

    override fun setPlaylist(songs: List<Song>) {
        originalQueue = songs.toList()
        applyQueueState()

        if (_currentQueue.value.isNotEmpty()) {
            currentIndex = 0
            play(_currentQueue.value[0])
        }
    }

    private fun applyQueueState() {
        _currentQueue.value = if (_isShuffle.value) {
            originalQueue.shuffled()
        } else {
            originalQueue.toList()
        }

        // Fix index if song is currently playing so next/prev works correctly
        val current = _currentSong.value
        currentIndex = if (current != null) {
            _currentQueue.value.indexOf(current).takeIf { it >= 0 } ?: -1
        } else {
            -1
        }
    }

    override fun pause() {
        player.controls().pause()
    }

    override fun resume() {
        player.controls().play()
    }

    override fun next() {
        if (_currentQueue.value.isEmpty()) return

        currentIndex++
        if (currentIndex >= _currentQueue.value.size) {
            if (_repeatMode.value == AudioPlayer.RepeatMode.ALL) {
                currentIndex = 0
            } else {
                currentIndex = _currentQueue.value.size - 1
                player.controls().stop()
                return
            }
        }
        play(_currentQueue.value[currentIndex])
    }

    override fun previous() {
        if (_currentQueue.value.isEmpty()) return

        // If we are more than 3 seconds into the song, restart it instead of going back
        if (_currentPosition.value > 3000) {
            seekTo(0)
            return
        }

        currentIndex--
        if (currentIndex < 0) {
            currentIndex = if (_repeatMode.value == AudioPlayer.RepeatMode.ALL) {
                _currentQueue.value.size - 1
            } else {
                0
            }
        }
        play(_currentQueue.value[currentIndex])
    }

    override fun toggleShuffle() {
        _isShuffle.update { !it }
        applyQueueState()
    }

    override fun toggleRepeat() {
        _repeatMode.update { current ->
            when (current) {
                AudioPlayer.RepeatMode.OFF -> AudioPlayer.RepeatMode.ALL
                AudioPlayer.RepeatMode.ALL -> AudioPlayer.RepeatMode.ONE
                AudioPlayer.RepeatMode.ONE -> AudioPlayer.RepeatMode.OFF
            }
        }
    }

    override fun seekTo(position: Long) {
        player.controls().setTime(position)
    }

    override fun seekForward(millis: Long) {
        player.controls().skipTime(millis)
    }

    override fun seekBackward(millis: Long) {
        player.controls().skipTime(-millis)
    }

    override fun setEqualizerBandLevel(bandIndex: Int, level: Int) {
        vlcEqualizer?.let { eq ->
            val vlcAmp = level / 100f
            eq.setAmp(bandIndex, vlcAmp)
            player.audio().setEqualizer(eq)

            val currentBands = _equalizerBands.value.toMutableList()
            if (bandIndex in currentBands.indices) {
                currentBands[bandIndex] = currentBands[bandIndex].copy(level = level)
                _equalizerBands.value = currentBands
            }
        }
    }

    override fun resetEqualizer() {
        setupEqualizer() // Reloads default flat preset
    }

    override fun toggleNormalization() {
        val willEnable = !_isNormalizationEnabled.value
        _isNormalizationEnabled.value = willEnable

        rebuildPlayerWithNormalization(willEnable)
    }

    private fun rebuildPlayerWithNormalization(enable: Boolean) {
        val wasPlaying = _isPlaying.value
        val currentSong = _currentSong.value
        val currentVolume = player.audio().volume()
        val currentRate = _playbackSpeed.value
        val currentPosition = _currentPosition.value

        player.controls().stop()
        player.release()
        factory.release()

        val args = if (enable) {
            arrayOf(
                "--no-video",
                "--audio-filter=normvol", // Enable VLC's normalizer
                "--norm-buff-size=10",    // Number of audio buffers to calculate average volume (may want to make these settings?)
                "--norm-max-level=2.0"    // Max volume amplification multiplier (may want to make these settings?)
            )
        } else {
            arrayOf("--no-video")
        }

        factory = MediaPlayerFactory(*args)
        player = factory.mediaPlayers().newMediaPlayer()

        setupEventListeners()

        vlcEqualizer?.let { eq ->
            player.audio().setEqualizer(eq)
        }

        player.audio().setVolume(currentVolume)
        player.controls().setRate(currentRate)

        if (currentSong != null) {
            val startSeconds = currentPosition / 1000f
            val startTimeOption = ":start-time=$startSeconds"

            if (wasPlaying) {
                player.media().play(currentSong.uri, startTimeOption)
            } else {
                player.media().prepare(currentSong.uri, startTimeOption)
            }
        }
    }

    override fun updateSongArt(songId: Long, art: ByteArray) {}

    override fun setPlaybackSpeed(speed: Float) {
        player.controls().setRate(speed)
        _playbackSpeed.value = speed
    }

    override fun setVolume(volume: Float) {
        val vlcVolume = (volume * 100).toInt().coerceIn(0, 150)
        player.audio().setVolume(vlcVolume)
    }

    override fun cleanUp() {
        player.controls().stop()
        player.release()
        factory.release()
    }
}