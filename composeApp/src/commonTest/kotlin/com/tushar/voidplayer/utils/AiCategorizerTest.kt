package com.tushar.voidplayer.utils

import com.tushar.voidplayer.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiCategorizerTest {

    @Test
    fun definitionsContainAllExpectedCategories() {
        val defs = AiCategorizer.definitions
        assertEquals(9, defs.size)
        val ids = defs.map { it.id }.toSet()
        assertTrue("night_vibes" in ids)
        assertTrue("high_energy" in ids)
        assertTrue("deep_focus" in ids)
        assertTrue("romance" in ids)
        assertTrue("extended" in ids)
        assertTrue("euphoric_pop" in ids)
        assertTrue("late_night_drive" in ids)
        assertTrue("acoustic_folk" in ids)
        assertTrue("heavy_metal" in ids)
    }

    @Test
    fun classifiesNightVibesFromLofiGenre() {
        val song = Song(
            id = 1L,
            title = "Midnight Coffee",
            artist = "Chill Artist",
            duration = 150_000L,
            uri = "content://media/1",
            genre = "Lo-Fi",
            bpm = 75,
            acousticEnergy = 0.28f
        )
        val (category, confidence) = AiCategorizer.classifySong(song)
        assertEquals("night_vibes", category.id)
        assertTrue(confidence in 0.45f..0.99f)
    }

    @Test
    fun classifiesHighEnergyFromWorkoutMetalGenre() {
        val song = Song(
            id = 2L,
            title = "Power Run",
            artist = "Beast Band",
            duration = 180_000L,
            uri = "content://media/2",
            genre = "Rock",
            bpm = 140,
            acousticEnergy = 0.88f
        )
        val (category, confidence) = AiCategorizer.classifySong(song)
        assertEquals("high_energy", category.id)
        assertTrue(confidence in 0.45f..0.99f)
    }

    @Test
    fun classifiesDeepFocusFromClassicalAndPiano() {
        val song = Song(
            id = 3L,
            title = "Study Sonata in A",
            artist = "Pianist",
            duration = 200_000L,
            uri = "content://media/3",
            genre = "Classical",
            bpm = 80,
            acousticEnergy = 0.30f
        )
        val (category, _) = AiCategorizer.classifySong(song)
        assertEquals("deep_focus", category.id)
    }

    @Test
    fun classifiesRomanceFromLoveKeywordsAndSoulGenre() {
        val song = Song(
            id = 4L,
            title = "Forever in Love With You",
            artist = "Soul Singer",
            duration = 210_000L,
            uri = "content://media/4",
            genre = "R&B",
            bpm = 90,
            acousticEnergy = 0.45f
        )
        val (category, _) = AiCategorizer.classifySong(song)
        assertEquals("romance", category.id)
    }

    @Test
    fun classifiesExtendedForTracksOverFiveMinutesWithProgKeywords() {
        val song = Song(
            id = 5L,
            title = "Epic Odyssey Part 1",
            artist = "Prog Master",
            duration = 450_000L, // 7.5 minutes
            uri = "content://media/5",
            genre = "Prog",
            bpm = 110,
            acousticEnergy = 0.60f
        )
        val (category, _) = AiCategorizer.classifySong(song)
        assertEquals("extended", category.id)
    }

    @Test
    fun classifiesHeavyMetalFromDistortionAndThrash() {
        val song = Song(
            id = 6L,
            title = "Crush and Bleed",
            artist = "Death Metal Band",
            duration = 190_000L,
            uri = "content://media/6",
            genre = "Death Metal",
            bpm = 160,
            acousticEnergy = 0.95f
        )
        val (category, _) = AiCategorizer.classifySong(song)
        assertEquals("heavy_metal", category.id)
    }

    @Test
    fun classifiesEuphoricPopFromDancePopKeywords() {
        val song = Song(
            id = 7L,
            title = "Summer Sunshine Smile",
            artist = "Pop Queen",
            duration = 180_000L,
            uri = "content://media/7",
            genre = "Dance-Pop",
            bpm = 120,
            acousticEnergy = 0.75f
        )
        val (category, _) = AiCategorizer.classifySong(song)
        assertEquals("euphoric_pop", category.id)
    }

    @Test
    fun classifiesLateNightDriveFromSynthwaveGenre() {
        val song = Song(
            id = 8L,
            title = "Highway Velocity 80s",
            artist = "Outrun Pilot",
            duration = 220_000L,
            uri = "content://media/8",
            genre = "Synthwave",
            bpm = 115,
            acousticEnergy = 0.65f
        )
        val (category, _) = AiCategorizer.classifySong(song)
        assertEquals("late_night_drive", category.id)
    }

    @Test
    fun classifiesAcousticFolkFromFolkGenreAndGuitarKeywords() {
        val song = Song(
            id = 9L,
            title = "Mountain Cabin Campfire",
            artist = "Acoustic Wanderer",
            duration = 180_000L,
            uri = "content://media/9",
            genre = "Acoustic",
            bpm = 95,
            acousticEnergy = 0.35f
        )
        val (category, _) = AiCategorizer.classifySong(song)
        assertEquals("acoustic_folk", category.id)
    }

    @Test
    fun categorizeEmptySongListReturnsEmpty() {
        val result = AiCategorizer.categorize(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun categorizeAssignsDominantMoodWhenBlankAndPreservesWhenPresent() {
        val songBlank = Song(
            id = 10L,
            title = "Night Rain",
            artist = "Chill",
            duration = 120_000L,
            uri = "content://media/10",
            genre = "Lo-Fi",
            dominantMood = ""
        )
        val songCustom = Song(
            id = 11L,
            title = "Night Rain 2",
            artist = "Chill",
            duration = 120_000L,
            uri = "content://media/11",
            genre = "Lo-Fi",
            dominantMood = "My Custom Mood"
        )

        val categories = AiCategorizer.categorize(listOf(songBlank, songCustom))
        assertFalse(categories.isEmpty())
        val nightCategory = categories.firstOrNull { it.id == "night_vibes" }
        assertTrue(nightCategory != null)
        val classifiedBlank = nightCategory.songs.first { it.id == 10L }
        val classifiedCustom = nightCategory.songs.first { it.id == 11L }

        assertEquals("Night Vibes & Lo-Fi", classifiedBlank.dominantMood)
        assertEquals("My Custom Mood", classifiedCustom.dominantMood)
    }

    @Test
    fun zeroOrNegativeBpmUsesNeutralScoreBaseline() {
        val songZeroBpm = Song(
            id = 12L,
            title = "Ambient Track",
            artist = "Artist",
            duration = 120_000L,
            uri = "content://media/12",
            genre = "Ambient",
            bpm = 0
        )
        val (cat1, conf1) = AiCategorizer.classifySong(songZeroBpm)
        assertTrue(conf1 >= 0.45f)

        val songNegativeBpm = songZeroBpm.copy(bpm = -10)
        val (cat2, conf2) = AiCategorizer.classifySong(songNegativeBpm)
        assertEquals(cat1.id, cat2.id)
        assertEquals(conf1, conf2)
    }
}

