package com.tushar.voidplayer.utils

import com.tushar.voidplayer.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AiEngineTest {

    @Test
    fun calculateAcousticEnergyHandlesVariousGenres() {
        val metalEnergy = AiEngine.calculateAcousticEnergy("Death Metal", bpm = 150, durationMs = 180_000L)
        assertTrue(metalEnergy > 0.85f, "Metal should have high acoustic energy, got $metalEnergy")

        val rockEnergy = AiEngine.calculateAcousticEnergy("Alternative Rock", bpm = 120, durationMs = 180_000L)
        assertTrue(rockEnergy in 0.70f..0.95f, "Rock should have moderately high energy, got $rockEnergy")

        val popEnergy = AiEngine.calculateAcousticEnergy("Synth Pop", bpm = 110, durationMs = 180_000L)
        assertTrue(popEnergy in 0.55f..0.85f, "Pop should have medium-high energy, got $popEnergy")

        val lofiEnergy = AiEngine.calculateAcousticEnergy("Lo-Fi Chillhop", bpm = 75, durationMs = 150_000L)
        assertTrue(lofiEnergy < 0.40f, "Lo-Fi should have low acoustic energy, got $lofiEnergy")

        val unknownEnergy = AiEngine.calculateAcousticEnergy("Unknown", bpm = 0, durationMs = 180_000L)
        assertEquals(0.55f, unknownEnergy)
    }

    @Test
    fun calculateAcousticEnergyAdjustsForDuration() {
        val baseEnergy = AiEngine.calculateAcousticEnergy("Rock", bpm = 100, durationMs = 180_000L) // 3 mins (no duration modifier)
        val shortEnergy = AiEngine.calculateAcousticEnergy("Rock", bpm = 100, durationMs = 90_000L) // 1.5 mins (+0.06)
        val longEnergy = AiEngine.calculateAcousticEnergy("Rock", bpm = 100, durationMs = 450_000L) // 7.5 mins (-0.08)

        assertTrue(shortEnergy > baseEnergy, "Short track should boost energy")
        assertTrue(longEnergy < baseEnergy, "Long track should lower energy")
    }

    @Test
    fun calculateAcousticEnergyClampsToValidRange() {
        val superHigh = AiEngine.calculateAcousticEnergy("Metal Hardcore Thrash", bpm = 250, durationMs = 60_000L)
        assertTrue(superHigh <= 0.98f)

        val superLow = AiEngine.calculateAcousticEnergy("Meditation Sleep Ambient", bpm = 30, durationMs = 600_000L)
        assertTrue(superLow >= 0.08f)
    }

    @Test
    fun generateHarmonicQueueHandlesEdgeCases() {
        val emptyResult = AiEngine.generateHarmonicQueue(emptyList())
        assertTrue(emptyResult.isEmpty())

        val singleSong = Song(1L, "Only One", "Artist", duration = 100_000L, uri = "content://1")
        val singleResult = AiEngine.generateHarmonicQueue(listOf(singleSong))
        assertEquals(1, singleResult.size)
        assertEquals(singleSong, singleResult[0])
    }

    @Test
    fun generateHarmonicQueueHonorsStartingSongWhenPresent() {
        val song1 = Song(1L, "Song 1", "Artist", duration = 100_000L, uri = "content://1", musicalKey = "8A", bpm = 120)
        val song2 = Song(2L, "Song 2", "Artist", duration = 100_000L, uri = "content://2", musicalKey = "8B", bpm = 120)
        val song3 = Song(3L, "Song 3", "Artist", duration = 100_000L, uri = "content://3", musicalKey = "9A", bpm = 122)

        val queue = AiEngine.generateHarmonicQueue(listOf(song1, song2, song3), startingSong = song3)
        assertEquals(3, queue.size)
        assertEquals(3L, queue[0].id, "Queue should start with song3")
    }

    @Test
    fun generateHarmonicQueueFallsBackToFirstWhenStartingSongNotInList() {
        val song1 = Song(1L, "Song 1", "Artist", duration = 100_000L, uri = "content://1")
        val song2 = Song(2L, "Song 2", "Artist", duration = 100_000L, uri = "content://2")
        val unknownSong = Song(99L, "Alien", "Nobody", duration = 100_000L, uri = "content://99")

        val queue = AiEngine.generateHarmonicQueue(listOf(song1, song2), startingSong = unknownSong)
        assertEquals(2, queue.size)
        assertEquals(1L, queue[0].id, "Should fallback to first song in source pool")
    }

    @Test
    fun generateInsightsForEmptySongsReturnsCleanSlate() {
        val insights = AiEngine.generateInsights(emptyList())
        assertEquals("Silence", insights.dominantVibe)
        assertEquals("The Clean Slate", insights.personalityTitle)
        assertEquals("0m", insights.totalHoursMinutes)
        assertEquals("None", insights.topArtist)
        assertEquals("Flat / Studio", insights.recommendedEq)
        assertTrue(insights.vibeBreakdown.isEmpty())
    }

    @Test
    fun generateInsightsComputesSummaryAndTopArtist() {
        val song1 = Song(1L, "Track 1", "Pink Floyd", duration = 300_000L, uri = "content://1", genre = "Rock", acousticEnergy = 0.8f)
        val song2 = Song(2L, "Track 2", "Pink Floyd", duration = 300_000L, uri = "content://2", genre = "Rock", acousticEnergy = 0.85f)
        val song3 = Song(3L, "Track 3", "Unknown Artist", duration = 120_000L, uri = "content://3", genre = "Ambient", acousticEnergy = 0.2f)

        val insights = AiEngine.generateInsights(listOf(song1, song2, song3))
        assertEquals("Pink Floyd", insights.topArtist)
        assertNotEquals("Silence", insights.dominantVibe)
        assertFalse(insights.personalityTitle.isBlank())
        assertTrue(insights.averageAcousticEnergy > 0f)
        assertTrue(insights.totalHoursMinutes.isNotBlank())
    }

    @Test
    fun generateInsightsHandlesHourDurationAndDifferentPersonalities() {
        // Test hour duration formatting (> 1 hour)
        val longSong = Song(
            id = 1L,
            title = "Epic Suite",
            artist = "Symphony X",
            duration = 3_720_000L, // 1h 2m
            uri = "content://1",
            genre = "Progressive Metal",
            acousticEnergy = 0.9f
        )
        val metalInsights = AiEngine.generateInsights(listOf(longSong))
        assertEquals("1h 2m", metalInsights.totalHoursMinutes)
        assertTrue(
            metalInsights.personalityTitle == "The Heavy Metal Juggernaut" ||
            metalInsights.personalityTitle == "The Symphonic Explorer" ||
            metalInsights.personalityTitle == "The High-Drive Dynamo"
        )

        // Night vibe
        val nightSong = Song(
            id = 2L,
            title = "Sleep Walk",
            artist = "Nocturne",
            duration = 180_000L,
            uri = "content://2",
            genre = "Lo-Fi Night Chill",
            acousticEnergy = 0.2f,
            dominantMood = "Late Night Chill"
        )
        val nightInsights = AiEngine.generateInsights(listOf(nightSong))
        assertEquals("The Midnight Wanderer", nightInsights.personalityTitle)
        assertEquals("Warm Studio / Ambient", nightInsights.recommendedEq)

        // Focus vibe
        val focusSong = Song(
            id = 3L,
            title = "Deep Study",
            artist = "Ambient Mind",
            duration = 180_000L,
            uri = "content://3",
            genre = "Acoustic Ambient Study",
            acousticEnergy = 0.25f,
            dominantMood = "Focus & Study"
        )
        val focusInsights = AiEngine.generateInsights(listOf(focusSong))
        assertEquals("The Analytical Zen", focusInsights.personalityTitle)
        assertEquals("Acoustic Clarity", focusInsights.recommendedEq)

        // Romance vibe
        val romanceSong = Song(
            id = 4L,
            title = "Sweet Heart",
            artist = "Romantic Soul",
            duration = 180_000L,
            uri = "content://4",
            genre = "R&B Soul Ballad",
            acousticEnergy = 0.45f,
            dominantMood = "Romantic Soul"
        )
        val romanceInsights = AiEngine.generateInsights(listOf(romanceSong))
        assertEquals("The Soulful Romantic", romanceInsights.personalityTitle)
        assertEquals("Vocal Pop Boost", romanceInsights.recommendedEq)

        // Euphoric & Pop vibe hits the else branch ("The Eclectic Connoisseur")
        val popSong = Song(
            id = 5L,
            title = "Disco Fever",
            artist = "Pop Queen",
            duration = 180_000L,
            uri = "content://5",
            genre = "Dance-Pop Disco",
            acousticEnergy = 0.70f,
            dominantMood = "Euphoric & Pop"
        )
        val popInsights = AiEngine.generateInsights(listOf(popSong))
        assertEquals("The Eclectic Connoisseur", popInsights.personalityTitle)
        assertEquals("Dynamic Normalization", popInsights.recommendedEq)
    }
}

