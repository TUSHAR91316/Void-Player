using VoidPlayer.WinUI.Services;
using Xunit;

namespace VoidPlayer.WinUI.Tests;

public class LibraryMergeTests
{
    private sealed class FakeTrack : ITrackUserState
    {
        public string FilePath { get; init; } = string.Empty;
        public string Id { get; set; } = Guid.NewGuid().ToString("N");
        public bool IsFavorite { get; set; }
        public DateTime DateAdded { get; set; } = DateTime.Now;
    }

    private static readonly DateTime OriginalDate = new(2025, 3, 14, 9, 26, 53);

    [Fact]
    public void RescannedTrack_KeepsFavoriteIdAndDateAdded()
    {
        var previous = new[]
        {
            new FakeTrack { FilePath = @"C:\Music\a.flac", Id = "stable-a", IsFavorite = true, DateAdded = OriginalDate }
        };
        var scanned = new[] { new FakeTrack { FilePath = @"C:\Music\a.flac" } };

        LibraryMerge.CarryOverUserState(previous, scanned);

        Assert.True(scanned[0].IsFavorite);
        Assert.Equal("stable-a", scanned[0].Id);
        Assert.Equal(OriginalDate, scanned[0].DateAdded);
    }

    [Fact]
    public void PathMatch_IgnoresCaseAndSlashDirection()
    {
        var previous = new[] { new FakeTrack { FilePath = @"C:\Music\Album\Song.MP3", IsFavorite = true } };
        var scanned = new[] { new FakeTrack { FilePath = "c:/music/album/song.mp3" } };

        LibraryMerge.CarryOverUserState(previous, scanned);

        Assert.True(scanned[0].IsFavorite);
    }

    [Fact]
    public void NewTrack_KeepsScannedValues()
    {
        var previous = new[] { new FakeTrack { FilePath = @"C:\Music\old.mp3", IsFavorite = true } };
        var fresh = new FakeTrack { FilePath = @"C:\Music\new.mp3", Id = "fresh-id" };
        var freshDate = fresh.DateAdded;

        LibraryMerge.CarryOverUserState(previous, new[] { fresh });

        Assert.False(fresh.IsFavorite);
        Assert.Equal("fresh-id", fresh.Id);
        Assert.Equal(freshDate, fresh.DateAdded);
    }

    [Fact]
    public void UnfavoritedTrack_StaysUnfavorited()
    {
        var previous = new[] { new FakeTrack { FilePath = @"C:\Music\a.mp3", IsFavorite = false } };
        var scanned = new[] { new FakeTrack { FilePath = @"C:\Music\a.mp3", IsFavorite = true } };

        LibraryMerge.CarryOverUserState(previous, scanned);

        Assert.False(scanned[0].IsFavorite);
    }

    [Fact]
    public void DuplicatePathsInPreviousLibrary_DoNotThrow()
    {
        var previous = new[]
        {
            new FakeTrack { FilePath = @"C:\Music\a.mp3", IsFavorite = true },
            new FakeTrack { FilePath = @"C:\MUSIC\A.mp3", IsFavorite = false }
        };
        var scanned = new[] { new FakeTrack { FilePath = @"C:\Music\a.mp3" } };

        LibraryMerge.CarryOverUserState(previous, scanned);

        Assert.True(scanned[0].IsFavorite); // first entry wins
    }
}
