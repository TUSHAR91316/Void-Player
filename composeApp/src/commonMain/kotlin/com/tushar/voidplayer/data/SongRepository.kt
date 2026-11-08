package com.tushar.voidplayer.data

import com.tushar.voidplayer.model.Playlist
import com.tushar.voidplayer.model.Song
import com.tushar.voidplayer.model.SongTagEdits

data class LyricsSearchResult(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationSeconds: Int,
    val syncedLyrics: String?,
    val plainLyrics: String?
)

interface SongRepository {
    suspend fun getSongs(): List<Song>
    suspend fun loadFromFolder(uriString: String): List<Song>
    suspend fun loadArt(uriString: String): ByteArray?

    /** Current tags read from the file, used to prefill the editor. Null when unreadable. */
    suspend fun readSongTags(song: Song): SongTagEdits? = null

    /**
     * Write locally selected tag edits. A blank field removes that tag, so callers must
     * prefill every field from [readSongTags]. Unsupported platforms return false.
     */
    suspend fun updateSongTags(song: Song, edits: SongTagEdits): Boolean = false
    suspend fun toggleFavorite(songId: Long, isFav: Boolean)
    suspend fun loadLyrics(song: Song): String?
    suspend fun loadLyrics(uriString: String): String?
    suspend fun fetchOnlineLyrics(song: Song): String?
    suspend fun searchOnlineLyrics(query: String): List<LyricsSearchResult>
    suspend fun isOnlineLyricsEnabled(): Boolean
    suspend fun setOnlineLyricsEnabled(enabled: Boolean)
    /** Persisted theme key ([com.tushar.voidplayer.ui.theme.VoidThemeMode.storageKey]); read synchronously at startup. */
    fun uiTheme(): String = "graphite"
    fun setUiTheme(key: String) {}
    fun isFdroidBuild(): Boolean = false
    fun appVersionName(): String = ""
    suspend fun saveLyrics(song: Song, lrcContent: String)
    suspend fun getPlaylists(): List<Playlist>
    suspend fun savePlaylist(playlist: Playlist)
    suspend fun deletePlaylist(playlistId: String)
    suspend fun checkForUpdates(): com.tushar.voidplayer.utils.UpdateInfo
}
