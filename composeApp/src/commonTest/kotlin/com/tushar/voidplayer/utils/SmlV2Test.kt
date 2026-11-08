package com.tushar.voidplayer.utils

import com.tushar.voidplayer.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SmlV2Test {
    @Test
    fun camelotKeyFixturesMatchTheDesktopSpec() {
        assertEquals("8A", SmlV2.camelot("Am"))
        assertEquals("12A", SmlV2.camelot("C#m"))
        assertEquals("8B", SmlV2.camelot("C"))
        assertEquals("2B", SmlV2.camelot("F#"))
        assertEquals("8A", SmlV2.camelot("8a"))
    }

    @Test
    fun harmonicTempoAndCircadianFixturesMatchTheDesktopSpec() {
        assertEquals(1.0, SmlV2.harmonicScore("8A", "Am"))
        assertEquals(0.9, SmlV2.harmonicScore("8A", "8B"))
        assertEquals(1.0, SmlV2.tempoScore(70, 140))
        assertEquals(0.55, SmlV2.targetEnergy(8))
        assertEquals(0.65, SmlV2.targetEnergy(12))
        assertEquals(0.6, SmlV2.targetEnergy(18))
        assertEquals(0.3, SmlV2.targetEnergy(23))
    }

    @Test
    fun featureVectorIsBoundedAndListeningBiasDecays() {
        val song = Song(1, "Summer joy", "Artist", duration = 180_000, uri = "file:///a", bpm = 120, genre = "dance pop")
        val features = SmlV2.features(song)
        assertTrue(listOf(features.energy, features.valence, features.danceability, features.acousticness, features.tempoNorm, features.durationNorm).all { it in 0.0..1.0 })
        val now = 90L * 86_400_000L
        val stats = ListeningStats(completions = 3, skips = 1, lastPlayedEpochMillis = 0)
        val fresh = SmlV2.listeningBias(stats, now)
        val old = SmlV2.listeningBias(stats.copy(lastPlayedEpochMillis = now - 30L * 86_400_000L), now)
        assertNotNull(fresh)
        assertTrue(kotlin.math.abs(old) < kotlin.math.abs(fresh))
    }
}
