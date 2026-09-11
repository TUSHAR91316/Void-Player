package com.tushar.voidplayer.data

import com.tushar.voidplayer.model.Playlist
import com.tushar.voidplayer.model.Song

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
    suspend fun toggleFavorite(songId: Long, isFav: Boolean)
    suspend fun loadLyrics(song: Song): String?
    suspend fun loadLyrics(uriString: String): String?
    suspend fun fetchOnlineLyrics(song: Song): String?
    suspend fun searchOnlineLyrics(query: String): List<LyricsSearchResult>
    suspend fun saveLyrics(song: Song, lrcContent: String)
    suspend fun getPlaylists(): List<Playlist>
    suspend fun savePlaylist(playlist: Playlist)
    suspend fun deletePlaylist(playlistId: String)
    suspend fun checkForUpdates(): com.tushar.voidplayer.utils.UpdateInfo
}
