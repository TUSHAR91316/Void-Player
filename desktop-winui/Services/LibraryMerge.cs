using System;
using System.Collections.Generic;

namespace VoidPlayer.WinUI.Services;

/// <summary>
/// The parts of a track that belong to the user rather than to the file's tags,
/// and therefore must survive a library rescan.
/// </summary>
public interface ITrackUserState
{
    string FilePath { get; }
    string Id { get; set; }
    bool IsFavorite { get; set; }
    DateTime DateAdded { get; set; }
}

public static class LibraryMerge
{
    /// <summary>
    /// Copies user-owned state (stable id, favorite flag, date added) from the previous
    /// library onto freshly scanned tracks, matched by file path. A rescan rebuilds every
    /// track from its tags, so without this favorites are lost and "Date Added" resets.
    /// Tracks that are new on disk keep the values they were scanned with.
    /// </summary>
    public static void CarryOverUserState<T>(IEnumerable<T> previous, IEnumerable<T> scanned)
        where T : ITrackUserState
    {
        var previousByPath = new Dictionary<string, T>(StringComparer.OrdinalIgnoreCase);
        foreach (var track in previous)
        {
            previousByPath.TryAdd(NormalizePath(track.FilePath), track);
        }

        foreach (var track in scanned)
        {
            if (previousByPath.TryGetValue(NormalizePath(track.FilePath), out var old))
            {
                track.Id = old.Id;
                track.IsFavorite = old.IsFavorite;
                track.DateAdded = old.DateAdded;
            }
        }
    }

    private static string NormalizePath(string path) => path.Trim().Replace('/', '\\');
}
