package com.tushar.voidplayer.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.audiofx.Equalizer
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.tushar.voidplayer.MainActivity
import com.tushar.voidplayer.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class AndroidAudioPlayer(private val context: Context) : AudioPlayer {

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    // Simple, default ExoPlayer â€” no custom buffers or renderer factories
    // which caused OOM and codec-loading crashes on many devices.
    private val player = ExoPlayer.Builder(context)
        .build()
        .apply {
            setAudioAttributes(audioAttributes, true)
            setHandleAudioBecomingNoisy(true)
            // WAKE_MODE_NONE: no WAKE_LOCK or WifiLock is acquired.
            // WAKE_MODE_LOCAL and WAKE_MODE_NETWORK both require android.permission.WAKE_LOCK
            // which is not declared in AndroidManifest.xml. Using them would throw
            // SecurityException on strict devices. For local file playback, WAKE_MODE_NONE is
            // correct â€” the screen stays on while the user is interacting with the app.
            setWakeMode(C.WAKE_MODE_NONE)
        }

    private val scope = CoroutineScope(Dispatchers.Main)
    
    private var mediaSession: MediaSession? = null
    private var androidEqualizer: Equalizer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var bassBoost: android.media.audiofx.BassBoost? = null
    private var virtualizer: android.media.audiofx.Virtualizer? = null

    private val audioPrefs = context.getSharedPreferences("VoidPlayerAudioSettings", Context.MODE_PRIVATE)

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

    private val _playbackSpeed = MutableStateFlow(1.0f)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    override val currentQueue: StateFlow<List<Song>> = _currentQueue.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(0)
    override val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    private val _currentEqPreset = MutableStateFlow("Flat")
    override val currentEqPreset: StateFlow<String> = _currentEqPreset.asStateFlow()

    private var playlist: List<Song> = emptyList()
    private var progressJob: Job? = null

    init {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        mediaSession = MediaSession.Builder(context, player)
            .setSessionActivity(pendingIntent)
            .build()

        loadSavedAudioSettings()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startProgressUpdate()
                    startPlaybackService()
                } else {
                    stopProgressUpdate()
                }
            }

            // Only attach DSP effects once ExoPlayer has a valid audio session.
            // Attaching with audioSessionId == 0 causes a native SIGSEGV crash
            // in libeffect that bypasses all Kotlin try-catch blocks.
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != 0 && audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    ensureEqualizer()
                    ensureBassBoost()
                    ensureNormalization()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = player.currentMediaItemIndex
                if (index in playlist.indices) {
                    val nextSong = playlist[index]
                    _currentSong.value = nextSong
                    loadArtworkForSong(nextSong)
                }
                if (com.tushar.voidplayer.utils.SleepTimerManager.stopAtEndOfSong) {
                    player.pause()
                    com.tushar.voidplayer.utils.SleepTimerManager.cancel()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _error.value = "Playback Error: ${error.message}"
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    updateCurrentSongMetadata()
                } else if (playbackState == Player.STATE_ENDED) {
                    if (com.tushar.voidplayer.utils.SleepTimerManager.stopAtEndOfSong) {
                        com.tushar.voidplayer.utils.SleepTimerManager.cancel()
                    }
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                updateCurrentSongMetadata()
            }
        })
    }

    private fun updateCurrentSongMetadata() {
        val current = _currentSong.value ?: return
        val duration = if (player.duration != C.TIME_UNSET) player.duration else current.duration
        val artist = player.mediaMetadata.artist?.toString() ?: current.artist
        val title = player.mediaMetadata.title?.toString() ?: current.title
        
        if (duration != current.duration || artist != current.artist || title != current.title) {
            _currentSong.value = current.copy(
                duration = duration,
                artist = if (artist == "Unknown Artist") "Unknown Artist" else artist,
                title = title
            )
        }
    }

    private fun ensureEqualizer() {
        val sessionId = player.audioSessionId
        if (sessionId == 0 || sessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (androidEqualizer != null) return
        
        try {
            androidEqualizer = Equalizer(0, sessionId).apply {
                enabled = true
            }
            updateEqualizerBandsState()
        } catch (e: Throwable) {
            // Thrown on devices without hardware DSP support or on custom ROMs
            // that strip the audio effect libraries. Silently ignore.
            e.printStackTrace()
        }
    }

    private fun updateEqualizerBandsState() {
        val eq = androidEqualizer ?: return
        try {
            val bands = mutableListOf<AudioPlayer.EqualizerBand>()
            val minMax = eq.bandLevelRange
            for (i in 0 until eq.numberOfBands) {
                bands.add(
                    AudioPlayer.EqualizerBand(
                        frequency = eq.getCenterFreq(i.toShort()) / 1000,
                        level = eq.getBandLevel(i.toShort()).toInt(),
                        minLevel = minMax[0].toInt(),
                        maxLevel = minMax[1].toInt()
                    )
                )
            }
            _equalizerBands.value = bands
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun setEqualizerBandLevel(bandIndex: Int, level: Int) {
        ensureEqualizer()
        try {
            androidEqualizer?.let { eq ->
                eq.setBandLevel(bandIndex.toShort(), level.toShort())
                updateEqualizerBandsState()
                _currentEqPreset.value = "Custom"
                saveAudioSettings()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun resetEqualizer() {
        try {
            androidEqualizer?.let { eq ->
                for (i in 0 until eq.numberOfBands) {
                    eq.setBandLevel(i.toShort(), 0)
                }
                updateEqualizerBandsState()
                _currentEqPreset.value = "Flat"
                saveAudioSettings()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun ensureBassBoost() {
        val sessionId = player.audioSessionId
        if (sessionId == 0 || sessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (bassBoost != null) return

        try {
            bassBoost = android.media.audiofx.BassBoost(0, sessionId).apply {
                enabled = true
                if (strengthSupported) {
                    setStrength(_bassBoostStrength.value.toShort())
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun setBassBoostStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _bassBoostStrength.value = clamped
        ensureBassBoost()
        try {
            bassBoost?.let { bb ->
                if (bb.strengthSupported) {
                    bb.setStrength(clamped.toShort())
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        saveAudioSettings()
    }

    override fun applyEqPreset(presetName: String) {
        _currentEqPreset.value = presetName
        ensureEqualizer()
        val eq = androidEqualizer ?: return
        try {
            val numBands = eq.numberOfBands.toInt()
            val minMax = eq.bandLevelRange
            val maxLevel = minMax[1].toInt()
            val minLevel = minMax[0].toInt()

            val factor = maxLevel / 10f

            val levels = when (presetName.lowercase()) {
                "bass boost" -> listOf(7f, 4f, 1f, 0f, -1f)
                "vocal pop" -> listOf(-2f, 2f, 5f, 3f, 1f)
                "electronic" -> listOf(5f, 3f, -1f, 2f, 5f)
                "rock" -> listOf(5f, 3f, -1f, 3f, 5f)
                "acoustic" -> listOf(3f, 2f, 1f, 3f, 2f)
                else -> listOf(0f, 0f, 0f, 0f, 0f) // Flat
            }

            for (i in 0 until numBands) {
                val offset = if (i < levels.size) levels[i] else 0f
                val bandLevel = (offset * factor).toInt().coerceIn(minLevel, maxLevel)
                eq.setBandLevel(i.toShort(), bandLevel.toShort())
            }
            updateEqualizerBandsState()
            saveAudioSettings()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun loadSavedAudioSettings() {
        try {
            val speed = audioPrefs.getFloat("playback_speed", 1.0f)
            _playbackSpeed.value = speed
            player.setPlaybackSpeed(speed)

            val shuffle = audioPrefs.getBoolean("is_shuffle", false)
            _isShuffle.value = shuffle
            player.shuffleModeEnabled = shuffle

            val repeatStr = audioPrefs.getString("repeat_mode", "OFF") ?: "OFF"
            val repeat = try { AudioPlayer.RepeatMode.valueOf(repeatStr) } catch (_: Throwable) { AudioPlayer.RepeatMode.OFF }
            _repeatMode.value = repeat
            player.repeatMode = when (repeat) {
                AudioPlayer.RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                AudioPlayer.RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                AudioPlayer.RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            }

            _isNormalizationEnabled.value = audioPrefs.getBoolean("is_normalization", false)
            _bassBoostStrength.value = audioPrefs.getInt("bass_boost", 0)
            _currentEqPreset.value = audioPrefs.getString("eq_preset", "Flat") ?: "Flat"
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun saveAudioSettings() {
        try {
            audioPrefs.edit()
                .putFloat("playback_speed", _playbackSpeed.value)
                .putBoolean("is_shuffle", _isShuffle.value)
                .putString("repeat_mode", _repeatMode.value.name)
                .putBoolean("is_normalization", _isNormalizationEnabled.value)
                .putInt("bass_boost", _bassBoostStrength.value)
                .putString("eq_preset", _currentEqPreset.value)
                .apply()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun ensureNormalization() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return // DynamicsProcessing requires API 28+
        val sessionId = player.audioSessionId
        if (sessionId == 0 || sessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (dynamicsProcessing != null) return
        
        try {
            val builder = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2, // 2 channels (Stereo)
                false, 0, // no pre-eq
                true, 1,  // multiband compressor (1 band)
                false, 0, // no post-eq
                true      // limiter enabled
            )
            dynamicsProcessing = DynamicsProcessing(0, sessionId, builder.build()).apply {
                enabled = _isNormalizationEnabled.value
            }
        } catch (e: Throwable) {
            // DynamicsProcessing may not exist on older/custom ROM devices even if API >= 28.
            // Silently ignore to preserve playback.
            e.printStackTrace()
        }
    }

    override fun toggleNormalization() {
        val newValue = !_isNormalizationEnabled.value
        _isNormalizationEnabled.value = newValue
        ensureNormalization()
        try {
            dynamicsProcessing?.enabled = newValue
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        saveAudioSettings()
    }

    private val artworkCache = LruCache<Long, ByteArray>(30)

    private fun extractAndScaleArtwork(uriString: String): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uriString))
            val rawArt = retriever.embeddedPicture ?: return null
            downsampleArtwork(rawArt, 512, 512)
        } catch (_: Throwable) {
            null
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    private fun downsampleArtwork(rawArt: ByteArray, maxW: Int, maxH: Int): ByteArray {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(rawArt, 0, rawArt.size, options)
            val w = options.outWidth
            val h = options.outHeight
            if (w <= 0 || h <= 0) return rawArt

            if (w <= maxW && h <= maxH && rawArt.size <= 250 * 1024) {
                return rawArt
            }

            var sampleSize = 1
            while ((w / sampleSize) > maxW || (h / sampleSize) > maxH) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeByteArray(rawArt, 0, rawArt.size, decodeOptions) ?: return rawArt

            val scaledBitmap = if (bitmap.width > maxW || bitmap.height > maxH) {
                val scale = minOf(maxW.toFloat() / bitmap.width, maxH.toFloat() / bitmap.height)
                val destW = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val destH = (bitmap.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, destW, destH, true).also {
                    if (it != bitmap) bitmap.recycle()
                }
            } else {
                bitmap
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            scaledBitmap.recycle()
            return outputStream.toByteArray()
        } catch (_: Throwable) {
            return rawArt
        }
    }

    private fun getArtworkUri(songId: Long, artBytes: ByteArray): Uri? {
        return try {
            val artDir = File(context.cacheDir, "art_cache").apply { if (!exists()) mkdirs() }
            val artFile = File(artDir, "art_${songId}.jpg")
            if (!artFile.exists() || artFile.length() == 0L) {
                artFile.writeBytes(artBytes)
            }
            Uri.fromFile(artFile)
        } catch (_: Throwable) {
            null
        }
    }

    private fun loadArtworkForSong(song: Song) {
        val cached = artworkCache.get(song.id) ?: song.coverArt
        if (cached != null) {
            applyArtwork(song.id, cached)
            return
        }

        scope.launch(Dispatchers.IO) {
            val art = extractAndScaleArtwork(song.uri)
            if (art != null) {
                artworkCache.put(song.id, art)
                withContext(Dispatchers.Main) {
                    if (_currentSong.value?.id == song.id) {
                        applyArtwork(song.id, art)
                    }
                }
            }
        }
    }

    private fun applyArtwork(songId: Long, art: ByteArray) {
        val current = _currentSong.value
        if (current?.id == songId) {
            _currentSong.value = current.copy(coverArt = art)
        }
        val idx = playlist.indexOfFirst { it.id == songId }
        if (idx != -1) {
            val updatedSong = playlist[idx].copy(coverArt = art)
            playlist = playlist.toMutableList().apply { set(idx, updatedSong) }
        }

        try {
            val currentItem = player.currentMediaItem
            if (currentItem != null && currentItem.mediaId == songId.toString()) {
                val artUri = getArtworkUri(songId, art)
                val updatedMetadata = currentItem.mediaMetadata.buildUpon()
                    .setTitle(current?.title ?: currentItem.mediaMetadata.title)
                    .setArtist(current?.artist ?: currentItem.mediaMetadata.artist)
                    .setAlbumTitle(current?.album ?: currentItem.mediaMetadata.albumTitle)
                    .setArtworkData(art, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    .apply {
                        if (artUri != null) setArtworkUri(artUri)
                    }
                    .build()

                val updatedMediaItem = currentItem.buildUpon()
                    .setMediaMetadata(updatedMetadata)
                    .build()

                player.replaceMediaItem(player.currentMediaItemIndex, updatedMediaItem)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun updateSongArt(songId: Long, art: ByteArray) {
        val safeArt = downsampleArtwork(art, 512, 512)
        artworkCache.put(songId, safeArt)
        applyArtwork(songId, safeArt)
    }

    override fun playNext(song: Song) {
        val current = _currentSong.value
        val currentIdx = if (current != null) playlist.indexOfFirst { it.id == current.id } else -1
        val insertIdx = if (currentIdx >= 0) currentIdx + 1 else 0

        val newPlaylist = playlist.toMutableList()
        val existingIdx = newPlaylist.indexOfFirst { it.id == song.id }
        if (existingIdx != -1) {
            newPlaylist.removeAt(existingIdx)
        }
        val targetIdx = if (existingIdx in 0..insertIdx && insertIdx > 0) insertIdx - 1 else insertIdx
        newPlaylist.add(targetIdx.coerceIn(0, newPlaylist.size), song)

        playlist = newPlaylist
        _currentQueue.value = newPlaylist

        try {
            val cachedArt = artworkCache.get(song.id) ?: song.coverArt
            val artDir = File(context.cacheDir, "art_cache")
            val artFile = File(artDir, "art_${song.id}.jpg")
            val artUri = if (artFile.exists() && artFile.length() > 0) Uri.fromFile(artFile) else null

            val mediaItem = MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setArtworkData(cachedArt, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        .apply {
                            if (artUri != null) setArtworkUri(artUri)
                        }
                        .build()
                )
                .build()
            if (existingIdx != -1) {
                player.removeMediaItem(existingIdx)
            }
            player.addMediaItem(targetIdx.coerceIn(0, player.mediaItemCount), mediaItem)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun startPlaybackService() {
        try {
            val intent = Intent(context, PlaybackService::class.java)
            context.startService(intent)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun getMediaSession(): MediaSession? = mediaSession

    override fun setPlaylist(songs: List<Song>) {
        this.playlist = songs
        _currentQueue.value = songs
        val artDir = File(context.cacheDir, "art_cache")
        val mediaItems = songs.map { song ->
            val cachedArt = artworkCache.get(song.id) ?: song.coverArt
            val artFile = File(artDir, "art_${song.id}.jpg")
            val artUri = if (artFile.exists() && artFile.length() > 0) Uri.fromFile(artFile) else null

            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setArtworkData(cachedArt, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        .apply {
                            if (artUri != null) setArtworkUri(artUri)
                        }
                        .build()
                )
                .build()
        }
        player.setMediaItems(mediaItems)
        player.prepare()
        
        if (_currentSong.value == null && songs.isNotEmpty()) {
            _currentSong.value = songs[0]
            loadArtworkForSong(songs[0])
        }
    }

    override fun play(song: Song) {
        val index = playlist.indexOfFirst { it.id == song.id }
        if (index != -1) {
            _currentSong.value = song
            player.seekTo(index, 0)
            player.play()
        } else {
            setPlaylist(listOf(song))
            _currentSong.value = song
            player.play()
        }
        startPlaybackService()
        loadArtworkForSong(song)
    }

    override fun setPlaybackSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.25f, 3.0f)
        _playbackSpeed.value = safeSpeed
        try {
            player.setPlaybackSpeed(safeSpeed)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        saveAudioSettings()
    }

    override fun setVolume(volume: Float) {
        try {
            player.volume = volume.coerceIn(0f, 1f)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun pause() { player.pause() }
    override fun resume() { player.play() }

    override fun next() {
        if (player.hasNextMediaItem()) {
            player.seekToNext()
        }
    }

    override fun previous() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPrevious()
        }
    }

    override fun toggleShuffle() {
        val newValue = !_isShuffle.value
        _isShuffle.value = newValue
        player.shuffleModeEnabled = newValue
        saveAudioSettings()
    }

    override fun toggleRepeat() {
        val nextMode = when (_repeatMode.value) {
            AudioPlayer.RepeatMode.OFF -> AudioPlayer.RepeatMode.ALL
            AudioPlayer.RepeatMode.ALL -> AudioPlayer.RepeatMode.ONE
            AudioPlayer.RepeatMode.ONE -> AudioPlayer.RepeatMode.OFF
        }
        _repeatMode.value = nextMode
        player.repeatMode = when (nextMode) {
            AudioPlayer.RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            AudioPlayer.RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            AudioPlayer.RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        saveAudioSettings()
    }

    override fun seekTo(position: Long) {
        player.seekTo(position)
        _currentPosition.value = player.currentPosition
    }

    override fun seekForward(millis: Long) {
        val newPos = (player.currentPosition + millis).coerceAtMost(player.duration)
        seekTo(newPos)
    }

    override fun seekBackward(millis: Long) {
        val newPos = (player.currentPosition - millis).coerceAtLeast(0L)
        seekTo(newPos)
    }

    private fun startProgressUpdate() {
        stopProgressUpdate()
        progressJob = scope.launch {
            while (isActive) {
                _currentPosition.value = player.currentPosition
                delay(250)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
        progressJob = null
    }

    override fun cleanUp() {
        mediaSession?.release()
        mediaSession = null
        try {
            androidEqualizer?.release()
        } catch (e: Throwable) { e.printStackTrace() }
        androidEqualizer = null
        try {
            bassBoost?.release()
        } catch (e: Throwable) { e.printStackTrace() }
        bassBoost = null
        try {
            virtualizer?.release()
        } catch (e: Throwable) { e.printStackTrace() }
        virtualizer = null
        try {
            dynamicsProcessing?.release()
        } catch (e: Throwable) { e.printStackTrace() }
        dynamicsProcessing = null
        player.release()
        stopProgressUpdate()
    }
}
