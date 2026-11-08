package com.tushar.voidplayer.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import android.provider.DocumentsContract
import com.tushar.voidplayer.model.Playlist
import com.tushar.voidplayer.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.json.JSONObject

class AndroidSongRepository(private val context: Context) : SongRepository {

    private data class SafAudioFile(val uri: android.net.Uri, val name: String, val lastModified: Long, val sizeBytes: Long)
    private data class MediaStoreFile(val song: Song, val modified: Long, val path: String?)
    private data class CachedTags(
        val modified: Long,
        val bpm: Int,
        val key: String,
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val duration: Long = 0L,
        val genre: String = ""
    )

    // SharedPreferences are already in memory once the repository exists, so this is a cheap
    // synchronous read that lets the first frame use the chosen theme (no flash).
    override fun uiTheme(): String = settingsPrefsForTheme.getString("ui_theme", "graphite") ?: "graphite"
    override fun setUiTheme(key: String) { settingsPrefsForTheme.edit().putString("ui_theme", key).apply() }
    private val settingsPrefsForTheme by lazy { context.getSharedPreferences("VoidPlayerAudioSettings", Context.MODE_PRIVATE) }

    override fun isFdroidBuild(): Boolean = com.tushar.voidplayer.BuildConfig.DISTRIBUTION == "fdroid"
    override fun appVersionName(): String = com.tushar.voidplayer.BuildConfig.VERSION_NAME

    private val prefs = context.getSharedPreferences("VoidPlayer_Favorites", Context.MODE_PRIVATE)
    private val playlistPrefs = context.getSharedPreferences("VoidPlayer_Playlists", Context.MODE_PRIVATE)
    private val settingsPrefs = context.getSharedPreferences("VoidPlayerAudioSettings", Context.MODE_PRIVATE)
    private val tagCachePrefs = context.getSharedPreferences("VoidPlayerTagMetadata", Context.MODE_PRIVATE)
    private val tagCacheLock = Any()
    private val tagCache = loadTagCache()
    private val tagEditor by lazy { AndroidTagEditor(context) }
    private val favoritesLock = Any()

    override suspend fun isOnlineLyricsEnabled(): Boolean = withContext(Dispatchers.IO) {
        settingsPrefs.getBoolean("online_lyrics_enabled", false)
    }

