package com.tushar.voidplayer.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import com.tushar.voidplayer.model.Playlist
import com.tushar.voidplayer.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class AndroidSongRepository(private val context: Context) : SongRepository {

    private val prefs = context.getSharedPreferences("VoidPlayer_Favorites", Context.MODE_PRIVATE)
    private val playlistPrefs = context.getSharedPreferences("VoidPlayer_Playlists", Context.MODE_PRIVATE)

    private fun getFavoriteIds(): Set<String> {
        return prefs.getStringSet("favorite_ids", emptySet()) ?: emptySet()
    }

    override suspend fun toggleFavorite(songId: Long, isFav: Boolean) = withContext(Dispatchers.IO) {
        val current = getFavoriteIds().toMutableSet()
        if (isFav) {
            current.add(songId.toString())
        } else {
            current.remove(songId.toString())
        }
        prefs.edit().putStringSet("favorite_ids", current).apply()
    }

    // ------------------------------------------------------------------
    // Playlist persistence
    // ------------------------------------------------------------------

    override suspend fun getPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        val allKeys = playlistPrefs.all
        val list = mutableListOf<Playlist>()
        for ((key, value) in allKeys) {
            if (key.startsWith("pl_") && value is String) {
                // format: "Name:::id1,id2,id3"
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
        val key = "pl_${playlist.id}"
        val value = "${playlist.name}:::${playlist.songIds.joinToString(",")}"
        playlistPrefs.edit().putString(key, value).apply()
    }

    override suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        val key = "pl_$playlistId"
        playlistPrefs.edit().remove(key).apply()
    }

    // ------------------------------------------------------------------
    // MediaStore path (device library scan)
    // ------------------------------------------------------------------

    override suspend fun getSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val favIds = getFavoriteIds()
        val projectionList = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            projectionList.add(MediaStore.Audio.Media.GENRE)
        }
        val projection = projectionList.toTypedArray()

        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        // Filter for music files only and exclude short audio clips (< 1 sec)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 1000"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
                val idColumn       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val genreColumn    = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                } else -1

                while (cursor.moveToNext()) {
                    val id       = cursor.getLong(idColumn)
                    val title    = cursor.getString(titleColumn)?.takeIf { it.isNotBlank() } ?: "Unknown"
                    val artist   = cursor.getString(artistColumn)?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
                    val album    = cursor.getString(albumColumn)?.takeIf { it.isNotBlank() } ?: "Unknown Album"
                    val duration = cursor.getLong(durationColumn)
                    val genre    = if (genreColumn >= 0) cursor.getString(genreColumn)?.takeIf { it.isNotBlank() } ?: "" else ""

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    )

                    val energy = com.tushar.voidplayer.utils.AiEngine.calculateAcousticEnergy(genre, 0, duration)

                    // Art is loaded lazily via ImageCache to keep initial load fast
                    songs.add(
                        Song(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            uri = contentUri.toString(),
                            coverArt = null,
                            isFavorite = favIds.contains(id.toString()),
                            genre = genre,
                            bpm = 0,
                            acousticEnergy = energy
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VoidPlayer", "Error querying MediaStore", e)
        }
        songs
    }

    // ------------------------------------------------------------------
    // SAF path (user-picked folder)
    // ------------------------------------------------------------------

    override suspend fun loadFromFolder(uriString: String): List<Song> = withContext(Dispatchers.IO) {
        val audioFiles = mutableListOf<androidx.documentfile.provider.DocumentFile>()
        val favIds = getFavoriteIds()
        try {
            val treeUri = android.net.Uri.parse(uriString)
            val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)

            if (docFile != null && docFile.isDirectory) {
                collectAudioFilesIterative(docFile, audioFiles)
            }
        } catch (e: Exception) {
            android.util.Log.e("VoidPlayer", "Error loading from folder: $uriString", e)
        }

        if (audioFiles.isEmpty()) return@withContext emptyList<Song>()

        // Extract metadata concurrently across threads for fast folder loading
        coroutineScope {
            audioFiles.chunked(15).flatMap { batch ->
                batch.map { file ->
                    async(Dispatchers.IO) {
                        extractSongFromFile(file, favIds)
                    }
                }.map { it.await() }
            }
        }
    }

    private fun extractSongFromFile(
        file: androidx.documentfile.provider.DocumentFile,
        favIds: Set<String>
    ): Song {
        val fileName = file.name ?: "Unknown"
        var title  = fileName.substringBeforeLast('.').takeIf { it.isNotBlank() } ?: "Unknown"
        var artist = "Unknown Artist"
        var album  = "Unknown Album"
        var duration = 0L

        var genre = ""
        var bpm = 0

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, file.uri)
            title    = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                           ?.takeIf { it.isNotBlank() } ?: title
            artist   = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                           ?.takeIf { it.isNotBlank() } ?: artist
            album    = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                           ?.takeIf { it.isNotBlank() } ?: album
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                           ?.toLongOrNull() ?: 0L
            genre    = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                           ?.takeIf { it.isNotBlank() } ?: ""
        } catch (_: Exception) {
            // Fallback to filename — already set above
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }

        val id = file.uri.toString().hashCode().toLong() and 0x7FFF_FFFF_FFFF_FFFFL
        val energy = com.tushar.voidplayer.utils.AiEngine.calculateAcousticEnergy(genre, bpm, duration)

        return Song(
            id       = id,
            title    = title,
            artist   = artist,
            album    = album,
            duration = duration,
            uri      = file.uri.toString(),
            coverArt = null,
            isFavorite = favIds.contains(id.toString()),
            genre    = genre,
            bpm      = bpm,
            acousticEnergy = energy
        )
    }

    override suspend fun loadArt(uriString: String): ByteArray? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, android.net.Uri.parse(uriString))
            retriever.embeddedPicture
        } catch (e: Throwable) {
            android.util.Log.e("VoidPlayer", "Failed to load art for $uriString", e)
            null
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    private val lyricsCacheDir: java.io.File by lazy {
        val dir = java.io.File(context.filesDir, "lyrics_cache")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    override suspend fun loadLyrics(song: Song): String? = withContext(Dispatchers.IO) {
        // 1. Check local persistent lyrics cache
        val cachedFile = java.io.File(lyricsCacheDir, "${song.id}.lrc")
        if (cachedFile.exists()) {
            try {
                val cached = cachedFile.readText()
                if (cached.isNotBlank()) return@withContext cached
            } catch (_: Throwable) {}
        }

        // 2. Check local companion file in same directory
        val localLyrics = loadLyrics(song.uri)
        if (!localLyrics.isNullOrBlank()) {
            return@withContext localLyrics
        }

        null
    }

    override suspend fun loadLyrics(uriString: String): String? = withContext(Dispatchers.IO) {
        try {
            val uri = android.net.Uri.parse(uriString)
            val docFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
            val parent = docFile?.parentFile
            if (parent != null) {
                val baseName = docFile.name?.substringBeforeLast('.') ?: ""
                val lrcFile = parent.findFile("$baseName.lrc") ?: parent.findFile("$baseName.LRC")
                if (lrcFile != null && lrcFile.exists()) {
                    context.contentResolver.openInputStream(lrcFile.uri)?.use { stream ->
                        return@withContext stream.bufferedReader().use { it.readText() }
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("VoidPlayer", "Error searching for LRC lyrics", e)
        }
        null
    }

    override suspend fun fetchOnlineLyrics(song: Song): String? = withContext(Dispatchers.IO) {
        // Check cache first
        val cachedFile = java.io.File(lyricsCacheDir, "${song.id}.lrc")
        if (cachedFile.exists()) {
            try {
                val text = cachedFile.readText()
                if (text.isNotBlank()) return@withContext text
            } catch (_: Throwable) {}
        }

        // Query LRCLIB API
        val result = com.tushar.voidplayer.utils.LrclibApi.getLyrics(
            trackName = song.title,
            artistName = song.artist,
            albumName = song.album,
            durationSeconds = (song.duration / 1000).toInt()
        )

        val lyrics = result?.syncedLyrics ?: result?.plainLyrics
        if (!lyrics.isNullOrBlank()) {
            saveLyrics(song, lyrics)
            return@withContext lyrics
        }
        null
    }

    override suspend fun searchOnlineLyrics(query: String): List<com.tushar.voidplayer.data.LyricsSearchResult> = withContext(Dispatchers.IO) {
        com.tushar.voidplayer.utils.LrclibApi.searchLyrics(query)
    }

    override suspend fun saveLyrics(song: Song, lrcContent: String) {
        withContext(Dispatchers.IO) {
            try {
                val file = java.io.File(lyricsCacheDir, "${song.id}.lrc")
                file.writeText(lrcContent)
            } catch (e: Throwable) {
                android.util.Log.e("VoidPlayer", "Failed to cache lyrics for song ${song.id}", e)
            }
        }
    }

    override suspend fun checkForUpdates(): com.tushar.voidplayer.utils.UpdateInfo = withContext(Dispatchers.IO) {
        com.tushar.voidplayer.utils.UpdateChecker.checkForUpdates(context)
    }

    private fun collectAudioFilesIterative(
        root: androidx.documentfile.provider.DocumentFile,
        audioFiles: MutableList<androidx.documentfile.provider.DocumentFile>
    ) {
        val stack = ArrayDeque<androidx.documentfile.provider.DocumentFile>()
        stack.addLast(root)

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = try { current.listFiles() } catch (_: Exception) { emptyArray() }
            for (child in children) {
                when {
                    child.isDirectory               -> stack.addLast(child)
                    isValidAudioFile(child.name)    -> audioFiles.add(child)
                }
            }
        }
    }

    private fun isValidAudioFile(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.lowercase()
        return lower.endsWith(".mp3")  ||
               lower.endsWith(".wav")  ||
               lower.endsWith(".flac") ||
               lower.endsWith(".aac")  ||
               lower.endsWith(".ogg")  ||
               lower.endsWith(".m4a")  ||
               lower.endsWith(".opus") ||
               lower.endsWith(".wma")
    }
}
