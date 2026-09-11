using System.Collections.Generic;
using Microsoft.UI.Xaml.Media;
using Windows.UI;

namespace VoidPlayer.WinUI.Models;

public class AiCategoryItem
{
    public string Id { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public string Subtitle { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public string IconGlyph { get; set; } = "\uE8D6";
    public Color GradientStartColor { get; set; }
    public Color GradientEndColor { get; set; }
    public List<SongItem> Songs { get; set; } = [];

    public int TrackCount => Songs.Count;
    public string FormattedTrackCount => $"{TrackCount} tracks";
    public int AverageConfidencePercent { get; set; } = 85;
    public string SmlConfidenceText => $"{AverageConfidencePercent}% SML Match";
    public string DetectedGenreSummary { get; set; } = string.Empty;

    public LinearGradientBrush BackgroundBrush => new()
    {
        StartPoint = new Windows.Foundation.Point(0, 0),
        EndPoint = new Windows.Foundation.Point(1, 1),
        GradientStops =
        {
            new GradientStop { Color = GradientStartColor, Offset = 0.0 },
            new GradientStop { Color = GradientEndColor, Offset = 1.0 }
        }
    };
}
