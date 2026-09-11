using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.IO;
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
    private readonly Random _random = new();

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

    public void Stop()
    {
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

    public void PlayCurrentIndex()
    {
        if (CurrentIndex < 0 || CurrentIndex >= Queue.Count) return;

        var song = Queue[CurrentIndex];
        CurrentSong = song;
        IsPlaying = false;

        try
        {
            if (!File.Exists(song.FilePath)) return;

            StopCurrentMedia();
            _currentMedia = new Media(_libVlc, song.FilePath, FromType.FromPath);
            if (IsNormalizationEnabled)
            {
                _currentMedia.AddOption(":audio-filter=compressor");
            }
            if (!_mediaPlayer.Play(_currentMedia))
            {
                IsPlaying = false;
                return;
            }
            _mediaPlayer.SetRate((float)PlaybackRate);
            ApplyEqualizer();
            IsPlaying = true;
            TotalDuration = song.Duration;
            CurrentPosition = TimeSpan.Zero;
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"Playback failed: {ex.Message}");
            IsPlaying = false;
        }
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
        else if (IsSmartFlowEnabled)
        {
            if (IsShuffle) ApplyShuffle(null);
            else CurrentIndex = 0;
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

    public void ResetEqualizer() => SetEqualizerBands([0, 0, 0, 0, 0], "Flat");

    partial void OnIsNormalizationEnabledChanged(bool value)
    {
        if (CurrentSong == null) return;
        var position = CurrentPosition;
        PlayCurrentIndex();
        if (position > TimeSpan.Zero) Seek(position);
    }

    private void ApplyEqualizer()
    {
        try
        {
            _equalizer = new Equalizer();
            uint bandCount = _equalizer.BandCount;
            if (bandCount == 0) return;

            for (uint controlBand = 0; controlBand < EqualizerBands.Length; controlBand++)
            {
                uint libVlcBand = bandCount == 1
                    ? 0
                    : (uint)Math.Round(controlBand * (bandCount - 1) / (double)(EqualizerBands.Length - 1));
                _equalizer.SetAmp((float)Math.Clamp(EqualizerBands[controlBand], -12, 12), libVlcBand);
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
        if (IsSmartFlowEnabled && _originalQueue.Count > 2)
        {
            list = AiEngineService.GenerateHarmonicQueue(_originalQueue, activeSong);
        }
        else
        {
            list = new List<SongItem>(_originalQueue);
            int n = list.Count;
            while (n > 1)
            {
                n--;
                int k = _random.Next(n + 1);
                (list[k], list[n]) = (list[n], list[k]);
            }

            // Put currently playing song at first position if specified
            if (activeSong != null && list.Contains(activeSong))
            {
                list.Remove(activeSong);
                list.Insert(0, activeSong);
            }
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
            Next();
            SongFinished?.Invoke(this, EventArgs.Empty);
        });
    }

    private void OnMediaFailed(object? sender, EventArgs args)
    {
        System.Diagnostics.Debug.WriteLine("LibVLC reported a playback error.");
        _dispatcherQueue?.TryEnqueue(() =>
        {
            StopCurrentMedia();
            IsPlaying = false;
            CurrentPosition = TimeSpan.Zero;
            TotalDuration = TimeSpan.Zero;
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
        GC.SuppressFinalize(this);
    }
}
