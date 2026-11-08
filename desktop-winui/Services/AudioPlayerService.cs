using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Threading;
using LibVLCSharp.Shared;
using Microsoft.UI.Dispatching;
using CommunityToolkit.Mvvm.ComponentModel;
using VoidPlayer.WinUI.Models;

namespace VoidPlayer.WinUI.Services;

public enum RepeatMode
{
    Off,
    All,
    One
}

public partial class AudioPlayerService : ObservableObject, IDisposable
{
    private readonly LibVLC _libVlc;
    private readonly LibVLCSharp.Shared.MediaPlayer _mediaPlayer;
    private Equalizer? _equalizer;
    private Media? _currentMedia;
    private readonly Timer _positionTimer;
    private readonly DispatcherQueue? _dispatcherQueue;
    private readonly List<SongItem> _originalQueue = [];
    private readonly Queue<string> _recentlyPlayedIds = new();
    private readonly HashSet<string> _recentlyPlayedSet = new(StringComparer.Ordinal);
    public Dictionary<string, SmlV2.ListeningStats> ListeningStats { get; private set; } = new(StringComparer.Ordinal);
    private readonly Random _random = new();
    private int _consecutiveFailures;
    private bool _advancingAfterNaturalEnd;
    private string? _lastPlayedSongId;
    private bool _restorePending;

    private const int MaxConsecutiveFailures = 5;
    private const int RecentHistoryLimit = 50;

    [ObservableProperty]
    public partial SongItem? CurrentSong { get; set; }

    [ObservableProperty]
    public partial bool IsPlaying { get; set; }

    [ObservableProperty]
    public partial TimeSpan CurrentPosition { get; set; } = TimeSpan.Zero;

    [ObservableProperty]
    public partial TimeSpan TotalDuration { get; set; } = TimeSpan.Zero;

    [ObservableProperty]
    public partial double Volume { get; set; } = 0.8;

    [ObservableProperty]
    public partial bool IsMuted { get; set; }

    [ObservableProperty]
    public partial bool IsShuffle { get; set; }

    [ObservableProperty]
    public partial RepeatMode RepeatMode { get; set; } = RepeatMode.Off;

    [ObservableProperty]
    public partial int CurrentIndex { get; set; } = -1;

    [ObservableProperty]
    public partial double PlaybackRate { get; set; } = 1.0;

    [ObservableProperty]
    public partial bool IsNormalizationEnabled { get; set; } = true;

    [ObservableProperty]
    public partial bool IsSmartFlowEnabled { get; set; } = true;

    [ObservableProperty]
    public partial string EqualizerPreset { get; set; } = "Flat";

    [ObservableProperty]
    public partial string? PlaybackError { get; set; }

    [ObservableProperty]
    public partial double[] EqualizerBands { get; set; } = [0, 0, 0, 0, 0];

    partial void OnVolumeChanged(double value)
    {
        if (!IsMuted)
        {
            _mediaPlayer.Volume = (int)(Math.Clamp(value, 0.0, 1.0) * 100);
        }
    }

    partial void OnIsMutedChanged(bool value)
    {
        _mediaPlayer.Volume = value ? 0 : (int)(Volume * 100);
    }

    partial void OnPlaybackRateChanged(double value)
    {
        try
        {
            _mediaPlayer.SetRate((float)Math.Clamp(value, 0.25, 2.0));
        }
        catch { }
    }

    public string AudioPipelineStatus { get; }

    public string AudioOutputDeviceStatus { get; }

    public ObservableCollection<SongItem> Queue { get; } = [];
    public Dictionary<string, double[]> CustomEqPresets { get; private set; } = new(StringComparer.OrdinalIgnoreCase);

    public event EventHandler? SongFinished;

