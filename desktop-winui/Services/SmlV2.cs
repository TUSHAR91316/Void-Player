using System;
using System.Collections.Generic;
using System.Linq;

namespace VoidPlayer.WinUI.Services;

public interface ISmlTrack
{
    string Title { get; }
    string Album { get; }
    string Genre { get; }
    uint Bpm { get; }
    string InitialKey { get; }
    TimeSpan Duration { get; }
    double AcousticEnergy { get; }
    string DominantMood { get; }
}

/// <summary>
/// Deterministic tag-derived SML v2.1 estimates. This does not analyze audio samples.
/// Must match the Kotlin port (composeApp/.../utils/SmlV2.kt); test-fixtures/sml-v2.json is
/// the shared contract both test suites run.
/// </summary>
public static class SmlV2
{
    public readonly record struct Features(double Energy, double Valence, double Danceability, double Acousticness, double TempoNorm, double DurationNorm);
    public readonly record struct ListeningStats(int Plays, int Completions, int Skips, int Repeats, long LastPlayedEpochMillis);

    // Matched as whole words of the title/album/genre ("sun" does not match "Sunday").
    private static readonly HashSet<string> Positive = ["happy", "joy", "love", "sun", "sunshine", "summer", "smile", "dance", "celebrate", "bright", "party", "fun"];
    private static readonly HashSet<string> Mellow = ["sad", "blue", "blues", "rain", "alone", "lonely", "tears", "night", "sleep", "lullaby", "ambient", "piano", "acoustic"];
    private static readonly System.Text.RegularExpressions.Regex WordSeparator = new(@"[^\p{L}\p{N}]+", System.Text.RegularExpressions.RegexOptions.Compiled);

    /// <summary>Largest effective BPM change (half/double-time aware) that radio picks accept.</summary>
    public const int MaxRadioBpmJump = 25;

    /// <summary>Picks within this score of the best are treated as equally good.</summary>
    public const double PickScoreMargin = 0.05;
    public const int PickPoolSize = 3;

    public static Features GetFeatures(ISmlTrack song)
    {
        var words = WordSeparator.Split($"{song.Title} {song.Album} {song.Genre}".ToLowerInvariant()).ToHashSet(StringComparer.Ordinal);
        string genre = (song.Genre ?? "").ToLowerInvariant();
        double energy = Math.Clamp(song.AcousticEnergy, 0.0, 1.0);
        double valence = words.Overlaps(Positive) ? 0.78 : words.Overlaps(Mellow) ? 0.32 : 0.5;
        double danceability = new[] { "dance", "disco", "house", "techno", "edm", "funk", "reggaeton" }.Any(genre.Contains)
            ? 0.85 : song.Bpm is >= 90 and <= 140 ? 0.68 : song.Bpm > 0 ? 0.42 : 0.5;
        double acousticness = new[] { "acoustic", "folk", "classical", "piano", "unplugged" }.Any(genre.Contains)
            ? 0.82 : new[] { "metal", "edm", "electronic", "techno", "house" }.Any(genre.Contains) ? 0.16 : 0.5;
        return new Features(energy, valence, danceability, acousticness,
            Math.Clamp((int)song.Bpm, 0, 240) / 240.0,
            Math.Clamp(song.Duration.TotalMilliseconds / 600_000.0, 0.0, 1.0));
    }

