using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Globalization;
using System.Text;
using System.Threading.Tasks;
using Windows.UI;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using VoidPlayer.WinUI.Models;
using VoidPlayer.WinUI.Services;

namespace VoidPlayer.WinUI.ViewModels;

public enum SongSortOrder
{
    Title,
    Artist,
    Album,
    Duration,
    DateAdded
}

public partial class MainViewModel : ObservableObject, IDisposable
{
    public string AppVersion => GetType().Assembly.GetName().Version?.ToString(3) ?? "";
    public AudioPlayerService Player { get; }
    public LibraryService Library { get; }

    [ObservableProperty]
    public partial string SearchQuery { get; set; } = string.Empty;

    [ObservableProperty]
    public partial string SelectedNavTag { get; set; } = "Songs";

    [ObservableProperty]
    public partial bool IsNowPlayingExpanded { get; set; }

    [ObservableProperty]
    public partial bool OnlineLyricsEnabled { get; set; }

    [ObservableProperty]
    public partial double CurrentMemoryUsageMB { get; set; }

    // Dynamic UI theming. Only the accent follows the current song's artwork; the "glow" and
    // "border" brushes stay neutral graphite surfaces (see App.xaml tokens) so cards never tint.
    private static readonly Color DefaultAccent = Color.FromArgb(255, 0x3C, 0xCF, 0x8E); // VoidAccentColor

    [ObservableProperty]
    public partial Color DynamicAccentColor { get; set; } = DefaultAccent;

    [ObservableProperty]
    public partial SolidColorBrush DynamicAccentBrush { get; set; } = new(DefaultAccent);

    [ObservableProperty]
    public partial SolidColorBrush DynamicGlowBrush { get; set; } = new(Color.FromArgb(255, 0x1F, 0x22, 0x26));

    [ObservableProperty]
    public partial SolidColorBrush DynamicBorderBrush { get; set; } = new(Color.FromArgb(255, 0x2A, 0x2D, 0x32));

    // Library Filtering & Favorites
    [ObservableProperty]
    public partial bool IsFavoritesFilterActive { get; set; }

    public int FavoritesCount => Library.Songs.Count(s => s.IsFavorite);
    public int TotalSongsCount => Library.Songs.Count;

    public ObservableCollection<SongItem> FilteredSongs { get; } = [];

    // AI Hub Data
    [ObservableProperty]
    public partial AiInsightsData? AiInsights { get; set; }

    public ObservableCollection<AiCategoryItem> AiCategories { get; } = [];

    [ObservableProperty]
    public partial bool IsSmartFlowActive { get; set; } = true;

    // Sleep Timer
    [ObservableProperty]
    public partial int SleepTimerMinutesRemaining { get; set; }

    [ObservableProperty]
    public partial bool IsSleepTimerActive { get; set; }

    private System.Threading.Timer? _sleepTimer;
    private int _sleepTimerSecondsRemaining;
    private double _sleepTimerStartVolume;
    private readonly DispatcherQueue? _dispatcherQueue;
    private readonly DispatcherQueueTimer? _searchDebounceTimer;
    private string _appliedSearchQuery = string.Empty;
    private SongItem? _activeSong;
    private PlayerSettings? _startupSettings;
    private bool _resumeRestored;
    private DateTime _lastPositionSaveUtc = DateTime.MinValue;

