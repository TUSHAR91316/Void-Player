using VoidPlayer.WinUI.Services;
using Xunit;

namespace VoidPlayer.WinUI.Tests;

public sealed class M3uPlaylistCodecTests
{
    [Fact]
    public void ParsesBomFractionalDurationCommentsAndCommaInTitle()
    {
        const string text = "\uFEFF#EXTM3U\r\n#EXTINF:123.5,Title, Episode\r\nMusic/episode.mp3\r\n#EXTVLCOPT:start-time=2\r\n#EXTINF:-1,Unknown length\r\nfile:///music/live.flac\r\n";
        Assert.Equal(new[]
        {
            new M3uEntry("Music/episode.mp3", "Title, Episode", 123),
            new M3uEntry("file:///music/live.flac", "Unknown length", null)
        }, M3uPlaylistCodec.Parse(text));
    }

    [Fact]
    public void EncodesRoundTripOrderAndSafeTitles()
    {
        var entries = new[]
        {
            new M3uEntry(@"C:\\Music\\first.mp3", "First\nSong", 120),
            new M3uEntry(@"C:\\Music\\second.flac", "Second", null)
        };
        var parsed = M3uPlaylistCodec.Parse(M3uPlaylistCodec.Encode(entries));
        Assert.Equal(entries.Select(entry => entry.Location), parsed.Select(entry => entry.Location));
        Assert.Equal("First Song", parsed[0].Title);
        Assert.Equal(120, parsed[0].DurationSeconds);
        Assert.Null(parsed[1].DurationSeconds);
    }
}
