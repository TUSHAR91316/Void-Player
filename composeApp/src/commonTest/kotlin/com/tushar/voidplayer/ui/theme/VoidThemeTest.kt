package com.tushar.voidplayer.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoidThemeTest {

    @AfterTest
    fun resetTheme() {
        VoidTheme.mode = VoidThemeMode.Graphite
    }

    @Test
    fun storedKeysRoundTripAndUnknownFallsBackToGraphite() {
        VoidThemeMode.entries.forEach { assertEquals(it, VoidThemeMode.fromKey(it.storageKey)) }
        assertEquals(VoidThemeMode.Graphite, VoidThemeMode.fromKey(null))
        assertEquals(VoidThemeMode.Graphite, VoidThemeMode.fromKey("sepia"))
    }

    @Test
    fun sharedColorNamesFollowTheActiveTheme() {
        VoidTheme.mode = VoidThemeMode.Graphite
        assertEquals(Color(0xFF0F1012), SurfaceBackground)
        assertEquals(Color(0xFF3CCF8E), DefaultAccent)
        assertTrue(VoidTheme.isFlat)

        VoidTheme.mode = VoidThemeMode.Classic
        assertEquals(Color(0xFF121212), SurfaceBackground)
        assertEquals(Color(0xFF1DB954), DefaultAccent)
        assertEquals(Color(0xFFAAAAAA), SecondaryText)
        assertTrue(!VoidTheme.isFlat)
    }

    @Test
    fun graphiteLiftsDarkAlbumAccentsToTextContrast() {
        VoidTheme.mode = VoidThemeMode.Graphite
        val navy = Color(0xFF101060)
        val readable = VoidTheme.readableAccent(navy)
        assertTrue(VoidTheme.contrastRatio(readable, SurfaceBackground) >= 4.5)
        assertTrue(readable.blue > readable.red, "Lightening should keep the blue tint")
    }

    @Test
    fun classicKeepsTheExtractedAccentUntouched() {
        VoidTheme.mode = VoidThemeMode.Classic
        val navy = Color(0xFF101060)
        assertEquals(navy, VoidTheme.readableAccent(navy))
    }

    @Test
    fun readableAccentsAreReturnedUnchanged() {
        VoidTheme.mode = VoidThemeMode.Graphite
        assertEquals(GraphitePalette.defaultAccent, VoidTheme.readableAccent(GraphitePalette.defaultAccent))
    }

    @Test
    fun graphiteTextTokensMeetContrastOnTheirSurfaces() {
        val p = GraphitePalette
        assertTrue(VoidTheme.contrastRatio(p.primaryText, p.background) >= 7.0)
        assertTrue(VoidTheme.contrastRatio(p.secondaryText, p.elevated) >= 4.5)
        assertTrue(VoidTheme.contrastRatio(p.tertiaryText, p.selected) >= 4.5)
        assertTrue(VoidTheme.contrastRatio(p.onAccent, p.defaultAccent) >= 4.5)
    }
}