    override suspend fun setOnlineLyricsEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        settingsPrefs.edit().putBoolean("online_lyrics_enabled", enabled).apply()
    }

    private fun getFavoriteIds(): Set<String> {
        return prefs.getStringSet("favorite_ids", emptySet()) ?: emptySet()
    }

    override suspend fun toggleFavorite(songId: Long, isFav: Boolean) = withContext(Dispatchers.IO) {
        // Rapid toggles run concurrently on IO; serialize the read-modify-write.
        synchronized(favoritesLock) {
            val current = getFavoriteIds().toMutableSet()
            if (isFav) {
                current.add(songId.toString())
            } else {
                current.remove(songId.toString())
            }
            prefs.edit().putStringSet("favorite_ids", current).apply()
        }
    }

    // ------------------------------------------------------------------
    // Playlist persistence
    // ------------------------------------------------------------------

    override suspend fun getPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        val allKeys = playlistPrefs.all
        val list = mutableListOf<Playlist>()
        for ((key, value) in allKeys) {
            if (key.startsWith("pl_") && value is String) {
                val id = key.removePrefix("pl_")
                val playlist = runCatching {
                    val json = JSONObject(value)
                    val ids = json.optJSONArray("songIds")
                    Playlist(id, json.optString("name", "Playlist"),
                        (0 until (ids?.length() ?: 0)).mapNotNull { ids?.optLong(it) })
                }.getOrElse {
                    // Migrate the legacy "Name:::id1,id2" representation in place.
                    val parts = value.split(":::", limit = 2)
                    Playlist(id, parts.getOrNull(0)?.ifBlank { "Playlist" } ?: "Playlist",
                        parts.getOrNull(1).orEmpty().split(',').mapNotNull(String::toLongOrNull))
                }
                list.add(playlist)
                if (!value.trimStart().startsWith("{")) {
                    playlistPrefs.edit().putString(key, playlist.toJson()).apply()
                }
            }
        }
        list
    }

    override suspend fun savePlaylist(playlist: Playlist) = withContext(Dispatchers.IO) {
        val key = "pl_${playlist.id}"
        val value = playlist.toJson()
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
        val mediaFiles = mutableListOf<MediaStoreFile>()
        val modifiedColumnName = MediaStore.Audio.Media.DATE_MODIFIED
        val favIds = getFavoriteIds()
        val projectionList = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )
        projectionList.add(modifiedColumnName)
        // Readable file path, used only to avoid copying files for tag reads.
        @Suppress("DEPRECATION")
        projectionList.add(MediaStore.Audio.Media.DATA)
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

                val modifiedColumn = cursor.getColumnIndex(modifiedColumnName)
                @Suppress("DEPRECATION")
                val pathColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
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
                    val song = Song(
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
                    val modified = if (modifiedColumn >= 0) cursor.getLong(modifiedColumn) else 0L
                    val path = if (pathColumn >= 0) cursor.getString(pathColumn) else null
                    mediaFiles.add(MediaStoreFile(song, modified, path))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VoidPlayer", "Error querying MediaStore", e)
        }
        val songs = coroutineScope {
            mediaFiles.chunked(15).flatMap { batch ->
                batch.map { file -> async(Dispatchers.IO) { readMediaStoreTags(file) } }
                    .map { it.await() }
            }
        }
        persistTagCache()
        songs
    }

    // ------------------------------------------------------------------
    // SAF path (user-picked folder)
    // ------------------------------------------------------------------

    override suspend fun loadFromFolder(uriString: String): List<Song> = withContext(Dispatchers.IO) {
        val audioFiles = mutableListOf<SafAudioFile>()
        try {
            val treeUri = android.net.Uri.parse(uriString)
            val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)

            if (docFile != null && docFile.isDirectory) {
                collectAudioFilesIterative(treeUri, audioFiles)
            }
        } catch (e: Exception) {
            android.util.Log.e("VoidPlayer", "Error loading from folder: $uriString", e)
        }

        if (audioFiles.isEmpty()) return@withContext emptyList<Song>()

        // Migrate first so the favorites read below already carries the new IDs.
        migrateLegacySafIds(audioFiles.map { it.uri.toString() })
        val favIds = getFavoriteIds()

        // Extract metadata concurrently across threads for fast folder loading
        val songs = coroutineScope {
            audioFiles.chunked(15).flatMap { batch ->
                batch.map { file ->
                    async(Dispatchers.IO) {
                    extractSongFromFile(file, favIds)
                    }
                }.map { it.await() }
            }
        }
        persistTagCache()
        songs
    }

    private suspend fun extractSongFromFile(
        file: SafAudioFile,
        favIds: Set<String>
    ): Song {
        val fileName = file.name
        val uri = file.uri.toString()
        val id = stableSafId(uri)
        val cached = getCachedTags("saf:$id", file.lastModified)
        if (cached?.title?.isNotBlank() == true) {
            val bpm = cached.bpm
            return Song(
                id = id,
                title = cached.title,
                artist = cached.artist,
                album = cached.album,
                duration = cached.duration,
                uri = uri,
                isFavorite = favIds.contains(id.toString()),
                genre = cached.genre,
                bpm = bpm,
                musicalKey = cached.key,
                acousticEnergy = com.tushar.voidplayer.utils.AiEngine.calculateAcousticEnergy(cached.genre, bpm, cached.duration)
            )
        }
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

        // SAF metadata APIs omit BPM and musical key. Read those tags through the
        // same jaudiotagger path used by the tag editor while each scan task is on IO.
        val tags = tagEditor.read(file.uri, displayName = fileName, maxCopyBytes = SCAN_TAG_COPY_LIMIT_BYTES)
        bpm = tags?.bpm?.toIntOrNull() ?: bpm
        val musicalKey = tags?.key.orEmpty()
        val energy = com.tushar.voidplayer.utils.AiEngine.calculateAcousticEnergy(genre, bpm, duration)

        if (file.lastModified > 0L) putCachedTags(
            "saf:$id",
            CachedTags(file.lastModified, bpm, musicalKey, title, artist, album, duration, genre)
        )
        return Song(
            id       = id,
            title    = title,
            artist   = artist,
            album    = album,
            duration = duration,
            uri      = uri,
            coverArt = null,
            isFavorite = favIds.contains(id.toString()),
            genre    = genre,
            bpm      = bpm,
            musicalKey = musicalKey,
            acousticEnergy = energy
        )
    }

    private suspend fun readMediaStoreTags(file: MediaStoreFile): Song {
        val (song, modified, path) = file
        val key = "media:${song.id}"
        val cached = getCachedTags(key, modified)
        val tags = if (cached == null) {
            tagEditor.read(android.net.Uri.parse(song.uri), directPath = path, maxCopyBytes = SCAN_TAG_COPY_LIMIT_BYTES)
        } else null
        val bpm = cached?.bpm ?: tags?.bpm?.toIntOrNull() ?: song.bpm
        val musicalKey = cached?.key ?: tags?.key.orEmpty()
        if (cached == null && modified > 0L) putCachedTags(key, CachedTags(modified, bpm, musicalKey, song.title, song.artist, song.album, song.duration, song.genre))
        return song.copy(
            bpm = bpm,
            musicalKey = musicalKey,
            acousticEnergy = com.tushar.voidplayer.utils.AiEngine.calculateAcousticEnergy(song.genre, bpm, song.duration)
        )
    }

    private fun getCachedTags(key: String, modified: Long): CachedTags? = synchronized(tagCacheLock) {
        tagCache[key]?.takeIf { modified > 0L && it.modified == modified }
    }

    private fun putCachedTags(key: String, tags: CachedTags) = synchronized(tagCacheLock) {
        tagCache[key] = tags
        if (tagCache.size > 5000) {
            val oldest = tagCache.minByOrNull { it.value.modified }?.key
            if (oldest != null && oldest != key) tagCache.remove(oldest)
        }
    }

    private fun loadTagCache(): MutableMap<String, CachedTags> = synchronized(tagCacheLock) {
        val result = mutableMapOf<String, CachedTags>()
        val json = runCatching { JSONObject(tagCachePrefs.getString("tag_cache", "{}") ?: "{}") }.getOrNull() ?: return@synchronized result
        json.keys().forEach { key ->
            val item = json.optJSONObject(key) ?: return@forEach
            val modified = item.optLong("modified", 0L)
            if (modified > 0L) result[key] = CachedTags(
                modified, item.optInt("bpm", 0), item.optString("key", ""),
                item.optString("title", ""), item.optString("artist", ""), item.optString("album", ""),
                item.optLong("duration", 0L), item.optString("genre", "")
            )
        }
        result
    }

    private fun persistTagCache() {
        val snapshot = synchronized(tagCacheLock) { tagCache.toMap() }
        val json = JSONObject()
        snapshot.entries.toList().takeLast(5000).forEach { (key, value) ->
            json.put(key, JSONObject().put("modified", value.modified).put("bpm", value.bpm).put("key", value.key)
                .put("title", value.title).put("artist", value.artist).put("album", value.album)
                .put("duration", value.duration).put("genre", value.genre))
        }
        tagCachePrefs.edit().putString("tag_cache", json.toString()).apply()
    }

    /** SAF IDs are negative SHA-256-derived values, separate from positive MediaStore IDs. */
    private fun stableSafId(uri: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray(Charsets.UTF_8))
        val value = ByteBuffer.wrap(digest, 0, Long.SIZE_BYTES).long
        return Long.MIN_VALUE or (value and Long.MAX_VALUE)
    }

    /**
     * Migrate old positive SAF hashes once per track, while preserving possible MediaStore IDs.
     *
     * Each migrated new ID is recorded. Without that record a legacy favorite or playlist
     * entry would be copied again on every scan, so removing a migrated track from
     * favorites or a playlist would not survive the next library load.
     */
    private fun migrateLegacySafIds(uris: List<String>) {
        val migrated = prefs.getStringSet(MIGRATED_SAF_IDS_KEY, emptySet()).orEmpty()
        val legacyToNew = uris.groupBy { it.hashCode().toLong() and Long.MAX_VALUE }
            .mapValues { (_, sameHashUris) -> sameHashUris.map(::stableSafId).filterNot { it.toString() in migrated }.toSet() }
            .filterValues { it.isNotEmpty() }
        if (legacyToNew.isEmpty()) return

        try {
            synchronized(favoritesLock) {
                val favorites = getFavoriteIds().toMutableSet()
                legacyToNew.forEach { (legacyId, newIds) ->
                    if (legacyId.toString() in favorites) newIds.forEach { favorites.add(it.toString()) }
                }
                prefs.edit().putStringSet("favorite_ids", favorites).apply()
            }

            val editor = playlistPrefs.edit()
            playlistPrefs.all.forEach { (key, value) ->
                if (!key.startsWith("pl_") || value !is String) return@forEach
                val json = runCatching { JSONObject(value) }.getOrNull()
                val separator = value.indexOf(":::")
                val originalIds = json?.optJSONArray("songIds")?.let { array ->
                    (0 until array.length()).mapNotNull { array.optLong(it) }
                } ?: if (separator >= 0) value.substring(separator + 3).split(',').mapNotNull(String::toLongOrNull) else return@forEach
                val ids = originalIds.toMutableList()
                var changed = false
                ids.toList().forEach { oldId ->
                    legacyToNew[oldId]?.forEach { newId ->
                        if (newId !in ids) {
                            ids.add(newId)
                            changed = true
                        }
                    }
                }
                if (changed) {
                    val name = json?.optString("name", "Playlist") ?: value.substring(0, separator)
                    editor.putString(key, Playlist(key.removePrefix("pl_"), name, ids).toJson())
                }
            }
            editor.apply()

            val lyricsDir = lyricsCacheDir
            legacyToNew.forEach { (oldId, newIds) ->
                val oldFile = java.io.File(lyricsDir, "$oldId.lrc")
                if (oldFile.isFile) newIds.forEach { newId ->
                    val newFile = java.io.File(lyricsDir, "$newId.lrc")
                    if (!newFile.exists()) oldFile.copyTo(newFile)
                }
            }
            val nowMigrated = migrated + legacyToNew.values.flatten().map(Long::toString)
            prefs.edit().putStringSet(MIGRATED_SAF_IDS_KEY, nowMigrated).apply()
        } catch (e: Throwable) {
            android.util.Log.e("VoidPlayer", "Failed to migrate legacy SAF track IDs", e)
        }
    }

    override suspend fun loadArt(uriString: String): ByteArray? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, android.net.Uri.parse(uriString))
            retriever.embeddedPicture?.let { com.tushar.voidplayer.ArtworkStore.downsampleToJpeg(it) }
        } catch (e: Throwable) {
            android.util.Log.e("VoidPlayer", "Failed to load art for $uriString", e)
            null
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    override suspend fun readSongTags(song: Song): com.tushar.voidplayer.model.SongTagEdits? = withContext(Dispatchers.IO) {
        val uri = android.net.Uri.parse(song.uri)
        tagEditor.read(uri, directPath = mediaStorePath(uri))?.let { tags ->
            com.tushar.voidplayer.model.SongTagEdits(
                title = tags.title, artist = tags.artist, album = tags.album, year = tags.year,
                genre = tags.genre, track = tags.track, bpm = tags.bpm, musicalKey = tags.key, lyrics = tags.lyrics
            )
        }
    }

    /** File path of a MediaStore item, used only to skip a full temp copy when it is readable. */
    private fun mediaStorePath(uri: android.net.Uri): String? {
        if (uri.authority != MediaStore.AUTHORITY) return null
        return try {
            @Suppress("DEPRECATION")
            val column = MediaStore.Audio.Media.DATA
            context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    override suspend fun updateSongTags(song: Song, edits: com.tushar.voidplayer.model.SongTagEdits): Boolean {
        val uri = android.net.Uri.parse(song.uri)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && uri.authority == MediaStore.AUTHORITY) {
            if (!MediaStoreWriteAccess.request(uri)) return false
        }
        val saved = tagEditor.write(uri, AudioTagFields(
            title = edits.title,
            artist = edits.artist,
            album = edits.album,
            year = edits.year,
            genre = edits.genre,
            track = edits.track,
            bpm = edits.bpm,
            key = edits.musicalKey,
            lyrics = edits.lyrics
        ))
        if (saved) {
            val cacheKey = if (song.id < 0L) "saf:${song.id}" else "media:${song.id}"
            synchronized(tagCacheLock) { tagCache.remove(cacheKey) }
            persistTagCache()
        }
        return saved
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
            if (uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY) {
                // RELATIVE_PATH needs API 29; older versions still get embedded lyrics below.
                val projection = arrayOf(MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.RELATIVE_PATH)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME))
                        val relativePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH))
                        val basename = name.substringBeforeLast('.')
                        val siblings = MediaStore.Files.getContentUri("external")
                        val siblingProjection = arrayOf(MediaStore.Files.FileColumns._ID)
                        val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ? AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} IN (?, ?)"
                        val args = arrayOf(relativePath, "$basename.lrc", "$basename.LRC")
                        context.contentResolver.query(siblings, siblingProjection, selection, args, null)?.use { lrcCursor ->
                            if (lrcCursor.moveToFirst()) {
                                val lrcUri = ContentUris.withAppendedId(
                                    siblings,
                                    lrcCursor.getLong(lrcCursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                                )
                                context.contentResolver.openInputStream(lrcUri)?.use { stream ->
                                    return@withContext stream.bufferedReader().use { it.readText() }
                                }
                            }
                        }
                    }
                }
            } else {
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
            }
            // No companion .lrc: fall back to embedded USLT / LYRICS tags (MediaStore and SAF).
            val embeddedLyrics = tagEditor.read(uri, directPath = mediaStorePath(uri))?.lyrics
            if (!embeddedLyrics.isNullOrBlank()) return@withContext embeddedLyrics
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
        treeUri: android.net.Uri,
        audioFiles: MutableList<SafAudioFile>
    ) {
        val stack = ArrayDeque<String>()
        val visited = HashSet<String>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        stack.addLast(rootId)

        while (stack.isNotEmpty()) {
            val parentId = stack.removeLast()
            if (!visited.add(parentId)) continue
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE
            )
            try {
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val modifiedColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    val sizeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(idColumn) ?: continue
                        val name = cursor.getString(nameColumn)
                        val mime = cursor.getString(mimeColumn)
                        when {
                            mime == DocumentsContract.Document.MIME_TYPE_DIR -> stack.addLast(id)
                            isValidAudioFile(name) -> {
                                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                                audioFiles += SafAudioFile(
                                    documentUri,
                                    name ?: "Unknown",
                                    if (modifiedColumn >= 0) cursor.getLong(modifiedColumn) else 0L,
                                    if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) cursor.getLong(sizeColumn) else -1L
                                )
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                android.util.Log.w("VoidPlayer", "Could not enumerate SAF directory", error)
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

private const val MIGRATED_SAF_IDS_KEY = "migrated_saf_ids"

/** Scan-time tag reads skip temp-copying files larger than this when no direct path is readable. */
private const val SCAN_TAG_COPY_LIMIT_BYTES = 150L * 1024L * 1024L

private fun Playlist.toJson(): String = JSONObject()
    .put("name", name)
    .put("songIds", org.json.JSONArray(songIds))
    .toString()
