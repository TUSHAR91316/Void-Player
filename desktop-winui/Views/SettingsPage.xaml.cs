using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Linq;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Navigation;
using Windows.Storage.Pickers;
using VoidPlayer.WinUI.ViewModels;

namespace VoidPlayer.WinUI.Views;

public sealed partial class SettingsPage : Page, INotifyPropertyChanged
{
    public MainViewModel? ViewModel { get; private set; }

    public event PropertyChangedEventHandler? PropertyChanged;

    public string MemoryUsageText => ViewModel != null ? $"{ViewModel.CurrentMemoryUsageMB} MB" : "Unavailable";

    public string SleepTimerStatusText => (ViewModel?.IsSleepTimerActive == true)
        ? $"Active ({ViewModel.SleepTimerMinutesRemaining}m remaining)"
        : "";

    public SettingsPage()
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
            ViewModel.PropertyChanged += OnViewModelPropertyChanged;
            ViewModel.UpdateMemoryStats();
            ApplySpeedButtonState(ViewModel.Player.PlaybackRate);
            ApplySleepButtonState(ViewModel.IsSleepTimerActive ? ViewModel.SleepTimerMinutesRemaining : 0);
            ApplyEqualizerSliders(ViewModel.Player.EqualizerBands);
            ApplyEqualizerPresetButtonState(ViewModel.Player.EqualizerPreset);
            RefreshCustomEqPresetButtons();
        }
    }

    protected override void OnNavigatedFrom(NavigationEventArgs e)
    {
        base.OnNavigatedFrom(e);
        if (ViewModel != null)
        {
            ViewModel.PropertyChanged -= OnViewModelPropertyChanged;
        }
    }

    private void OnViewModelPropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName == nameof(ViewModel.CurrentMemoryUsageMB))
        {
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(MemoryUsageText)));
        }
        else if (e.PropertyName == nameof(ViewModel.SleepTimerMinutesRemaining) ||
                 e.PropertyName == nameof(ViewModel.IsSleepTimerActive))
        {
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(SleepTimerStatusText)));
        }
    }

    private void OnSpeedOptionClick(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string tag && double.TryParse(tag, System.Globalization.CultureInfo.InvariantCulture, out double speed))
        {
            ViewModel?.SetPlaybackSpeed(speed);
            ApplySpeedButtonState(speed);
            ViewModel?.SaveSettings();
        }
    }

    private void OnSleepTimerOptionClick(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string tag && int.TryParse(tag, out int minutes))
        {
            ViewModel?.SetSleepTimer(minutes);
            ApplySleepButtonState(minutes);
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(SleepTimerStatusText)));
        }
    }

    private void OnNormalizationToggled(object sender, RoutedEventArgs e)
    {
        if (ViewModel != null && sender is ToggleSwitch ts)
        {
            ViewModel.Player.IsNormalizationEnabled = ts.IsOn;
            ViewModel.SaveSettings();
        }
    }

    private void OnEqualizerPresetClick(object sender, RoutedEventArgs e)
    {
        if (sender is not Button button || button.Tag is not string preset) return;

        double[] bands = preset switch
        {
            "Bass Boost" => [8, 6, 2, 0, -1],
            "Vocal Pop" => [-2, 1, 5, 4, 2],
            "Electronic" => [6, 3, 0, 4, 7],
            "Acoustic" => [3, 2, 1, 2, 3],
            _ => [0, 0, 0, 0, 0]
        };

        ViewModel?.Player.SetEqualizerBands(bands, preset);
        ApplyEqualizerSliders(bands);
        SetActiveButton(button, EqFlatButton, EqBassButton, EqVocalButton, EqElectronicButton, EqAcousticButton);
        ViewModel?.SaveSettings();
    }

    private void OnEqualizerResetClick(object sender, RoutedEventArgs e)
    {
        ViewModel?.Player.ResetEqualizer();
        ApplyEqualizerSliders([0, 0, 0, 0, 0]);
        SetActiveButton(EqFlatButton, EqFlatButton, EqBassButton, EqVocalButton, EqElectronicButton, EqAcousticButton);
        ViewModel?.SaveSettings();
    }

    private void OnEqualizerSliderChanged(object sender, RangeBaseValueChangedEventArgs e)
    {
        if (Eq60Slider == null || Eq230Slider == null || Eq910Slider == null || Eq36kSlider == null || Eq14kSlider == null) return;
        var bands = new[] { Eq60Slider.Value, Eq230Slider.Value, Eq910Slider.Value, Eq36kSlider.Value, Eq14kSlider.Value };
        ViewModel?.Player.SetEqualizerBands(bands, "Custom");
        SetActiveButton(null, EqFlatButton, EqBassButton, EqVocalButton, EqElectronicButton, EqAcousticButton);
        ViewModel?.SaveSettings();
    }

    private void ApplyEqualizerSliders(IReadOnlyList<double> bands)
    {
        if (bands.Count != 5) return;
        Eq60Slider.Value = bands[0];
        Eq230Slider.Value = bands[1];
        Eq910Slider.Value = bands[2];
        Eq36kSlider.Value = bands[3];
        Eq14kSlider.Value = bands[4];
    }

    private void ApplyEqualizerPresetButtonState(string preset)
    {
        Button? selected = preset switch
        {
            "Bass Boost" => EqBassButton,
            "Vocal Pop" => EqVocalButton,
            "Electronic" => EqElectronicButton,
            "Acoustic" => EqAcousticButton,
            "Flat" => EqFlatButton,
            _ => null
        };
        SetActiveButton(selected, EqFlatButton, EqBassButton, EqVocalButton, EqElectronicButton, EqAcousticButton);
    }

    private void ApplySpeedButtonState(double speed)
    {
        Button? selected = speed switch
        {
            0.5 => Speed05Button,
            0.75 => Speed075Button,
            1.25 => Speed125Button,
            1.5 => Speed15Button,
            2.0 => Speed20Button,
            _ => Speed10Button
        };
        SetActiveButton(selected, Speed05Button, Speed075Button, Speed10Button, Speed125Button, Speed15Button, Speed20Button);
    }

    private void ApplySleepButtonState(int minutes)
    {
        Button? selected = minutes switch
        {
            15 => Sleep15Button,
            30 => Sleep30Button,
            45 => Sleep45Button,
            60 => Sleep60Button,
            _ => SleepOffButton
        };
        SetActiveButton(selected, SleepOffButton, Sleep15Button, Sleep30Button, Sleep45Button, Sleep60Button);
    }

    private static void SetActiveButton(Button? selected, params Button[] buttons)
    {
        foreach (var button in buttons)
        {
            button.Style = (Style)Application.Current.Resources[
                button == selected ? "VoidActivePillButtonStyle" : "VoidPillButtonStyle"];
        }
    }

    private async void OnAddFolderClick(object sender, RoutedEventArgs e)
    {
        if (ViewModel == null) return;

        try
        {
            var picker = new FolderPicker();
            picker.SuggestedStartLocation = PickerLocationId.MusicLibrary;
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
            }
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"Folder picker error: {ex.Message}");
        }
    }

    private async void OnRemoveFolderClick(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string folderPath && ViewModel != null)
        {
            await ViewModel.Library.RemoveFolderAsync(folderPath);
        }
    }

    private async void OnRescanClick(object sender, RoutedEventArgs e)
    {
        if (ViewModel != null)
        {
            await ViewModel.ScanLibraryAsync();
        }
    }

    private void OnCompactMemoryClick(object sender, RoutedEventArgs e)
    {
        ViewModel?.CompactMemory();
    }

    private void OnRefreshStatsClick(object sender, RoutedEventArgs e)
    {
        ViewModel?.UpdateMemoryStats();
    }

    private async void OnSaveCustomEqPresetClick(object sender, RoutedEventArgs e)
    {
        if (ViewModel == null) return;
        string? name = await PromptTextAsync("Save equalizer preset", "Preset name");
        if (name == null) return;
        ViewModel.Player.SaveCustomEqPreset(name);
        ViewModel.SaveSettings();
        RefreshCustomEqPresetButtons();
    }

    private async void OnImportEqPresetsClick(object sender, RoutedEventArgs e)
    {
        if (ViewModel == null) return;
        string? json = await PromptTextAsync("Import equalizer presets", "Paste preset JSON");
        if (json == null || !ViewModel.Player.ImportCustomEqPresets(json)) return;
        ViewModel.SaveSettings();
        RefreshCustomEqPresetButtons();
    }

    private async void OnExportEqPresetsClick(object sender, RoutedEventArgs e)
    {
        if (ViewModel == null) return;
        await ShowTextAsync("Export equalizer presets", ViewModel.Player.ExportCustomEqPresets());
    }

    private async System.Threading.Tasks.Task<string?> PromptTextAsync(string title, string placeholder)
    {
        var input = new TextBox { PlaceholderText = placeholder };
        var dialog = new ContentDialog
        {
            Title = title,
            Content = input,
            PrimaryButtonText = "Save",
            CloseButtonText = "Cancel",
            XamlRoot = XamlRoot
        };
        return await dialog.ShowAsync() == ContentDialogResult.Primary ? input.Text.Trim() : null;
    }

    private async System.Threading.Tasks.Task ShowTextAsync(string title, string value)
    {
        var input = new TextBox { Text = value, AcceptsReturn = true, TextWrapping = TextWrapping.Wrap, IsReadOnly = true, MinHeight = 160 };
        var dialog = new ContentDialog { Title = title, Content = input, CloseButtonText = "Close", XamlRoot = XamlRoot };
        await dialog.ShowAsync();
    }

    private void RefreshCustomEqPresetButtons()
    {
        if (ViewModel == null || CustomEqPresetsPanel == null) return;
        CustomEqPresetsPanel.Children.Clear();
        foreach (var preset in ViewModel.Player.CustomEqPresets.Keys.ToArray())
        {
            var apply = new Button { Content = preset, Tag = preset, Padding = new Thickness(12, 5, 12, 5) };
            apply.Click += (_, _) =>
            {
                ViewModel.Player.ApplyCustomEqPreset(preset);
                ApplyEqualizerSliders(ViewModel.Player.EqualizerBands);
                ViewModel.SaveSettings();
            };
            var flyout = new MenuFlyout();
            var rename = new MenuFlyoutItem { Text = "Rename" };
            rename.Click += async (_, _) =>
            {
                string? name = await PromptTextAsync("Rename equalizer preset", preset);
                if (name == null) return;
                ViewModel.Player.RenameCustomEqPreset(preset, name);
                ViewModel.SaveSettings();
                RefreshCustomEqPresetButtons();
            };
            var delete = new MenuFlyoutItem { Text = "Delete" };
            delete.Click += (_, _) =>
            {
                ViewModel.Player.DeleteCustomEqPreset(preset);
                ViewModel.SaveSettings();
                RefreshCustomEqPresetButtons();
            };
            flyout.Items.Add(rename);
            flyout.Items.Add(delete);
            apply.ContextFlyout = flyout;
            CustomEqPresetsPanel.Children.Add(apply);
        }
    }

    private static string ReadLocalCrashLog()
    {
        string path = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "VoidPlayer", "logs", "crash.log");
        return System.IO.File.Exists(path) ? System.IO.File.ReadAllText(path) : "No crash log has been recorded.";
    }

    private async void OnViewCrashLogClick(object sender, RoutedEventArgs e)
    {
        var dialog = new ContentDialog
        {
            Title = "Local crash log",
            Content = new ScrollViewer { Content = new TextBlock { Text = ReadLocalCrashLog(), TextWrapping = TextWrapping.Wrap }, MaxHeight = 500 },
            CloseButtonText = "Close",
            XamlRoot = XamlRoot
        };
        await dialog.ShowAsync();
    }

    private async void OnCopyCrashLogClick(object sender, RoutedEventArgs e)
    {
        var data = new Windows.ApplicationModel.DataTransfer.DataPackage();
        data.SetText(ReadLocalCrashLog());
        Windows.ApplicationModel.DataTransfer.Clipboard.SetContent(data);
        var dialog = new ContentDialog { Title = "Crash log copied", Content = "The local log is on the clipboard. Nothing was sent.", CloseButtonText = "OK", XamlRoot = XamlRoot };
        await dialog.ShowAsync();
    }
}
