using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using VoidPlayer.WinUI.Models;

namespace VoidPlayer.WinUI.Services;

public partial class LibraryService : ObservableObject
{
    private static readonly HashSet<string> SupportedExtensions = new(StringComparer.OrdinalIgnoreCase)
    {
        ".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg", ".wma", ".opus", ".aiff"
    };

    private static readonly string ConfigDir = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "VoidPlayer");

    private static readonly string LibraryFilePath = Path.Combine(ConfigDir, "library.json");
    private readonly SemaphoreSlim _scanGate = new(1, 1);
    private CancellationTokenSource? _saveDebounce;

    public ObservableCollection<SongItem> Songs { get; } = [];
    public ObservableCollection<AlbumItem> Albums { get; } = [];
    public ObservableCollection<ArtistItem> Artists { get; } = [];
    public ObservableCollection<string> MonitoredFolders { get; } = [];

    [ObservableProperty]
    public partial bool IsScanning { get; set; }

    [ObservableProperty]
    public partial string ScanStatus { get; set; } = string.Empty;

    public event EventHandler? ScanCompleted;

    public LibraryService()
    {
        LoadSavedLibrary();
    }

    public async Task AddFolderAndScanAsync(string folderPath)
    {
        if (string.IsNullOrWhiteSpace(folderPath) || !Directory.Exists(folderPath)) return;

        if (!MonitoredFolders.Contains(folderPath, StringComparer.OrdinalIgnoreCase))
        {
            MonitoredFolders.Add(folderPath);
        }

        await ScanAllFoldersAsync();
    }

    public async Task RemoveFolderAsync(string folderPath)
    {
        MonitoredFolders.Remove(folderPath);
        await ScanAllFoldersAsync();
    }

    public async Task<bool> UpdateTagsAsync(string filePath, string title, string artist, string album,
        uint year, string genre, uint track, uint bpm, string initialKey, byte[]? coverArt = null)
    {
        if (string.IsNullOrWhiteSpace(filePath) || !File.Exists(filePath)) return false;
        try
        {
            await Task.Run(() =>
            {
                using var audioFile = TagLib.File.Create(filePath);
                audioFile.Tag.Title = title.Trim();
                audioFile.Tag.Performers = [artist.Trim()];
                audioFile.Tag.Album = album.Trim();
                audioFile.Tag.Year = year;
                audioFile.Tag.Genres = genre.Split([';', ','], StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries);
                audioFile.Tag.Track = track;
                audioFile.Tag.BeatsPerMinute = bpm;
                audioFile.Tag.InitialKey = initialKey.Trim();
                if (coverArt is { Length: > 0 })
                    audioFile.Tag.Pictures = [new TagLib.Picture(new TagLib.ByteVector(coverArt))];
                audioFile.Save();
            });
            await ScanAllFoldersAsync();
            return true;
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"Tag update failed: {ex.Message}");
            return false;
        }
    }

    public async Task<List<SongItem>> ImportAudioFilesAsync(IEnumerable<string> filePaths)
    {
        var paths = filePaths.Where(path => File.Exists(path) && SupportedExtensions.Contains(Path.GetExtension(path)))
            .Distinct(StringComparer.OrdinalIgnoreCase).ToArray();
        var existingByPath = Songs.GroupBy(song => song.FilePath, StringComparer.OrdinalIgnoreCase)
            .ToDictionary(group => group.Key, group => group.First(), StringComparer.OrdinalIgnoreCase);
        var imported = await Task.Run(() => paths.Select(path =>
        {
            existingByPath.TryGetValue(path, out var previous);
            return ReadSongMetadata(path, previous);
        }).Where(song => song != null).Cast<SongItem>().ToList());
        foreach (var song in imported)
        {
            var existing = Songs.FirstOrDefault(item => string.Equals(item.FilePath, song.FilePath, StringComparison.OrdinalIgnoreCase));
            if (existing == null) Songs.Add(song);
            else
            {
                int index = Songs.IndexOf(existing);
                Songs[index] = song;
            }
        }
        if (imported.Count > 0)
        {
            RebuildAlbumsAndArtists();
            SaveLibrary();
        }
        return imported;
    }

    public async Task ScanAllFoldersAsync()
    {
        await _scanGate.WaitAsync();
        IsScanning = true;
        ScanStatus = "Scanning music library...";
        try
        {
            var folders = MonitoredFolders.ToArray();
            var previousByPath = Songs
                .Where(s => !string.IsNullOrWhiteSpace(s.FilePath))
                .GroupBy(s => s.FilePath, StringComparer.OrdinalIgnoreCase)
                .ToDictionary(group => group.Key, group => group.First(), StringComparer.OrdinalIgnoreCase);
            var scannedSongs = new List<SongItem>();

            await Task.Run(() =>
            {
                foreach (var folder in folders)
                {
                    if (!Directory.Exists(folder)) continue;

                    try
                    {
                        var options = new EnumerationOptions
                        {
                            RecurseSubdirectories = true,
                            IgnoreInaccessible = true,
                            ReturnSpecialDirectories = false,
                            AttributesToSkip = FileAttributes.ReparsePoint
                        };
                        var files = Directory.EnumerateFiles(folder, "*.*", options)
                            .Where(f => SupportedExtensions.Contains(Path.GetExtension(f)));

                        foreach (var file in files)
                        {
                            previousByPath.TryGetValue(file, out var previous);
                            var song = ReadSongMetadata(file, previous);
                            if (song != null) scannedSongs.Add(song);
                        }
                    }
                    catch (Exception ex)
                    {
                        System.Diagnostics.Debug.WriteLine($"Error scanning folder {folder}: {ex.Message}");
                    }
                }
            });

            // Keep favorites, ids and date added for files that were already in the library
            LibraryMerge.CarryOverUserState(Songs, scannedSongs);

            // Update collections on UI thread
            Songs.Clear();
            foreach (var song in scannedSongs.OrderBy(s => s.Title))
            {
                Songs.Add(song);
            }

            RebuildAlbumsAndArtists();
            SaveLibrary();
            ScanStatus = $"Library updated: {Songs.Count} songs loaded.";
            ScanCompleted?.Invoke(this, EventArgs.Empty);
        }
        finally
        {
            IsScanning = false;
            _scanGate.Release();
        }
    }

    private static SongItem? ReadSongMetadata(string filePath, SongItem? previous = null)
    {
        try
        {
            var fileInfo = new FileInfo(filePath);
            if (previous != null && previous.FileSizeBytes == fileInfo.Length &&
                previous.LastWriteTimeUtcTicks == fileInfo.LastWriteTimeUtc.Ticks)
                return previous;

            using var tagFile = TagLib.File.Create(filePath);
            var tag = tagFile.Tag;
            var props = tagFile.Properties;

            string title = !string.IsNullOrWhiteSpace(tag.Title)
                ? tag.Title.Trim()
                : Path.GetFileNameWithoutExtension(filePath);

            string artist = !string.IsNullOrWhiteSpace(tag.FirstPerformer)
                ? tag.FirstPerformer.Trim()
                : (!string.IsNullOrWhiteSpace(tag.FirstAlbumArtist) ? tag.FirstAlbumArtist.Trim() : "Unknown Artist");

            string album = !string.IsNullOrWhiteSpace(tag.Album)
                ? tag.Album.Trim()
                : "Unknown Album";

            // Extract cover art
            string? artPath = null;
            if (tag.Pictures?.Length > 0)
            {
                var pic = tag.Pictures[0];
                string albumKey = $"{artist} - {album}";
                artPath = MemoryOptimizer.SaveArtToCache(pic.Data.Data, albumKey);
            }

            // Extract detailed codec stream information
            string ext = Path.GetExtension(filePath).TrimStart('.').ToUpperInvariant();
            var codecInfo = new AudioCodecInfo
            {
                FormatName = ext,
                CodecDescription = string.IsNullOrWhiteSpace(props.Description) ? $"{ext} stream" : props.Description.Trim(),
                BitrateKbps = props.AudioBitrate,
                SampleRateHz = props.AudioSampleRate,
                Channels = props.AudioChannels,
                BitsPerSample = props.BitsPerSample,
                FileSizeBytes = new FileInfo(filePath).Length,
                FilePath = filePath
            };

            string genre = !string.IsNullOrWhiteSpace(tag.FirstGenre)
                ? tag.FirstGenre.Trim()
                : (tag.Genres?.Length > 0 ? string.Join(", ", tag.Genres) : string.Empty);
            uint bpm = tag.BeatsPerMinute;

            return new SongItem
            {
                Title = title,
                Artist = artist,
                Album = album,
                Duration = props.Duration,
                FilePath = filePath,
                ArtCachePath = artPath,
                Year = tag.Year,
                TrackNumber = tag.Track,
                Genre = genre,
                Bpm = bpm,
                InitialKey = tag.InitialKey ?? string.Empty,
                CodecInfo = codecInfo,
                LastWriteTimeUtcTicks = fileInfo.LastWriteTimeUtc.Ticks,
                FileSizeBytes = fileInfo.Length
            };
        }
        catch
        {
            // Fallback for corrupted/tagless files
            try
            {
                var fi = new FileInfo(filePath);
                string ext = fi.Extension.TrimStart('.').ToUpperInvariant();
                return new SongItem
                {
                    Title = Path.GetFileNameWithoutExtension(filePath),
                    Artist = "Unknown Artist",
                    Album = "Unknown Album",
                    Duration = TimeSpan.Zero,
                    FilePath = filePath,
                    LastWriteTimeUtcTicks = fi.LastWriteTimeUtc.Ticks,
                    FileSizeBytes = fi.Length,
                    CodecInfo = new AudioCodecInfo
                    {
                        FormatName = ext,
                        FileSizeBytes = fi.Length,
                        FilePath = filePath
                    }
                };
            }
            catch
            {
                return null;
            }
        }
    }

    private void RebuildAlbumsAndArtists()
    {
        Albums.Clear();
        var albumGroups = Songs
            .GroupBy(s => $"{s.Album}|{s.Artist}")
            .Select(g => new AlbumItem
            {
                Name = g.First().Album,
                Artist = g.First().Artist,
                ArtCachePath = g.FirstOrDefault(s => !string.IsNullOrEmpty(s.ArtCachePath))?.ArtCachePath,
                Year = g.First().Year,
                Songs = [.. g.OrderBy(s => s.TrackNumber)]
            })
            .OrderBy(a => a.Name);

        foreach (var alb in albumGroups)
        {
            Albums.Add(alb);
        }

        Artists.Clear();
        var artistGroups = Songs
            .GroupBy(s => s.Artist)
            .Select(g => new ArtistItem
            {
                Name = g.Key,
                Songs = [.. g],
                Albums = [.. g.Select(s => s.Album).Distinct()]
            })
            .OrderBy(a => a.Name);

        foreach (var art in artistGroups)
        {
            Artists.Add(art);
        }
    }

    public void SaveLibrary()
    {
        try
        {
            if (!Directory.Exists(ConfigDir)) Directory.CreateDirectory(ConfigDir);

            var data = new LibrarySaveData
            {
                MonitoredFolders = [.. MonitoredFolders],
                Songs = [.. Songs]
            };

            _saveDebounce?.Cancel();
            _saveDebounce?.Dispose();
            _saveDebounce = new CancellationTokenSource();
            _ = SaveLibraryDebouncedAsync(data, _saveDebounce.Token);
        }
        catch { }
    }

    private static async Task SaveLibraryDebouncedAsync(LibrarySaveData snapshot, CancellationToken cancellationToken)
    {
        string tempPath = LibraryFilePath + "." + Guid.NewGuid().ToString("N") + ".tmp";
        try
        {
            await Task.Delay(250, cancellationToken);
            string json = await Task.Run(() => JsonSerializer.Serialize(snapshot, VoidJsonContext.Default.LibrarySaveData), cancellationToken);
            await File.WriteAllTextAsync(tempPath, json, cancellationToken);
            cancellationToken.ThrowIfCancellationRequested();
            File.Move(tempPath, LibraryFilePath, overwrite: true);
        }
        catch (OperationCanceledException) { }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"Failed to save library: {ex.Message}");
        }
        finally
        {
            try { if (File.Exists(tempPath)) File.Delete(tempPath); } catch { }
        }
    }

    private void LoadSavedLibrary()
    {
        try
        {
            if (File.Exists(LibraryFilePath))
            {
                string json = File.ReadAllText(LibraryFilePath);
                var data = JsonSerializer.Deserialize(json, VoidJsonContext.Default.LibrarySaveData);

                if (data != null)
                {
                    MonitoredFolders.Clear();
                    foreach (var f in data.MonitoredFolders) MonitoredFolders.Add(f);

                    Songs.Clear();
                    foreach (var s in data.Songs) Songs.Add(s);

                    RebuildAlbumsAndArtists();
                }
            }
            else
            {
                // Default to Windows Music folder
                string musicPath = Environment.GetFolderPath(Environment.SpecialFolder.MyMusic);
                if (Directory.Exists(musicPath))
                {
                    MonitoredFolders.Add(musicPath);
                }
            }
        }
        catch { }
    }

    public class LibrarySaveData
    {
        public List<string> MonitoredFolders { get; set; } = [];
        public List<SongItem> Songs { get; set; } = [];
    }
}