    public AudioPlayerService()
    {
        Core.Initialize();
        _dispatcherQueue = DispatcherQueue.GetForCurrentThread();
        _libVlc = new LibVLC("--aout=wasapi");
        _mediaPlayer = new LibVLCSharp.Shared.MediaPlayer(_libVlc)
        {
            Volume = (int)(Volume * 100)
        };

        AudioPipelineStatus = "WASAPI requested | Native 64-bit";
        AudioOutputDeviceStatus = "Windows default device";

        _mediaPlayer.EndReached += OnMediaEnded;
        _mediaPlayer.EncounteredError += OnMediaFailed;

        // Poll position every 250ms for smooth UI scrubbing with low CPU usage
        _positionTimer = new Timer(OnPositionTimerTick, null, 0, 250);
    }

    private void OnPositionTimerTick(object? state)
    {
        if (!_mediaPlayer.IsPlaying) return;

        try
        {
            var pos = TimeSpan.FromMilliseconds(Math.Max(0, _mediaPlayer.Time));
            var dur = TimeSpan.FromMilliseconds(Math.Max(0, _mediaPlayer.Length));

            _dispatcherQueue?.TryEnqueue(() =>
            {
                if (!IsPlaying) return;
                CurrentPosition = pos;
                if (dur > TimeSpan.Zero && dur != TotalDuration)
                {
                    TotalDuration = dur;
                }
            });
        }
        catch { }
    }

    public void PlaySong(SongItem song, IEnumerable<SongItem>? contextQueue = null)
    {
        _restorePending = false;
        if (contextQueue != null)
        {
            SetQueue(contextQueue, song);
        }
        else if (!Queue.Contains(song))
        {
            Queue.Add(song);
            _originalQueue.Add(song);
            CurrentIndex = Queue.Count - 1;
        }
        else
        {
            CurrentIndex = Queue.IndexOf(song);
        }

        PlayCurrentIndex();
    }

    public void SetQueue(IEnumerable<SongItem> songs, SongItem? startSong = null)
    {
        Queue.Clear();
        _originalQueue.Clear();

        foreach (var s in songs)
        {
            Queue.Add(s);
            _originalQueue.Add(s);
        }

        if (IsShuffle)
        {
            ApplyShuffle(startSong);
        }
        else
        {
            CurrentIndex = startSong != null ? Queue.IndexOf(startSong) : (Queue.Count > 0 ? 0 : -1);
        }
    }

    public void RestorePlayback(IEnumerable<SongItem> songs, string? currentSongId, long positionMs)
    {
        var restoredQueue = songs.ToArray();
        if (restoredQueue.Length == 0) return;
        var startSong = restoredQueue.FirstOrDefault(song => string.Equals(song.Id, currentSongId, StringComparison.Ordinal))
            ?? restoredQueue[0];
        Queue.Clear();
        _originalQueue.Clear();
        foreach (var song in restoredQueue)
        {
            Queue.Add(song);
            _originalQueue.Add(song);
        }
        CurrentIndex = Array.IndexOf(restoredQueue, startSong);
        CurrentSong = startSong;
        CurrentPosition = TimeSpan.FromMilliseconds(Math.Max(0, positionMs));
        TotalDuration = startSong.Duration;
        _restorePending = true;
        IsPlaying = false;
    }

    public void Stop()
    {
        _restorePending = false;
        StopCurrentMedia();
        IsPlaying = false;
        CurrentSong = null;
        CurrentIndex = -1;
        CurrentPosition = TimeSpan.Zero;
        TotalDuration = TimeSpan.Zero;
    }

    public void RemoveFromQueue(SongItem song)
    {
        int index = Queue.IndexOf(song);
        if (index >= 0)
        {
            Queue.RemoveAt(index);
            _originalQueue.Remove(song);
            if (index == CurrentIndex)
            {
                if (Queue.Count > 0)
                {
                    CurrentIndex = Math.Min(index, Queue.Count - 1);
                    PlayCurrentIndex();
                }
                else
                {
                    Stop();
                }
            }
            else if (index < CurrentIndex)
            {
                CurrentIndex--;
            }
        }
    }

