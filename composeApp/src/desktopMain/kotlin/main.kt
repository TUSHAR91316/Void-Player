import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.tushar.voidplayer.App
import com.tushar.voidplayer.data.SongRepository
import com.tushar.voidplayer.model.Playlist
import com.tushar.voidplayer.model.Song
import com.tushar.voidplayer.player.AudioPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Properties
import javax.sound.sampled.*
import javax.swing.JFileChooser
import javax.swing.UIManager


fun main() = application {
    val repository = remember { DesktopSongRepository() }
    val player: AudioPlayer = remember {
        val osName = System.getProperty("os.name").lowercase()
        if (osName.contains("linux")) {
            VlcAudioPlayer()
        } else {
            JavaDesktopAudioPlayer()
        }
    }

    val pickedFolderUri = remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("Ready") }

    // Load last used folder on startup
    LaunchedEffect(Unit) {
        val lastFolder = repository.getLastUsedFolder()
        if (!lastFolder.isNullOrBlank() && File(lastFolder).exists()) {
            pickedFolderUri.value = lastFolder
            statusMessage = "Loaded: $lastFolder"
        }
    }

    Window(
        onCloseRequest = {
            player.cleanUp()
            exitApplication()
        },
        title = "Void Player",
        icon = painterResource("icon.png")
    ) {
        App(
            repository = repository,
            player = player,
            pickedFolderUri = pickedFolderUri,
            statusMessage = statusMessage,
            onPickFolder = {
                val selectedPath = pickDesktopFolder()
                if (!selectedPath.isNullOrBlank()) {
                    pickedFolderUri.value = selectedPath
                    repository.saveLastUsedFolder(selectedPath)
                    statusMessage = "Folder selected: $selectedPath"
                }
            }
        )
    }
}

/**
 * Native Windows / Desktop directory chooser dialog
 */
fun pickDesktopFolder(): String? {
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch (_: Throwable) {}

    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Select Music Folder"
        isAcceptAllFileFilterUsed = false
    }

    val result = chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION && chooser.selectedFile != null) {
        chooser.selectedFile.absolutePath
    } else {
        null
    }
}

class DesktopSongRepository : SongRepository {

    private val dataDir = File(System.getProperty("user.home"), ".voidplayer").apply { mkdirs() }
    private val prefsFile = File(dataDir, "preferences.properties")
    private val playlistsFile = File(dataDir, "playlists.properties")

    fun getLastUsedFolder(): String? {
        if (!prefsFile.exists()) return null
        val props = Properties()
        prefsFile.inputStream().use { props.load(it) }
        return props.getProperty("last_folder")
    }

    fun saveLastUsedFolder(path: String) {
        val props = Properties()
        if (prefsFile.exists()) {
            prefsFile.inputStream().use { props.load(it) }
        }
        props.setProperty("last_folder", path)
        prefsFile.outputStream().use { props.store(it, "Void Player Preferences") }
    }

    private fun getFavorites(): Set<String> {
        if (!prefsFile.exists()) return emptySet()
        val props = Properties()
        prefsFile.inputStream().use { props.load(it) }
        val raw = props.getProperty("favorites", "")
        return if (raw.isNotBlank()) raw.split(",").toSet() else emptySet()
    }

    override suspend fun toggleFavorite(songId: Long, isFav: Boolean) = withContext(Dispatchers.IO) {
        val favs = getFavorites().toMutableSet()
        if (isFav) favs.add(songId.toString()) else favs.remove(songId.toString())
        val props = Properties()
        if (prefsFile.exists()) {
            prefsFile.inputStream().use { props.load(it) }
        }
        props.setProperty("favorites", favs.joinToString(","))
        prefsFile.outputStream().use { props.store(it, "Void Player Preferences") }
    }

