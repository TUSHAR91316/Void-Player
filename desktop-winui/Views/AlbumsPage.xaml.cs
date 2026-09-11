using System;
using System.ComponentModel;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using VoidPlayer.WinUI.Models;
using VoidPlayer.WinUI.ViewModels;

namespace VoidPlayer.WinUI.Views;

public sealed partial class AlbumsPage : Page, INotifyPropertyChanged
{
    public MainViewModel? ViewModel { get; private set; }

    public event PropertyChangedEventHandler? PropertyChanged;

    public string HeaderStatsText
    {
        get
        {
            if (ViewModel == null) return "0 albums";
            int count = ViewModel.Library.Albums.Count;
            return count == 1 ? "1 album in library" : $"{count} albums in library";
        }
    }

    public Visibility EmptyStateVisibility => (ViewModel?.Library.Albums.Count ?? 0) == 0 ? Visibility.Visible : Visibility.Collapsed;
    public Visibility AlbumsListVisibility => (ViewModel?.Library.Albums.Count ?? 0) > 0 ? Visibility.Visible : Visibility.Collapsed;

    public AlbumsPage()
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
            ViewModel.Library.Albums.CollectionChanged += (_, _) =>
            {
                NotifyChanges();
            };
        }
    }

    private void NotifyChanges()
    {
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(HeaderStatsText)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(EmptyStateVisibility)));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(AlbumsListVisibility)));
    }

    private void OnAlbumClick(object sender, ItemClickEventArgs e)
    {
        if (e.ClickedItem is AlbumItem album)
        {
            ViewModel?.PlayAlbum(album);
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
                ViewModel.RefreshAiHub();
                NotifyChanges();
            }
        }
        catch { }
    }
}
