using System.Text.Json;
using VoidPlayer.WinUI.Services;
using Xunit;

namespace VoidPlayer.WinUI.Tests;

public sealed class SmlV2SharedFixtureTests
{
    private sealed class Fixture
    {
        public string Kind { get; set; } = "";
        public string A { get; set; } = "";
        public string B { get; set; } = "";
        public JsonElement Expected { get; set; }
        public int Hour { get; set; }
        public int BpmA { get; set; }
        public int BpmB { get; set; }
        public string Title { get; set; } = "";
        public string Album { get; set; } = "";
        public string Genre { get; set; } = "";
        public int Bpm { get; set; }
        public long DurationMs { get; set; }
        public double Energy { get; set; }
        public double Valence { get; set; }
        public double Danceability { get; set; }
        public double Acousticness { get; set; }
        public double TempoNorm { get; set; }
        public double DurationNorm { get; set; }
        public string ATitle { get; set; } = "";
        public string AGenre { get; set; } = "";
        public int ABpm { get; set; }
        public double AEnergy { get; set; }
        public string AMood { get; set; } = "";
        public string AKey { get; set; } = "";
        public long ADurationMs { get; set; }
        public string BTitle { get; set; } = "";
        public string BGenre { get; set; } = "";
        public int BBpm { get; set; }
        public double BEnergy { get; set; }
        public string BMood { get; set; } = "";
        public string BKey { get; set; } = "";
        public long BDurationMs { get; set; }
        public double Flow { get; set; }
        public double Bias { get; set; }
        public int Completions { get; set; }
        public int Skips { get; set; }
        public int Repeats { get; set; }
        public long LastPlayed { get; set; }
        public long Now { get; set; }
        public string Candidates { get; set; } = "";
        public string Recent { get; set; } = "";
        public string ExpectedIds { get; set; } = "";
        public int CurrentBpm { get; set; }
        public string Scores { get; set; } = "";
    }

    private sealed class Track(string title, string genre, uint bpm, double energy, string mood, string key, long durationMs, string album = "Album") : ISmlTrack
    {
        public string Title { get; } = title;
        public string Album { get; } = album;
        public string Genre { get; } = genre;
        public uint Bpm { get; } = bpm;
        public string InitialKey { get; } = key;
        public TimeSpan Duration { get; } = TimeSpan.FromMilliseconds(durationMs);
        public double AcousticEnergy { get; } = energy;
        public string DominantMood { get; } = mood;
    }

    [Fact]
    public void SharedJsonFixturesMatchTheCSharpPort()
    {
        foreach (var fixture in LoadFixtures())
        {
            switch (fixture.Kind)
            {
                case "camelot":
                {
                    string expected = ExpectedString(fixture);
                    Assert.Equal(expected == "none" ? null : expected, SmlV2.ToCamelot(fixture.A));
                    break;
                }
                case "bpmDelta":
                    Assert.Equal((int)ExpectedDouble(fixture), SmlV2.EffectiveBpmDelta(fixture.BpmA, fixture.BpmB));
                    break;
                case "tempoFilter":
                {
                    var candidates = fixture.Candidates.Split('|')
                        .Select(entry => (Id: entry.Split(':')[0], Bpm: int.Parse(entry.Split(':')[1])));
                    var kept = SmlV2.FilterTempoJumps(fixture.CurrentBpm, candidates, candidate => candidate.Bpm);
                    Assert.Equal(fixture.ExpectedIds.Split('|'), kept.Select(candidate => candidate.Id));
                    break;
                }
                case "pickPool":
                {
                    var scored = fixture.Scores.Split('|').Select(entry => (entry.Split(':')[0],
                        double.Parse(entry.Split(':')[1], System.Globalization.CultureInfo.InvariantCulture)));
                    Assert.Equal(fixture.ExpectedIds.Split('|'), SmlV2.PickPool(scored));
                    break;
                }
                case "harmonic":
                    Assert.Equal(ExpectedDouble(fixture), SmlV2.HarmonicScore(fixture.A, fixture.B), 9);
                    break;
                case "tempo":
                    Assert.Equal(ExpectedDouble(fixture), SmlV2.TempoScore(fixture.BpmA, fixture.BpmB), 9);
                    break;
                case "target":
                    Assert.Equal(ExpectedDouble(fixture), SmlV2.TargetEnergy(fixture.Hour), 9);
                    break;
                case "features":
                {
                    var features = SmlV2.GetFeatures(new Track(fixture.Title, fixture.Genre, (uint)fixture.Bpm, fixture.Energy, "", "", fixture.DurationMs, fixture.Album));
                    Assert.Equal(fixture.Energy, features.Energy, 9);
                    Assert.Equal(fixture.Valence, features.Valence, 9);
                    Assert.Equal(fixture.Danceability, features.Danceability, 9);
                    Assert.Equal(fixture.Acousticness, features.Acousticness, 9);
                    Assert.Equal(fixture.TempoNorm, features.TempoNorm, 9);
                    Assert.Equal(fixture.DurationNorm, features.DurationNorm, 9);
                    break;
                }
                case "flow":
                {
                    var a = new Track(fixture.ATitle, fixture.AGenre, (uint)fixture.ABpm, fixture.AEnergy, fixture.AMood, fixture.AKey, fixture.ADurationMs);
                    var b = new Track(fixture.BTitle, fixture.BGenre, (uint)fixture.BBpm, fixture.BEnergy, fixture.BMood, fixture.BKey, fixture.BDurationMs);
                    Assert.Equal(ExpectedDouble(fixture), SmlV2.FlowScore(a, b), 9);
                    break;
                }
                case "radioScore":
                {
                    var song = new Track(fixture.Title, fixture.Genre, (uint)fixture.Bpm, fixture.Energy, "", "", fixture.DurationMs);
                    Assert.Equal(ExpectedDouble(fixture), SmlV2.RadioScore(fixture.Flow, song, fixture.Hour, fixture.Bias), 9);
                    break;
                }
                case "bias":
                {
                    var stats = new SmlV2.ListeningStats(0, fixture.Completions, fixture.Skips, fixture.Repeats, fixture.LastPlayed);
                    Assert.Equal(ExpectedDouble(fixture), SmlV2.ListeningBias(stats, fixture.Now), 9);
                    break;
                }
                case "dedupe":
                {
                    var candidates = fixture.Candidates.Split('|');
                    var recent = fixture.Recent.Split('|');
                    Assert.Equal(fixture.ExpectedIds.Split('|'), SmlV2.FilterRadioCandidateIds(candidates, recent));
                    break;
                }
                default:
                    throw new InvalidOperationException($"Unknown shared SML fixture kind: {fixture.Kind}");
            }
        }
    }

    private static List<Fixture> LoadFixtures()
    {
        var directory = new DirectoryInfo(AppContext.BaseDirectory);
        for (int depth = 0; depth < 10 && directory != null; depth++, directory = directory.Parent)
        {
            var path = Path.Combine(directory.FullName, "test-fixtures", "sml-v2.json");
            if (File.Exists(path))
                return JsonSerializer.Deserialize<List<Fixture>>(File.ReadAllText(path), new JsonSerializerOptions { PropertyNameCaseInsensitive = true })!;
        }
        throw new FileNotFoundException("Could not locate shared test-fixtures/sml-v2.json.");
    }

    private static string ExpectedString(Fixture fixture) => fixture.Expected.GetString() ?? "";
    private static double ExpectedDouble(Fixture fixture) => fixture.Expected.ValueKind == JsonValueKind.String
        ? double.Parse(fixture.Expected.GetString()!)
        : fixture.Expected.GetDouble();
}