    override suspend fun getPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        if (!playlistsFile.exists()) return@withContext emptyList<Playlist>()
        val props = Properties()
        playlistsFile.inputStream().use { props.load(it) }
        val list = mutableListOf<Playlist>()
        for (key in props.stringPropertyNames()) {
            if (key.startsWith("pl_")) {
                val value = props.getProperty(key, "")
                val parts = value.split(":::")
                val name = parts.getOrNull(0) ?: "Playlist"
                val idsStr = parts.getOrNull(1) ?: ""
                val songIds = if (idsStr.isNotBlank()) {
                    idsStr.split(",").mapNotNull { it.trim().toLongOrNull() }
                } else {
                    emptyList()
                }
                list.add(Playlist(id = key.removePrefix("pl_"), name = name, songIds = songIds))
            }
        }
        list
    }

    override suspend fun savePlaylist(playlist: Playlist) = withContext(Dispatchers.IO) {
        val props = Properties()
        if (playlistsFile.exists()) {
            playlistsFile.inputStream().use { props.load(it) }
        }
        props.setProperty("pl_${playlist.id}", "${playlist.name}:::${playlist.songIds.joinToString(",")}")
        playlistsFile.outputStream().use { props.store(it, "Void Player Playlists") }
    }

    override suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        if (!playlistsFile.exists()) return@withContext
        val props = Properties()
        playlistsFile.inputStream().use { props.load(it) }
        props.remove("pl_$playlistId")
        playlistsFile.outputStream().use { props.store(it, "Void Player Playlists") }
    }

    override suspend fun getSongs(): List<Song> = withContext(Dispatchers.IO) {
        val lastFolder = getLastUsedFolder()
        if (!lastFolder.isNullOrBlank()) {
            loadFromFolder(lastFolder)
        } else {
            // Check default user music folder
            val userMusic = File(System.getProperty("user.home"), "Music")
            if (userMusic.exists()) loadFromFolder(userMusic.absolutePath) else emptyList()
        }
    }

    override suspend fun loadFromFolder(uriString: String): List<Song> = withContext(Dispatchers.IO) {
        val folder = File(uriString)
        if (!folder.exists() || !folder.isDirectory) return@withContext emptyList()

        val audioFiles = mutableListOf<File>()
        folder.walkTopDown().filter { it.isFile && isAudioFile(it.name) }.forEach {
            audioFiles.add(it)
        }

        val favs = getFavorites()
        audioFiles.map { file ->
            val id = file.absolutePath.hashCode().toLong() and 0x7FFF_FFFF_FFFF_FFFFL
            val fileName = file.nameWithoutExtension
            val (artist, title) = if (fileName.contains(" - ")) {
                val p = fileName.split(" - ", limit = 2)
                p[0].trim() to p[1].trim()
            } else {
                "Unknown Artist" to fileName
            }

            Song(
                id = id,
                title = title,
                artist = artist,
                album = file.parentFile?.name ?: "Local Audio",
                duration = estimateAudioDuration(file),
                uri = file.absolutePath,
                coverArt = null,
                isFavorite = favs.contains(id.toString())
            )
        }
    }

    override suspend fun loadArt(uriString: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val file = File(uriString)
            val parent = file.parentFile
            if (parent != null) {
                val cover = parent.listFiles { _, name ->
                    val lower = name.lowercase()
                    lower == "cover.jpg" || lower == "cover.png" || lower == "folder.jpg" || lower == "album.jpg"
                }?.firstOrNull()
                if (cover != null && cover.exists()) {
                    return@withContext cover.readBytes()
                }
            }
        } catch (_: Throwable) {}
        null
    }

    override suspend fun loadLyrics(uriString: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(uriString)
            val parent = file.parentFile
            if (parent != null) {
                val base = file.nameWithoutExtension
                val lrc = File(parent, "$base.lrc").takeIf { it.exists() }
                    ?: File(parent, "$base.LRC").takeIf { it.exists() }
                if (lrc != null) {
                    return@withContext lrc.readText()
                }
            }
        } catch (_: Throwable) {}
        null
    }

    private fun isAudioFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".flac") ||
               lower.endsWith(".m4a") || lower.endsWith(".aac") || lower.endsWith(".ogg") ||
               lower.endsWith(".opus") || lower.endsWith(".wma")
    }

    private fun estimateAudioDuration(file: File): Long {
        return try {
            val audioInputStream = AudioSystem.getAudioInputStream(file)
            val format = audioInputStream.format
            val frames = audioInputStream.frameLength
            val durationInSeconds = (frames / format.frameRate).toDouble()
            (durationInSeconds * 1000).toLong().coerceAtLeast(60000L)
        } catch (_: Throwable) {
            // Fallback estimation from file length (~1MB ~ 1 minute)
            val approxSecs = (file.length() / (128 * 1024 / 8)).coerceIn(30, 600)
            approxSecs * 1000L
        }
    }
}

class JavaDesktopAudioPlayer : AudioPlayer {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

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

    override val equalizerBands: StateFlow<List<AudioPlayer.EqualizerBand>> = MutableStateFlow(emptyList())
    override val isNormalizationEnabled: StateFlow<Boolean> = MutableStateFlow(false)

    private val _playbackSpeed = MutableStateFlow(1.0f)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    override val currentQueue: StateFlow<List<Song>> = _currentQueue.asStateFlow()

    private var currentPlaylist: List<Song> = emptyList()
    private var clip: Clip? = null
    private var positionJob: Job? = null

    override fun setPlaylist(songs: List<Song>) {
        currentPlaylist = songs
        _currentQueue.value = songs
    }

