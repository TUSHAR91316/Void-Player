package com.tushar.voidplayer.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pure helpers behind the opt-in network features (update check, LRCLIB search). */
class NetworkHelpersTest {

    @Test
    fun versionComparisonIsNumericNotLexical() {
        assertTrue(UpdateChecker.isVersionNewer("2.4.0", "2.3"))
        assertTrue(UpdateChecker.isVersionNewer("2.10", "2.9"))
        assertTrue(UpdateChecker.isVersionNewer("3", "2.9.9"))
        assertTrue(UpdateChecker.isVersionNewer("2.3.1", "2.3"))
        assertTrue(UpdateChecker.isVersionNewer("2.4.beta", "2.3.1"))
        assertTrue(UpdateChecker.isVersionNewer("2.4", ""))

        assertFalse(UpdateChecker.isVersionNewer("2.4", "2.4.0"))
        assertFalse(UpdateChecker.isVersionNewer("2.4", "2.4"))
        assertFalse(UpdateChecker.isVersionNewer("2.3.9", "2.4.0"))
        assertFalse(UpdateChecker.isVersionNewer("2.3", "2.3.1"))
        assertFalse(UpdateChecker.isVersionNewer("1.9", "2.0.0"))
        assertFalse(UpdateChecker.isVersionNewer("", "2.4.0"))
        assertFalse(UpdateChecker.isVersionNewer("   ", "2.4.0"))
    }

    @Test
    fun searchTermsKeepDotsInsideTitlesAndArtists() {
        assertEquals("Mr. Brightside", LrclibApi.cleanSearchTerm("Mr. Brightside"))
        assertEquals("P.O.D.", LrclibApi.cleanSearchTerm("P.O.D."))
        assertEquals("Dr. Dre", LrclibApi.cleanSearchTerm("Dr. Dre"))
    }

    @Test
    fun searchTermsDropFileExtensionsAndVideoNoise() {
        assertEquals("Clair de Lune", LrclibApi.cleanSearchTerm("Clair de Lune.flac"))
        assertEquals("Track", LrclibApi.cleanSearchTerm("Track.mp3"))
        assertEquals("Track", LrclibApi.cleanSearchTerm("Track.M4A"))
        assertEquals("Track", LrclibApi.cleanSearchTerm("Track.ogg"))
        assertEquals("Track", LrclibApi.cleanSearchTerm("Track.opus"))
        assertEquals("Track", LrclibApi.cleanSearchTerm("Track.wav"))
        assertEquals("Track", LrclibApi.cleanSearchTerm("Track.aac"))
        assertEquals("Track", LrclibApi.cleanSearchTerm("Track.wma"))

        assertEquals("Song", LrclibApi.cleanSearchTerm("Song (Official Video)"))
        assertEquals("Song", LrclibApi.cleanSearchTerm("Song (official audio)"))
        assertEquals("Song", LrclibApi.cleanSearchTerm("Song (official music video)"))
        assertEquals("Song", LrclibApi.cleanSearchTerm("Song (official)"))
        assertEquals("Song", LrclibApi.cleanSearchTerm("Song [Official Video]"))
        assertEquals("Song", LrclibApi.cleanSearchTerm("Song [official audio]"))
        assertEquals("Song", LrclibApi.cleanSearchTerm("Song [official]"))
        assertEquals("Song", LrclibApi.cleanSearchTerm("Song (Lyric Video)"))
        assertEquals("Song", LrclibApi.cleanSearchTerm("Song (lyric)"))
        assertEquals("Song", LrclibApi.cleanSearchTerm("Song (feat. Someone)"))
        assertEquals("Song", LrclibApi.cleanSearchTerm("Song [Remastered 2011]"))
        assertEquals("Song", LrclibApi.cleanSearchTerm("Song (Remastered 2024)"))
        assertEquals("Song", LrclibApi.cleanSearchTerm("   Song   "))
    }
}
