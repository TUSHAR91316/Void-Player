using System;
using CommunityToolkit.Mvvm.ComponentModel;
using Microsoft.UI.Xaml.Media.Imaging;

namespace VoidPlayer.WinUI.Models;

public partial class SongItem : ObservableObject
{
    public string Id { get; set; } = Guid.NewGuid().ToString("N");
    public string Title { get; set; } = "Unknown Title";
    public string Artist { get; set; } = "Unknown Artist";
    public string Album { get; set; } = "Unknown Album";
    public TimeSpan Duration { get; set; } = TimeSpan.Zero;
    public string FilePath { get; set; } = string.Empty;
    public string? ArtCachePath { get; set; }
    public BitmapImage? ArtCacheImageSource
    {
        get
        {
            if (string.IsNullOrWhiteSpace(ArtCachePath)) return null;
            try
            {
                return System.IO.File.Exists(ArtCachePath) ? new BitmapImage(new Uri(ArtCachePath)) : null;
            }
            catch
            {
                return null;
            }
        }
    }
    public uint Year { get; set; }
    public uint TrackNumber { get; set; }
    public DateTime DateAdded { get; set; } = DateTime.Now;

    // SML (Small Machine Learning) Audio Intelligence Properties
    public string Genre { get; set; } = string.Empty;
    public uint Bpm { get; set; }
    public double AcousticEnergy { get; set; } = 0.5;
    public string DominantMood { get; set; } = "Eclectic";

    [ObservableProperty]
    public partial bool IsFavorite { get; set; }

    [ObservableProperty]
    public partial bool IsCurrentlyPlaying { get; set; }

    public AudioCodecInfo CodecInfo { get; set; } = new();

    public string FormattedDuration => Duration.TotalHours >= 1
        ? Duration.ToString(@"h\:mm\:ss")
        : Duration.ToString(@"m\:ss");

    public string CodecBadge
    {
        get
        {
            var ext = System.IO.Path.GetExtension(FilePath).TrimStart('.').ToUpperInvariant();
            if (string.IsNullOrEmpty(ext)) ext = CodecInfo.FormatName.ToUpperInvariant();
            return ext switch
            {
                "FLAC" => CodecInfo.BitsPerSample >= 24 ? "FLAC 24-bit" : "FLAC",
                "WAV" => "WAV Lossless",
                "M4A" or "AAC" => "AAC",
                "MP3" => CodecInfo.BitrateKbps > 0 ? $"MP3 {CodecInfo.BitrateKbps}k" : "MP3",
                "OGG" => "OGG Vorbis",
                "OPUS" => "OPUS",
                _ => ext
            };
        }
    }
}
