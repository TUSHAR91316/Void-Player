using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
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
    public AudioPlayerService Player { get; }
    public LibraryService Library { get; }

    [ObservableProperty]
    public partial string SearchQuery { get; set; } = string.Empty;

    [ObservableProperty]
    public partial string SelectedNavTag { get; set; } = "Songs";

    [ObservableProperty]
    public partial bool IsNowPlayingExpanded { get; set; }

    [ObservableProperty]
    public partial double CurrentMemoryUsageMB { get; set; }

    // Dynamic UI Theming based on current song
    [ObservableProperty]
    public partial Color DynamicAccentColor { get; set; } = Color.FromArgb(255, 0, 230, 118);

    [ObservableProperty]
    public partial SolidColorBrush DynamicAccentBrush { get; set; } = new(Color.FromArgb(255, 0, 230, 118));

    [ObservableProperty]
    public partial SolidColorBrush DynamicGlowBrush { get; set; } = new(Color.FromArgb(60, 0, 230, 118));

    [ObservableProperty]
    public partial SolidColorBrush DynamicBorderBrush { get; set; } = new(Color.FromArgb(120, 0, 230, 118));

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
    private readonly DispatcherQueue? _dispatcherQueue;
    private SongItem? _activeSong;

    public MainViewModel()
    {
        _dispatcherQueue = DispatcherQueue.GetForCurrentThread();
        Player = new AudioPlayerService();
        Library = new LibraryService();

        // 1. Load persisted settings
        var settings = PlayerSettingsStore.Load();
        Player.PlaybackRate = settings.PlaybackRate;
        Player.Volume = settings.Volume;
        Player.IsMuted = settings.IsMuted;
        Player.IsShuffle = settings.IsShuffle;
        Player.RepeatMode = settings.RepeatMode;
        Player.IsNormalizationEnabled = settings.IsNormalizationEnabled;
        Player.IsSmartFlowEnabled = settings.IsSmartFlowEnabled;
        Player.EqualizerPreset = settings.EqualizerPreset;
        Player.SetEqualizerBands(settings.EqualizerBands, settings.EqualizerPreset);

        CurrentSort = settings.PreferredSortOrder;
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
            }
            else if (e.PropertyName is nameof(Player.PlaybackRate)
                or nameof(Player.Volume)
                or nameof(Player.IsMuted)
                or nameof(Player.IsShuffle)
                or nameof(Player.RepeatMode)
                or nameof(Player.IsNormalizationEnabled)
                or nameof(Player.EqualizerPreset)
                or nameof(Player.EqualizerBands))
            {
                SaveSettings();
            }
        };

        UpdateFilteredSongs();
        RefreshAiHub();
        UpdateMemoryStats();
    }

    private void OnLibraryScanCompleted(object? sender, EventArgs e)
    {
        UpdateFilteredSongs();
        RefreshAiHub();
        OnPropertyChanged(nameof(FavoritesCount));
        OnPropertyChanged(nameof(TotalSongsCount));
        UpdateMemoryStats();
    }

    private async Task UpdateSongColorAsync(SongItem? song)
    {
        try
        {
            Color color;
            if (song == null)
            {
                color = Color.FromArgb(255, 0, 230, 118); // Default Neon Emerald
            }
            else
            {
                var fallbackKey = $"{song.Artist} {song.Album} {song.Title}";
                color = await ColorExtractorService.ExtractDominantColorAsync(song.ArtCachePath, fallbackKey);
            }

            DynamicAccentColor = color;
            DynamicAccentBrush = new SolidColorBrush(color);
            DynamicGlowBrush = new SolidColorBrush(Color.FromArgb(60, color.R, color.G, color.B));
            DynamicBorderBrush = new SolidColorBrush(Color.FromArgb(140, color.R, color.G, color.B));
        }
        catch { }
    }

    partial void OnSearchQueryChanged(string value)
    {
        UpdateFilteredSongs();
    }

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

        string query = SearchQuery?.Trim() ?? string.Empty;

        var matches = Library.Songs.AsEnumerable();

        if (IsFavoritesFilterActive)
        {
            matches = matches.Where(s => s.IsFavorite);
        }

        if (!string.IsNullOrWhiteSpace(query))
        {
            matches = matches.Where(s =>
                s.Title.Contains(query, StringComparison.OrdinalIgnoreCase) ||
                s.Artist.Contains(query, StringComparison.OrdinalIgnoreCase) ||
                s.Album.Contains(query, StringComparison.OrdinalIgnoreCase));
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

        if (minutes <= 0)
        {
            IsSleepTimerActive = false;
            SleepTimerMinutesRemaining = 0;
            SaveSettings();
            return;
        }

        SleepTimerMinutesRemaining = minutes;
        IsSleepTimerActive = true;
        SaveSettings();

        _sleepTimer = new System.Threading.Timer(OnSleepTimerTick, null, 60000, 60000);
    }

    private void OnSleepTimerTick(object? state)
    {
        _dispatcherQueue?.TryEnqueue(() =>
        {
            if (SleepTimerMinutesRemaining > 1)
            {
                SleepTimerMinutesRemaining--;
            }
            else
            {
                SleepTimerMinutesRemaining = 0;
                IsSleepTimerActive = false;
                _sleepTimer?.Dispose();
                _sleepTimer = null;

                if (Player.IsPlaying)
                {
                    Player.TogglePlayPause();
                }
            }
        });
    }

    [RelayCommand]
    public async Task ScanLibraryAsync()
    {
        await Library.ScanAllFoldersAsync();
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
            PreferredSortOrder = CurrentSort,
            DefaultSleepTimerMinutes = IsSleepTimerActive ? SleepTimerMinutesRemaining : 0
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
            PreferredSortOrder = CurrentSort,
            DefaultSleepTimerMinutes = IsSleepTimerActive ? SleepTimerMinutesRemaining : 0
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
