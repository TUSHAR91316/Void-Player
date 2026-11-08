using VoidPlayer.WinUI.Services;
using Xunit;

namespace VoidPlayer.WinUI.Tests;

public sealed class SmlV2Tests
{
    private sealed class Track(string title, uint bpm = 120, string genre = "pop") : ISmlTrack
    {
        public string Title { get; } = title;
        public string Album { get; } = "Album";
        public string Genre { get; } = genre;
        public uint Bpm { get; } = bpm;
        public string InitialKey { get; } = "Am";
        public TimeSpan Duration { get; } = TimeSpan.FromMinutes(3);
        public double AcousticEnergy { get; } = 0.7;
        public string DominantMood { get; } = "Pop";
    }

    [Theory]
    [InlineData("Am", "8A")]
    [InlineData("C#m", "12A")]
    [InlineData("C", "8B")]
    [InlineData("F#", "2B")]
    [InlineData("8a", "8A")]
    public void CamelotFixtures_AreStable(string key, string expected) => Assert.Equal(expected, SmlV2.ToCamelot(key));

    [Fact]
    public void HarmonicAndTempoScores_RespectPerfectAndHalfTimeMatches()
    {
        Assert.Equal(1, SmlV2.HarmonicScore("8A", "Am"));
        Assert.Equal(0.9, SmlV2.HarmonicScore("8A", "8B"), 5);
        Assert.Equal(1, SmlV2.TempoScore(70, 140), 5);
    }

    [Fact]
    public void KnownKeyClash_RanksBelowUnknownKey()
    {
        Assert.True(SmlV2.HarmonicScore("8A", "2A") < SmlV2.HarmonicScore("8A", ""));
        Assert.True(SmlV2.HarmonicScore("8A", "9A") > SmlV2.HarmonicScore("8A", "9B"));
    }

    [Fact]
    public void WordMatching_DoesNotHitWordFragments()
    {
        Assert.Equal(0.5, SmlV2.GetFeatures(new Track("Sunday", 0, "nightcore")).Valence);
        Assert.Equal(0.78, SmlV2.GetFeatures(new Track("Sun goes up", 0, "rock")).Valence);
    }

    private sealed class RadioTrack(string id, uint bpm, string key, double energy) : ISmlTrack
    {
        public string Id { get; } = id;
        public string Title { get; } = $"Track {id}";
        public string Album { get; } = "Album";
        public string Genre { get; } = "electronic";
        public uint Bpm { get; } = bpm;
        public string InitialKey { get; } = key;
        public TimeSpan Duration { get; } = TimeSpan.FromMinutes(4);
        public double AcousticEnergy { get; } = energy;
        public string DominantMood { get; } = "Drive";
    }

    /// <summary>v2.4 acceptance: 100 radio picks, no repeat within the last 50, no BPM jump over 25.</summary>
    [Fact]
    public void LongRadioSession_HasNoRecentRepeatsOrLargeTempoJumps()
    {
        string[] keys = ["8A", "9A", "10A", "8B", "9B", "3A", "4B", "11A"];
        var library = Enumerable.Range(0, 150)
            .Select(i => new RadioTrack($"t{i}", (uint)(80 + i * 7 % 80), keys[i % keys.Length], 0.3 + i % 7 * 0.1))
            .ToList();
        var random = new Random(42);
        var history = new List<string>();
        var current = library[0];
        for (int pick = 0; pick < 100; pick++)
        {
            var recent = history.TakeLast(50).ToHashSet();
            var fresh = SmlV2.FilterRadioCandidateIds(library.Where(t => t.Id != current.Id).Select(t => t.Id), recent).ToHashSet();
            var candidates = SmlV2.FilterTempoJumps((int)current.Bpm, library.Where(t => fresh.Contains(t.Id)), t => (int)t.Bpm);
            var pool = SmlV2.PickPool(candidates.Select(t => (t, SmlV2.RadioScore(SmlV2.FlowScore(current, t), t, 20))));
            var next = pool[random.Next(pool.Count)];

            Assert.DoesNotContain(next.Id, recent);
            Assert.InRange(SmlV2.EffectiveBpmDelta((int)current.Bpm, (int)next.Bpm), 0, SmlV2.MaxRadioBpmJump);
            history.Add(current.Id);
            current = next;
        }
    }

    [Fact]
    public void FeatureVector_IsNormalizedAndCircadianEnergyMatchesSpecification()
    {
        var features = SmlV2.GetFeatures(new Track("Summer joy"));
        Assert.InRange(features.Valence, 0, 1);
        Assert.InRange(features.TempoNorm, 0, 1);
        Assert.Equal(0.55, SmlV2.TargetEnergy(8));
        Assert.Equal(0.65, SmlV2.TargetEnergy(12));
        Assert.Equal(0.6, SmlV2.TargetEnergy(18));
        Assert.Equal(0.3, SmlV2.TargetEnergy(23));
    }
}
