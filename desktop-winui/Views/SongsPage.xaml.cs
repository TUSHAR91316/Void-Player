using System;
using System.ComponentModel;
using Windows.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Navigation;
using VoidPlayer.WinUI.Models;
using VoidPlayer.WinUI.ViewModels;

namespace VoidPlayer.WinUI.Views;

public sealed partial class SongsPage : Page, INotifyPropertyChanged
{
    public MainViewModel? ViewModel { get; private set; }

    public event PropertyChangedEventHandler? PropertyChanged;

    /// <summary>"8 songs · 43 min" for the visible list.</summary>
    public string HeaderStatsText
    {
        get
        {
            if (ViewModel == null) return "No songs";
            int count = ViewModel.FilteredSongs.Count;
            if (count == 0) return "No songs";
            var total = TimeSpan.FromTicks(ViewModel.FilteredSongs.Sum(song => song.Duration.Ticks));
            string length = total.TotalHours >= 1
                ? $"{(int)total.TotalHours} h {total.Minutes} min"
                : $"{Math.Max(1, (int)Math.Round(total.TotalMinutes))} min";
            return $"{count} {(count == 1 ? "song" : "songs")} · {length}";
        }
    }

    public string AllFilterText => $"All  {ViewModel?.TotalSongsCount ?? 0}";
    public string FavsFilterText => $"Favorites  {ViewModel?.FavoritesCount ?? 0}";

    // Segmented filter: the selected segment gets the hairline "thumb", the other stays flat.
    private static Brush Res(string key) => (Brush)Application.Current.Resources[key];
    private static readonly Brush TransparentBrush = new SolidColorBrush(Color.FromArgb(0, 0, 0, 0));

    public Brush AllPillBackground => ViewModel?.IsFavoritesFilterActive != true ? Res("VoidBorderBrush") : TransparentBrush;
    public Brush AllPillBorder => TransparentBrush;
    public Brush AllPillForeground => ViewModel?.IsFavoritesFilterActive != true ? Res("VoidTextBrush") : Res("VoidTextSecondaryBrush");

    public Brush FavsPillBackground => ViewModel?.IsFavoritesFilterActive == true ? Res("VoidBorderBrush") : TransparentBrush;
    public Brush FavsPillBorder => TransparentBrush;
    public Brush FavsPillForeground => ViewModel?.IsFavoritesFilterActive == true ? Res("VoidTextBrush") : Res("VoidTextSecondaryBrush");

    public string CurrentSortText => ViewModel?.CurrentSortLabel ?? "Sort: Title";

    private bool LibraryIsEmpty => ViewModel == null || ViewModel.TotalSongsCount == 0;

    /// <summary>Only for a library with no songs at all.</summary>
    public Visibility EmptyStateVisibility => LibraryIsEmpty ? Visibility.Visible : Visibility.Collapsed;

    /// <summary>The library has songs, but the search or Favorites filter hides them all.</summary>
    public Visibility NoResultsVisibility => !LibraryIsEmpty && ViewModel!.FilteredSongs.Count == 0
        ? Visibility.Visible
        : Visibility.Collapsed;

    public string NoResultsText => ViewModel?.IsFavoritesFilterActive == true && string.IsNullOrWhiteSpace(ViewModel.SearchQuery)
        ? "No favorites yet. Select the heart on any song to add it here."
        : "No songs match your search.";

    public Visibility SongsListVisibility => (ViewModel != null && ViewModel.FilteredSongs.Count > 0)
        ? Visibility.Visible
        : Visibility.Collapsed;