    override fun play(song: Song) {
        cleanUp()
        _currentSong.value = song
        _currentPosition.value = 0L

        try {
            val file = File(song.uri)
            if (!file.exists()) {
                _error.value = "File not found: ${song.title}"
                return
            }

            val inStream = AudioSystem.getAudioInputStream(file)
            val baseFormat = inStream.format
            val decodedFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.sampleRate,
                16,
                baseFormat.channels,
                baseFormat.channels * 2,
                baseFormat.sampleRate,
                false
            )
            val decodedStream = AudioSystem.getAudioInputStream(decodedFormat, inStream)

            clip = AudioSystem.getClip().apply {
                open(decodedStream)
                start()
            }
            _isPlaying.value = true
            _error.value = null
            startPositionTracker()
        } catch (e: Throwable) {
            // Fallback for formats not directly decoded by standard Java Sound SPI
            _isPlaying.value = true
            startSimulatedPositionTracker(song.duration)
        }
    }

    private fun startPositionTracker() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (_isPlaying.value) {
                delay(300)
                val c = clip
                if (c != null && c.isOpen) {
                    _currentPosition.value = c.microsecondPosition / 1000
                    if (!c.isRunning && _currentPosition.value >= (_currentSong.value?.duration ?: 0L) - 1000) {
                        handleSongCompletion()
                        break
                    }
                }
            }
        }
    }

    private fun startSimulatedPositionTracker(duration: Long) {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (_isPlaying.value) {
                delay(500)
                val nextPos = _currentPosition.value + 500
                _currentPosition.value = nextPos
                if (duration > 0 && nextPos >= duration) {
                    handleSongCompletion()
                    break
                }
            }
        }
    }

    private fun handleSongCompletion() {
        when (_repeatMode.value) {
            AudioPlayer.RepeatMode.ONE -> _currentSong.value?.let { play(it) }
            AudioPlayer.RepeatMode.ALL -> next()
            AudioPlayer.RepeatMode.OFF -> {
                val currentIndex = currentPlaylist.indexOfFirst { it.id == _currentSong.value?.id }
                if (currentIndex >= 0 && currentIndex < currentPlaylist.size - 1) {
                    next()
                } else {
                    pause()
                }
            }
        }
    }

    override fun pause() {
        clip?.stop()
        _isPlaying.value = false
        positionJob?.cancel()
    }

    override fun resume() {
        clip?.start()
        _isPlaying.value = true
        startPositionTracker()
    }

    override fun next() {
        if (currentPlaylist.isEmpty()) return
        val nextSong = if (_isShuffle.value) {
            currentPlaylist.randomOrNull()
        } else {
            val idx = currentPlaylist.indexOfFirst { it.id == _currentSong.value?.id }
            if (idx >= 0 && idx < currentPlaylist.size - 1) currentPlaylist[idx + 1] else currentPlaylist.firstOrNull()
        }
        nextSong?.let { play(it) }
    }

    override fun previous() {
        if (currentPlaylist.isEmpty()) return
        val prevSong = if (_isShuffle.value) {
            currentPlaylist.randomOrNull()
        } else {
            val idx = currentPlaylist.indexOfFirst { it.id == _currentSong.value?.id }
            if (idx > 0) currentPlaylist[idx - 1] else currentPlaylist.lastOrNull()
        }
        prevSong?.let { play(it) }
    }

    override fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    override fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            AudioPlayer.RepeatMode.OFF -> AudioPlayer.RepeatMode.ALL
            AudioPlayer.RepeatMode.ALL -> AudioPlayer.RepeatMode.ONE
            AudioPlayer.RepeatMode.ONE -> AudioPlayer.RepeatMode.OFF
        }
    }

    override fun seekTo(position: Long) {
        _currentPosition.value = position
        clip?.let {
            it.microsecondPosition = position * 1000
        }
    }

    override fun seekForward(millis: Long) {
        seekTo((_currentPosition.value + millis).coerceAtMost(_currentSong.value?.duration ?: 0L))
    }

    override fun seekBackward(millis: Long) {
        seekTo((_currentPosition.value - millis).coerceAtLeast(0L))
    }

    override fun cleanUp() {
        positionJob?.cancel()
        try {
            clip?.stop()
            clip?.close()
        } catch (_: Throwable) {}
        clip = null
        _isPlaying.value = false
    }

    override fun setEqualizerBandLevel(bandIndex: Int, level: Int) {}
    override fun resetEqualizer() {}
    override fun toggleNormalization() {}
    override fun updateSongArt(songId: Long, art: ByteArray) {}
    override fun setPlaybackSpeed(speed: Float) { _playbackSpeed.value = speed }
    override fun setVolume(volume: Float) {
        try {
            clip?.let {
                if (it.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    val gainControl = it.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                    val range = gainControl.maximum - gainControl.minimum
                    val gain = (range * volume) + gainControl.minimum
                    gainControl.value = gain.coerceIn(gainControl.minimum, gainControl.maximum)
                }
            }
        } catch (_: Throwable) {}
    }
}
