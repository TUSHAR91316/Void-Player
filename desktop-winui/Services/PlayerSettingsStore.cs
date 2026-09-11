using System;
using System.IO;
using System.Text.Json;
using System.Threading;
using VoidPlayer.WinUI.ViewModels;

namespace VoidPlayer.WinUI.Services;

public sealed class PlayerSettings
{
    public double PlaybackRate { get; set; } = 1.0;
    public double Volume { get; set; } = 0.8;
    public bool IsMuted { get; set; }
    public bool IsShuffle { get; set; }
    public RepeatMode RepeatMode { get; set; } = RepeatMode.Off;
    public bool IsNormalizationEnabled { get; set; } = true;
    public bool IsSmartFlowEnabled { get; set; } = true;
    public string EqualizerPreset { get; set; } = "Flat";
    public double[] EqualizerBands { get; set; } = [0, 0, 0, 0, 0];
    public SongSortOrder PreferredSortOrder { get; set; } = SongSortOrder.Title;
    public int DefaultSleepTimerMinutes { get; set; }
}

public static class PlayerSettingsStore
{
    private static readonly string SettingsPath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "VoidPlayer",
        "settings.json");

    private static readonly Lock FileLock = new();
    private static Timer? _debounceTimer;
    private static PlayerSettings? _pendingSettings;

    public static PlayerSettings Load()
    {
        lock (FileLock)
        {
            try
            {
                if (File.Exists(SettingsPath))
                {
                    var json = File.ReadAllText(SettingsPath);
                    var settings = JsonSerializer.Deserialize(json, VoidJsonContext.Default.PlayerSettings);
                    if (settings != null)
                    {
                        settings.EqualizerBands = NormalizeBands(settings.EqualizerBands);
                        settings.PlaybackRate = Math.Clamp(settings.PlaybackRate, 0.25, 2.0);
                        settings.Volume = Math.Clamp(settings.Volume, 0.0, 1.0);
                        if (string.IsNullOrWhiteSpace(settings.EqualizerPreset))
                        {
                            settings.EqualizerPreset = "Flat";
                        }
                        return settings;
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Failed to load settings: {ex.Message}");
            }

            var defaults = new PlayerSettings();
            try
            {
                SaveImmediate(defaults);
            }
            catch { }

            return defaults;
        }
    }

    public static void SaveDebounced(PlayerSettings settings)
    {
        lock (FileLock)
        {
            _pendingSettings = settings;
            if (_debounceTimer == null)
            {
                _debounceTimer = new Timer(_ =>
                {
                    PlayerSettings? toSave;
                    lock (FileLock)
                    {
                        toSave = _pendingSettings;
                        _pendingSettings = null;
                        _debounceTimer?.Dispose();
                        _debounceTimer = null;
                    }

                    if (toSave != null)
                    {
                        SaveImmediate(toSave);
                    }
                }, null, 300, Timeout.Infinite);
            }
            else
            {
                _debounceTimer.Change(300, Timeout.Infinite);
            }
        }
    }

    public static void SaveImmediate(PlayerSettings settings)
    {
        lock (FileLock)
        {
            try
            {
                string? directory = Path.GetDirectoryName(SettingsPath);
                if (!string.IsNullOrEmpty(directory)) Directory.CreateDirectory(directory);

                settings.EqualizerBands = NormalizeBands(settings.EqualizerBands);
                string json = JsonSerializer.Serialize(settings, VoidJsonContext.Default.PlayerSettings);

                // Atomic write via temp file to guard against corruption
                string tempPath = SettingsPath + ".tmp";
                File.WriteAllText(tempPath, json);
                File.Move(tempPath, SettingsPath, overwrite: true);
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Failed to save settings: {ex.Message}");
            }
        }
    }

    public static void Save(PlayerSettings settings) => SaveDebounced(settings);

    private static double[] NormalizeBands(double[]? bands)
    {
        var normalized = new double[5];
        if (bands == null) return normalized;
        for (int index = 0; index < normalized.Length && index < bands.Length; index++)
        {
            normalized[index] = Math.Clamp(bands[index], -12.0, 12.0);
        }
        return normalized;
    }
}
