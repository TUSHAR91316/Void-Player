package com.tushar.voidplayer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** The two app themes. [storageKey] is what the repository persists. */
enum class VoidThemeMode(val storageKey: String) {
    /** 2.4 default: flat graphite surfaces, hairlines, one accent, no gradient washes. */
    Graphite("graphite"),

    /** The original 2.3 look, kept for people who prefer it. */
    Classic("classic");

    companion object {
        fun fromKey(key: String?): VoidThemeMode = entries.firstOrNull { it.storageKey == key } ?: Graphite
    }
}

/** Every color the UI reads. Matches the Windows graphite tokens (desktop-winui/App.xaml). */
data class VoidPalette(
    val background: Color,
    val pane: Color,
    val elevated: Color,
    val selected: Color,
    val variant: Color,
    val hairline: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val defaultAccent: Color,
    val onAccent: Color,
    val favorite: Color,
    /** True for Graphite: screens skip gradient washes, glows and accent-tinted fills. */
    val flat: Boolean
)

val GraphitePalette = VoidPalette(
    background = Color(0xFF0F1012),
    pane = Color(0xFF131417),
    elevated = Color(0xFF17191C),
    selected = Color(0xFF1F2226),
    variant = Color(0xFF1F2226),
    hairline = Color(0xFF2A2D32),
    primaryText = Color(0xFFEDEEF0),
    secondaryText = Color(0xFFA9AEB5),
    tertiaryText = Color(0xFF868C94),
    defaultAccent = Color(0xFF3CCF8E),
    onAccent = Color(0xFF06140D),
    favorite = Color(0xFFF2637E),
    flat = true
)

val ClassicPalette = VoidPalette(
    background = Color(0xFF121212),
    pane = Color(0xFF1E1E1E),
    elevated = Color(0xFF1E1E1E),
    selected = Color(0xFF2C2C2C),
    variant = Color(0xFF2C2C2C),
    hairline = Color.White.copy(alpha = 0.1f),
    primaryText = Color.White,
    secondaryText = Color(0xFFAAAAAA),
    tertiaryText = Color(0xFFAAAAAA),
    defaultAccent = Color(0xFF1DB954),
    onAccent = Color.Black,
    favorite = Color(0xFFFF4081),
    flat = false
)

/**
 * App-wide theme state. [mode] is Compose state, so every composable that reads a color below
 * recomposes when the user switches themes in Settings; no restart needed.
 */
object VoidTheme {
    var mode by mutableStateOf(VoidThemeMode.Graphite)

    val palette: VoidPalette
        get() = if (mode == VoidThemeMode.Classic) ClassicPalette else GraphitePalette

    val isFlat: Boolean get() = palette.flat

    /**
     * Album-derived accents are often too dark to read on the background. Graphite lightens them
     * toward white in 15 % steps until they reach 4.5:1 (same rule as the Windows app). Classic
     * keeps the extracted color exactly as 2.3 did.
     */
    fun readableAccent(color: Color): Color =
        if (palette.flat) ensureContrast(color, palette.background) else color

    fun ensureContrast(color: Color, background: Color, minimum: Double = 4.5): Color {
        var result = color.copy(alpha = 1f)
        repeat(20) {
            if (contrastRatio(result, background) >= minimum) return result
            result = Color(
                red = result.red + (1f - result.red) * 0.15f,
                green = result.green + (1f - result.green) * 0.15f,
                blue = result.blue + (1f - result.blue) * 0.15f
            )
        }
        return result
    }

    fun contrastRatio(first: Color, second: Color): Double {
        val a = first.luminance().toDouble()
        val b = second.luminance().toDouble()
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}

// The names every screen already uses. They read the active palette on each access.
val SurfaceBackground: Color get() = VoidTheme.palette.background
val SurfaceElevated: Color get() = VoidTheme.palette.elevated
val SurfaceVariant: Color get() = VoidTheme.palette.variant
val SurfacePane: Color get() = VoidTheme.palette.pane
val SurfaceSelected: Color get() = VoidTheme.palette.selected
val HairlineColor: Color get() = VoidTheme.palette.hairline
val PrimaryText: Color get() = VoidTheme.palette.primaryText
val SecondaryText: Color get() = VoidTheme.palette.secondaryText
val TertiaryText: Color get() = VoidTheme.palette.tertiaryText
val DefaultAccent: Color get() = VoidTheme.palette.defaultAccent
val OnAccent: Color get() = VoidTheme.palette.onAccent
val FavoriteColor: Color get() = VoidTheme.palette.favorite
val DividerColor: Color get() = VoidTheme.palette.hairline

val ModernScheme: ColorScheme
    get() = darkColorScheme(
        primary = DefaultAccent,
        onPrimary = OnAccent,
        background = SurfaceBackground,
        surface = SurfaceElevated,
        onSurface = PrimaryText,
        surfaceVariant = SurfaceVariant,
        onSurfaceVariant = SecondaryText,
        outline = HairlineColor
    )
