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
            try
            {
                var logoPath = System.IO.Path.Combine(AppContext.BaseDirectory, "Assets", "Square44x44Logo.png");
                if (System.IO.File.Exists(logoPath))
                {
                    NavHeaderLogo.Source = new Microsoft.UI.Xaml.Media.Imaging.BitmapImage(new Uri(logoPath));
                }
            }
            catch { }

            NavView.SelectedItem = NavView.MenuItems[0];
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
