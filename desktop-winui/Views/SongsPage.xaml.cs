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

    public string HeaderStatsText
    {
        get
        {
            if (ViewModel == null) return "0 songs";
            int count = ViewModel.FilteredSongs.Count;
            return count == 1 ? "1 song in library" : $"{count} songs in library";
        }
    }

    public string AllFilterText => $"All ({ViewModel?.TotalSongsCount ?? 0})";
    public string FavsFilterText => $"Favs ({ViewModel?.FavoritesCount ?? 0})";

    public Brush AllPillBackground => (ViewModel?.IsFavoritesFilterActive != true)
        ? (ViewModel?.DynamicGlowBrush ?? new SolidColorBrush(Color.FromArgb(40, 0, 230, 118)))
        : new SolidColorBrush(Color.FromArgb(20, 255, 255, 255));

    public Brush AllPillBorder => (ViewModel?.IsFavoritesFilterActive != true)
        ? (ViewModel?.DynamicBorderBrush ?? new SolidColorBrush(Color.FromArgb(120, 0, 230, 118)))
        : new SolidColorBrush(Color.FromArgb(40, 255, 255, 255));

    public Brush AllPillForeground => (ViewModel?.IsFavoritesFilterActive != true)
        ? (ViewModel?.DynamicAccentBrush ?? new SolidColorBrush(Color.FromArgb(255, 0, 230, 118)))
        : (Brush)Application.Current.Resources["TextFillColorSecondaryBrush"];

    public Brush FavsPillBackground => (ViewModel?.IsFavoritesFilterActive == true)
        ? new SolidColorBrush(Color.FromArgb(40, 244, 63, 94))
        : new SolidColorBrush(Color.FromArgb(20, 255, 255, 255));

    public Brush FavsPillBorder => (ViewModel?.IsFavoritesFilterActive == true)
        ? new SolidColorBrush(Color.FromArgb(140, 244, 63, 94))
        : new SolidColorBrush(Color.FromArgb(40, 255, 255, 255));

    public string CurrentSortText => ViewModel?.CurrentSortLabel ?? "Sort: Title";

    public Visibility EmptyStateVisibility => (ViewModel == null || ViewModel.FilteredSongs.Count == 0)
        ? Visibility.Visible
        : Visibility.Collapsed;

    public Visibility SongsListVisibility => (ViewModel != null && ViewModel.FilteredSongs.Count > 0)
        ? Visibility.Visible
        : Visibility.Collapsed;

    public Brush FavsPillForeground => (ViewModel?.IsFavoritesFilterActive == true)
        ? new SolidColorBrush(Color.FromArgb(255, 244, 63, 94))
        : (Brush)Application.Current.Resources["TextFillColorSecondaryBrush"];

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
