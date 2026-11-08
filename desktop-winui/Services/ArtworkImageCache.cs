using System;
using System.Collections.Generic;
using System.IO;
using Microsoft.UI.Xaml.Media.Imaging;

namespace VoidPlayer.WinUI.Services;

internal static class ArtworkImageCache
{
    private const int Capacity = 128;
    private const int DecodeWidth = 256;
    private static readonly object Gate = new();
    private static readonly Dictionary<string, LinkedListNode<(string Key, BitmapImage Image)>> Entries = new(StringComparer.OrdinalIgnoreCase);
    private static readonly LinkedList<(string Key, BitmapImage Image)> Lru = new();

    public static BitmapImage? Get(string? path)
    {
        if (string.IsNullOrWhiteSpace(path) || !File.Exists(path)) return null;
        string key = Path.GetFullPath(path);
        lock (Gate)
        {
            if (Entries.TryGetValue(key, out var existing))
            {
                Lru.Remove(existing);
                Lru.AddFirst(existing);
                return existing.Value.Image;
            }

            var image = new BitmapImage { DecodePixelWidth = DecodeWidth };
            image.UriSource = new Uri(key);
            var node = Lru.AddFirst((key, image));
            Entries[key] = node;
            while (Entries.Count > Capacity)
            {
                var last = Lru.Last!;
                Lru.RemoveLast();
                Entries.Remove(last.Value.Key);
            }
            return image;
        }
    }
}
