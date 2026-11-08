package com.tushar.voidplayer.utils

import com.tushar.voidplayer.model.Song
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiDjFlowTest {

    private fun song(id: Long, genre: String, energy: Float, title: String = "Track $id") = Song(
        id = id,
        title = title,
        artist = "Artist",
        duration = 210_000L,
        uri = "content://media/$id",
        genre = genre,
        acousticEnergy = energy
    )

    private val current = song(1, "metal", 0.94f, "Iron Storm")
    private val heavy = listOf(
        song(2, "heavy metal", 0.93f, "Steel Rage"),
        song(3, "thrash metal", 0.95f, "Blood Battle"),
        song(4, "metalcore", 0.92f, "Crush the Grave")
    )
    private val calm = listOf(
        song(5, "ambient", 0.15f, "Quiet Rain"),
        song(6, "lo-fi", 0.2f, "Midnight Pillow"),
        song(7, "classical", 0.18f, "Piano Sonata")
    )
    private val library = listOf(current) + heavy + calm

    @Test
    fun testNeverPicksTheCurrentSong() {
        repeat(25) { seed ->
            val pick = AiEngine.getAiDjNextSong(current, library, random = Random(seed))
            assertNotNull(pick)
            assertTrue(pick.id != current.id)
        }
    }

    @Test
    fun testPrefersHarmonicallyCloseTracks() {
        val heavyIds = heavy.map { it.id }.toSet()
        repeat(25) { seed ->
            val pick = AiEngine.getAiDjNextSong(current, library, random = Random(seed))
            assertTrue(pick!!.id in heavyIds, "Expected a high-energy metal follow-up, got ${pick.title}")
        }
    }

    @Test
    fun testAvoidsRecentlyPlayedWhileAlternativesExist() {
        val recent = listOf(1L, 2L, 3L)
        repeat(25) { seed ->
            val pick = AiEngine.getAiDjNextSong(current, library, recent, Random(seed))
            assertTrue(pick!!.id !in recent)
        }
    }

    @Test
    fun testFallsBackToRecentWhenEverythingWasPlayed() {
        val pick = AiEngine.getAiDjNextSong(current, library, library.map { it.id }, Random(0))
        assertNotNull(pick)
        assertTrue(pick.id != current.id)
    }

    @Test
    fun testSingleSongLibraryReturnsNull() {
        assertNull(AiEngine.getAiDjNextSong(current, listOf(current)))
    }

    @Test
    fun testTwoSongLibraryReturnsTheOtherSong() {
        val other = calm.first()
        assertEquals(other.id, AiEngine.getAiDjNextSong(current, listOf(current, other))?.id)
    }

    @Test
    fun testCurrentSongMatchedByIdentityNotEquality() {
        // The player's current song can be a copy with art or favorite state the list lacks.
        val currentCopy = current.copy(isFavorite = true, coverArt = byteArrayOf(1))
        repeat(25) { seed ->
            val pick = AiEngine.getAiDjNextSong(currentCopy, library, random = Random(seed))
            assertTrue(pick!!.id != current.id)
        }
    }

    @Test
    fun testHarmonicQueueWithCopiedStartHasNoDuplicates() {
        val queue = AiEngine.generateHarmonicQueue(library, current.copy(isFavorite = true))
        assertEquals(library.size, queue.size)
        assertEquals(library.map { it.id }.toSet(), queue.map { it.id }.toSet())
        assertEquals(current.id, queue.first().id)
    }
}
