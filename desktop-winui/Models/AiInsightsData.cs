using System.Collections.Generic;

namespace VoidPlayer.WinUI.Models;

public class MoodScoreItem
{
    public string MoodName { get; set; } = string.Empty;
    public int ScorePercentage { get; set; }
    public string FormattedScore => $"{ScorePercentage}%";
    public string ColorHex { get; set; } = "#00E676";
}

public class AiInsightsData
{
    public string DominantVibe { get; set; } = "Eclectic Vibe";
    public string PersonaTitle { get; set; } = "The Soulful Romantic";
    public string PersonaDescription { get; set; } = "Rich with emotional vocals, heartfelt harmonies, and expressive melodies.";
    public int AnalyzedTracksCount { get; set; }
    public string TotalPlayTime { get; set; } = "0m";
    public string TopArtist { get; set; } = "None";
    public string RecommendedEq { get; set; } = "Vocal Pop Boost";
    public int LosslessTrackCount { get; set; }
    public int HiResTrackCount { get; set; }
    public int AverageBitrateKbps { get; set; }
    public string TopFormat { get; set; } = "Unknown";
    public long LibrarySizeBytes { get; set; }
    public string LibrarySizeText => LibrarySizeBytes <= 0
        ? "0 MB"
        : LibrarySizeBytes >= 1024L * 1024L * 1024L
            ? $"{LibrarySizeBytes / (1024d * 1024d * 1024d):0.##} GB"
            : $"{LibrarySizeBytes / (1024d * 1024d):0.##} MB";
    public string QualitySummary => $"{LosslessTrackCount} lossless | {HiResTrackCount} hi-res";
    public string StreamSummary => $"{TopFormat} dominant | {AverageBitrateKbps} kbps average";
    public int AverageAcousticEnergyPercent { get; set; } = 50;
    public string AcousticEnergyLabel { get; set; } = "Balanced Dynamics";
    public string TopGenresSummary { get; set; } = "Eclectic selection";
    public string SmlStatusBadge { get; set; } = "SML Acoustic Engine Active";
    public List<MoodScoreItem> MoodDistribution { get; set; } = [];
}
