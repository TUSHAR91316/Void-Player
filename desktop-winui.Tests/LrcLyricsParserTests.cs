using VoidPlayer.WinUI.Services;
using Xunit;

namespace VoidPlayer.WinUI.Tests;

public sealed class LrcLyricsParserTests
{
    [Fact]
    public void HandlesNullOrEmptyText()
    {
        LrcLyricsParser.Parse(null!, out var timed1, out var plain1);
        Assert.Empty(timed1);
        Assert.Equal("", plain1);

        LrcLyricsParser.Parse("   \n\r\n  ", out var timed2, out var plain2);
        Assert.Empty(timed2);
        Assert.Equal("", plain2);
    }

    [Fact]
    public void ParsesShortLongAndFractionalTimestamps()
    {
        LrcLyricsParser.Parse("[1:23.4]First\n[123:00]Late\n[00:01.250]Opening", out var timed, out _);
        Assert.Equal(
            new (TimeSpan Time, string Text)[] { (TimeSpan.FromMilliseconds(1_250), "Opening"), (TimeSpan.FromMilliseconds(83_400), "First"), (TimeSpan.FromMilliseconds(7_380_000), "Late") },
            timed);
    }

    [Fact]
    public void PositiveOffsetShowsLyricsSooner()
    {
        LrcLyricsParser.Parse("[offset:+250]\n[00:10.00]Line", out var timed, out _);
        Assert.Equal(TimeSpan.FromMilliseconds(9_750), Assert.Single(timed).Time);
    }

    [Fact]
    public void NegativeOffsetDelaysAndNeverGoesBelowZero()
    {
        LrcLyricsParser.Parse("[offset:-500]\n[00:10.00]Line", out var later, out _);
        Assert.Equal(TimeSpan.FromMilliseconds(10_500), Assert.Single(later).Time);
        LrcLyricsParser.Parse("[offset:1000]\n[00:00.50]Intro", out var clamped, out _);
        Assert.Equal(TimeSpan.Zero, Assert.Single(clamped).Time);
    }

    [Fact]
    public void EmptyTimestampedLinesAreKeptAsGaps()
    {
        LrcLyricsParser.Parse("[00:01.00]Verse\n[00:05.00]\n[00:09.00]Chorus", out var timed, out _);
        Assert.Equal(new[] { "Verse", "", "Chorus" }, timed.Select(line => line.Text));
    }

    [Fact]
    public void MetadataTagsAreDroppedAndUntimedTextBecomesPlainLyrics()
    {
        LrcLyricsParser.Parse("[ar:Artist]\n[ti:Title]\nFirst plain line\nSecond plain line", out var timed, out var plain);
        Assert.Empty(timed);
        Assert.Equal($"First plain line{Environment.NewLine}{Environment.NewLine}Second plain line", plain);
    }

    [Fact]
    public void RepeatedTimestampsProduceOneLineEach()
    {
        LrcLyricsParser.Parse("[00:01.00][00:20.00]Chorus", out var timed, out _);
        Assert.Equal(new[] { TimeSpan.FromSeconds(1), TimeSpan.FromSeconds(20) }, timed.Select(line => line.Time));
    }
}
