using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media.Imaging;
using Microsoft.UI.Dispatching;
using Windows.Media;
using Windows.Media.Playback;
using Windows.Storage;
using Windows.Storage.Streams;

namespace VoidPlayer.WinUI;

public sealed partial class MainWindow : Window
{
    private readonly MediaPlayer _transportPlayer = new();
    private SystemMediaTransportControls? _transportControls;
    private readonly List<string> _pendingActivatedFiles = [];
    public string AppVersion => GetType().Assembly.GetName().Version?.ToString(3) ?? "";

    public MainWindow()
    {
        InitializeComponent();

        Title = $"Void Player - Hi-Fi Audio Experience (v{typeof(MainWindow).Assembly.GetName().Version?.ToString(3)})";
        ExtendsContentIntoTitleBar = true;
        SetTitleBar(AppTitleBar);

        // Load Icon & Logo from AppContext.BaseDirectory for reliable unpackaged resolution
        var iconPath = Path.Combine(AppContext.BaseDirectory, "Assets", "AppIcon.ico");
        if (File.Exists(iconPath))
        {
            AppWindow.SetIcon(iconPath);
        }

        var logoPath = Path.Combine(AppContext.BaseDirectory, "Assets", "Square44x44Logo.png");
        if (File.Exists(logoPath))
        {
            TitleBarIconImage.Source = new BitmapImage(new Uri(logoPath));
        }

        // Navigate the root frame to the main page on startup.
        RootFrame.Navigate(typeof(MainPage));
        _transportControls = _transportPlayer.SystemMediaTransportControls;
        _transportControls.IsEnabled = true;
        _transportControls.IsPlayEnabled = true;
        _transportControls.IsPauseEnabled = true;
        _transportControls.IsNextEnabled = true;
        _transportControls.IsPreviousEnabled = true;
        _transportControls.ButtonPressed += OnTransportButtonPressed;
        _transportControls.DisplayUpdater.Type = MediaPlaybackType.Music;
        RootFrame.Loaded += (_, _) =>
        {
            if (RootFrame.Content is MainPage page)
            {
                page.ViewModel.Player.PropertyChanged += OnPlayerPropertyChanged;
                RefreshTransportState(page.ViewModel);
                ProcessActivatedFiles();
            }
        };
        Closed += OnClosed;
    }

    public void QueueFileActivation(IEnumerable<string> paths)
    {
        _pendingActivatedFiles.AddRange(paths.Where(File.Exists));
        ProcessActivatedFiles();
    }

    private void ProcessActivatedFiles()
    {
        if (_pendingActivatedFiles.Count == 0 || RootFrame.Content is not MainPage page) return;
        var paths = _pendingActivatedFiles.ToArray();
        _pendingActivatedFiles.Clear();
        _ = page.ViewModel.PlayExternalFilesAsync(paths);
    }

    private void OnTransportButtonPressed(SystemMediaTransportControls sender, SystemMediaTransportControlsButtonPressedEventArgs args)
    {
        DispatcherQueue.TryEnqueue(() =>
        {
            if (RootFrame.Content is not MainPage page) return;
            switch (args.Button)
            {
                case SystemMediaTransportControlsButton.Play:
                    if (!page.ViewModel.Player.IsPlaying) page.ViewModel.Player.TogglePlayPause();
                    break;
                case SystemMediaTransportControlsButton.Pause:
                    if (page.ViewModel.Player.IsPlaying) page.ViewModel.Player.TogglePlayPause();
                    break;
                case SystemMediaTransportControlsButton.Next: page.ViewModel.Player.Next(); break;
                case SystemMediaTransportControlsButton.Previous: page.ViewModel.Player.Previous(); break;
            }
        });
    }

    private void OnPlayerPropertyChanged(object? sender, System.ComponentModel.PropertyChangedEventArgs e)
    {
        if (RootFrame.Content is not MainPage page) return;
        if (e.PropertyName == nameof(page.ViewModel.Player.CurrentSong))
            DispatcherQueue.TryEnqueue(() => _ = UpdateTransportMetadataAsync(page.ViewModel));
        else if (e.PropertyName == nameof(page.ViewModel.Player.IsPlaying))
            DispatcherQueue.TryEnqueue(() => RefreshTransportState(page.ViewModel));
    }

    private async System.Threading.Tasks.Task UpdateTransportMetadataAsync(ViewModels.MainViewModel viewModel)
    {
        try
        {
            var song = viewModel.Player.CurrentSong;
            if (song == null) return;
            var updater = _transportControls?.DisplayUpdater;
            if (updater == null) return;
            updater.Type = MediaPlaybackType.Music;
            updater.MusicProperties.Title = song.Title;
            updater.MusicProperties.Artist = song.Artist;
            updater.MusicProperties.AlbumTitle = song.Album;
            updater.Thumbnail = null;
            if (!string.IsNullOrWhiteSpace(song.ArtCachePath) && File.Exists(song.ArtCachePath))
            {
                StorageFile art = await StorageFile.GetFileFromPathAsync(song.ArtCachePath);
                updater.Thumbnail = RandomAccessStreamReference.CreateFromFile(art);
            }
            updater.Update();
        }
        catch (Exception ex) { System.Diagnostics.Debug.WriteLine($"SMTC metadata update failed: {ex.Message}"); }
    }

    private void RefreshTransportState(ViewModels.MainViewModel viewModel)
    {
        if (_transportControls == null) return;
        _transportControls.PlaybackStatus = viewModel.Player.IsPlaying
            ? MediaPlaybackStatus.Playing
            : viewModel.Player.CurrentSong == null ? MediaPlaybackStatus.Stopped : MediaPlaybackStatus.Paused;
        _ = UpdateTransportMetadataAsync(viewModel);
    }

    private void OnClosed(object sender, WindowEventArgs args)
    {
        if (RootFrame.Content is MainPage mainPage)
        {
            mainPage.ViewModel.Player.PropertyChanged -= OnPlayerPropertyChanged;
            mainPage.ViewModel.Dispose();
        }
        _transportControls?.ButtonPressed -= OnTransportButtonPressed;
        _transportPlayer.Dispose();
    }
}