    public MainViewModel()
    {
        _dispatcherQueue = DispatcherQueue.GetForCurrentThread();
        _searchDebounceTimer = _dispatcherQueue?.CreateTimer();
        if (_searchDebounceTimer != null)
        {
            _searchDebounceTimer.Interval = TimeSpan.FromMilliseconds(150);
            _searchDebounceTimer.IsRepeating = false;
            _searchDebounceTimer.Tick += (_, _) =>
            {
                _appliedSearchQuery = SearchQuery;
                UpdateFilteredSongs();
            };
        }
        Player = new AudioPlayerService();
        Library = new LibraryService();

        // 1. Load persisted settings
        var settings = PlayerSettingsStore.Load();
        _startupSettings = settings;
        Player.PlaybackRate = settings.PlaybackRate;
        Player.Volume = settings.Volume;
        Player.IsMuted = settings.IsMuted;
        Player.IsShuffle = settings.IsShuffle;
        Player.RepeatMode = settings.RepeatMode;
        Player.IsNormalizationEnabled = settings.IsNormalizationEnabled;
        Player.IsSmartFlowEnabled = settings.IsSmartFlowEnabled;
        Player.EqualizerPreset = settings.EqualizerPreset;
        Player.SetEqualizerBands(settings.EqualizerBands, settings.EqualizerPreset);
        Player.LoadCustomEqPresets(settings.CustomEqPresets);
        Player.LoadListeningStats(settings.ListeningStats);

        CurrentSort = settings.PreferredSortOrder;
        OnlineLyricsEnabled = settings.OnlineLyricsEnabled;
        IsSmartFlowActive = settings.IsSmartFlowEnabled;

        Library.Songs.CollectionChanged += (_, _) =>
        {
            if (Library.IsScanning) return;
            UpdateFilteredSongs();
            RefreshAiHub();
            OnPropertyChanged(nameof(FavoritesCount));
            OnPropertyChanged(nameof(TotalSongsCount));
        };
        Library.ScanCompleted += OnLibraryScanCompleted;

        Player.PropertyChanged += (s, e) =>
        {
            if (e.PropertyName == nameof(Player.CurrentSong))
            {
                if (_activeSong != null)
                {
                    _activeSong.IsCurrentlyPlaying = false;
                }

                _activeSong = Player.CurrentSong;
                if (_activeSong != null)
                {
                    _activeSong.IsCurrentlyPlaying = true;
                }

                _ = UpdateSongColorAsync(Player.CurrentSong);
                SaveSettings();
            }
            else if (e.PropertyName == nameof(Player.CurrentPosition))
            {
                var now = DateTime.UtcNow;
                if (now - _lastPositionSaveUtc >= TimeSpan.FromSeconds(10))
                {
                    _lastPositionSaveUtc = now;
                    SaveSettings();
                }
            }
            else if (e.PropertyName is nameof(Player.PlaybackRate)
                or nameof(Player.Volume)
                or nameof(Player.IsMuted)
                or nameof(Player.IsShuffle)
                or nameof(Player.IsPlaying)
                or nameof(Player.RepeatMode)
                or nameof(Player.IsNormalizationEnabled)
                or nameof(Player.EqualizerPreset)
                or nameof(Player.EqualizerBands))
            {
                SaveSettings();
            }
        };
        Player.Queue.CollectionChanged += (_, _) => SaveSettings();

        UpdateFilteredSongs();
        RefreshAiHub();
        UpdateMemoryStats();
        TryRestorePlayback();
    }

    private void OnLibraryScanCompleted(object? sender, EventArgs e)
    {
        UpdateFilteredSongs();
        RefreshAiHub();
        OnPropertyChanged(nameof(FavoritesCount));
        OnPropertyChanged(nameof(TotalSongsCount));
        UpdateMemoryStats();
        TryRestorePlayback();
    }

    private void TryRestorePlayback()
    {
        if (_resumeRestored || _startupSettings == null || _startupSettings.LastQueueIds.Count == 0 || Library.Songs.Count == 0) return;
        var byId = Library.Songs.ToDictionary(song => song.Id, StringComparer.Ordinal);
        var queue = _startupSettings.LastQueueIds
            .Select(id => byId.TryGetValue(id, out var song) ? song : null)
            .Where(song => song != null)
            .Cast<SongItem>()
            .ToArray();
        if (queue.Length == 0) return;
        Player.RestorePlayback(queue, _startupSettings.LastCurrentSongId, _startupSettings.LastPositionMs);
        _resumeRestored = true;
    }

