using System;
using System.ComponentModel;
using Microsoft.UI.Xaml.Controls;
using VoidPlayer.WinUI.ViewModels;
using VoidPlayer.WinUI.Views;

namespace VoidPlayer.WinUI;

public sealed partial class MainPage : Page
{
    public MainViewModel ViewModel { get; }

    public MainPage()
    {
        InitializeComponent();
        ViewModel = new MainViewModel();

        ViewModel.PropertyChanged += OnViewModelPropertyChanged;

        // Default to All Songs on load
        Loaded += (_, _) =>
        {
            NavView.SelectedItem = SongsNavItem;
            ContentFrame.Navigate(typeof(SongsPage), ViewModel);
            NowPlayingFrame.Navigate(typeof(NowPlayingPage), ViewModel);
        };
    }

    private void OnViewModelPropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName == nameof(ViewModel.IsNowPlayingExpanded))
        {
            NowPlayingOverlay.Visibility = ViewModel.IsNowPlayingExpanded
                ? Microsoft.UI.Xaml.Visibility.Visible
                : Microsoft.UI.Xaml.Visibility.Collapsed;
        }
    }

    // The first menu entry is the "Library" header, so look the Songs item up by tag.
    private NavigationViewItem? SongsNavItem => NavView.MenuItems
        .OfType<NavigationViewItem>()
        .FirstOrDefault(item => item.Tag as string == "Songs");

    /// <summary>Search lives in the navigation pane; typing from another page shows the results in Songs.</summary>
    private void OnSearchTextChanged(AutoSuggestBox sender, AutoSuggestBoxTextChangedEventArgs args)
    {
        if (args.Reason != AutoSuggestionBoxTextChangeReason.UserInput || string.IsNullOrEmpty(sender.Text)) return;
        if (ViewModel.IsNowPlayingExpanded) ViewModel.IsNowPlayingExpanded = false;
        if (ContentFrame.CurrentSourcePageType != typeof(SongsPage) && SongsNavItem is { } songs)
        {
            NavView.SelectedItem = songs;
        }
    }

    private void OnNavSelectionChanged(NavigationView sender, NavigationViewSelectionChangedEventArgs args)
    {
        if (args.SelectedItem is NavigationViewItem item && item.Tag is string tag)
        {
            Type targetPage = tag switch
            {
                "Songs" => typeof(SongsPage),
                "Albums" => typeof(AlbumsPage),
                "AIHub" => typeof(AIHubPage),
                "NowPlaying" => typeof(NowPlayingPage),
                "Settings" => typeof(SettingsPage),
                _ => typeof(SongsPage)
            };

            if (ContentFrame.CurrentSourcePageType != targetPage)
            {
                ContentFrame.Navigate(targetPage, ViewModel);
            }
        }
    }
}
