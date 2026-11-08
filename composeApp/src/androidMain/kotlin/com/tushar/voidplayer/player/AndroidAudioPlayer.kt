package com.tushar.voidplayer.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.audiofx.Equalizer
import android.media.audiofx.DynamicsProcessing
import android.os.Build
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AndroidAudioPlayer(private val context: Context) : AudioPlayer {

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    // Use the default ExoPlayer configuration without custom buffers or renderers.
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
            // Keep the screen on while the user is interacting with the app.
            setWakeMode(C.WAKE_MODE_NONE)
        }

    private val scope = CoroutineScope(Dispatchers.Main)
    
    private var mediaSession: MediaSession? = null
    private var androidEqualizer: Equalizer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var bassBoost: android.media.audiofx.BassBoost? = null
    private var virtualizer: android.media.audiofx.Virtualizer? = null

    private val audioPrefs = context.getSharedPreferences("VoidPlayerAudioSettings", Context.MODE_PRIVATE)
    private val listeningStats = loadListeningStats()
    private val _customEqPresets = MutableStateFlow(loadCustomEqPresets())
    override val customEqPresets: StateFlow<List<com.tushar.voidplayer.player.EqPreset>> = _customEqPresets.asStateFlow()

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

    private val _isAiDjFlowEnabled = MutableStateFlow(false)
    override val isAiDjFlowEnabled: StateFlow<Boolean> = _isAiDjFlowEnabled.asStateFlow()

    private var playlist: List<Song> = emptyList()
    private var progressJob: Job? = null
    private var aiDjJob: Job? = null
    private var lastSnapshotAtMs = 0L

    // Most recent last; AI DJ avoids picking these again while alternatives exist.
    private val playHistory = ArrayDeque<Long>()

    // The track whose playback actually started, used for listening stats. It differs from
    // _currentSong during setPlaylist(), which briefly points at the first queue item
    // before play() seeks to the chosen song.
    private var activeTrackId: Long? = null
    // Last position polled for the active track. ExoPlayer has already moved to the next
    // item when onMediaItemTransition runs, so player.currentPosition is no longer useful.
    private var lastPolledPositionMs = 0L
    private var restoringPlayback = false

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
                    savePlaybackSnapshot()
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
                val endedNaturally = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                val previousId = _currentSong.value?.id
                val index = player.currentMediaItemIndex
                if (index in playlist.indices) {
                    val nextSong = playlist[index]
                    _currentSong.value = nextSong
                    // The artwork replaceMediaItem reports PLAYLIST_CHANGED for the same
                    // track; reloading art for it would only trigger another replace.
                    if (nextSong.id != previousId) loadArtworkForSong(nextSong)
                    savePlaybackSnapshot()
                    // PLAYLIST_CHANGED also fires for queue edits and for setPlaylist()
                    // before play() seeks, neither of which is a new track starting.
                    if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                        onTrackStarted(endedNaturally)
                    }
                }
                // Only a song that finished on its own ends "stop at end of song";
                // a manual skip (REASON_SEEK) must keep playing.
                if (endedNaturally && com.tushar.voidplayer.utils.SleepTimerManager.stopAtEndOfSong) {
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
                    activeTrackId?.let { recordListening(it, completion = true) }
                    activeTrackId = null
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
            applySavedEqualizerSettings(androidEqualizer!!)
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
            for (i in 0 until eq.numberOfBands.toInt()) {
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

    /** Restore the selected preset or the user's custom curve for this audio session. */
    private fun applySavedEqualizerSettings(eq: Equalizer) {
        try {
            val bandCount = eq.numberOfBands.toInt()
            if (bandCount <= 0) return
            val minMax = eq.bandLevelRange
            val minLevel = minMax[0].toInt()
            val maxLevel = minMax[1].toInt()
            val presetName = _currentEqPreset.value

            if (presetName.equals("Custom", ignoreCase = true)) {
                val saved = audioPrefs.getString(EQ_BAND_LEVELS_KEY, null)
                    ?.split(',')
                    ?.mapNotNull { it.toIntOrNull() }
                    .orEmpty()
                val levels = if (saved.isEmpty()) List(bandCount) { 0 } else resampleBandLevels(saved, bandCount)
                levels.forEachIndexed { index, level ->
                    eq.setBandLevel(index.toShort(), level.coerceIn(minLevel, maxLevel).toShort())
                }
            } else {
                val levels = presetLevels(presetName)
                val factor = maxLevel / 10f
                for (index in 0 until bandCount) {
                    val offset = levels.getOrElse(index) { 0f }
                    val level = (offset * factor).toInt().coerceIn(minLevel, maxLevel)
                    eq.setBandLevel(index.toShort(), level.toShort())
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    /** Map a saved curve to the current device's band count by linear interpolation. */
    private fun resampleBandLevels(levels: List<Int>, targetCount: Int): List<Int> {
        if (targetCount <= 0) return emptyList()
        if (levels.size == targetCount) return levels
        if (levels.size == 1) return List(targetCount) { levels.first() }
        return List(targetCount) { targetIndex ->
            val position = if (targetCount == 1) 0f else targetIndex * (levels.lastIndex.toFloat() / (targetCount - 1))
            val leftIndex = position.toInt()
            val rightIndex = (leftIndex + 1).coerceAtMost(levels.lastIndex)
            val fraction = position - leftIndex
            (levels[leftIndex] + (levels[rightIndex] - levels[leftIndex]) * fraction).toInt()
        }
    }

    private fun presetLevels(presetName: String): List<Float> = when (presetName.lowercase()) {
        "bass boost" -> listOf(7f, 4f, 1f, 0f, -1f)
        "vocal pop" -> listOf(-2f, 2f, 5f, 3f, 1f)
        "electronic" -> listOf(5f, 3f, -1f, 2f, 5f)
        "rock" -> listOf(5f, 3f, -1f, 3f, 5f)
        "acoustic" -> listOf(3f, 2f, 1f, 3f, 2f)
        else -> listOf(0f, 0f, 0f, 0f, 0f)
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
                for (i in 0 until eq.numberOfBands.toInt()) {
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
            val minMax = eq.bandLevelRange
            val maxLevel = minMax[1].toInt()
            val minLevel = minMax[0].toInt()

            val factor = maxLevel / 10f
            val levels = _customEqPresets.value.firstOrNull { it.name.equals(presetName, true) }?.bandsDb ?: presetLevels(presetName)
            for (i in 0 until eq.numberOfBands.toInt()) {
                val point = if (levels.size <= 1 || eq.numberOfBands <= 1) 0f
                    else i.toFloat() * (levels.lastIndex) / (eq.numberOfBands - 1)
                val left = point.toInt().coerceIn(0, levels.lastIndex)
                val right = (left + 1).coerceAtMost(levels.lastIndex)
                val offset = levels[left] + (levels[right] - levels[left]) * (point - left)
                val bandLevel = (offset * factor).toInt().coerceIn(minLevel, maxLevel)
                eq.setBandLevel(i.toShort(), bandLevel.toShort())
            }
            updateEqualizerBandsState()
            saveAudioSettings()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun saveCustomEqPreset(name: String) {
        val cleanName = name.trim().take(40)
        if (cleanName.isBlank() || _customEqPresets.value.any { it.name.equals(cleanName, true) }) return
        val bands = androidEqualizer?.let { eq -> (0 until eq.numberOfBands.toInt()).map { eq.getBandLevel(it.toShort()) / 100f } }
            ?: audioPrefs.getString(EQ_BAND_LEVELS_KEY, "")?.split(',')?.mapNotNull { it.toFloatOrNull()?.div(100f) }.orEmpty()
        if (bands.isEmpty()) return
        _customEqPresets.value = _customEqPresets.value + com.tushar.voidplayer.player.EqPreset(java.util.UUID.randomUUID().toString(), cleanName, bands)
        persistCustomEqPresets()
    }

    override fun deleteCustomEqPreset(id: String) {
        _customEqPresets.value = _customEqPresets.value.filterNot { it.id == id }
        persistCustomEqPresets()
    }

    override fun renameCustomEqPreset(id: String, name: String) {
        val cleanName = name.trim().take(40)
        if (cleanName.isBlank() || _customEqPresets.value.any { it.id != id && it.name.equals(cleanName, true) }) return
        _customEqPresets.value = _customEqPresets.value.map { if (it.id == id) it.copy(name = cleanName) else it }
        persistCustomEqPresets()
    }

    override fun exportCustomEqPresets(): String = encodeCustomEqPresets(_customEqPresets.value)

    override fun importCustomEqPresets(json: String): Boolean = try {
        val imported = decodeCustomEqPresets(json).take(50)
        val merged = (_customEqPresets.value + imported).distinctBy { it.name.lowercase() }
        _customEqPresets.value = merged
        persistCustomEqPresets()
        true
    } catch (_: Throwable) { false }

    private fun loadCustomEqPresets(): List<com.tushar.voidplayer.player.EqPreset> =
        decodeCustomEqPresets(audioPrefs.getString("custom_eq_presets", "[]") ?: "[]")

    private fun persistCustomEqPresets() {
        audioPrefs.edit().putString("custom_eq_presets", encodeCustomEqPresets(_customEqPresets.value)).apply()
    }

    private fun encodeCustomEqPresets(presets: List<com.tushar.voidplayer.player.EqPreset>): String =
        org.json.JSONArray().apply { presets.forEach { preset ->
            put(org.json.JSONObject().put("id", preset.id).put("name", preset.name)
                .put("bandsDb", org.json.JSONArray(preset.bandsDb)))
        } }.toString()

    private fun decodeCustomEqPresets(json: String): List<com.tushar.voidplayer.player.EqPreset> = try {
        val array = org.json.JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val values = item.optJSONArray("bandsDb") ?: return@mapNotNull null
            val levels = (0 until values.length()).map { values.optDouble(it, 0.0).toFloat().coerceIn(-15f, 15f) }
            val name = item.optString("name", "").trim()
            if (name.isBlank() || levels.isEmpty()) null
            else com.tushar.voidplayer.player.EqPreset(item.optString("id", java.util.UUID.randomUUID().toString()), name.take(40), levels)
        }
    } catch (_: Throwable) { emptyList() }

    private fun loadSavedAudioSettings() {
        try {
            val speed = audioPrefs.getFloat("playback_speed", 1.0f)
            _playbackSpeed.value = speed
            player.setPlaybackSpeed(speed)

            _isAiDjFlowEnabled.value = audioPrefs.getBoolean("ai_dj_flow", false)

            val shuffle = audioPrefs.getBoolean("is_shuffle", false)
            _isShuffle.value = shuffle
            applyShuffleMode()

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
                .putString(EQ_BAND_LEVELS_KEY, androidEqualizer?.let { eq ->
                    (0 until eq.numberOfBands.toInt()).joinToString(",") { index ->
                        eq.getBandLevel(index.toShort()).toString()
                    }
                } ?: audioPrefs.getString(EQ_BAND_LEVELS_KEY, ""))
                .putBoolean("ai_dj_flow", _isAiDjFlowEnabled.value)
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
        return com.tushar.voidplayer.ArtworkStore.downsampleToJpeg(rawArt) ?: rawArt
    }

    // Content URIs already written for a song's art. setPlaylist() builds metadata for the
    // whole queue on the main thread, so hashing and writing art again for each item is
    // avoided once a song's URI is known.
    private val artworkUriCache = LruCache<Long, Uri>(256)

    private fun getArtworkUri(artBytes: ByteArray): Uri? {
        return try {
            com.tushar.voidplayer.ArtworkStore.store(context, artBytes)?.also { uri ->
                context.grantUriPermission("com.android.systemui", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun artworkUriFor(songId: Long, art: ByteArray?): Uri? {
        artworkUriCache.get(songId)?.let { cached ->
            // The disk cache is size-bounded, so a remembered file may have been pruned.
            if (com.tushar.voidplayer.ArtworkStore.isAvailable(context, cached)) return cached
            artworkUriCache.remove(songId)
        }
        val bytes = art ?: return null
        return getArtworkUri(bytes)?.also { artworkUriCache.put(songId, it) }
    }

    private fun artworkUriFor(song: Song): Uri? = artworkUriFor(song.id, artworkCache.get(song.id) ?: song.coverArt)

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
            _currentQueue.value = playlist
        }

        try {
            val currentItem = player.currentMediaItem
            if (currentItem != null && currentItem.mediaId == songId.toString()) {
                val artUri = artworkUriFor(songId, art)
                val updatedMetadata = currentItem.mediaMetadata.buildUpon()
                    .setTitle(current?.title ?: currentItem.mediaMetadata.title)
                    .setArtist(current?.artist ?: currentItem.mediaMetadata.artist)
                    .setAlbumTitle(current?.album ?: currentItem.mediaMetadata.albumTitle)
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
        // New bytes (for example after a cover edit) must not reuse the old URI.
        artworkUriCache.remove(songId)
        applyArtwork(songId, safeArt)
    }

    override fun updateSongMetadata(song: Song) {
        val index = playlist.indexOfFirst { it.id == song.id }
        if (index < 0) return
        playlist = playlist.toMutableList().apply { this[index] = song }
        _currentQueue.value = playlist
        if (_currentSong.value?.id == song.id) _currentSong.value = song
        try {
            val artUri = artworkUriFor(song)
            val item = MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).setAlbumTitle(song.album)
                        .apply { if (artUri != null) setArtworkUri(artUri) }.build()
                )
                .build()
            player.replaceMediaItem(index, item)
        } catch (error: Throwable) {
            android.util.Log.w("VoidPlayer", "Could not refresh session metadata after tag edit", error)
        }
        savePlaybackSnapshot()
    }

    override fun playNext(song: Song) {
        val current = _currentSong.value
        // Moving the playing item would remove it from the player and interrupt playback.
        if (current?.id == song.id) return
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
        savePlaybackSnapshot()

        try {
            val artUri = artworkUriFor(song)

            val mediaItem = MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
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

    override fun addToQueue(song: Song) {
        if (playlist.any { it.id == song.id }) return
        val artUri = artworkUriFor(song)
        val item = MediaItem.Builder().setMediaId(song.id.toString()).setUri(song.uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).setAlbumTitle(song.album)
                .apply { if (artUri != null) setArtworkUri(artUri) }.build()).build()
        player.addMediaItem(item)
        playlist = playlist + song
        _currentQueue.value = playlist
        player.prepare()
        savePlaybackSnapshot()
    }

    override fun removeFromQueue(song: Song) {
        val index = playlist.indexOfFirst { it.id == song.id }
        if (index < 0 || playlist.size != player.mediaItemCount) return
        val wasCurrent = _currentSong.value?.id == song.id
        try {
            // Update our copy first: removing the current item fires onMediaItemTransition
            // synchronously, and it resolves the new current song from `playlist`.
            playlist = playlist.toMutableList().apply { removeAt(index) }
            _currentQueue.value = playlist
            player.removeMediaItem(index)
            if (playlist.isEmpty()) {
                _currentSong.value = null
                activeTrackId = null
            } else if (wasCurrent) {
                onTrackStarted()
            }
            savePlaybackSnapshot()
        } catch (error: Throwable) {
            android.util.Log.e("VoidPlayer", "Could not remove queue item", error)
        }
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in playlist.indices || toIndex !in playlist.indices || fromIndex == toIndex) return
        try {
            player.moveMediaItem(fromIndex, toIndex)
            playlist = playlist.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            _currentQueue.value = playlist
            savePlaybackSnapshot()
        } catch (error: Throwable) {
            android.util.Log.e("VoidPlayer", "Could not reorder queue", error)
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
        val mediaItems = songs.map { song ->
            val artUri = artworkUriFor(song)

            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
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
        savePlaybackSnapshot()
    }

    override fun restoreLastPlayback(songs: List<Song>) {
        if (songs.isEmpty() || _currentSong.value != null) return
        val savedIds = runCatching {
            val json = org.json.JSONArray(audioPrefs.getString("resume_queue_ids", "[]") ?: "[]")
            (0 until json.length()).mapNotNull { json.optLong(it).takeIf { id -> id != 0L } }
        }.getOrDefault(emptyList())
        if (savedIds.isEmpty()) return
        val songsById = songs.associateBy(Song::id)
        val restoredQueue = savedIds.mapNotNull(songsById::get)
        if (restoredQueue.isEmpty()) return

        val savedSongId = audioPrefs.getLong("resume_song_id", savedIds.first())
        val currentIndex = restoredQueue.indexOfFirst { it.id == savedSongId }.takeIf { it >= 0 } ?: 0
        val knownDuration = restoredQueue[currentIndex].duration
        // Some SAF files report duration 0 until prepared; keep the saved position then.
        val savedPosition = audioPrefs.getLong("resume_position_ms", 0L).coerceAtLeast(0L)
            .let { if (knownDuration > 0L) it.coerceAtMost(knownDuration) else it }
        // Restoring a paused queue is not a play: keep it out of the listening stats.
        restoringPlayback = true
        try {
            setPlaylist(restoredQueue)
            player.seekTo(currentIndex, savedPosition)
            player.pause()
            _currentSong.value = restoredQueue[currentIndex]
        } finally {
            restoringPlayback = false
        }
        activeTrackId = restoredQueue[currentIndex].id
        _currentPosition.value = savedPosition
        lastPolledPositionMs = savedPosition
        loadArtworkForSong(restoredQueue[currentIndex])
        savePlaybackSnapshot(savedPosition)
        queueAiDjPick()
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
        // Playing the item that is already current fires no media item transition.
        onTrackStarted()
    }

    override fun setAiDjFlowEnabled(enabled: Boolean) {
        _isAiDjFlowEnabled.value = enabled
        applyShuffleMode()
        saveAudioSettings()
        if (enabled) queueAiDjPick() else aiDjJob?.cancel()
    }

    // AI DJ decides the next track itself, so ExoPlayer's shuffle order is bypassed while
    // it is on. The user's shuffle preference is kept and restored when DJ is turned off.
    private fun applyShuffleMode() {
        player.shuffleModeEnabled = _isShuffle.value && !_isAiDjFlowEnabled.value
    }

    /**
     * Book-keeping when a track starts: closes out the previous track's stats (completion
     * when it ended naturally, skip when it was left within [SKIP_THRESHOLD_MS]) and counts
     * a play for the new one. Calling it again for the same track is a no-op for stats.
     */
    private fun onTrackStarted(endedNaturally: Boolean = false) {
        if (restoringPlayback) return
        val id = _currentSong.value?.id ?: return
        val previousId = activeTrackId
        val isNewTrack = previousId != id
        if (previousId != null) {
            when {
                endedNaturally -> recordListening(previousId, completion = true)
                isNewTrack && lastPolledPositionMs < SKIP_THRESHOLD_MS -> recordListening(previousId, skip = true)
            }
        }
        if (isNewTrack || endedNaturally) recordListening(id, play = true)
        activeTrackId = id
        lastPolledPositionMs = 0L
        if (playHistory.lastOrNull() != id) {
            playHistory.addLast(id)
            while (playHistory.size > PLAY_HISTORY_SIZE) playHistory.removeFirst()
        }
        queueAiDjPick()
    }

    private fun loadListeningStats(): MutableMap<Long, com.tushar.voidplayer.utils.ListeningStats> {
        return try {
            val root = org.json.JSONObject(audioPrefs.getString("sml_listening_stats", "{}") ?: "{}")
            val result = mutableMapOf<Long, com.tushar.voidplayer.utils.ListeningStats>()
            root.keys().forEach { key ->
                val item = root.optJSONObject(key) ?: return@forEach
                val id = key.toLongOrNull() ?: return@forEach
                result[id] = com.tushar.voidplayer.utils.ListeningStats(
                    plays = item.optInt("plays"), completions = item.optInt("completions"),
                    skips = item.optInt("skips"), repeats = item.optInt("repeats"),
                    lastPlayedEpochMillis = item.optLong("lastPlayed")
                )
            }
            result
        } catch (_: Throwable) { mutableMapOf() }
    }

    private fun recordListening(songId: Long, play: Boolean = false, completion: Boolean = false, skip: Boolean = false) {
        val old = listeningStats[songId] ?: com.tushar.voidplayer.utils.ListeningStats()
        val sameAsLast = audioPrefs.getLong("sml_last_song_id", Long.MIN_VALUE) == songId
        listeningStats[songId] = old.copy(
            plays = old.plays + if (play) 1 else 0,
            completions = old.completions + if (completion) 1 else 0,
            skips = old.skips + if (skip) 1 else 0,
            repeats = old.repeats + if (play && sameAsLast) 1 else 0,
            lastPlayedEpochMillis = if (play) System.currentTimeMillis() else old.lastPlayedEpochMillis
        )
        if (play) audioPrefs.edit().putLong("sml_last_song_id", songId).apply()
        if (listeningStats.size > 5000) {
            val oldest = listeningStats.entries.sortedBy { it.value.lastPlayedEpochMillis }.take(listeningStats.size - 5000).map { it.key }
            oldest.forEach(listeningStats::remove)
        }
        val json = org.json.JSONObject()
        listeningStats.forEach { (id, stats) ->
            json.put(id.toString(), org.json.JSONObject().put("plays", stats.plays).put("completions", stats.completions)
                .put("skips", stats.skips).put("repeats", stats.repeats).put("lastPlayed", stats.lastPlayedEpochMillis))
        }
        audioPrefs.edit().putString("sml_listening_stats", json.toString()).apply()
    }

    /** Scores candidates off the main thread, then moves the DJ pick into the next slot. */
    private fun queueAiDjPick() {
        if (!_isAiDjFlowEnabled.value) return
        val current = _currentSong.value ?: return
        val candidates = playlist
        val recent = playHistory.toList()
        val statsSnapshot = listeningStats.toMap()
        aiDjJob?.cancel()
        aiDjJob = scope.launch {
            val pick = withContext(Dispatchers.Default) {
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val now = System.currentTimeMillis()
                val biases = statsSnapshot.mapValues { (_, stats) -> com.tushar.voidplayer.utils.SmlV2.listeningBias(stats, now) }
                com.tushar.voidplayer.utils.AiEngine.getAiDjNextSong(current, candidates, recent, localHour = hour, listeningBiases = biases)
            } ?: return@launch
            // The user may have skipped while we were scoring.
            if (_currentSong.value?.id != current.id || !_isAiDjFlowEnabled.value) return@launch
            moveToNextSlot(pick.id)
        }
    }

    private fun moveToNextSlot(songId: Long) {
        val currentIdx = player.currentMediaItemIndex
        val fromIdx = playlist.indexOfFirst { it.id == songId }
        if (fromIdx < 0 || fromIdx == currentIdx || fromIdx == currentIdx + 1) return
        if (playlist.size != player.mediaItemCount) return
        // Removing an item before the current one shifts the current index down by one.
        val toIdx = if (fromIdx < currentIdx) currentIdx else currentIdx + 1
        try {
            player.moveMediaItem(fromIdx, toIdx)
            playlist = playlist.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
            _currentQueue.value = playlist
        } catch (e: Throwable) {
            e.printStackTrace()
        }
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
        applyShuffleMode()
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
        lastPolledPositionMs = player.currentPosition
    }

    override fun seekForward(millis: Long) {
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration < 0L) return
        val newPos = (player.currentPosition + millis).coerceAtMost(duration)
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
                lastPolledPositionMs = player.currentPosition
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastSnapshotAtMs >= 10_000L) {
                    lastSnapshotAtMs = now
                    savePlaybackSnapshot(player.currentPosition)
                }
                delay(250)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun savePlaybackSnapshot(positionMs: Long = player.currentPosition) {
        if (playlist.isEmpty()) return
        val currentId = _currentSong.value?.id?.takeIf { id -> playlist.any { it.id == id } }
            ?: playlist.getOrNull(player.currentMediaItemIndex)?.id
            ?: return
        audioPrefs.edit()
            .putString("resume_queue_ids", org.json.JSONArray(playlist.map(Song::id)).toString())
            .putLong("resume_song_id", currentId)
            .putLong("resume_position_ms", positionMs.coerceAtLeast(0L))
            .apply()
    }

    override fun cleanUp() {
        savePlaybackSnapshot()
        aiDjJob?.cancel()
        scope.cancel()
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

    private companion object {
        const val PLAY_HISTORY_SIZE = 50
        const val SKIP_THRESHOLD_MS = 30_000L
        const val EQ_BAND_LEVELS_KEY = "eq_band_levels"
    }
}
