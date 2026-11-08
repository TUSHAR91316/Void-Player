package com.tushar.voidplayer.utils

import com.tushar.voidplayer.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmlV2ExtendedTest {

    @Test
    fun foldForSearchReplacesAllSupportedDiacritics() {
        val input = "ÀÁÂÃÄÅÇÈÉÊËÌÍÎÏÑÒÓÔÕÖÙÚÛÜÝàáâãäåçèéêëìíîïñòóôõöùúûüýÿ"
        val expected = "aaaaaaceeeeiiiinooooouuuuyaaaaaaceeeeiiiinooooouuuuyy"
        assertEquals(expected, SmlV2.foldForSearch(input))

        val extended = "āăąćčďđēėęěīįłńňōřśšşťūžźż"
        val expectedExt = "aaaccddeeeeiilnnorssstuzzz"
        assertEquals(expectedExt, SmlV2.foldForSearch(extended))

        val normal = "Hello World 123 !?_"
        assertEquals("hello world 123 !?_", SmlV2.foldForSearch(normal))
    }

    @Test
    fun camelotParsesAllEnharmonicsAndKeys() {
        // Direct Camelot
        assertEquals("1A", SmlV2.camelot("1A"))
        assertEquals("12B", SmlV2.camelot("12b"))

        // Open Key (1m -> 8A, 1d -> 8B)
        assertEquals("8A", SmlV2.camelot("1m"))
        assertEquals("8B", SmlV2.camelot("1d"))
        assertEquals("9A", SmlV2.camelot("2m"))
        assertEquals("9B", SmlV2.camelot("2d"))

        // Standard Note Names with symbols
        assertEquals("8B", SmlV2.camelot("C"))
        assertEquals("8B", SmlV2.camelot("C major"))
        assertEquals("8B", SmlV2.camelot("Cmaj"))
        assertEquals("8B", SmlV2.camelot("CM"))
        assertEquals("8A", SmlV2.camelot("Am"))
        assertEquals("8A", SmlV2.camelot("A minor"))
        assertEquals("8A", SmlV2.camelot("Amin"))

        // Enharmonic flats and sharps
        assertEquals("3B", SmlV2.camelot("Db")) // Db -> C# (3B)
        assertEquals("3B", SmlV2.camelot("D♭"))
        assertEquals("5B", SmlV2.camelot("Eb")) // Eb -> D# (5B)
        assertEquals("2B", SmlV2.camelot("Gb")) // Gb -> F# (2B)
        assertEquals("2B", SmlV2.camelot("F♯"))
        assertEquals("4B", SmlV2.camelot("Ab")) // Ab -> G# (4B)
        assertEquals("6B", SmlV2.camelot("Bb")) // Bb -> A# (6B)
        assertEquals("8B", SmlV2.camelot("B#")) // B# -> C (8B)
        assertEquals("7B", SmlV2.camelot("E#")) // E# -> F (7B)
        assertEquals("1B", SmlV2.camelot("Cb")) // Cb -> B (1B)
        assertEquals("12B", SmlV2.camelot("Fb")) // Fb -> E (12B)

        // Invalid inputs
        assertNull(SmlV2.camelot(""))
        assertNull(SmlV2.camelot("   "))
        assertNull(SmlV2.camelot("InvalidKey"))
        assertNull(SmlV2.camelot("H#"))
    }

    @Test
    fun harmonicScoreEvaluatesDistancesOnWheel() {
        // Unknown key gives neutral 0.5
        assertEquals(0.5, SmlV2.harmonicScore("", "8A"))
        assertEquals(0.5, SmlV2.harmonicScore("8A", ""))

        // Same key = 1.0
        assertEquals(1.0, SmlV2.harmonicScore("8A", "8A"))

        // Adjacent number, same mode = 0.9
        assertEquals(0.9, SmlV2.harmonicScore("8A", "9A"))
        assertEquals(0.9, SmlV2.harmonicScore("8A", "7A"))
        assertEquals(0.9, SmlV2.harmonicScore("12A", "1A")) // Circular wrap-around

        // Same number, mode change = 0.9
        assertEquals(0.9, SmlV2.harmonicScore("8A", "8B"))

        // Diagonal (adjacent number + mode change) = 0.7
        assertEquals(0.7, SmlV2.harmonicScore("8A", "9B"))
        assertEquals(0.7, SmlV2.harmonicScore("8A", "7B"))

        // 2 steps distance = 0.6
        assertEquals(0.6, SmlV2.harmonicScore("8A", "10A"))

        // 3 steps = 0.45 (with delta for floating point)
        assertTrue(kotlin.math.abs(SmlV2.harmonicScore("8A", "11A") - 0.45) < 1e-6)

        // Maximum clash (opposite on wheel, distance 6) -> 0.0
        assertEquals(0.0, SmlV2.harmonicScore("8A", "2A"))
    }

    @Test
    fun effectiveBpmDeltaAndTempoScore() {
        assertEquals(-1, SmlV2.effectiveBpmDelta(0, 120))
        assertEquals(-1, SmlV2.effectiveBpmDelta(120, -5))
        assertEquals(0, SmlV2.effectiveBpmDelta(120, 120))
        assertEquals(0, SmlV2.effectiveBpmDelta(60, 120)) // Half-time
        assertEquals(0, SmlV2.effectiveBpmDelta(140, 70)) // Double-time
        assertEquals(5, SmlV2.effectiveBpmDelta(125, 120))

        // Unknown tempo returns 0.6
        assertEquals(0.6, SmlV2.tempoScore(0, 120))
        assertEquals(0.6, SmlV2.tempoScore(120, 0))

        // Exact match (including half/double) = 1.0
        assertEquals(1.0, SmlV2.tempoScore(128, 128))
        assertEquals(1.0, SmlV2.tempoScore(64, 128))

        // Over 25% change drops to 0.0
        assertEquals(0.0, SmlV2.tempoScore(100, 140))
    }

    @Test
    fun filterRadioCandidateIdsPrioritizesUnplayed() {
        val candidates = listOf("s1", "s2", "s3", "s4")
        val recent = setOf("s1", "s2")
        val filtered = SmlV2.filterRadioCandidateIds(candidates, recent)
        assertEquals(listOf("s3", "s4"), filtered)

        // When all candidates were recently played, fall back to all candidates
        val allRecent = setOf("s1", "s2", "s3", "s4")
        val fallback = SmlV2.filterRadioCandidateIds(candidates, allRecent)
        assertEquals(candidates, fallback)
    }

    @Test
    fun filterTempoJumpsProtectsAgainstBigBpmDiscontinuities() {
        val candidates = listOf(120, 125, 140, 180, 0) // 0 means untagged
        val filtered = SmlV2.filterTempoJumps(120, candidates) { it }
        // 120 (delta 0), 125 (delta 5), 140 (delta 20), 0 (untagged delta -1 kept)
        // 180 (delta 60) should be dropped
        assertEquals(listOf(120, 125, 140, 0), filtered)

        // Current BPM <= 0 returns all candidates untouched
        val noCurrent = SmlV2.filterTempoJumps(0, candidates) { it }
        assertEquals(candidates, noCurrent)

        // If no candidates qualify, returns original list so radio never stalls
        val extreme = listOf(220)
        val stalledFallback = SmlV2.filterTempoJumps(100, extreme) { it }
        assertEquals(extreme, stalledFallback)
    }

    @Test
    fun pickPoolTakesTopScoresWithinMarginUpToPoolSize() {
        val scored = listOf(
            "a" to 0.95,
            "b" to 0.92, // within 0.05
            "c" to 0.91, // within 0.05
            "d" to 0.905, // within 0.05, but pool size max is 3
            "e" to 0.80  // outside margin
        )
        val pool = SmlV2.pickPool(scored)
        assertEquals(listOf("a", "b", "c"), pool)

        assertTrue(SmlV2.pickPool<String>(emptyList()).isEmpty())
    }

    @Test
    fun targetEnergyFollowsCircadianRhythm() {
        assertEquals(0.55, SmlV2.targetEnergy(12, enabled = false))

        assertEquals(0.55, SmlV2.targetEnergy(6, enabled = true))  // Morning 5..10
        assertEquals(0.65, SmlV2.targetEnergy(14, enabled = true)) // Afternoon 11..16
        assertEquals(0.60, SmlV2.targetEnergy(19, enabled = true)) // Evening 17..21
        assertEquals(0.30, SmlV2.targetEnergy(23, enabled = true)) // Night 22..4
        assertEquals(0.30, SmlV2.targetEnergy(2, enabled = true))  // Night 22..4
    }

    @Test
    fun flowScoreCombinesMoodEnergyTempoHarmonicAndTexture() {
        val song1 = Song(1L, "Happy Summer", "Artist", duration = 180_000L, uri = "content://1", bpm = 120, musicalKey = "8A", dominantMood = "Euphoric")
        val song2 = Song(2L, "Joy Summer", "Artist", duration = 180_000L, uri = "content://2", bpm = 120, musicalKey = "8A", dominantMood = "Euphoric")

        val scoreSame = SmlV2.flowScore(song1, song2)
        assertTrue(scoreSame >= 0.95, "Identical mood, key, tempo and texture should yield high flow score, got $scoreSame")

        val songDiff = Song(3L, "Dark Night", "Artist", duration = 180_000L, uri = "content://3", bpm = 160, musicalKey = "2A", dominantMood = "Heavy")
        val scoreDiff = SmlV2.flowScore(song1, songDiff)
        assertTrue(scoreDiff < scoreSame, "Mismatched song should yield substantially lower flow score")
    }

    @Test
    fun radioScoreAppliesLearnedBiasAndClamps() {
        val song = Song(1L, "Track", "Artist", duration = 180_000L, uri = "content://1")
        val baseScore = SmlV2.radioScore(0.8, song, localHour = 12, learnedBias = 0.0)

        val boostedScore = SmlV2.radioScore(0.8, song, localHour = 12, learnedBias = 0.2)
        assertTrue(boostedScore > baseScore)

        val clampedHigh = SmlV2.radioScore(0.8, song, localHour = 12, learnedBias = 0.99)
        val clampedExpected = SmlV2.radioScore(0.8, song, localHour = 12, learnedBias = 0.25)
        assertEquals(clampedExpected, clampedHigh, "Learned bias should be clamped to 0.25 max")
    }
}
