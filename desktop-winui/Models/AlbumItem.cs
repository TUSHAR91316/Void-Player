using System.Collections.Generic;
using System;
using Microsoft.UI.Xaml.Media.Imaging;

namespace VoidPlayer.WinUI.Models;

public class AlbumItem
{
    public string Name { get; set; } = "Unknown Album";
    public string Artist { get; set; } = "Unknown Artist";
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
    public List<SongItem> Songs { get; set; } = [];

    public int TrackCount => Songs.Count;
    public string FormattedTrackCount => TrackCount == 1 ? "1 song" : $"{TrackCount} songs";
}

public class ArtistItem
{
    public string Name { get; set; } = "Unknown Artist";
    public List<SongItem> Songs { get; set; } = [];
    public List<string> Albums { get; set; } = [];

    public int TrackCount => Songs.Count;
    public string FormattedTrackCount => TrackCount == 1 ? "1 song" : $"{TrackCount} songs";
    public string FormattedAlbumCount => Albums.Count == 1 ? "1 album" : $"{Albums.Count} albums";
}