    private async Task UpdateSongColorAsync(SongItem? song)
    {
        try
        {
            Color color;
            if (song == null)
            {
                color = DefaultAccent;
            }
            else
            {
                var fallbackKey = $"{song.Artist} {song.Album} {song.Title}";
                color = EnsureReadableOnGround(await ColorExtractorService.ExtractDominantColorAsync(song.ArtCachePath, fallbackKey));
            }

            DynamicAccentColor = color;
            DynamicAccentBrush = new SolidColorBrush(color);
        }
        catch { }
    }

    /// <summary>
    /// Album colors are often dark. The accent is used for text and slider fills on the graphite
    /// ground, so lighten it until it reaches 4.5:1 contrast (see Services/AccentContrast).
    /// </summary>
    internal static Color EnsureReadableOnGround(Color color)
    {
        var (r, g, b) = Services.AccentContrast.EnsureReadableOnGround((color.R, color.G, color.B));
        return Color.FromArgb(255, r, g, b);
    }

    partial void OnSearchQueryChanged(string value)
    {
        if (_searchDebounceTimer == null)
        {
            _appliedSearchQuery = value;
            UpdateFilteredSongs();
            return;
        }
        _searchDebounceTimer.Stop();
        _searchDebounceTimer.Start();
    }

    partial void OnOnlineLyricsEnabledChanged(bool value) => SaveSettings();

    [ObservableProperty]
    public partial SongSortOrder CurrentSort { get; set; } = SongSortOrder.Title;

    public string CurrentSortLabel => CurrentSort switch
    {
        SongSortOrder.Title => "Sort: Title",
        SongSortOrder.Artist => "Sort: Artist",
        SongSortOrder.Album => "Sort: Album",
        SongSortOrder.Duration => "Sort: Duration",
        SongSortOrder.DateAdded => "Sort: Date Added",
        _ => "Sort: Title"
    };

    public void SetSort(SongSortOrder order)
    {
        CurrentSort = order;
        OnPropertyChanged(nameof(CurrentSortLabel));
        UpdateFilteredSongs();
        SaveSettings();
    }

    public void UpdateFilteredSongs()
    {
        FilteredSongs.Clear();

        string query = FoldSearch(_appliedSearchQuery.Trim());

        var matches = Library.Songs.AsEnumerable();

        if (IsFavoritesFilterActive)
        {
            matches = matches.Where(s => s.IsFavorite);
        }

        if (!string.IsNullOrWhiteSpace(query))
        {
            matches = matches.Where(s =>
                FoldSearch(s.Title).Contains(query, StringComparison.Ordinal) ||
                FoldSearch(s.Artist).Contains(query, StringComparison.Ordinal) ||
                FoldSearch(s.Album).Contains(query, StringComparison.Ordinal));
        }

        matches = CurrentSort switch
        {
            SongSortOrder.Title => matches.OrderBy(s => s.Title),
            SongSortOrder.Artist => matches.OrderBy(s => s.Artist).ThenBy(s => s.Title),
            SongSortOrder.Album => matches.OrderBy(s => s.Album).ThenBy(s => s.TrackNumber),
            SongSortOrder.Duration => matches.OrderByDescending(s => s.Duration.TotalSeconds),
            SongSortOrder.DateAdded => matches.OrderByDescending(s => s.DateAdded),
            _ => matches.OrderBy(s => s.Title)
        };

        foreach (var s in matches)
        {
            s.IsCurrentlyPlaying = IsSameTrack(s, Player.CurrentSong);
            FilteredSongs.Add(s);
        }
    }

    private static bool IsSameTrack(SongItem left, SongItem? right)
        => right != null && string.Equals(left.FilePath, right.FilePath, StringComparison.OrdinalIgnoreCase);

    private static string FoldSearch(string value) => new string(value.Normalize(NormalizationForm.FormD)
        .Where(character => CharUnicodeInfo.GetUnicodeCategory(character) != UnicodeCategory.NonSpacingMark)
        .ToArray()).Normalize(NormalizationForm.FormC);

