using System;
using System.Globalization;
using System.Net.Http;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using VoidPlayer.WinUI.Models;

namespace VoidPlayer.WinUI.Services;

public sealed record LyricsResult(string Text, bool IsSynced);

public static class LyricsService
{
    private static readonly HttpClient Client = new() { Timeout = TimeSpan.FromSeconds(8) };

    public static async Task<LyricsResult?> FetchAsync(SongItem song, CancellationToken cancellationToken)
    {
        string query = $"track_name={Uri.EscapeDataString(song.Title)}&artist_name={Uri.EscapeDataString(song.Artist)}";
        if (song.Duration > TimeSpan.Zero)
            query += $"&duration={Math.Round(song.Duration.TotalSeconds, MidpointRounding.AwayFromZero).ToString(CultureInfo.InvariantCulture)}";
        using var request = new HttpRequestMessage(HttpMethod.Get, $"https://lrclib.net/api/get?{query}");
        request.Headers.UserAgent.ParseAdd("VoidPlayer/2.4.0 (desktop)");
        using var response = await Client.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
        if (!response.IsSuccessStatusCode) return null;
        await using var stream = await response.Content.ReadAsStreamAsync(cancellationToken);
        using var document = await JsonDocument.ParseAsync(stream, cancellationToken: cancellationToken);
        var root = document.RootElement;
        string? synced = root.TryGetProperty("syncedLyrics", out var syncedValue) ? syncedValue.GetString() : null;
        string? plain = root.TryGetProperty("plainLyrics", out var plainValue) ? plainValue.GetString() : null;
        if (!string.IsNullOrWhiteSpace(synced)) return new LyricsResult(synced, true);
        return string.IsNullOrWhiteSpace(plain) ? null : new LyricsResult(plain, false);
    }
}
