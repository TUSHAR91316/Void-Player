package com.tushar.voidplayer.utils

import android.util.Log
import com.tushar.voidplayer.data.LyricsSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Privacy-preserving client for the LRCLIB open synchronized lyrics API.
 * Free, open-source, and does not require API keys or tracking telemetry.
 */
object LrclibApi {

    private const val TAG = "LrclibApi"
    private const val BASE_URL = "https://lrclib.net/api"
    private const val USER_AGENT = "VoidPlayer/2.3 (https://github.com/TUSHAR91316/Void-Player)"
    private const val TIMEOUT_MS = 5000

    /**
     * Attempts to fetch exact matched lyrics for the given track metadata.
     */
    suspend fun getLyrics(
        trackName: String,
        artistName: String,
        albumName: String? = null,
        durationSeconds: Int? = null
    ): LyricsSearchResult? = withContext(Dispatchers.IO) {
        if (trackName.isBlank() || artistName.isBlank()) return@withContext null

        try {
            val cleanTitle = cleanSearchTerm(trackName)
            val cleanArtist = cleanSearchTerm(artistName)

            val params = StringBuilder()
            params.append("track_name=").append(URLEncoder.encode(cleanTitle, "UTF-8"))
            params.append("&artist_name=").append(URLEncoder.encode(cleanArtist, "UTF-8"))

            if (!albumName.isNullOrBlank() && !albumName.contains("unknown", ignoreCase = true)) {
                params.append("&album_name=").append(URLEncoder.encode(cleanSearchTerm(albumName), "UTF-8"))
            }
            if (durationSeconds != null && durationSeconds > 0) {
                params.append("&duration=").append(durationSeconds)
            }

            val url = URL("$BASE_URL/get?$params")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                return@withContext parseJsonResult(json)
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                // If exact match not found, attempt search fallback
                val searchResults = searchLyrics("$cleanTitle $cleanArtist")
                return@withContext searchResults.firstOrNull()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to get lyrics from LRCLIB: ${e.message}")
        }
        null
    }

    /**
     * Searches for lyrics matching a user query string.
     */
    suspend fun searchLyrics(query: String): List<LyricsSearchResult> = withContext(Dispatchers.IO) {
        val cleanQuery = cleanSearchTerm(query)
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val results = mutableListOf<LyricsSearchResult>()
        try {
            val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
            val url = URL("$BASE_URL/search?q=$encodedQuery")

            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(responseText)
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    parseJsonResult(item)?.let { results.add(it) }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to search lyrics on LRCLIB: ${e.message}")
        }
        results
    }

    private fun parseJsonResult(json: JSONObject): LyricsSearchResult? {
        val id = json.optLong("id", 0L)
        val track = json.optString("trackName", "")
        val artist = json.optString("artistName", "")
        val album = json.optString("albumName", "")
        val duration = json.optInt("duration", 0)
        val synced = json.optString("syncedLyrics", "").takeIf { it.isNotBlank() }
        val plain = json.optString("plainLyrics", "").takeIf { it.isNotBlank() }

        if (synced == null && plain == null) return null

        return LyricsSearchResult(
            id = id,
            trackName = track,
            artistName = artist,
            albumName = album,
            durationSeconds = duration,
            syncedLyrics = synced,
            plainLyrics = plain
        )
    }

    private fun cleanSearchTerm(term: String): String {
        // Strip out file extensions and trailing noise like (Official Video), [Remastered], etc.
        return term.substringBeforeLast('.')
            .replace(Regex("""(?i)\(official(\s+video|\s+audio|\s+music\s+video)?\)"""), "")
            .replace(Regex("""(?i)\[official(\s+video|\s+audio)?\]"""), "")
            .replace(Regex("""(?i)\(lyric(\s+video)?\)"""), "")
            .replace(Regex("""(?i)\[remastered[^\]]*\]"""), "")
            .replace(Regex("""(?i)\(remastered[^\)]*\)"""), "")
            .replace(Regex("""(?i)\(feat\.[^\)]*\)"""), "")
            .trim()
    }
}
