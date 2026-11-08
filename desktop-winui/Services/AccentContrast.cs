using System;

namespace VoidPlayer.WinUI.Services;

/// <summary>
/// WCAG contrast helpers for the album-derived accent. Plain bytes in and out, so the logic is
/// unit-testable without WinUI types.
/// </summary>
public static class AccentContrast
{
    /// <summary>The graphite ground (VoidGroundColor #0F1012).</summary>
    public static readonly (byte R, byte G, byte B) Ground = (0x0F, 0x10, 0x12);

    public const double MinimumTextContrast = 4.5;

    public static double RelativeLuminance(byte r, byte g, byte b)
    {
        static double Channel(byte value)
        {
            double c = value / 255.0;
            return c <= 0.03928 ? c / 12.92 : Math.Pow((c + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * Channel(r) + 0.7152 * Channel(g) + 0.0722 * Channel(b);
    }

    public static double ContrastRatio((byte R, byte G, byte B) first, (byte R, byte G, byte B) second)
    {
        double a = RelativeLuminance(first.R, first.G, first.B);
        double b = RelativeLuminance(second.R, second.G, second.B);
        return (Math.Max(a, b) + 0.05) / (Math.Min(a, b) + 0.05);
    }

    /// <summary>
    /// Lightens a color toward white in 15 % steps until it reaches 4.5:1 against the ground.
    /// Colors that already pass are returned unchanged.
    /// </summary>
    public static (byte R, byte G, byte B) EnsureReadableOnGround((byte R, byte G, byte B) color)
    {
        var result = color;
        for (int step = 0; step < 20 && ContrastRatio(result, Ground) < MinimumTextContrast; step++)
        {
            result = ((byte)(result.R + (255 - result.R) * 0.15),
                      (byte)(result.G + (255 - result.G) * 0.15),
                      (byte)(result.B + (255 - result.B) * 0.15));
        }
        return result;
    }
}
