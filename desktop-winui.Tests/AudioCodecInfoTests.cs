using VoidPlayer.WinUI.Models;
using Xunit;

namespace VoidPlayer.WinUI.Tests;

public sealed class AudioCodecInfoTests
{
    [Theory]
    [InlineData("FLAC", true)]
    [InlineData("flac", true)]
    [InlineData("WAV", true)]
    [InlineData("wav", true)]
    [InlineData("AIFF", true)]
    [InlineData("ALAC", true)]
    [InlineData("MP3", false)]
    [InlineData("AAC", false)]
    [InlineData("OGG", false)]
    [InlineData("OPUS", false)]
    [InlineData("Unknown", false)]
    public void IsLossless_DetectsAccurately(string format, bool expectedLossless)
    {
        var info = new AudioCodecInfo { FormatName = format };
        Assert.Equal(expectedLossless, info.IsLossless);
    }

    [Fact]
    public void FormattedBitrate_HandlesVariousBitrates()
    {
        var withBitrate = new AudioCodecInfo { BitrateKbps = 320, FormatName = "MP3" };
        Assert.Equal("320 kbps", withBitrate.FormattedBitrate);

        var losslessNoBitrate = new AudioCodecInfo { BitrateKbps = 0, FormatName = "FLAC" };
        Assert.Equal("Lossless", losslessNoBitrate.FormattedBitrate);

        var lossyNoBitrate = new AudioCodecInfo { BitrateKbps = 0, FormatName = "AAC" };
        Assert.Equal("Unknown", lossyNoBitrate.FormattedBitrate);
    }

    [Fact]
    public void StreamProfile_CombinesFormatAndDescription()
    {
        var withDesc = new AudioCodecInfo { FormatName = "FLAC", CodecDescription = "Free Lossless Audio Codec" };
        Assert.Equal("FLAC | Free Lossless Audio Codec", withDesc.StreamProfile);

        var emptyDesc = new AudioCodecInfo { FormatName = "WAV", CodecDescription = "" };
        Assert.Equal("WAV", emptyDesc.StreamProfile);
    }

    [Theory]
    [InlineData(44100, "44.1 kHz")]
    [InlineData(48000, "48 kHz")]
    [InlineData(96000, "96 kHz")]
    [InlineData(192000, "192 kHz")]
    [InlineData(0, "Unknown")]
    [InlineData(-100, "Unknown")]
    public void FormattedSampleRate_FormatsCorrectly(int sampleRate, string expected)
    {
        var info = new AudioCodecInfo { SampleRateHz = sampleRate };
        Assert.Equal(expected, info.FormattedSampleRate);
    }

    [Theory]
    [InlineData(1, "Mono (1.0)")]
    [InlineData(2, "Stereo (2.0)")]
    [InlineData(6, "5.1 Surround")]
    [InlineData(8, "7.1 Surround")]
    [InlineData(0, "Unknown")]
    [InlineData(4, "4 channels")]
    public void ChannelsDescription_MapsCorrectly(int channels, string expected)
    {
        var info = new AudioCodecInfo { Channels = channels };
        Assert.Equal(expected, info.ChannelsDescription);
    }

    [Theory]
    [InlineData(16, "16-bit")]
    [InlineData(24, "24-bit")]
    [InlineData(32, "32-bit")]
    [InlineData(0, "Unknown")]
    [InlineData(-1, "Unknown")]
    public void FormattedBitDepth_MapsCorrectly(int bitDepth, string expected)
    {
        var info = new AudioCodecInfo { BitsPerSample = bitDepth };
        Assert.Equal(expected, info.FormattedBitDepth);
    }

    [Fact]
    public void FormattedFileSize_CalculatesMegaBytes()
    {
        var zero = new AudioCodecInfo { FileSizeBytes = 0 };
        Assert.Equal("0 MB", zero.FormattedFileSize);

        var fiveMb = new AudioCodecInfo { FileSizeBytes = 5 * 1024 * 1024 };
        Assert.Equal("5 MB", fiveMb.FormattedFileSize);

        var partial = new AudioCodecInfo { FileSizeBytes = (long)(12.34 * 1024 * 1024) };
        Assert.Equal("12.34 MB", partial.FormattedFileSize);
    }

    [Fact]
    public void QualityTier_IdentifiesTiersAccurately()
    {
        var hiRes = new AudioCodecInfo { FormatName = "FLAC", SampleRateHz = 96000, BitsPerSample = 24 };
        Assert.Equal("Hi-Res Lossless (Studio Master)", hiRes.QualityTier);

        var cdLossless = new AudioCodecInfo { FormatName = "FLAC", SampleRateHz = 44100, BitsPerSample = 16 };
        Assert.Equal("Lossless CD Quality", cdLossless.QualityTier);

        var highQuality = new AudioCodecInfo { FormatName = "MP3", BitrateKbps = 320 };
        Assert.Equal("High Quality (320k)", highQuality.QualityTier);

        var standard = new AudioCodecInfo { FormatName = "MP3", BitrateKbps = 192 };
        Assert.Equal("Standard Audio", standard.QualityTier);
    }
}

