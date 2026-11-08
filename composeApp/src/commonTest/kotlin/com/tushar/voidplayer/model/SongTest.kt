package com.tushar.voidplayer.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SongTest {

    private val song = Song(id = 1L, title = "Track", artist = "Artist", duration = 1000L, uri = "content://media/1")

    @Test
    fun testFavoriteToggleProducesUnequalValue() {
        assertNotEquals(song, song.copy(isFavorite = true))
    }

    @Test
    fun testMappedListWithToggledFavoriteIsUnequal() {
        // Mirrors App.onToggleFavorite: the new list must not be equal, or Compose drops the write.
        val songs = listOf(song, song.copy(id = 2L, uri = "content://media/2"))
        val updated = songs.map { if (it.id == 1L) it.copy(isFavorite = true) else it }
        assertNotEquals(songs, updated)
    }

    @Test
    fun testStateFlowAcceptsArtworkAndDurationUpdates() {
        val flow = MutableStateFlow(song)
        val art = byteArrayOf(1, 2, 3)

        flow.value = song.copy(coverArt = art)
        assertSame(art, flow.value.coverArt)

        flow.value = flow.value.copy(duration = 245_000L)
        assertEquals(245_000L, flow.value.duration)
    }

    @Test
    fun testIsSameTrackIgnoresMetadata() {
        assertTrue(song.isSameTrack(song.copy(isFavorite = true, coverArt = byteArrayOf(9), title = "Renamed")))
        assertFalse(song.isSameTrack(song.copy(id = 2L)))
        assertFalse(song.isSameTrack(song.copy(uri = "content://media/other")))
        assertFalse(song.isSameTrack(null))
    }
}
