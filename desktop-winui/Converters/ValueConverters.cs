using System;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Data;
using VoidPlayer.WinUI.Services;

namespace VoidPlayer.WinUI.Converters;

public class NullToVisibilityConverter : IValueConverter
{
    public object Convert(object? value, Type targetType, object parameter, string language)
    {
        bool isNull = value == null || (value is string str && string.IsNullOrEmpty(str));
        bool invert = parameter is string p && p.Equals("invert", StringComparison.OrdinalIgnoreCase);

        if (invert)
            return isNull ? Visibility.Visible : Visibility.Collapsed;
        return isNull ? Visibility.Collapsed : Visibility.Visible;
    }

    public object ConvertBack(object value, Type targetType, object parameter, string language) => throw new NotImplementedException();
}

public class BoolToVisibilityConverter : IValueConverter
{
    public object Convert(object? value, Type targetType, object parameter, string language)
    {
        bool isTrue = value is true;
        bool invert = parameter is string p && p.Equals("invert", StringComparison.OrdinalIgnoreCase);
        if (invert) isTrue = !isTrue;
        return isTrue ? Visibility.Visible : Visibility.Collapsed;
    }

    public object ConvertBack(object value, Type targetType, object parameter, string language) => throw new NotImplementedException();
}

public class BoolToPlayPauseIconConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language)
    {
        bool isPlaying = value is true;
        return isPlaying ? "\uE769" : "\uE768"; // Pause : Play (Segoe Fluent Icons / MDL2)
    }

    public object ConvertBack(object value, Type targetType, object parameter, string language) => throw new NotImplementedException();
}

public class RepeatModeToIconConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language)
    {
        if (value is RepeatMode mode)
        {
            return mode switch
            {
                RepeatMode.One => "\uE8ED", // Repeat One
                RepeatMode.All => "\uE8EE", // Repeat All
                _ => "\uE8EE"
            };
        }
        return "\uE8EE";
    }

    public object ConvertBack(object value, Type targetType, object parameter, string language) => throw new NotImplementedException();
}

public class RepeatModeToOpacityConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language)
    {
        if (value is RepeatMode mode)
        {
            return mode == RepeatMode.Off ? 0.4 : 1.0;
        }
        return 0.4;
    }

    public object ConvertBack(object value, Type targetType, object parameter, string language) => throw new NotImplementedException();
}

public class BoolToOpacityConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language)
    {
        return value is true ? 1.0 : 0.4;
    }

    public object ConvertBack(object value, Type targetType, object parameter, string language) => throw new NotImplementedException();
}

public class VolumeToIconConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language)
    {
        if (value is double vol)
        {
            if (vol <= 0.001) return "\uE74F"; // Mute
            if (vol < 0.33) return "\uE992";  // Low
            if (vol < 0.66) return "\uE993";  // Medium
            return "\uE995";                 // High
        }
        return "\uE995";
    }

    public object ConvertBack(object value, Type targetType, object parameter, string language) => throw new NotImplementedException();
}

public class VolumeToPercentConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language)
        => value is double volume ? Math.Clamp(volume, 0.0, 1.0) * 100.0 : 0.0;

    public object ConvertBack(object value, Type targetType, object parameter, string language) => throw new NotImplementedException();
}

public class BoolToHeartGlyphConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language)
    {
        return value is true ? "\uEB52" : "\uEB51"; // Filled Heart : Outline Heart
    }

    public object ConvertBack(object value, Type targetType, object parameter, string language) => throw new NotImplementedException();
}

public class BoolToHeartBrushConverter : IValueConverter
{
    private static readonly Microsoft.UI.Xaml.Media.SolidColorBrush HeartActiveBrush = new(Windows.UI.Color.FromArgb(255, 244, 63, 94)); // Rose
    private static readonly Microsoft.UI.Xaml.Media.SolidColorBrush HeartInactiveBrush = new(Windows.UI.Color.FromArgb(100, 255, 255, 255)); // Muted

    public object Convert(object value, Type targetType, object parameter, string language)
    {
        return value is true ? HeartActiveBrush : HeartInactiveBrush;
    }

    public object ConvertBack(object value, Type targetType, object parameter, string language) => throw new NotImplementedException();
}

public class PlayingRowBackgroundConverter : IValueConverter
{
    private static readonly Microsoft.UI.Xaml.Media.SolidColorBrush ActiveBrush = new(Windows.UI.Color.FromArgb(42, 0, 229, 117));
    private static readonly Microsoft.UI.Xaml.Media.SolidColorBrush InactiveBrush = new(Windows.UI.Color.FromArgb(0, 0, 0, 0));

    public object Convert(object? value, Type targetType, object parameter, string language)
        => value is true ? ActiveBrush : InactiveBrush;

    public object ConvertBack(object value, Type targetType, object parameter, string language) => throw new NotImplementedException();
}

public class StringToImageSourceConverter : IValueConverter
{
    public object? Convert(object? value, Type targetType, object parameter, string language)
    {
        if (value is string path && !string.IsNullOrWhiteSpace(path))
        {
            try
            {
                if (System.IO.File.Exists(path))
                {
                    return new Microsoft.UI.Xaml.Media.Imaging.BitmapImage(new Uri(path));
                }
                if (Uri.TryCreate(path, UriKind.Absolute, out var uri))
                {
                    return new Microsoft.UI.Xaml.Media.Imaging.BitmapImage(uri);
                }
            }
            catch { }
        }
        return null;
    }

    public object ConvertBack(object value, Type targetType, object parameter, string language) => throw new NotImplementedException();
}

