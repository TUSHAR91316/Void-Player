using System.Collections.Generic;
using System.Text.Json.Serialization;
using VoidPlayer.WinUI.Models;

namespace VoidPlayer.WinUI.Services;

[JsonSourceGenerationOptions(WriteIndented = true)]
[JsonSerializable(typeof(PlayerSettings))]
[JsonSerializable(typeof(LibraryService.LibrarySaveData))]
[JsonSerializable(typeof(SongItem))]
[JsonSerializable(typeof(List<SongItem>))]
[JsonSerializable(typeof(List<string>))]
[JsonSerializable(typeof(AudioCodecInfo))]
[JsonSerializable(typeof(double[]))]
public partial class VoidJsonContext : JsonSerializerContext;
