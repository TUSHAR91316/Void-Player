using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Threading.Tasks;
using Windows.Graphics.Imaging;
using Windows.Storage.Streams;
using Windows.UI;

namespace VoidPlayer.WinUI.Services;

public static class ColorExtractorService
{
    private const int MaxCachedColors = 256;
    private static readonly ConcurrentDictionary<string, Color> ColorCache = new(StringComparer.OrdinalIgnoreCase);

    public static void ClearCache() => ColorCache.Clear();

    public static async Task<Color> ExtractDominantColorAsync(string? imagePath, string fallbackKey = "")
    {
        if (string.IsNullOrEmpty(imagePath) || !File.Exists(imagePath))
        {
            return GetFallbackColor(fallbackKey);
        }

        if (ColorCache.TryGetValue(imagePath, out var cached))
        {
            return cached;
        }

        try
        {
            using var fileStream = File.OpenRead(imagePath);
            using var memStream = new InMemoryRandomAccessStream();
            using (var outputStream = memStream.GetOutputStreamAt(0))
            {
                using var netStream = outputStream.AsStreamForWrite();
                await fileStream.CopyToAsync(netStream);
                await netStream.FlushAsync();
            }

            var decoder = await BitmapDecoder.CreateAsync(memStream);

            // Downsample to 32x32 for instantaneous extraction and negligible memory
            var transform = new BitmapTransform
            {
                ScaledWidth = 32,
                ScaledHeight = 32,
                InterpolationMode = BitmapInterpolationMode.NearestNeighbor
            };

            var pixelData = await decoder.GetPixelDataAsync(
                BitmapPixelFormat.Bgra8,
                BitmapAlphaMode.Premultiplied,
                transform,
                ExifOrientationMode.IgnoreExifOrientation,
                ColorManagementMode.DoNotColorManage);

            var pixels = pixelData.DetachPixelData();
            var vibrant = ComputeVibrantColor(pixels);
            if (ColorCache.Count >= MaxCachedColors)
            {
                var keyToRemove = ColorCache.Keys.FirstOrDefault();
                if (keyToRemove != null) ColorCache.TryRemove(keyToRemove, out _);
            }
            ColorCache[imagePath] = vibrant;
            return vibrant;
        }
        catch
        {
            return GetFallbackColor(fallbackKey);
        }
    }

    private static Color ComputeVibrantColor(byte[] bgraPixels)
    {
        float bestScore = -1;
        byte bestR = 0, bestG = 230, bestB = 118; // Default Void neon emerald

        for (int i = 0; i < bgraPixels.Length; i += 4)
        {
            byte b = bgraPixels[i];
            byte g = bgraPixels[i + 1];
            byte r = bgraPixels[i + 2];
            byte a = bgraPixels[i + 3];

            if (a < 128) continue;

            ColorToHsv(r, g, b, out _, out float s, out float v);

            // Filter out near-black, near-white, or washed-out grays
            if (v < 0.25f || v > 0.95f || s < 0.28f) continue;

            // Score favoring saturated, lively colors
            float score = s * 0.7f + v * 0.3f;
            if (score > bestScore)
            {
                bestScore = score;
                bestR = r;
                bestG = g;
                bestB = b;
            }
        }

        if (bestScore < 0)
        {
            return Color.FromArgb(255, 0, 230, 118);
        }

        return Color.FromArgb(255, bestR, bestG, bestB);
    }

    public static Color GetFallbackColor(string key)
    {
        var palette = new[]
        {
            Color.FromArgb(255, 0, 230, 118),   // Neon Emerald
            Color.FromArgb(255, 139, 92, 246),  // Electric Violet
            Color.FromArgb(255, 14, 165, 233),  // Cyan Wave
            Color.FromArgb(255, 244, 63, 94),   // Rose Pink
            Color.FromArgb(255, 245, 158, 11),  // Amber Glow
            Color.FromArgb(255, 16, 185, 129),  // Mint Green
        };

        if (string.IsNullOrEmpty(key)) return palette[0];
        int hash = Math.Abs(key.GetHashCode());
        return palette[hash % palette.Length];
    }

    private static void ColorToHsv(byte r, byte g, byte b, out float h, out float s, out float v)
    {
        float rf = r / 255f;
        float gf = g / 255f;
        float bf = b / 255f;

        float max = Math.Max(rf, Math.Max(gf, bf));
        float min = Math.Min(rf, Math.Min(gf, bf));
        float delta = max - min;

        v = max;
        s = max == 0 ? 0 : delta / max;

        if (delta == 0)
        {
            h = 0;
        }
        else if (Math.Abs(max - rf) < 0.0001f)
        {
            h = ((gf - bf) / delta) % 6;
        }
        else if (Math.Abs(max - gf) < 0.0001f)
        {
            h = (bf - rf) / delta + 2;
        }
        else
        {
            h = (rf - gf) / delta + 4;
        }

        h *= 60;
        if (h < 0) h += 360;
    }
}
