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
        assertFalse(UpdateChecker.isVersionNewer("2.4", "2.4.0"))
        assertFalse(UpdateChecker.isVersionNewer("2.3.9", "2.4.0"))
        assertFalse(UpdateChecker.isVersionNewer("", "2.4.0"))
    }

    @Test
    fun searchTermsKeepDotsInsideTitlesAndArtists() {
        assertEquals("Mr. Brightside", LrclibApi.cleanSearchTerm("Mr. Brightside"))
        assertEquals("P.O.D.", LrclibApi.cleanSearchTerm("P.O.D."))
    }

    @Test
    fun searchTermsDropFileExtensionsAndVideoNoise() {
        assertEquals("Clair de Lune", LrclibApi.cleanSearchTerm("Clair de Lune.flac"))
        assertEquals("Song", LrclibApi.cleanSearchTerm("Song (Official Video)"))
        assertEquals("Song", LrclibApi.cleanSearchTerm("Song (feat. Someone)"))
        assertEquals("Song", LrclibApi.cleanSearchTerm("Song [Remastered 2011]"))
    }
}