    [RelayCommand]
    public void SetFilterAll()
    {
        IsFavoritesFilterActive = false;
        UpdateFilteredSongs();
    }

    [RelayCommand]
    public void SetFilterFavorites()
    {
        IsFavoritesFilterActive = true;
        UpdateFilteredSongs();
    }

    [RelayCommand]
    public void ToggleFavorite(SongItem song)
    {
        song.IsFavorite = !song.IsFavorite;
        OnPropertyChanged(nameof(FavoritesCount));
        if (IsFavoritesFilterActive)
        {
            UpdateFilteredSongs();
        }
        Library.SaveLibrary();
    }

    public void RefreshAiHub()
    {
        try
        {
            AiInsights = AiEngineService.GenerateInsights(Library.Songs);
            var categories = AiEngineService.Categorize(Library.Songs);

            AiCategories.Clear();
            foreach (var cat in categories)
            {
                AiCategories.Add(cat);
            }
        }
        catch { }
    }

    [RelayCommand]
    public void PlayAiCategory(AiCategoryItem category)
    {
        if (category.Songs.Count > 0)
        {
            Player.SetQueue(category.Songs, category.Songs[0]);
            Player.PlayCurrentIndex();
        }
    }

    [RelayCommand]
    public void PlaySong(SongItem song)
    {
        Player.PlaySong(song, FilteredSongs.Count > 0 ? FilteredSongs : Library.Songs);
    }

    [RelayCommand]
    public void PlayAll()
    {
        if (FilteredSongs.Count > 0)
        {
            Player.SetQueue(FilteredSongs, FilteredSongs[0]);
            Player.PlayCurrentIndex();
        }
    }

    [RelayCommand]
    public void ShuffleAll()
    {
        if (FilteredSongs.Count > 0)
        {
            Player.IsShuffle = true;
            Player.SetQueue(FilteredSongs);
            Player.PlayCurrentIndex();
        }
    }

    [RelayCommand]
    public void PlayAlbum(AlbumItem album)
    {
        if (album.Songs.Count > 0)
        {
            Player.SetQueue(album.Songs, album.Songs[0]);
            Player.PlayCurrentIndex();
        }
    }

    [RelayCommand]
    public void ToggleNowPlaying()
    {
        IsNowPlayingExpanded = !IsNowPlayingExpanded;
    }

    [RelayCommand]
    public void SetPlaybackSpeed(double speed)
    {
        Player.SetPlaybackRate(speed);
    }

    partial void OnIsSmartFlowActiveChanged(bool value)
    {
        Player.IsSmartFlowEnabled = value;
        SaveSettings();
    }

    [RelayCommand]
    public void SetSleepTimer(int minutes)
    {
        _sleepTimer?.Dispose();
        _sleepTimer = null;
        Player.SetVolume(_sleepTimerStartVolume > 0 ? _sleepTimerStartVolume : Player.Volume);

        if (minutes <= 0)
        {
            IsSleepTimerActive = false;
            _sleepTimerSecondsRemaining = 0;
            SleepTimerMinutesRemaining = 0;
            SaveSettings();
            return;
        }

        _sleepTimerStartVolume = Player.Volume;
        _sleepTimerSecondsRemaining = checked(minutes * 60);
        SleepTimerMinutesRemaining = minutes;
        IsSleepTimerActive = true;
        SaveSettings();

        _sleepTimer = new System.Threading.Timer(OnSleepTimerTick, null, 1000, 1000);
    }

    private void OnSleepTimerTick(object? state)
    {
        _dispatcherQueue?.TryEnqueue(() =>
        {
            if (!IsSleepTimerActive || _sleepTimerSecondsRemaining <= 0) return;

            _sleepTimerSecondsRemaining--;
            SleepTimerMinutesRemaining = (int)Math.Ceiling(_sleepTimerSecondsRemaining / 60d);
            if (_sleepTimerSecondsRemaining <= 20)
            {
                Player.SetVolume(_sleepTimerStartVolume * (_sleepTimerSecondsRemaining / 20d));
            }

            if (_sleepTimerSecondsRemaining == 0)
            {
                SleepTimerMinutesRemaining = 0;
                IsSleepTimerActive = false;
                _sleepTimer?.Dispose();
                _sleepTimer = null;

                if (Player.IsPlaying)
                {
                    Player.TogglePlayPause();
                }
                Player.SetVolume(_sleepTimerStartVolume);
            }
        });
    }