    /// <summary>
    /// Camelot notation for a key tag, or null when it cannot be read. Accepts Camelot ("8A"),
    /// Open Key ("1m" = 8A, "1d" = 8B) and note names ("Am", "A minor", "Amin", "C", "Cmaj",
    /// "C major", "F#", "Bb"). A lowercase "m" suffix means minor; an uppercase "M" means major.
    /// </summary>
    public static string? ToCamelot(string key)
    {
        string normalized = (key ?? "").Trim().Replace("♭", "b").Replace("♯", "#").Replace(" ", "");
        if (normalized.Length == 0) return null;
        var direct = System.Text.RegularExpressions.Regex.Match(normalized, @"^(1[0-2]|[1-9])([ab])$", System.Text.RegularExpressions.RegexOptions.IgnoreCase);
        if (direct.Success) return direct.Groups[1].Value + direct.Groups[2].Value.ToUpperInvariant();
        var openKey = System.Text.RegularExpressions.Regex.Match(normalized, @"^(1[0-2]|[1-9])([dm])$", System.Text.RegularExpressions.RegexOptions.IgnoreCase);
        if (openKey.Success)
        {
            int openNumber = (int.Parse(openKey.Groups[1].Value) + 6) % 12 + 1;
            return $"{openNumber}{(openKey.Groups[2].Value.Equals("m", StringComparison.OrdinalIgnoreCase) ? "A" : "B")}";
        }
        string lower = normalized.ToLowerInvariant();
        (string note, bool minor) = lower switch
        {
            _ when lower.EndsWith("minor", StringComparison.Ordinal) => (lower[..^5], true),
            _ when lower.EndsWith("major", StringComparison.Ordinal) => (lower[..^5], false),
            _ when lower.EndsWith("min", StringComparison.Ordinal) => (lower[..^3], true),
            _ when lower.EndsWith("maj", StringComparison.Ordinal) => (lower[..^3], false),
            _ when normalized.EndsWith('m') => (lower[..^1], true),
            _ when normalized.EndsWith('M') => (lower[..^1], false),
            _ => (lower, false)
        };
        var aliases = new Dictionary<string, string> { ["db"] = "c#", ["eb"] = "d#", ["gb"] = "f#", ["ab"] = "g#", ["bb"] = "a#", ["b#"] = "c", ["e#"] = "f", ["cb"] = "b", ["fb"] = "e" };
        if (aliases.TryGetValue(note, out string? alias)) note = alias;
        var major = new Dictionary<string, int> { ["c"] = 8, ["g"] = 9, ["d"] = 10, ["a"] = 11, ["e"] = 12, ["b"] = 1, ["f#"] = 2, ["c#"] = 3, ["g#"] = 4, ["d#"] = 5, ["a#"] = 6, ["f"] = 7 };
        var minorMap = new Dictionary<string, int> { ["a"] = 8, ["e"] = 9, ["b"] = 10, ["f#"] = 11, ["c#"] = 12, ["g#"] = 1, ["d#"] = 2, ["a#"] = 3, ["f"] = 4, ["c"] = 5, ["g"] = 6, ["d"] = 7 };
        var map = minor ? minorMap : major;
        return map.TryGetValue(note, out int number) ? $"{number}{(minor ? "A" : "B")}" : null;
    }

    /// <summary>
    /// Mix compatibility on the Camelot wheel, 0..1; unknown keys are neutral (0.5). Same key 1.0;
    /// adjacent or relative 0.9; diagonal 0.7; two steps 0.6; then 0.15 less per step, down to 0.
    /// </summary>
    public static double HarmonicScore(string firstKey, string secondKey)
    {
        string? first = ToCamelot(firstKey), second = ToCamelot(secondKey);
        if (first == null || second == null) return 0.5;
        if (first == second) return 1;
        int a = int.Parse(first[..^1]), b = int.Parse(second[..^1]);
        bool modeChange = first[^1] != second[^1];
        int distance = Math.Min((a - b + 12) % 12, (b - a + 12) % 12);
        if (!modeChange && distance == 1) return 0.9;
        if (modeChange && distance == 0) return 0.9;
        if (modeChange && distance == 1) return 0.7;
        int steps = distance + (modeChange ? 1 : 0);
        return Math.Clamp(0.6 - 0.15 * (steps - 2), 0.0, 0.6);
    }

    /// <summary>Smallest BPM change allowing half/double time, or -1 when either tempo is unknown.</summary>
    public static int EffectiveBpmDelta(int bpmA, int bpmB)
    {
        if (bpmA <= 0 || bpmB <= 0) return -1;
        return Math.Min(Math.Abs(bpmA - bpmB), Math.Min(Math.Abs(bpmA * 2 - bpmB), Math.Abs(bpmA - bpmB * 2)));
    }

    /// <summary>1.0 for the same (or half/double) tempo, falling linearly to 0 at a 25 % change; unknown 0.6.</summary>
    public static double TempoScore(int bpmA, int bpmB)
    {
        if (bpmA <= 0 || bpmB <= 0) return 0.6;
        double a = bpmA, b = bpmB;
        double relativeChange = new[] { (a, b), (a * 2, b), (a, b * 2) }.Min(pair => Math.Abs(pair.Item1 - pair.Item2) / Math.Max(pair.Item1, pair.Item2));
        return Math.Clamp(1.0 - relativeChange / 0.25, 0.0, 1.0);
    }