    public SongsPage()
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
            ViewModel.FilteredSongs.CollectionChanged += (_, _) =>
            {
                NotifyPillChanges();
            };
            ViewModel.PropertyChanged += (_, args) =>
            {
                if (args.PropertyName == nameof(ViewModel.DynamicAccentBrush) ||
                    args.PropertyName == nameof(ViewModel.FavoritesCount) ||
                    args.PropertyName == nameof(ViewModel.TotalSongsCount) ||
                    args.PropertyName == nameof(ViewModel.IsFavoritesFilterActive))
                {
                    NotifyPillChanges();
                }
            };
        }
    }

    private void OnFilterAllClick(object sender, RoutedEventArgs e)
    {
        ViewModel?.SetFilterAll();
        NotifyPillChanges();
    }

    private void OnFilterFavsClick(object sender, RoutedEventArgs e)
    {
        ViewModel?.SetFilterFavorites();
        NotifyPillChanges();
    }

    private void OnItemFavoriteClick(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is SongItem song && ViewModel != null)
        {
            ViewModel.ToggleFavorite(song);
            NotifyPillChanges();
        }
    }

    private async void OnEditTagsClick(object sender, RoutedEventArgs e)
    {
        if (ViewModel == null || sender is not MenuFlyoutItem { Tag: SongItem song }) return;
        var title = new TextBox { Header = "Title", Text = song.Title };
        var artist = new TextBox { Header = "Artist", Text = song.Artist };
        var album = new TextBox { Header = "Album", Text = song.Album };
        var genre = new TextBox { Header = "Genre", Text = song.Genre };
        var year = new TextBox { Header = "Year", Text = song.Year.ToString() };
        var track = new TextBox { Header = "Track", Text = song.TrackNumber.ToString() };
        var bpm = new TextBox { Header = "BPM", Text = song.Bpm.ToString() };
        var initialKey = new TextBox { Header = "Key", Text = song.InitialKey };
        byte[]? coverBytes = null;
        var coverButton = new Button { Content = "Choose replacement cover" };
        coverButton.Click += async (_, _) =>
        {
            try
            {
                var picker = new Windows.Storage.Pickers.FileOpenPicker();
                picker.SuggestedStartLocation = Windows.Storage.Pickers.PickerLocationId.PicturesLibrary;
                foreach (string extension in new[] { ".jpg", ".jpeg", ".png" }) picker.FileTypeFilter.Add(extension);
                if (App.CurrentWindow != null)
                {
                    var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(App.CurrentWindow);
                    WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);
                }
                var file = await picker.PickSingleFileAsync();
                if (file != null) coverBytes = await System.IO.File.ReadAllBytesAsync(file.Path);
            }
            catch (Exception ex) { System.Diagnostics.Debug.WriteLine($"Cover picker failed: {ex.Message}"); }
        };
        var fields = new StackPanel { Spacing = 8 };
        foreach (var field in new[] { title, artist, album, genre, year, track, bpm, initialKey }) fields.Children.Add(field);
        fields.Children.Add(coverButton);
        var dialog = new ContentDialog
        {
            Title = "Edit audio tags",
            Content = new ScrollViewer { Content = fields, MaxHeight = 560 },
            PrimaryButtonText = "Save tags",
            CloseButtonText = "Cancel",
            XamlRoot = XamlRoot
        };
        if (await dialog.ShowAsync() != ContentDialogResult.Primary) return;
        bool saved = await ViewModel.Library.UpdateTagsAsync(song.FilePath, title.Text, artist.Text, album.Text,
            uint.TryParse(year.Text, out var yearValue) ? yearValue : 0,
            genre.Text,
            uint.TryParse(track.Text, out var trackValue) ? trackValue : 0,
            uint.TryParse(bpm.Text, out var bpmValue) ? bpmValue : 0,
            initialKey.Text, coverBytes);
        if (!saved)
        {
            var error = new ContentDialog { Title = "Tags could not be saved", Content = "Check that the file is writable and uses a supported audio format.", CloseButtonText = "Close", XamlRoot = XamlRoot };
            await error.ShowAsync();
        }
    }

    private void NotifyPillChanges()
    {
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(AllFilterText)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(FavsFilterText)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(AllPillBackground)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(AllPillBorder)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(AllPillForeground)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(FavsPillBackground)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(FavsPillBorder)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(FavsPillForeground)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(HeaderStatsText)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(CurrentSortText)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(EmptyStateVisibility)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(NoResultsVisibility)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(NoResultsText)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(SongsListVisibility)));
    }

    private void OnSongItemClick(object sender, ItemClickEventArgs e)
    {
        if (e.ClickedItem is SongItem song)
        {
            ViewModel?.PlaySong(song);
        }
    }

    private void OnPlayAllClick(object sender, RoutedEventArgs e) => ViewModel?.PlayAll();
    private void OnShuffleAllClick(object sender, RoutedEventArgs e) => ViewModel?.ShuffleAll();

    private async void OnScanClick(object sender, RoutedEventArgs e)
    {
        if (ViewModel != null)
        {
            await ViewModel.ScanLibraryAsync();
            NotifyPillChanges();
        }
    }

    private async void OnAddFolderClick(object sender, RoutedEventArgs e)
    {
        if (ViewModel == null) return;
        try
        {
            var picker = new Windows.Storage.Pickers.FolderPicker();
            picker.SuggestedStartLocation = Windows.Storage.Pickers.PickerLocationId.MusicLibrary;
            picker.FileTypeFilter.Add("*");

            if (App.CurrentWindow != null)
            {
                var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(App.CurrentWindow);
                WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);
            }

            var folder = await picker.PickSingleFolderAsync();
            if (folder != null)
            {
                await ViewModel.Library.AddFolderAndScanAsync(folder.Path);
                NotifyPillChanges();
            }
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"Error selecting folder: {ex.Message}");
        }
    }

    private void OnSortTitleClick(object sender, RoutedEventArgs e)
    {
        ViewModel?.SetSort(SongSortOrder.Title);
        NotifyPillChanges();
    }

    private void OnSortArtistClick(object sender, RoutedEventArgs e)
    {
        ViewModel?.SetSort(SongSortOrder.Artist);
        NotifyPillChanges();
    }

    private void OnSortAlbumClick(object sender, RoutedEventArgs e)
    {
        ViewModel?.SetSort(SongSortOrder.Album);
        NotifyPillChanges();
    }

    private void OnSortDurationClick(object sender, RoutedEventArgs e)
    {
        ViewModel?.SetSort(SongSortOrder.Duration);
        NotifyPillChanges();
    }

    private void OnSortDateAddedClick(object sender, RoutedEventArgs e)
    {
        ViewModel?.SetSort(SongSortOrder.DateAdded);
        NotifyPillChanges();
    }
}
