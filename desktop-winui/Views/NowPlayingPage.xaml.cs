using System;
using System.ComponentModel;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using Windows.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Navigation;
using VoidPlayer.WinUI.Models;
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
        ? (ViewModel.DynamicAccentBrush)
        : (Brush)Application.Current.Resources["TextFillColorSecondaryBrush"];

    public string SelectedTab { get; set; } = "Cover";

    public Visibility CoverVisibility => SelectedTab == "Cover" ? Visibility.Visible : Visibility.Collapsed;
    public Visibility LyricsVisibility => SelectedTab == "Lyrics" ? Visibility.Visible : Visibility.Collapsed;
    public Visibility QueueVisibility => SelectedTab == "Queue" ? Visibility.Visible : Visibility.Collapsed;
    public Visibility SpecsVisibility => SelectedTab == "Specs" ? Visibility.Visible : Visibility.Collapsed;

    public Brush CoverPillBg => SelectedTab == "Cover" ? (ViewModel?.DynamicAccentBrush ?? new SolidColorBrush(Color.FromArgb(255, 0, 230, 118))) : new SolidColorBrush(Color.FromArgb(25, 255, 255, 255));
    public Brush CoverPillFg => SelectedTab == "Cover" ? new SolidColorBrush(Color.FromArgb(255, 0, 0, 0)) : (Brush)Application.Current.Resources["TextFillColorPrimaryBrush"];

    public Brush LyricsPillBg => SelectedTab == "Lyrics" ? (ViewModel?.DynamicAccentBrush ?? new SolidColorBrush(Color.FromArgb(255, 0, 230, 118))) : new SolidColorBrush(Color.FromArgb(25, 255, 255, 255));
    public Brush LyricsPillFg => SelectedTab == "Lyrics" ? new SolidColorBrush(Color.FromArgb(255, 0, 0, 0)) : (Brush)Application.Current.Resources["TextFillColorPrimaryBrush"];

    public Brush QueuePillBg => SelectedTab == "Queue" ? (ViewModel?.DynamicAccentBrush ?? new SolidColorBrush(Color.FromArgb(255, 0, 230, 118))) : new SolidColorBrush(Color.FromArgb(25, 255, 255, 255));
    public Brush QueuePillFg => SelectedTab == "Queue" ? new SolidColorBrush(Color.FromArgb(255, 0, 0, 0)) : (Brush)Application.Current.Resources["TextFillColorPrimaryBrush"];

    public Brush SpecsPillBg => SelectedTab == "Specs" ? (ViewModel?.DynamicAccentBrush ?? new SolidColorBrush(Color.FromArgb(255, 0, 230, 118))) : new SolidColorBrush(Color.FromArgb(25, 255, 255, 255));
    public Brush SpecsPillFg => SelectedTab == "Specs" ? new SolidColorBrush(Color.FromArgb(255, 0, 0, 0)) : (Brush)Application.Current.Resources["TextFillColorPrimaryBrush"];

    public string QueueCountText => ViewModel != null ? $"{ViewModel.Player.Queue.Count} tracks" : "0 tracks";

    public string LyricsText { get; private set; } = "No lyrics found for this song.";

    public string LyricsStatusText { get; private set; } = "No local lyrics";

    private int _lyricsRequestId;

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
        int requestId = ++_lyricsRequestId;
        string lyrics = "No lyrics found for this song.";
        string status = "No local lyrics";

        if (song != null && !string.IsNullOrWhiteSpace(song.FilePath))
        {
            try
            {
                string lrcPath = Path.ChangeExtension(song.FilePath, ".lrc");
                if (File.Exists(lrcPath))
                {
                    string rawLyrics = await File.ReadAllTextAsync(lrcPath);
                    string[] lines = rawLyrics
                        .Split(["\r\n", "\n"], StringSplitOptions.RemoveEmptyEntries)
                        .Select(line => Regex.Replace(line, @"\[\d{1,2}:\d{2}(?:[.:]\d{1,3})?\]", string.Empty).Trim())
                        .Where(line => !string.IsNullOrWhiteSpace(line))
                        .ToArray();

                    if (lines.Length > 0)
                    {
                        lyrics = string.Join(Environment.NewLine + Environment.NewLine, lines);
                        status = "Local .lrc lyrics";
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Lyrics load failed: {ex.Message}");
            }
        }

        if (requestId != _lyricsRequestId) return;
        LyricsText = lyrics;
        LyricsStatusText = status;
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(LyricsText)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(LyricsStatusText)));
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
