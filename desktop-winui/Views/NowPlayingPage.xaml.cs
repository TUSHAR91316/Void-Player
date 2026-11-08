using System;
using System.ComponentModel;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using System.Threading;
using Windows.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Navigation;
using VoidPlayer.WinUI.Models;
using VoidPlayer.WinUI.Services;
using VoidPlayer.WinUI.ViewModels;

namespace VoidPlayer.WinUI.Views;

public sealed partial class NowPlayingPage : Page, INotifyPropertyChanged
{
    public MainViewModel? ViewModel { get; private set; }

    public event PropertyChangedEventHandler? PropertyChanged;

    public double TotalSeconds => ViewModel?.Player.TotalDuration.TotalSeconds ?? 0;

    public double CurrentSeconds
    {
        get => ViewModel?.Player.CurrentPosition.TotalSeconds ?? 0;
        set
        {
            if (ViewModel != null && Math.Abs(CurrentSeconds - value) > 0.5)
            {
                ViewModel.Player.Seek(TimeSpan.FromSeconds(value));
            }
        }
    }

    public string CurrentPositionText => ViewModel != null && ViewModel.Player.CurrentPosition > TimeSpan.Zero
        ? ViewModel.Player.CurrentPosition.ToString(@"m\:ss")
        : "0:00";

    public string TotalDurationText => ViewModel != null && ViewModel.Player.TotalDuration > TimeSpan.Zero
        ? ViewModel.Player.TotalDuration.ToString(@"m\:ss")
        : "0:00";

    public string FavoriteGlyph => (ViewModel?.Player.CurrentSong?.IsFavorite == true)
        ? "\uEB52" // Filled Heart
        : "\uEB51"; // Heart Outline

    public Brush FavoriteBrush => (ViewModel?.Player.CurrentSong?.IsFavorite == true)
        ? (Brush)Application.Current.Resources["VoidFavoriteBrush"]
        : (Brush)Application.Current.Resources["VoidTextSecondaryBrush"];

    public string SelectedTab { get; set; } = "Cover";

    public Visibility CoverVisibility => SelectedTab == "Cover" ? Visibility.Visible : Visibility.Collapsed;
    public Visibility LyricsVisibility => SelectedTab == "Lyrics" ? Visibility.Visible : Visibility.Collapsed;
    public Visibility QueueVisibility => SelectedTab == "Queue" ? Visibility.Visible : Visibility.Collapsed;
    public Visibility SpecsVisibility => SelectedTab == "Specs" ? Visibility.Visible : Visibility.Collapsed;

    // Tabs render as a segmented control: the selected tab gets the hairline thumb and primary
    // text, the others stay flat with secondary text. No accent fill, so any album color works.
    private static Brush TabRes(string key) => (Brush)Application.Current.Resources[key];
    private static readonly Brush FlatTabBrush = new SolidColorBrush(Color.FromArgb(0, 0, 0, 0));
    private Brush TabBg(string tab) => SelectedTab == tab ? TabRes("VoidBorderBrush") : FlatTabBrush;
    private Brush TabFg(string tab) => SelectedTab == tab ? TabRes("VoidTextBrush") : TabRes("VoidTextSecondaryBrush");

    public Brush CoverPillBg => TabBg("Cover");
    public Brush CoverPillFg => TabFg("Cover");
    public Brush LyricsPillBg => TabBg("Lyrics");
    public Brush LyricsPillFg => TabFg("Lyrics");
    public Brush QueuePillBg => TabBg("Queue");
    public Brush QueuePillFg => TabFg("Queue");
    public Brush SpecsPillBg => TabBg("Specs");
    public Brush SpecsPillFg => TabFg("Specs");

    public string QueueCountText => ViewModel != null ? $"{ViewModel.Player.Queue.Count} tracks" : "0 tracks";

    public string LyricsText { get; private set; } = "No lyrics found for this song.";

    public string LyricsStatusText { get; private set; } = "No local lyrics";

    private int _lyricsRequestId;
    private CancellationTokenSource? _lyricsCancellation;
    private List<(TimeSpan Time, string Text)> _timedLyrics = [];
    private string _plainLyrics = string.Empty;

    public NowPlayingPage()
    {
        InitializeComponent();
    }