    /// <summary>Similarity of valence, danceability and acousticness, 0..1.</summary>
    public static double TextureScore(Features x, Features y) =>
        1.0 - (Math.Abs(x.Valence - y.Valence) + Math.Abs(x.Danceability - y.Danceability) + Math.Abs(x.Acousticness - y.Acousticness)) / 3.0;

    public static double FlowScore(ISmlTrack a, ISmlTrack b, string keyA = "", string keyB = "")
    {
        Features x = GetFeatures(a), y = GetFeatures(b);
        double mood = !string.IsNullOrWhiteSpace(a.DominantMood) && string.Equals(a.DominantMood, b.DominantMood, StringComparison.OrdinalIgnoreCase) ? 1 : 0.5;
        double energy = Math.Clamp(1 - Math.Abs(x.Energy - y.Energy), 0, 1);
        return 0.25 * mood + 0.20 * energy + 0.20 * TempoScore((int)a.Bpm, (int)b.Bpm) +
            0.25 * HarmonicScore(string.IsNullOrWhiteSpace(keyA) ? a.InitialKey : keyA,
                string.IsNullOrWhiteSpace(keyB) ? b.InitialKey : keyB) +
            0.10 * TextureScore(x, y);
    }

    /// <summary>
    /// Drops candidates whose tempo jumps more than <see cref="MaxRadioBpmJump"/> (half/double-time
    /// aware). Candidates without BPM are kept; when nothing passes, all are returned.
    /// </summary>
    public static List<T> FilterTempoJumps<T>(int currentBpm, IEnumerable<T> candidates, Func<T, int> bpmOf)
    {
        var all = candidates.ToList();
        if (currentBpm <= 0) return all;
        var smooth = all.Where(candidate =>
        {
            int delta = EffectiveBpmDelta(currentBpm, bpmOf(candidate));
            return delta < 0 || delta <= MaxRadioBpmJump;
        }).ToList();
        return smooth.Count > 0 ? smooth : all;
    }

    /// <summary>Candidates within <see cref="PickScoreMargin"/> of the best score, best first, at most <see cref="PickPoolSize"/>.</summary>
    public static List<T> PickPool<T>(IEnumerable<(T Item, double Score)> scored)
    {
        var sorted = scored.OrderByDescending(entry => entry.Score).ToList();
        if (sorted.Count == 0) return [];
        double best = sorted[0].Score;
        return sorted.TakeWhile(entry => entry.Score >= best - PickScoreMargin - 1e-12).Take(PickPoolSize).Select(entry => entry.Item).ToList();
    }

    public static double RadioScore(double flow, ISmlTrack song, int localHour, double learnedBias = 0, bool circadianEnabled = true)
    {
        double energyMatch = 1.0 - Math.Abs(GetFeatures(song).Energy - TargetEnergy(localHour, circadianEnabled));
        return flow + 0.05 * energyMatch + Math.Clamp(learnedBias, -0.25, 0.25);
    }

    /// <summary>Prefers candidates outside recent history, falling back for small libraries.</summary>
    public static List<string> FilterRadioCandidateIds(IEnumerable<string> candidateIds, IEnumerable<string> recentlyPlayedIds)
    {
        var all = candidateIds.ToList();
        var recent = recentlyPlayedIds.ToHashSet(StringComparer.Ordinal);
        var fresh = all.Where(id => !recent.Contains(id)).ToList();
        return fresh.Count > 0 ? fresh : all;
    }

    public static double ListeningBias(ListeningStats stats, long nowEpochMillis)
    {
        double raw = Math.Log(1.0 + stats.Completions + 2.0 * stats.Repeats) - 1.5 * Math.Log(1.0 + stats.Skips);
        if (stats.LastPlayedEpochMillis <= 0) return raw;
        double ageDays = Math.Max(0, nowEpochMillis - stats.LastPlayedEpochMillis) / 86_400_000.0;
        return raw * Math.Pow(0.5, ageDays / 30.0);
    }

    public static double TargetEnergy(int localHour, bool enabled = true) => !enabled ? 0.55 : localHour switch
    {
        >= 5 and <= 10 => 0.55,
        >= 11 and <= 16 => 0.65,
        >= 17 and <= 21 => 0.60,
        _ => 0.30
    };
}
