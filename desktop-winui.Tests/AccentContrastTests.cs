using VoidPlayer.WinUI.Services;
using Xunit;

namespace VoidPlayer.WinUI.Tests;

public sealed class AccentContrastTests
{
    [Fact]
    public void ContrastRatioMatchesWcagReferenceValues()
    {
        Assert.Equal(21.0, AccentContrast.ContrastRatio((0, 0, 0), (255, 255, 255)), 2);
        Assert.Equal(1.0, AccentContrast.ContrastRatio((0x80, 0x80, 0x80), (0x80, 0x80, 0x80)), 6);
    }

    [Theory]
    [InlineData(0x3C, 0xCF, 0x8E)] // default emerald accent
    [InlineData(0xED, 0xEE, 0xF0)] // primary text
    public void ReadableColorsAreReturnedUnchanged(byte r, byte g, byte b)
    {
        Assert.Equal(((byte)r, (byte)g, (byte)b), AccentContrast.EnsureReadableOnGround((r, g, b)));
    }

    [Theory]
    [InlineData(0x10, 0x10, 0x60)] // dark navy from album art
    [InlineData(0x40, 0x10, 0x10)] // deep red
    [InlineData(0x00, 0x00, 0x00)] // black
    public void DarkAlbumColorsAreLightenedToTextContrast(byte r, byte g, byte b)
    {
        var readable = AccentContrast.EnsureReadableOnGround((r, g, b));
        Assert.True(AccentContrast.ContrastRatio(readable, AccentContrast.Ground) >= AccentContrast.MinimumTextContrast);
    }

    [Fact]
    public void LighteningKeepsTheHueFamily()
    {
        var readable = AccentContrast.EnsureReadableOnGround((0x10, 0x10, 0x60));
        Assert.True(readable.B > readable.R && readable.B > readable.G, $"Expected a blue tint, got {readable}");
    }

    [Fact]
    public void SecondaryTextTokensMeetContrastOnTheirSurfaces()
    {
        (byte, byte, byte) secondary = (0xA9, 0xAE, 0xB5), tertiary = (0x86, 0x8C, 0x94), raised = (0x1F, 0x22, 0x26);
        Assert.True(AccentContrast.ContrastRatio(secondary, AccentContrast.Ground) >= 4.5);
        Assert.True(AccentContrast.ContrastRatio(tertiary, AccentContrast.Ground) >= 4.5);
        Assert.True(AccentContrast.ContrastRatio(tertiary, raised) >= 4.5);
    }
}
