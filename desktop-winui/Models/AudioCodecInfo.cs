namespace VoidPlayer.WinUI.Models;

public class AudioCodecInfo
{
    public string FormatName { get; set; } = "Unknown";
    public string CodecDescription { get; set; } = "Standard Audio";
    public int BitrateKbps { get; set; }
    public int SampleRateHz { get; set; }
    public int Channels { get; set; }
    public int BitsPerSample { get; set; }
    public long FileSizeBytes { get; set; }
    public string FilePath { get; set; } = string.Empty;

    public bool IsLossless => FormatName.Equals("FLAC", System.StringComparison.OrdinalIgnoreCase)
        || FormatName.Equals("WAV", System.StringComparison.OrdinalIgnoreCase)
        || FormatName.Equals("AIFF", System.StringComparison.OrdinalIgnoreCase)
        || FormatName.Equals("ALAC", System.StringComparison.OrdinalIgnoreCase);

    public string FormattedBitrate => BitrateKbps > 0
        ? $"{BitrateKbps} kbps"
        : IsLossless ? "Lossless" : "Unknown";

    public string StreamProfile => string.IsNullOrWhiteSpace(CodecDescription)
        ? FormatName
        : $"{FormatName} | {CodecDescription}";

    public string FormattedSampleRate
    {
        get
        {
            if (SampleRateHz <= 0) return "Unknown";
            double khz = SampleRateHz / 1000.0;
            return $"{khz:0.#} kHz";
        }
    }

    public string ChannelsDescription => Channels switch
    {
        1 => "Mono (1.0)",
        2 => "Stereo (2.0)",
        6 => "5.1 Surround",
        8 => "7.1 Surround",
        0 => "Unknown",
        _ => $"{Channels} channels"
    };

    public string FormattedBitDepth => BitsPerSample > 0 ? $"{BitsPerSample}-bit" : "Unknown";

    public string FormattedFileSize
    {
        get
        {
            if (FileSizeBytes <= 0) return "0 MB";
            double mb = FileSizeBytes / (1024.0 * 1024.0);
            return $"{mb:0.##} MB";
        }
    }

    public string QualityTier
    {
        get
        {
            if (IsLossless && (SampleRateHz >= 88200 || BitsPerSample >= 24))
                return "Hi-Res Lossless (Studio Master)";
            if (IsLossless)
                return "Lossless CD Quality";
            if (BitrateKbps >= 320)
                return "High Quality (320k)";
            return "Standard Audio";
        }
    }
}
