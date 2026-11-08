package com.tushar.voidplayer.utils

import com.tushar.voidplayer.model.Song
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Behavior of the SML v2.1 radio rules through the real AI DJ entry point. */
class SmlRadioTest {

    private val keys = listOf("8A", "9A", "10A", "8B", "9B", "3A", "4B", "11A")

    private fun track(id: Long, bpm: Int, key: String = keys[(id % keys.size).toInt()], energy: Float = 0.6f) = Song(
        id = id,
        title = "Track $id",
        artist = "Artist",
        duration = 240_000L,
        uri = "content://media/$id",
        genre = "electronic",
        bpm = bpm,
        musicalKey = key,
        acousticEnergy = energy,
        dominantMood = "Drive"
    )

    /** v2.4 acceptance: 100 radio picks, no repeat within the last 50, no BPM jump over 25. */
    @Test
    fun longRadioSessionHasNoRecentRepeatsOrLargeTempoJumps() {
        val library = (0L until 150L).map { id -> track(id, 80 + (id * 7 % 80).toInt(), energy = 0.3f + (id % 7) * 0.1f) }
        val random = Random(42)
        val history = ArrayDeque<Long>()
        var current = library.first()
        repeat(100) {
            val recent = history.takeLast(50)
            val next = AiEngine.getAiDjNextSong(current, library, recent, random, localHour = 20)!!
            assertFalse(next.id in recent, "Track ${next.id} repeated within the last 50 picks")
            val delta = SmlV2.effectiveBpmDelta(current.bpm, next.bpm)
            assertTrue(delta in 0..SmlV2.MAX_RADIO_BPM_JUMP, "BPM jump $delta from ${current.bpm} to ${next.bpm}")
            history.addLast(current.id)
            current = next
        }
    }

    @Test
    fun tempoFilterFallsBackInsteadOfStallingRadio() {
        val current = track(1, 120)
        val farAway = listOf(track(2, 170), track(3, 30))
        val pick = AiEngine.getAiDjNextSong(current, listOf(current) + farAway, random = Random(0))
        assertTrue(pick != null && pick.id != current.id)
    }

    @Test
    fun clearlyWorseTransitionIsNeverPicked() {
        val current = track(1, 120, "8A")
        val perfect = track(2, 120, "8A")
        val clash = track(3, 120, "2B", energy = 0.1f)
        repeat(30) { seed ->
            assertEquals(perfect.id, AiEngine.getAiDjNextSong(current, listOf(current, perfect, clash), random = Random(seed))?.id)
        }
    }

    @Test
    fun keyParserReadsCommonTagFormats() {
        assertEquals("8A", SmlV2.camelot("A minor"))
        assertEquals("11B", SmlV2.camelot("Amaj"))
        assertEquals("8A", SmlV2.camelot("1m"))
        assertEquals("1B", SmlV2.camelot("6d"))
        assertEquals("10B", SmlV2.camelot("DM"))
        assertEquals("7A", SmlV2.camelot("Dm"))
        assertEquals(null, SmlV2.camelot("unknown"))
    }

    @Test
    fun knownKeyClashRanksBelowUnknownKey() {
        assertTrue(SmlV2.harmonicScore("8A", "2A") < SmlV2.harmonicScore("8A", ""))
        assertTrue(SmlV2.harmonicScore("8A", "9A") > SmlV2.harmonicScore("8A", "9B"))
        assertTrue(SmlV2.harmonicScore("8A", "9B") > SmlV2.harmonicScore("8A", "10B"))
    }

    @Test
    fun tempoScoreIsStrictAboutLargeJumpsButAllowsHalfTime() {
        assertEquals(1.0, SmlV2.tempoScore(87, 174), 1e-9)
        assertTrue(SmlV2.tempoScore(120, 124) > 0.8)
        assertEquals(0.0, SmlV2.tempoScore(120, 160), 1e-9)
    }

    @Test
    fun wordMatchingDoesNotHitWordFragments() {
        assertEquals(0.5, SmlV2.features(track(1, 0).copy(title = "Sunday", genre = "nightcore")).valence)
        assertEquals(0.78, SmlV2.features(track(1, 0).copy(title = "Sun goes up", genre = "rock")).valence)
        assertEquals(0.32, SmlV2.features(track(1, 0).copy(title = "Delta Blues", genre = "blues")).valence)
    }
}