    [RelayCommand]
    public async Task ScanLibraryAsync()
    {
        await Library.ScanAllFoldersAsync();
    }

    public async Task PlayExternalFilesAsync(IEnumerable<string> filePaths)
    {
        var songs = await Library.ImportAudioFilesAsync(filePaths);
        if (songs.Count == 0) return;
        Player.SetQueue(songs, songs[0]);
        Player.PlayCurrentIndex();
        UpdateFilteredSongs();
        RefreshAiHub();
    }

    [RelayCommand]
    public void CompactMemory()
    {
        MemoryOptimizer.CompactMemory();
        UpdateMemoryStats();
    }

    public void UpdateMemoryStats()
    {
        CurrentMemoryUsageMB = Math.Round(MemoryOptimizer.GetCurrentMemoryUsageMB(), 1);
    }

    public void SaveSettings()
    {
        var settings = new PlayerSettings
        {
            PlaybackRate = Player.PlaybackRate,
            Volume = Player.Volume,
            IsMuted = Player.IsMuted,
            IsShuffle = Player.IsShuffle,
            RepeatMode = Player.RepeatMode,
            IsNormalizationEnabled = Player.IsNormalizationEnabled,
            IsSmartFlowEnabled = IsSmartFlowActive,
            EqualizerPreset = Player.EqualizerPreset,
            EqualizerBands = Player.EqualizerBands,
            CustomEqPresets = Player.CustomEqPresets,
            PreferredSortOrder = CurrentSort,
            DefaultSleepTimerMinutes = IsSleepTimerActive ? SleepTimerMinutesRemaining : 0,
            OnlineLyricsEnabled = OnlineLyricsEnabled,
            ListeningStats = Player.ListeningStats,
            LastQueueIds = Player.Queue.Select(song => song.Id).ToList(),
            LastCurrentSongId = Player.CurrentSong?.Id,
            LastPositionMs = (long)Math.Max(0, Player.CurrentPosition.TotalMilliseconds)
        };
        PlayerSettingsStore.SaveDebounced(settings);
    }

    public void SaveSettingsImmediate()
    {
        var settings = new PlayerSettings
        {
            PlaybackRate = Player.PlaybackRate,
            Volume = Player.Volume,
            IsMuted = Player.IsMuted,
            IsShuffle = Player.IsShuffle,
            RepeatMode = Player.RepeatMode,
            IsNormalizationEnabled = Player.IsNormalizationEnabled,
            IsSmartFlowEnabled = IsSmartFlowActive,
            EqualizerPreset = Player.EqualizerPreset,
            EqualizerBands = Player.EqualizerBands,
            CustomEqPresets = Player.CustomEqPresets,
            PreferredSortOrder = CurrentSort,
            DefaultSleepTimerMinutes = IsSleepTimerActive ? SleepTimerMinutesRemaining : 0,
            OnlineLyricsEnabled = OnlineLyricsEnabled,
            ListeningStats = Player.ListeningStats,
            LastQueueIds = Player.Queue.Select(song => song.Id).ToList(),
            LastCurrentSongId = Player.CurrentSong?.Id,
            LastPositionMs = (long)Math.Max(0, Player.CurrentPosition.TotalMilliseconds)
        };
        PlayerSettingsStore.SaveImmediate(settings);
    }

    public void Dispose()
    {
        SaveSettingsImmediate();
        _sleepTimer?.Dispose();
        _sleepTimer = null;
        Player.Dispose();
        GC.SuppressFinalize(this);
    }
}
