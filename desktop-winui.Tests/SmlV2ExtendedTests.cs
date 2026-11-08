using VoidPlayer.WinUI.Services;
using Xunit;

namespace VoidPlayer.WinUI.Tests;

public sealed class SmlV2ExtendedTests
{
    private sealed class StubTrack : ISmlTrack
    {
        public string Title { get; init; } = "Title";
        public string Album { get; init; } = "Album";
        public string Genre { get; init; } = "Pop";
        public uint Bpm { get; init; } = 120;
        public string InitialKey { get; init; } = "8A";
        public TimeSpan Duration { get; init; } = TimeSpan.FromMinutes(3);
        public double AcousticEnergy { get; init; } = 0.5;
        public string DominantMood { get; init; } = "Happy";
    }

    [Theory]
    [InlineData("1m", "8A")]
    [InlineData("1d", "8B")]
    [InlineData("2m", "9A")]
    [InlineData("2d", "9B")]
    [InlineData("Db", "3B")]
    [InlineData("D♭", "3B")]
    [InlineData("Eb", "5B")]
    [InlineData("Gb", "2B")]
    [InlineData("F♯", "2B")]
    [InlineData("Ab", "4B")]
    [InlineData("Bb", "6B")]
    [InlineData("B#", "8B")]
    [InlineData("E#", "7B")]
    [InlineData("Cb", "1B")]
    [InlineData("Fb", "12B")]
    [InlineData("C major", "8B")]
    [InlineData("A minor", "8A")]
    [InlineData("Amin", "8A")]
    [InlineData("Cmaj", "8B")]
    public void ToCamelot_ParsesOpenKeyAndEnharmonics(string input, string expected)
    {
        Assert.Equal(expected, SmlV2.ToCamelot(input));
    }

    [Fact]
    public void ToCamelot_ReturnsNullOnInvalid()
    {
        Assert.Null(SmlV2.ToCamelot(""));
        Assert.Null(SmlV2.ToCamelot("   "));
        Assert.Null(SmlV2.ToCamelot("H#"));
        Assert.Null(SmlV2.ToCamelot("InvalidKey"));
    }

    [Fact]
    public void EffectiveBpmDelta_CalculatesSmallestChange()
    {
        Assert.Equal(-1, SmlV2.EffectiveBpmDelta(0, 120));
        Assert.Equal(0, SmlV2.EffectiveBpmDelta(120, 120));
        Assert.Equal(0, SmlV2.EffectiveBpmDelta(60, 120)); // Half-time
        Assert.Equal(0, SmlV2.EffectiveBpmDelta(140, 70)); // Double-time
        Assert.Equal(6, SmlV2.EffectiveBpmDelta(126, 120));
    }

    [Fact]
    public void GetFeatures_HandlesNullGenreGracefully()
    {
        var track = new StubTrack { Genre = null! };
        var features = SmlV2.GetFeatures(track);
        Assert.Equal(0.5, features.Acousticness);
    }

    [Fact]
    public void FlowScore_HandlesZeroBpmTracks()
    {
        var trackA = new StubTrack { Bpm = 0 };
        var trackB = new StubTrack { Bpm = 120 };
        var score = SmlV2.FlowScore(trackA, trackB);
        Assert.True(score > 0);
    }

    [Fact]
    public void FilterRadioCandidateIds_AvoidsRecentUnlessAllPlayed()
    {
        var candidates = new List<string> { "a", "b", "c", "d" };
        var recent = new HashSet<string> { "a", "b" };
        var filtered = SmlV2.FilterRadioCandidateIds(candidates, recent);
        Assert.Equal(new[] { "c", "d" }, filtered);

        var allRecent = new HashSet<string> { "a", "b", "c", "d" };
        var fallback = SmlV2.FilterRadioCandidateIds(candidates, allRecent);
        Assert.Equal(candidates, fallback);
    }

    [Fact]
    public void FilterTempoJumps_LimitsExcessiveTempoDiscontinuities()
    {
        var candidates = new List<uint> { 120, 125, 140, 185, 0 };
        var filtered = SmlV2.FilterTempoJumps(120, candidates, x => (int)x);
        Assert.Equal(new uint[] { 120, 125, 140, 0 }, filtered);

        var zeroCurrentBpm = SmlV2.FilterTempoJumps(0, candidates, x => (int)x);
        Assert.Equal(candidates, zeroCurrentBpm);
    }

    [Fact]
    public void PickPool_SelectsCandidatesWithinMarginCappedToThree()
    {
        var scored = new List<(string Item, double Score)>
        {
            ("first", 0.95),
            ("second", 0.92), // within 0.05
            ("third", 0.91),  // within 0.05
            ("fourth", 0.905),// within 0.05, but cap is 3
            ("low", 0.70)
        };
        var pool = SmlV2.PickPool(scored);
        Assert.Equal(new[] { "first", "second", "third" }, pool);

        Assert.Empty(SmlV2.PickPool(new List<(string, double)>()));
    }

    [Theory]
    [InlineData(6, 0.55)]  // Morning 5..10
    [InlineData(12, 0.65)] // Afternoon 11..16
    [InlineData(18, 0.60)] // Evening 17..21
    [InlineData(23, 0.30)] // Night 22..4
    [InlineData(2, 0.30)]  // Night 22..4
    public void TargetEnergy_FollowsCircadianSchedule(int hour, double expected)
    {
        Assert.Equal(expected, SmlV2.TargetEnergy(hour, enabled: true));
        Assert.Equal(0.55, SmlV2.TargetEnergy(hour, enabled: false));
    }

    [Fact]
    public void RadioScore_AppliesTargetEnergyAndLearnedBias()
    {
        var track = new StubTrack();
        var baseScore = SmlV2.RadioScore(0.8, track, 12, learnedBias: 0.0);
        var boosted = SmlV2.RadioScore(0.8, track, 12, learnedBias: 0.15);
        Assert.True(boosted > baseScore);

        var clampedHigh = SmlV2.RadioScore(0.8, track, 12, learnedBias: 1.0);
        var clampedExpected = SmlV2.RadioScore(0.8, track, 12, learnedBias: 0.25);
        Assert.Equal(clampedExpected, clampedHigh);
    }

    [Fact]
    public void ListeningBias_CalculatesScoreAndDecays()
    {
        long now = 100L * 86_400_000L;
        var stats = new SmlV2.ListeningStats
        {
            Completions = 5,
            Repeats = 2,
            Skips = 1,
            LastPlayedEpochMillis = now
        };
        var freshBias = SmlV2.ListeningBias(stats, now);
        Assert.True(freshBias > 0);

        var statsWithDate = stats with { LastPlayedEpochMillis = now - (30L * 86_400_000L) };
        var agedBias = SmlV2.ListeningBias(statsWithDate, now);
        Assert.True(agedBias < freshBias, "Aged bias should decay over 30 days");
    }
}
