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

    @Test
    fun testAllColorTokensAndModernSchemeAccessors() {
        VoidTheme.mode = VoidThemeMode.Graphite
        assertEquals(GraphitePalette.elevated, SurfaceElevated)
        assertEquals(GraphitePalette.variant, SurfaceVariant)
        assertEquals(GraphitePalette.pane, SurfacePane)
        assertEquals(GraphitePalette.selected, SurfaceSelected)
        assertEquals(GraphitePalette.hairline, HairlineColor)
        assertEquals(GraphitePalette.primaryText, PrimaryText)
        assertEquals(GraphitePalette.tertiaryText, TertiaryText)
        assertEquals(GraphitePalette.onAccent, OnAccent)
        assertEquals(GraphitePalette.favorite, FavoriteColor)
        assertEquals(HairlineColor, DividerColor)

        val scheme = ModernScheme
        assertEquals(DefaultAccent, scheme.primary)
        assertEquals(OnAccent, scheme.onPrimary)
        assertEquals(SurfaceBackground, scheme.background)
        assertEquals(SurfaceElevated, scheme.surface)
        assertEquals(PrimaryText, scheme.onSurface)
        assertEquals(SurfaceVariant, scheme.surfaceVariant)
        assertEquals(SecondaryText, scheme.onSurfaceVariant)
        assertEquals(HairlineColor, scheme.outline)

        VoidTheme.mode = VoidThemeMode.Classic
        assertEquals(ClassicPalette.elevated, SurfaceElevated)
        assertEquals(ClassicPalette.variant, SurfaceVariant)
        assertEquals(ClassicPalette.pane, SurfacePane)
        assertEquals(ClassicPalette.selected, SurfaceSelected)
        assertEquals(ClassicPalette.hairline, HairlineColor)
        assertEquals(ClassicPalette.primaryText, PrimaryText)
        assertEquals(ClassicPalette.tertiaryText, TertiaryText)
        assertEquals(ClassicPalette.onAccent, OnAccent)
        assertEquals(ClassicPalette.favorite, FavoriteColor)
    }

    @Test
    fun testEnsureContrastEdgeCases() {
        // Already high contrast
        val white = Color.White
        val black = Color.Black
        assertEquals(white, VoidTheme.ensureContrast(white, black, 4.5))

        // Same color on same background requires maximum lightening iterations
        val adjusted = VoidTheme.ensureContrast(black, black, 4.5)
        assertTrue(adjusted.red > 0f)
        assertTrue(VoidTheme.contrastRatio(adjusted, black) >= 4.5)
    }
}