    protected override void OnNavigatedTo(NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        if (e.Parameter is MainViewModel vm)
        {
            ViewModel = vm;
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(ViewModel)));
            ViewModel.Player.PropertyChanged += OnPlayerPropertyChanged;
            ViewModel.PropertyChanged += OnViewModelPropertyChanged;
            ApplySpeedButtonState(ViewModel.Player.PlaybackRate);
            _ = LoadLyricsAsync(ViewModel.Player.CurrentSong);
        }
    }

    protected override void OnNavigatedFrom(NavigationEventArgs e)
    {
        base.OnNavigatedFrom(e);
        if (ViewModel != null)
        {
            _lyricsCancellation?.Cancel();
            _lyricsCancellation?.Dispose();
            _lyricsCancellation = null;
            ViewModel.Player.PropertyChanged -= OnPlayerPropertyChanged;
            ViewModel.PropertyChanged -= OnViewModelPropertyChanged;
        }
    }

    private void OnViewModelPropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName == nameof(ViewModel.DynamicAccentBrush) ||
            e.PropertyName == nameof(ViewModel.DynamicAccentColor))
        {
            NotifyTabChanges();
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(FavoriteBrush)));
        }
    }

    private void OnPlayerPropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName == nameof(ViewModel.Player.CurrentPosition))
        {
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(CurrentSeconds)));
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(CurrentPositionText)));
            UpdateCurrentLyric();
        }
        else if (e.PropertyName == nameof(ViewModel.Player.TotalDuration))
        {
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(TotalSeconds)));
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(TotalDurationText)));
        }
        else if (e.PropertyName == nameof(ViewModel.Player.CurrentSong))
        {
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(FavoriteGlyph)));
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(FavoriteBrush)));
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(QueueCountText)));
            _ = LoadLyricsAsync(ViewModel?.Player.CurrentSong);
        }
        else if (e.PropertyName == nameof(ViewModel.Player.PlaybackRate))
        {
            if (ViewModel != null)
            {
                ApplySpeedButtonState(ViewModel.Player.PlaybackRate);
            }
        }
    }

    private async Task LoadLyricsAsync(SongItem? song)
    {
        _lyricsCancellation?.Cancel();
        _lyricsCancellation?.Dispose();
        _lyricsCancellation = new CancellationTokenSource();
        var cancellationToken = _lyricsCancellation.Token;
        int requestId = ++_lyricsRequestId;
        string lyrics = "No lyrics found for this song.";
        string status = "No local lyrics";
        _timedLyrics = [];
        _plainLyrics = lyrics;

        if (song != null && !string.IsNullOrWhiteSpace(song.FilePath))
        {
            try
            {
                string lrcPath = Path.ChangeExtension(song.FilePath, ".lrc");
                if (File.Exists(lrcPath))
                {
                    string rawLyrics = await File.ReadAllTextAsync(lrcPath);
                    ParseLyrics(rawLyrics, out var timed, out var plain);
                    _timedLyrics = timed;
                    _plainLyrics = plain;
                    if (_timedLyrics.Count > 0)
                    {
                        lyrics = _timedLyrics[0].Text;
                        status = "Synced local .lrc lyrics";
                    }
                    else if (!string.IsNullOrWhiteSpace(plain))
                    {
                        lyrics = plain;
                        status = "Local lyrics";
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Lyrics load failed: {ex.Message}");
            }
        }

        if (_timedLyrics.Count == 0 && _plainLyrics == "No lyrics found for this song." &&
            song != null && ViewModel?.OnlineLyricsEnabled == true)
        {
            try
            {
                var result = await VoidPlayer.WinUI.Services.LyricsService.FetchAsync(song, cancellationToken);
                if (result != null)
                {
                    _plainLyrics = result.Text;
                    if (result.IsSynced) ParseLyrics(result.Text, out _timedLyrics, out _plainLyrics);
                    lyrics = result.IsSynced && _timedLyrics.Count > 0 ? _timedLyrics[0].Text : result.Text;
                    status = result.IsSynced ? "Synced LRCLIB lyrics" : "LRCLIB lyrics";
                }
                else status = "No LRCLIB match";
            }
            catch (OperationCanceledException) { return; }
            catch (Exception ex) { System.Diagnostics.Debug.WriteLine($"LRCLIB request failed: {ex.Message}"); status = "LRCLIB unavailable"; }
        }
        else if (_timedLyrics.Count == 0 && ViewModel?.OnlineLyricsEnabled != true)
        {
            status = "Enable online lyrics in Settings";
        }

        if (requestId != _lyricsRequestId || cancellationToken.IsCancellationRequested) return;
        LyricsText = lyrics;
        LyricsStatusText = status;
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(LyricsText)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(LyricsStatusText)));
        UpdateCurrentLyric();
    }

    private static void ParseLyrics(string raw, out List<(TimeSpan Time, string Text)> timed, out string plain)
        => VoidPlayer.WinUI.Services.LrcLyricsParser.Parse(raw, out timed, out plain);

    private void UpdateCurrentLyric()
    {
        if (_timedLyrics.Count == 0) return;
        TimeSpan position = ViewModel?.Player.CurrentPosition ?? TimeSpan.Zero;
        string? current = _timedLyrics.LastOrDefault(item => item.Time <= position).Text;
        string value = current ?? _timedLyrics[0].Text;
        if (LyricsText == value) return;
        LyricsText = value;
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(LyricsText)));
    }

    private void OnFavoriteClick(object sender, RoutedEventArgs e)
    {
        if (ViewModel?.Player.CurrentSong != null)
        {
            ViewModel.ToggleFavorite(ViewModel.Player.CurrentSong);
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(FavoriteGlyph)));
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(FavoriteBrush)));
        }
    }

    private void OnSpeedClick(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string speedStr && double.TryParse(speedStr, System.Globalization.CultureInfo.InvariantCulture, out double speed))
        {
            ViewModel?.SetPlaybackSpeed(speed);
            ApplySpeedButtonState(speed);
        }
    }

    private void ApplySpeedButtonState(double speed)
    {
        Button? selected = speed switch
        {
            0.5 => NowPlayingSpeed05Button,
            0.75 => NowPlayingSpeed075Button,
            1.25 => NowPlayingSpeed125Button,
            1.5 => NowPlayingSpeed15Button,
            2.0 => NowPlayingSpeed20Button,
            _ => NowPlayingSpeed10Button
        };

        SetSpeedButtonStyle(selected, NowPlayingSpeed05Button, NowPlayingSpeed075Button, NowPlayingSpeed10Button, NowPlayingSpeed125Button, NowPlayingSpeed15Button, NowPlayingSpeed20Button);
    }

    private static void SetSpeedButtonStyle(Button? selected, params Button[] buttons)
    {
        foreach (var button in buttons)
        {
            button.Style = (Style)Application.Current.Resources[
                button == selected ? "VoidActivePillButtonStyle" : "VoidPillButtonStyle"];
        }
    }

    private void OnBackClick(object sender, RoutedEventArgs e)
    {
        if (ViewModel != null)
        {
            ViewModel.IsNowPlayingExpanded = false;
        }
        if (Frame.CanGoBack)
        {
            Frame.GoBack();
        }
    }

    private void OnPlayPauseClick(object sender, RoutedEventArgs e) => ViewModel?.Player.TogglePlayPause();
    private void OnNextClick(object sender, RoutedEventArgs e) => ViewModel?.Player.Next();
    private void OnPreviousClick(object sender, RoutedEventArgs e) => ViewModel?.Player.Previous();
    private void OnShuffleClick(object sender, RoutedEventArgs e) => ViewModel?.Player.ToggleShuffle();
    private void OnRepeatClick(object sender, RoutedEventArgs e) => ViewModel?.Player.CycleRepeatMode();

    private void OnCoverTabClick(object sender, RoutedEventArgs e)
    {
        SelectedTab = "Cover";
        NotifyTabChanges();
    }

    private void OnLyricsTabClick(object sender, RoutedEventArgs e)
    {
        SelectedTab = "Lyrics";
        NotifyTabChanges();
    }

    private void OnQueueTabClick(object sender, RoutedEventArgs e)
    {
        SelectedTab = "Queue";
        NotifyTabChanges();
    }

    private void OnSpecsTabClick(object sender, RoutedEventArgs e)
    {
        SelectedTab = "Specs";
        NotifyTabChanges();
    }

    private void OnQueueItemPlayClick(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement el && el.DataContext is SongItem song && ViewModel != null)
        {
            ViewModel.PlaySong(song);
        }
    }

    private void OnQueueItemRemoveClick(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement el && el.DataContext is SongItem song && ViewModel != null)
        {
            ViewModel.Player.RemoveFromQueue(song);
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(QueueCountText)));
        }
    }

    private async void OnImportM3uClick(object sender, RoutedEventArgs e)
    {
        if (ViewModel == null) return;
        try
        {
            var picker = new Windows.Storage.Pickers.FileOpenPicker();
            picker.SuggestedStartLocation = Windows.Storage.Pickers.PickerLocationId.MusicLibrary;
            picker.FileTypeFilter.Add(".m3u");
            picker.FileTypeFilter.Add(".m3u8");
            if (App.CurrentWindow != null)
            {
                var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(App.CurrentWindow);
                WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);
            }
            var file = await picker.PickSingleFileAsync();
            if (file == null) return;
            string contents = await Windows.Storage.FileIO.ReadTextAsync(file);
            var entries = M3uPlaylistCodec.Parse(contents);
            var songs = ViewModel.Library.Songs.ToList();
            var resolved = entries.Select(entry =>
            {
                string location = entry.Location;
                if (Uri.TryCreate(location, UriKind.Absolute, out var uri) && uri.IsFile) location = uri.LocalPath;
                else if (!Path.IsPathRooted(location)) location = Path.GetFullPath(Path.Combine(Path.GetDirectoryName(file.Path)!, location));
                return songs.FirstOrDefault(song => string.Equals(Path.GetFullPath(song.FilePath), location, StringComparison.OrdinalIgnoreCase))
                    ?? songs.FirstOrDefault(song => string.Equals(Path.GetFileName(song.FilePath), Path.GetFileName(location), StringComparison.OrdinalIgnoreCase));
            }).Where(song => song != null).Cast<SongItem>().ToList();
            if (resolved.Count > 0)
            {
                ViewModel.Player.SetQueue(resolved, resolved[0]);
                ViewModel.Player.PlayCurrentIndex();
            }
            var dialog = new ContentDialog
            {
                Title = "M3U playlist imported",
                Content = $"Added {resolved.Count} of {entries.Count} tracks to the queue.",
                CloseButtonText = "Close",
                XamlRoot = XamlRoot
            };
            await dialog.ShowAsync();
        }
        catch (Exception ex)
        {
            var dialog = new ContentDialog { Title = "Could not import playlist", Content = ex.Message, CloseButtonText = "Close", XamlRoot = XamlRoot };
            await dialog.ShowAsync();
        }
    }

    private async void OnExportM3uClick(object sender, RoutedEventArgs e)
    {
        if (ViewModel == null || ViewModel.Player.Queue.Count == 0) return;
        try
        {
            var picker = new Windows.Storage.Pickers.FileSavePicker
            {
                SuggestedStartLocation = Windows.Storage.Pickers.PickerLocationId.MusicLibrary,
                SuggestedFileName = "VoidPlayer Queue"
            };
            picker.FileTypeChoices.Add("M3U8 playlist", new System.Collections.Generic.List<string> { ".m3u8" });
            if (App.CurrentWindow != null)
            {
                var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(App.CurrentWindow);
                WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);
            }
            var file = await picker.PickSaveFileAsync();
            if (file == null) return;
            var entries = ViewModel.Player.Queue.Select(song => new M3uEntry(song.FilePath, song.Title,
                song.Duration > TimeSpan.Zero ? (long)song.Duration.TotalSeconds : null));
            await Windows.Storage.FileIO.WriteTextAsync(file, M3uPlaylistCodec.Encode(entries));
        }
        catch (Exception ex)
        {
            var dialog = new ContentDialog { Title = "Could not export playlist", Content = ex.Message, CloseButtonText = "Close", XamlRoot = XamlRoot };
            await dialog.ShowAsync();
        }
    }

    private void NotifyTabChanges()
    {
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(CoverVisibility)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(LyricsVisibility)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(QueueVisibility)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(SpecsVisibility)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(CoverPillBg)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(CoverPillFg)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(LyricsPillBg)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(LyricsPillFg)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(QueuePillBg)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(QueuePillFg)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(SpecsPillBg)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(SpecsPillFg)));
    }
}
