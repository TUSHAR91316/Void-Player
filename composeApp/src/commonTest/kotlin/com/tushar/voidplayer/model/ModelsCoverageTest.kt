package com.tushar.voidplayer.model

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ModelsCoverageTest {

    @Test
    fun testSongTagEditsPropertiesAndCopy() {
        val edits = SongTagEdits(
            title = "New Title",
            artist = "New Artist",
            album = "New Album",
            year = "2026",
            genre = "Electronic",
            track = "1",
            bpm = "128",
            musicalKey = "8A",
            lyrics = "[00:01.00]Hello world"
        )

        assertEquals("New Title", edits.title)
        assertEquals("New Artist", edits.artist)
        assertEquals("New Album", edits.album)
        assertEquals("2026", edits.year)
        assertEquals("Electronic", edits.genre)
        assertEquals("1", edits.track)
        assertEquals("128", edits.bpm)
        assertEquals("8A", edits.musicalKey)
        assertEquals("[00:01.00]Hello world", edits.lyrics)

        val modified = edits.copy(title = "Updated Title")
        assertEquals("Updated Title", modified.title)
        assertEquals(edits.artist, modified.artist)
        assertNotEquals(edits, modified)
        assertEquals(edits, edits.copy())
        assertEquals(edits.hashCode(), edits.copy().hashCode())
        assertTrue(edits.toString().contains("New Title"))
    }

    @Test
    fun testPlaylistDefaultsAndOperations() {
        val defaultPlaylist = Playlist(id = "p1", name = "Favorites Mix")
        assertEquals("p1", defaultPlaylist.id)
        assertEquals("Favorites Mix", defaultPlaylist.name)
        assertTrue(defaultPlaylist.songIds.isEmpty())

        val withSongs = defaultPlaylist.copy(songIds = listOf(10L, 20L, 30L))
        assertEquals(3, withSongs.songIds.size)
        assertEquals(10L, withSongs.songIds[0])
        assertNotEquals(defaultPlaylist, withSongs)
        assertEquals(withSongs, withSongs.copy())
        assertEquals(withSongs.hashCode(), withSongs.copy().hashCode())
    }

    @Test
    fun testAiCategoryDefaultsAndProperties() {
        val song = Song(1L, "Song", "Artist", duration = 100_000L, uri = "content://1")
        val category = AiCategory(
            id = "cat_1",
            title = "Chill Out",
            description = "Mellow beats",
            emoji = "",
            gradientColors = listOf(Color.Blue, Color.Cyan),
            songs = listOf(song)
        )

        assertEquals("cat_1", category.id)
        assertEquals("Chill Out", category.title)
        assertEquals("Mellow beats", category.description)
        assertEquals(0.95f, category.confidence)
        assertEquals("Flat", category.recommendedEq)
        assertEquals(0.5f, category.averageEnergy)
        assertEquals(1, category.songs.size)

        val updatedCategory = category.copy(confidence = 0.88f, recommendedEq = "Warm")
        assertEquals(0.88f, updatedCategory.confidence)
        assertEquals("Warm", updatedCategory.recommendedEq)
        assertNotEquals(category, updatedCategory)
    }

    @Test
    fun testSongFullFieldInitializationAndIsSameTrack() {
        val song1 = Song(
            id = 500L,
            title = "Deep Resonance",
            artist = "Void Artist",
            album = "Space Drift",
            duration = 240_000L,
            uri = "content://media/external/audio/media/500",
            coverArt = byteArrayOf(1, 2, 3),
            isFavorite = true,
            genre = "Ambient",
            bpm = 90,
            musicalKey = "5A",
            acousticEnergy = 0.35f,
            dominantMood = "Night Vibes"
        )

        assertEquals(500L, song1.id)
        assertEquals("Deep Resonance", song1.title)
        assertEquals("Void Artist", song1.artist)
        assertEquals("Space Drift", song1.album)
        assertEquals(240_000L, song1.duration)
        assertTrue(song1.isFavorite)
        assertEquals("Ambient", song1.genre)
        assertEquals(90, song1.bpm)
        assertEquals("5A", song1.musicalKey)
        assertEquals(0.35f, song1.acousticEnergy)
        assertEquals("Night Vibes", song1.dominantMood)

        val song1MetadataModified = song1.copy(
            title = "Changed Title",
            isFavorite = false,
            dominantMood = "Different Mood"
        )

        // isSameTrack should still be true because physical ID and URI match
        assertTrue(song1.isSameTrack(song1MetadataModified))

        // Different ID or URI must be false
        assertFalse(song1.isSameTrack(song1.copy(id = 501L)))
        assertFalse(song1.isSameTrack(song1.copy(uri = "content://different")))
        assertFalse(song1.isSameTrack(null))
    }

    @Test
    fun testLyricsSearchResultPropertiesAndDataCopy() {
        val result = com.tushar.voidplayer.data.LyricsSearchResult(
            id = 12345L,
            trackName = "Solaris",
            artistName = "Photay",
            albumName = "Oneness",
            durationSeconds = 215,
            syncedLyrics = "[00:10.00]First line",
            plainLyrics = "First line"
        )

        assertEquals(12345L, result.id)
        assertEquals("Solaris", result.trackName)
        assertEquals("Photay", result.artistName)
        assertEquals("Oneness", result.albumName)
        assertEquals(215, result.durationSeconds)
        assertEquals("[00:10.00]First line", result.syncedLyrics)
        assertEquals("First line", result.plainLyrics)

        val modified = result.copy(syncedLyrics = null)
        assertTrue(modified.syncedLyrics == null)
        assertNotEquals(result, modified)
        assertEquals(result, result.copy())
    }

    @Test
    fun testUpdateInfoPropertiesAndDataCopy() {
        val info = com.tushar.voidplayer.utils.UpdateInfo(
            isUpdateAvailable = true,
            latestVersion = "2.4.0",
            currentVersion = "2.3.0",
            releaseNotes = "Major overhaul",
            downloadUrl = "https://github.com/TUSHAR91316/Void-Player/releases/download/v2.4.0/VoidPlayer-2.4.0-release.apk",
            releasePageUrl = "https://github.com/TUSHAR91316/Void-Player/releases/tag/v2.4.0",
            isFdroidInstall = false
        )

        assertTrue(info.isUpdateAvailable)
        assertEquals("2.4.0", info.latestVersion)
        assertEquals("2.3.0", info.currentVersion)
        assertEquals("Major overhaul", info.releaseNotes)
        assertEquals("https://github.com/TUSHAR91316/Void-Player/releases/tag/v2.4.0", info.releasePageUrl)
        assertFalse(info.isFdroidInstall)

        val fdroidInfo = info.copy(isUpdateAvailable = false, isFdroidInstall = true, downloadUrl = null)
        assertFalse(fdroidInfo.isUpdateAvailable)
        assertTrue(fdroidInfo.isFdroidInstall)
        assertTrue(fdroidInfo.downloadUrl == null)
        assertEquals(info, info.copy())
    }
}

