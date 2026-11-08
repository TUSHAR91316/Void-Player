using VoidPlayer.WinUI.Models;
using Xunit;

namespace VoidPlayer.WinUI.Tests;

public class AiInsightsDataTests
{
    [Fact]
    public void Defaults_ArePopulatedWithExpectedFallbacks()
    {
        var insights = new AiInsightsData();

        Assert.Equal("Eclectic Vibe", insights.DominantVibe);
        Assert.Equal("The Soulful Romantic", insights.PersonaTitle);
        Assert.NotEmpty(insights.PersonaDescription);
        Assert.Equal(0, insights.AnalyzedTracksCount);
        Assert.Equal("0m", insights.TotalPlayTime);
        Assert.Equal("None", insights.TopArtist);
        Assert.Equal("Vocal Pop Boost", insights.RecommendedEq);
        Assert.Equal(0, insights.LosslessTrackCount);
        Assert.Equal(0, insights.HiResTrackCount);
        Assert.Equal(0, insights.AverageBitrateKbps);
        Assert.Equal("Unknown", insights.TopFormat);
        Assert.Equal(0L, insights.LibrarySizeBytes);
        Assert.Equal("0 MB", insights.LibrarySizeText);
        Assert.Equal("0 lossless | 0 hi-res", insights.QualitySummary);
        Assert.Equal("Unknown dominant | 0 kbps average", insights.StreamSummary);
        Assert.Equal(50, insights.AverageAcousticEnergyPercent);
        Assert.Equal("Balanced Dynamics", insights.AcousticEnergyLabel);
        Assert.Equal("Eclectic selection", insights.TopGenresSummary);
        Assert.Equal("Tag-based estimates · no audio analysis", insights.SmlStatusBadge);
        Assert.Empty(insights.MoodDistribution);
    }

    [Theory]
    [InlineData(0L, "0 MB")]
    [InlineData(-100L, "0 MB")]
    [InlineData(524288000L, "500 MB")]
    [InlineData(1073741824L, "1 GB")]
    [InlineData(2684354560L, "2.5 GB")]
    public void LibrarySizeText_FormatsMegabytesAndGigabytesAccurately(long bytes, string expected)
    {
        var insights = new AiInsightsData { LibrarySizeBytes = bytes };
        Assert.Equal(expected, insights.LibrarySizeText);
    }

    [Fact]
    public void QualitySummary_ReflectsLosslessAndHiResCounts()
    {
        var insights = new AiInsightsData
        {
            LosslessTrackCount = 142,
            HiResTrackCount = 28
        };
        Assert.Equal("142 lossless | 28 hi-res", insights.QualitySummary);
    }

    [Fact]
    public void StreamSummary_ReflectsTopFormatAndAverageBitrate()
    {
        var insights = new AiInsightsData
        {
            TopFormat = "FLAC",
            AverageBitrateKbps = 980
        };
        Assert.Equal("FLAC dominant | 980 kbps average", insights.StreamSummary);
    }

    [Fact]
    public void MoodScoreItem_PropertiesAndFormatting()
    {
        var item = new MoodScoreItem
        {
            MoodName = "Energetic",
            ScorePercentage = 78,
            ColorHex = "#FF4081"
        };

        Assert.Equal("Energetic", item.MoodName);
        Assert.Equal(78, item.ScorePercentage);
        Assert.Equal("78%", item.FormattedScore);
        Assert.Equal("#FF4081", item.ColorHex);
    }
}

