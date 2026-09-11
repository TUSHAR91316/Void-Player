using System;
using System.ComponentModel;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using VoidPlayer.WinUI.ViewModels;

namespace VoidPlayer.WinUI.Controls;

public sealed partial class PlayerBarControl : UserControl, INotifyPropertyChanged
{
    public static readonly DependencyProperty ViewModelProperty =
        DependencyProperty.Register(nameof(ViewModel), typeof(MainViewModel), typeof(PlayerBarControl), new PropertyMetadata(null, OnViewModelChanged));

    public MainViewModel? ViewModel
    {
        get => (MainViewModel?)GetValue(ViewModelProperty);
        set => SetValue(ViewModelProperty, value);
    }

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

    public PlayerBarControl()
    {
        InitializeComponent();
    }

    private static void OnViewModelChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
    {
        if (d is PlayerBarControl control)
        {
            if (e.OldValue is MainViewModel oldVm)
            {
                oldVm.Player.PropertyChanged -= control.OnPlayerPropertyChanged;
            }
            if (e.NewValue is MainViewModel newVm)
            {
                newVm.Player.PropertyChanged += control.OnPlayerPropertyChanged;
            }
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
    }

#pragma warning disable IDE0060 // Remove unused parameter
    private void OnPlayPauseClick(object sender, RoutedEventArgs e) => ViewModel?.Player.TogglePlayPause();
    private void OnNextClick(object sender, RoutedEventArgs e) => ViewModel?.Player.Next();
    private void OnPreviousClick(object sender, RoutedEventArgs e) => ViewModel?.Player.Previous();
    private void OnShuffleClick(object sender, RoutedEventArgs e) => ViewModel?.Player.ToggleShuffle();
    private void OnRepeatClick(object sender, RoutedEventArgs e) => ViewModel?.Player.CycleRepeatMode();
    private void OnExpandNowPlayingClick(object sender, RoutedEventArgs e) => ViewModel?.ToggleNowPlaying();
    private void OnTrackInfoTapped(object sender, Microsoft.UI.Xaml.Input.TappedRoutedEventArgs e) => ViewModel?.ToggleNowPlaying();

    private void OnPositionSliderValueChanged(object sender, RangeBaseValueChangedEventArgs e)
    {
        // Handled via two-way binding or seek
    }

    private void OnVolumeChanged(object sender, RangeBaseValueChangedEventArgs e)
    {
        ViewModel?.Player.SetVolume(e.NewValue / 100.0);
    }
#pragma warning restore IDE0060
}
