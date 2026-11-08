using System.Globalization;
using System.Text;

namespace VoidPlayer.WinUI.Services;

public sealed record M3uEntry(string Location, string Title = "", long? DurationSeconds = null);

public static class M3uPlaylistCodec
{
    public static List<M3uEntry> Parse(string text)
    {
        var entries = new List<M3uEntry>();
        string title = "";
        long? duration = null;
        foreach (string raw in (text ?? "").TrimStart('\uFEFF').Split(['\r', '\n'], StringSplitOptions.RemoveEmptyEntries))
        {
            string line = raw.Trim();
            if (line.Length == 0) continue;
            if (line.StartsWith("#EXTINF:", StringComparison.OrdinalIgnoreCase))
            {
                string payload = line[(line.IndexOf(':') + 1)..];
                int comma = payload.IndexOf(',');
                if (comma >= 0)
                {
                    if (double.TryParse(payload[..comma], NumberStyles.Float, CultureInfo.InvariantCulture, out double seconds) && seconds >= 0)
                        duration = (long)seconds;
                    else duration = null;
                    title = payload[(comma + 1)..].Trim();
                }
                continue;
            }
            if (line.StartsWith('#')) continue;
            entries.Add(new M3uEntry(line, title, duration));
            title = "";
            duration = null;
        }
        return entries;
    }

    public static string Encode(IEnumerable<M3uEntry> entries)
    {
        var output = new StringBuilder("#EXTM3U\n");
        foreach (var entry in entries)
        {
            long seconds = entry.DurationSeconds is >= 0 ? entry.DurationSeconds.Value : -1;
            string safeTitle = (entry.Title ?? "").Replace('\r', ' ').Replace('\n', ' ').Trim();
            output.Append("#EXTINF:").Append(seconds.ToString(CultureInfo.InvariantCulture)).Append(',').AppendLine(safeTitle);
            output.AppendLine((entry.Location ?? "").Trim());
        }
        return output.ToString();
    }
}