    public void ClearQueue()
    {
        Queue.Clear();
        _originalQueue.Clear();
        Stop();
    }

    public void PlayCurrentIndex(TimeSpan? startAt = null)
    {
        if (CurrentIndex < 0 || CurrentIndex >= Queue.Count) return;

        var song = Queue[CurrentIndex];
        CurrentSong = song;
        IsPlaying = false;
        StopCurrentMedia();

        try
        {
            if (!File.Exists(song.FilePath))
            {
                AdvanceAfterFailure($"File not found: {song.Title}");
                return;
            }
            _currentMedia = new Media(_libVlc, song.FilePath, FromType.FromPath);
            if (IsNormalizationEnabled)
            {
                _currentMedia.AddOption(":audio-filter=compressor");
            }
            if (!_mediaPlayer.Play(_currentMedia))
            {
                AdvanceAfterFailure($"Could not play: {song.Title}");
                return;
            }
            if (startAt is { } restorePosition && restorePosition > TimeSpan.Zero)
            {
                _mediaPlayer.Time = Math.Clamp((long)restorePosition.TotalMilliseconds, 0, Math.Max(0, (long)song.Duration.TotalMilliseconds));
            }
            _consecutiveFailures = 0;
            PlaybackError = null;
            RememberPlayed(song);
            RecordPlay(song);
            _mediaPlayer.SetRate((float)PlaybackRate);
            ApplyEqualizer();
            IsPlaying = true;
            TotalDuration = song.Duration;
            CurrentPosition = startAt ?? TimeSpan.Zero;
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"Playback failed: {ex.Message}");
            AdvanceAfterFailure($"Playback failed: {song.Title}");
        }
    }

    private void AdvanceAfterFailure(string message)
    {
        StopCurrentMedia();
        PlaybackError = message;
        _consecutiveFailures++;
        if (_consecutiveFailures >= MaxConsecutiveFailures)
        {
            IsPlaying = false;
            CurrentPosition = TimeSpan.Zero;
            TotalDuration = TimeSpan.Zero;
            return;
        }

        if (CurrentIndex + 1 < Queue.Count)
        {
            CurrentIndex++;
            PlayCurrentIndex();
        }
        else if (RepeatMode == RepeatMode.All && Queue.Count > 0)
        {
            CurrentIndex = 0;
            PlayCurrentIndex();
        }
        else
        {
            IsPlaying = false;
            CurrentPosition = TimeSpan.Zero;
            TotalDuration = TimeSpan.Zero;
        }
    }

    private void RememberPlayed(SongItem song)
    {
        if (_recentlyPlayedSet.Contains(song.Id)) return;
        _recentlyPlayedIds.Enqueue(song.Id);
        _recentlyPlayedSet.Add(song.Id);
        while (_recentlyPlayedIds.Count > RecentHistoryLimit)
            _recentlyPlayedSet.Remove(_recentlyPlayedIds.Dequeue());
    }

    public void LoadListeningStats(IReadOnlyDictionary<string, SmlV2.ListeningStats> stats)
    {
        ListeningStats = stats.ToDictionary(pair => pair.Key, pair => pair.Value, StringComparer.Ordinal);
    }

    private void RecordPlay(SongItem song)
    {
        RecordListeningEvent(song.Id, play: true, repeat: string.Equals(_lastPlayedSongId, song.Id, StringComparison.Ordinal));
        _lastPlayedSongId = song.Id;
    }

    private void RecordListeningEvent(string id, bool play = false, bool completion = false, bool skip = false, bool repeat = false)
    {
        var old = ListeningStats.TryGetValue(id, out var existing) ? existing : new SmlV2.ListeningStats(0, 0, 0, 0, 0);
        ListeningStats[id] = old with
        {
            Plays = old.Plays + (play ? 1 : 0),
            Completions = old.Completions + (completion ? 1 : 0),
            Skips = old.Skips + (skip ? 1 : 0),
            Repeats = old.Repeats + (repeat ? 1 : 0),
            LastPlayedEpochMillis = play ? DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() : old.LastPlayedEpochMillis
        };
        if (ListeningStats.Count > 5000)
        {
            foreach (var key in ListeningStats.OrderBy(pair => pair.Value.LastPlayedEpochMillis).Take(ListeningStats.Count - 5000).Select(pair => pair.Key).ToArray())
                ListeningStats.Remove(key);
        }
        OnPropertyChanged(nameof(ListeningStats));
    }

    public void SetPlaybackRate(double rate)
    {
        PlaybackRate = Math.Clamp(rate, 0.25, 2.0);
        try
        {
            _mediaPlayer.SetRate((float)PlaybackRate);
        }
        catch { }
    }

    public void TogglePlayPause()
    {
        if (_restorePending)
        {
            _restorePending = false;
            PlayCurrentIndex(CurrentPosition);
            return;
        }

        if (CurrentSong == null && Queue.Count > 0)
        {
            CurrentIndex = 0;
            PlayCurrentIndex();
            return;
        }

        if (IsPlaying)
        {
            _mediaPlayer.Pause();
            IsPlaying = false;
        }
        else
        {
            IsPlaying = _mediaPlayer.Play();
        }
    }

    public void Next()
    {
        if (Queue.Count == 0) return;
        if (!_advancingAfterNaturalEnd && RepeatMode != RepeatMode.One && CurrentSong != null && CurrentPosition.TotalSeconds < 30)
            RecordListeningEvent(CurrentSong.Id, skip: true);

        if (RepeatMode == RepeatMode.One)
        {
            PlayCurrentIndex();
            return;
        }

        if (CurrentIndex + 1 < Queue.Count)
        {
            CurrentIndex++;
            PlayCurrentIndex();
        }
        else if (RepeatMode == RepeatMode.All)
        {
            if (IsShuffle)
            {
                ApplyShuffle(null);
            }
            else
            {
                CurrentIndex = 0;
            }
            PlayCurrentIndex();
        }
        else if (IsSmartFlowEnabled && CurrentSong != null)
        {
            // Score the whole queue: scoring is O(n) per pick, unlike the O(n²) harmonic reorder.
            var candidateSongs = _originalQueue.Where(song => song.Id != CurrentSong.Id).ToList();
            var freshIds = SmlV2.FilterRadioCandidateIds(candidateSongs.Select(song => song.Id), _recentlyPlayedSet)
                .ToHashSet(StringComparer.Ordinal);
            var unplayed = candidateSongs.Where(song => freshIds.Contains(song.Id)).ToList();
            // No tempo jump over SmlV2.MaxRadioBpmJump when both songs carry a BPM tag.
            var candidates = SmlV2.FilterTempoJumps((int)CurrentSong.Bpm, unplayed, song => (int)song.Bpm);
            if (candidates.Count == 0)
            {
                _mediaPlayer.Pause();
                IsPlaying = false;
                CurrentPosition = TimeSpan.Zero;
                return;
            }
            long now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            int hour = DateTime.Now.Hour;
            var pool = SmlV2.PickPool(candidates.Select(song => (song, SmlV2.RadioScore(
                AiEngineService.ComputeFlowScore(CurrentSong, song), song, hour,
                ListeningStats.TryGetValue(song.Id, out var stats) ? SmlV2.ListeningBias(stats, now) : 0))));
            // Vary only among near-equal picks, matching the Android DJ.
            var next = pool[Random.Shared.Next(pool.Count)];
            Queue.Add(next);
            CurrentIndex = Queue.Count - 1;
            PlayCurrentIndex();
        }
        else
        {
            _mediaPlayer.Pause();
            IsPlaying = false;
            CurrentPosition = TimeSpan.Zero;
        }
    }

    public void Previous()
    {
        if (Queue.Count == 0) return;

        // If played more than 3 seconds, restart current track
        if (CurrentPosition.TotalSeconds > 3)
        {
            Seek(TimeSpan.Zero);
            return;
        }

        if (CurrentIndex > 0)
        {
            CurrentIndex--;
            PlayCurrentIndex();
        }
        else if (RepeatMode == RepeatMode.All)
        {
            CurrentIndex = Queue.Count - 1;
            PlayCurrentIndex();
        }
        else
        {
            Seek(TimeSpan.Zero);
        }
    }

    public void Seek(TimeSpan position)
    {
        try
        {
            _mediaPlayer.Time = (long)Math.Max(0, position.TotalMilliseconds);
            CurrentPosition = position;
        }
        catch { }
    }

    public void SetVolume(double volume)
    {
        Volume = Math.Clamp(volume, 0.0, 1.0);
    }

    public void SetEqualizerBands(IReadOnlyList<double> bands, string? preset = null)
    {
        if (bands.Count != 5) return;
        EqualizerBands = [.. bands];
        if (!string.IsNullOrEmpty(preset))
        {
            EqualizerPreset = preset;
        }
        ApplyEqualizer();
    }

    public void LoadCustomEqPresets(IReadOnlyDictionary<string, double[]> presets)
        => CustomEqPresets = presets.Where(pair => !string.IsNullOrWhiteSpace(pair.Key) && pair.Value.Length == 5)
            .Take(50).ToDictionary(pair => pair.Key.Trim(), pair => pair.Value.Select(value => Math.Clamp(value, -12, 12)).ToArray(), StringComparer.OrdinalIgnoreCase);

    public void SaveCustomEqPreset(string name)
    {
        string clean = (name ?? "").Trim();
        if (clean.Length == 0 || clean.Length > 40 || CustomEqPresets.ContainsKey(clean)) return;
        CustomEqPresets[clean] = [.. EqualizerBands];
    }

    public void DeleteCustomEqPreset(string name) => CustomEqPresets.Remove(name);

    public bool RenameCustomEqPreset(string oldName, string newName)
    {
        string clean = (newName ?? "").Trim();
        if (!CustomEqPresets.TryGetValue(oldName, out var bands) || clean.Length == 0 || clean.Length > 40 ||
            (!string.Equals(oldName, clean, StringComparison.OrdinalIgnoreCase) && CustomEqPresets.ContainsKey(clean))) return false;
        CustomEqPresets.Remove(oldName);
        CustomEqPresets[clean] = bands;
        if (string.Equals(EqualizerPreset, oldName, StringComparison.OrdinalIgnoreCase))
            SetEqualizerBands(bands, clean);
        return true;
    }

    public string ExportCustomEqPresets() => JsonSerializer.Serialize(CustomEqPresets);

    public bool ImportCustomEqPresets(string json)
    {
        try
        {
            var imported = JsonSerializer.Deserialize<Dictionary<string, double[]>>(json);
            if (imported == null) return false;
            foreach (var pair in imported.Take(50))
            {
                if (!string.IsNullOrWhiteSpace(pair.Key) && pair.Key.Length <= 40 && pair.Value.Length == 5 && !CustomEqPresets.ContainsKey(pair.Key))
                    CustomEqPresets[pair.Key] = pair.Value.Select(value => Math.Clamp(value, -12, 12)).ToArray();
            }
            return true;
        }
        catch (JsonException) { return false; }
    }

    public void ApplyCustomEqPreset(string name)
    {
        if (CustomEqPresets.TryGetValue(name, out var bands)) SetEqualizerBands(bands, name);
    }

    public void ResetEqualizer() => SetEqualizerBands([0, 0, 0, 0, 0], "Flat");

    partial void OnIsNormalizationEnabledChanged(bool value)
    {
        if (CurrentSong == null) return;
        var wasPlaying = IsPlaying;
        var position = CurrentPosition;
        PlayCurrentIndex();
        if (position > TimeSpan.Zero) Seek(position);
        if (!wasPlaying && IsPlaying)
        {
            _mediaPlayer.Pause();
            IsPlaying = false;
        }
    }

    private void ApplyEqualizer()
    {
        try
        {
            _equalizer ??= new Equalizer();
            uint bandCount = _equalizer.BandCount;
            if (bandCount == 0) return;

            for (uint band = 0; band < bandCount; band++)
            {
                double position = bandCount == 1 ? 0 : band * (EqualizerBands.Length - 1d) / (bandCount - 1d);
                int left = (int)position;
                int right = Math.Min(left + 1, EqualizerBands.Length - 1);
                double amount = position - left;
                double interpolated = EqualizerBands[left] + (EqualizerBands[right] - EqualizerBands[left]) * amount;
                _equalizer.SetAmp((float)Math.Clamp(interpolated, -12, 12), band);
            }

            _mediaPlayer.SetEqualizer(_equalizer);
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"Equalizer update failed: {ex.Message}");
        }
    }

    public void ToggleMute()
    {
        IsMuted = !IsMuted;
    }

    public void ToggleShuffle()
    {
        IsShuffle = !IsShuffle;
        if (IsShuffle)
        {
            ApplyShuffle(CurrentSong);
        }
        else
        {
            // Restore original queue order
            var curr = CurrentSong;
            Queue.Clear();
            foreach (var s in _originalQueue)
            {
                Queue.Add(s);
            }
            CurrentIndex = curr != null ? Queue.IndexOf(curr) : -1;
        }
    }

    public void CycleRepeatMode()
    {
        RepeatMode = RepeatMode switch
        {
            RepeatMode.Off => RepeatMode.All,
            RepeatMode.All => RepeatMode.One,
            RepeatMode.One => RepeatMode.Off,
            _ => RepeatMode.Off
        };
    }

    private void ApplyShuffle(SongItem? activeSong)
    {
        List<SongItem> list;
        list = new List<SongItem>(_originalQueue);
        int n = list.Count;
        while (n > 1)
        {
            n--;
            int k = _random.Next(n + 1);
            (list[k], list[n]) = (list[n], list[k]);
        }

        // Put currently playing song at first position if specified.
        if (activeSong != null && list.Contains(activeSong))
        {
            list.Remove(activeSong);
            list.Insert(0, activeSong);
        }

        Queue.Clear();
        foreach (var s in list)
        {
            Queue.Add(s);
        }
        CurrentIndex = activeSong != null ? 0 : (Queue.Count > 0 ? 0 : -1);
    }

    private void OnMediaEnded(object? sender, EventArgs args)
    {
        _dispatcherQueue?.TryEnqueue(() =>
        {
            if (CurrentSong != null) RecordListeningEvent(CurrentSong.Id, completion: true);
            _advancingAfterNaturalEnd = true;
            try { Next(); }
            finally { _advancingAfterNaturalEnd = false; }
            SongFinished?.Invoke(this, EventArgs.Empty);
        });
    }

    private void OnMediaFailed(object? sender, EventArgs args)
    {
        System.Diagnostics.Debug.WriteLine("LibVLC reported a playback error.");
        _dispatcherQueue?.TryEnqueue(() =>
        {
            AdvanceAfterFailure($"Unable to decode: {CurrentSong?.Title ?? "audio file"}");
        });
    }

    private void StopCurrentMedia()
    {
        try
        {
            _mediaPlayer.Stop();
            _currentMedia?.Dispose();
            _currentMedia = null;
        }
        catch { }
    }

    public void Dispose()
    {
        _positionTimer.Dispose();
        _mediaPlayer.EndReached -= OnMediaEnded;
        _mediaPlayer.EncounteredError -= OnMediaFailed;
        StopCurrentMedia();
        _mediaPlayer.Dispose();
        _libVlc.Dispose();
        _equalizer?.Dispose();
        _equalizer = null;
        GC.SuppressFinalize(this);
    }
}
