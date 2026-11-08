using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.RegularExpressions;

namespace VoidPlayer.WinUI.Services;

/// <summary>
/// LRC parsing shared with the Now Playing page. Mirrors the Android LrcParser:
/// 1–3 digit minutes, optional 1–3 digit fractions, <c>[offset:]</c> where a positive value
/// shows lyrics sooner, and empty timestamped lines kept as instrumental gaps.
/// </summary>
public static class LrcLyricsParser
{
    private static readonly Regex StampRegex = new(@"\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]", RegexOptions.Compiled);
    private static readonly Regex OffsetRegex = new(@"(?im)^\[offset:([+-]?\d+)\]", RegexOptions.Compiled);
    private static readonly Regex TagRegex = new(@"\[[^\]]*\]", RegexOptions.Compiled);

    public static void Parse(string raw, out List<(TimeSpan Time, string Text)> timed, out string plain)
    {
        timed = [];
        var plainLines = new List<string>();
        int offsetMs = 0;
        var offsetMatch = OffsetRegex.Match(raw ?? "");
        if (offsetMatch.Success) int.TryParse(offsetMatch.Groups[1].Value, out offsetMs);
        foreach (string line in (raw ?? "").Split(["\r\n", "\n"], StringSplitOptions.RemoveEmptyEntries))
        {
            var stamps = StampRegex.Matches(line);
            string text = TagRegex.Replace(StampRegex.Replace(line, string.Empty), string.Empty).Trim();
            if (stamps.Count == 0)
            {
                if (!string.IsNullOrWhiteSpace(text)) plainLines.Add(text);
                continue;
            }
            foreach (Match stamp in stamps)
            {
                int minutes = int.Parse(stamp.Groups[1].Value, System.Globalization.CultureInfo.InvariantCulture);
                int seconds = int.Parse(stamp.Groups[2].Value, System.Globalization.CultureInfo.InvariantCulture);
                string fraction = stamp.Groups[3].Value;
                int millis = fraction.Length switch
                {
                    1 => int.Parse(fraction, System.Globalization.CultureInfo.InvariantCulture) * 100,
                    2 => int.Parse(fraction, System.Globalization.CultureInfo.InvariantCulture) * 10,
                    3 => int.Parse(fraction, System.Globalization.CultureInfo.InvariantCulture),
                    _ => 0
                };
                long total = minutes * 60_000L + seconds * 1000L + millis - offsetMs;
                timed.Add((TimeSpan.FromMilliseconds(Math.Max(0, total)), text));
            }
        }
        timed = timed.OrderBy(item => item.Time).ToList();
        plain = string.Join(Environment.NewLine + Environment.NewLine, plainLines);
    }
}
