import 'dart:io';
import 'package:flutter/foundation.dart'; 
import 'package:just_audio/just_audio.dart';
import 'package:flutter_overlay_window/flutter_overlay_window.dart';
import 'package:just_audio_background/just_audio_background.dart';
import 'package:audio_metadata_reader/audio_metadata_reader.dart';
import '../services/db_service.dart';
import '../services/file_scanner.dart';
import '../services/crash_logger.dart';

class PlayerProvider extends ChangeNotifier {
  // Use JustAudio Player directly
  final AudioPlayer _player = AudioPlayer();
  final DbService _dbService = DbService();
  final FileScannerService _scanner = FileScannerService();

  List<TrackModel> _library = [];
  bool _isLoading = false;
  
  // Public Accessors
  AudioPlayer get player => _player;
  List<TrackModel> get library => _library;
  bool get isLoading => _isLoading;
  
  // Helpers
  // Helpers
  bool get isPlaying => _player.playing;
  
  TrackModel? get currentTrack {
    final sequence = _player.sequenceState;
    if (sequence == null || sequence.currentSource == null) return null;
    final tag = sequence.currentSource!.tag;
    if (tag is MediaItem) {
      return TrackModel()
        ..title = tag.title
        ..artist = tag.artist ?? "Unknown"
        ..album = tag.album ?? "Unknown"
        ..path = tag.id
        ..duration = tag.duration?.inMilliseconds.toDouble() ?? 0.0;
    }
    return null;
  }

  PlayerProvider() {
    _init();
  }

  Future<void> _init() async {
    try {
      await _dbService.init();
      await _loadLibrary();
      
      // Listen to Player State to update UI
      _player.playerStateStream.listen((state) {
        notifyListeners();
        _updateOverlay();
      });
      
      _player.positionStream.listen((pos) {
        // notifyListeners(); // Optimization: Use StreamBuilder in UI instead
      });

      _player.sequenceStateStream.listen((_) {
        notifyListeners();
        _updateOverlay();
      });
      
      // Setup Loop Mode to simulate a playlist behavior (optional)
      // await _player.setLoopMode(LoopMode.all); 

    } catch (e, stack) {
      CrashLogger.log("Provider Init Error", stack);
    }
  }

  @override
  void dispose() {
    _player.dispose();
    super.dispose();
  }

  Future<void> _loadLibrary() async {
    try {
      _library = await _dbService.getAllTracks();
      notifyListeners();
    } catch (e) {
      debugPrint("Library Load Error: $e");
    }
  }

  void _updateOverlay() {
    if (currentTrack != null) {
      try {
        FlutterOverlayWindow.shareData({
          'title': currentTrack!.title,
          'isPlaying': isPlaying,
        });
      } catch (_) {}
    }
  }

  Future<void> scanFiles(String rootPath) async {
    _isLoading = true;
    notifyListeners();

    try {
      if (Platform.isAndroid) {
         await _scanner.requestPermissions();
      }
      
      // Unified Scan (Handled by Service)
      final newTracks = await _scanner.scanTracks();
      
      if (newTracks.isNotEmpty) {
        await _dbService.clearLibrary();
        await _dbService.saveTracks(newTracks);
        _library = newTracks;
      }
      
    } catch (e, stack) {
      CrashLogger.log("Scan Error", stack);
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> playTrack(TrackModel track) async {
    try {
      // 1. Load and Play
      
      final playlist = _library.map((t) {
          final uri = t.path.startsWith('content://') 
             ? Uri.parse(t.path) 
             : Uri.file(t.path);
             
          return AudioSource.uri(
            uri,
            tag: MediaItem(
              id: t.path,
              title: t.title,
              artist: t.artist,
              album: t.album,
              duration: Duration(milliseconds: t.duration?.toInt() ?? 0),
            ),
          );
        }).toList();

      final index = _library.indexWhere((t) => t.path == track.path);
      await _player.setAudioSources(playlist, initialIndex: index >= 0 ? index : 0);
      await _player.play();
      
    } catch (e, stack) {
      CrashLogger.log("PlayTrack Error", stack);
    }
  }


  Future<void> togglePlay() async {
    if (_player.playing) {
      await _player.pause();
    } else {
      await _player.play();
    }
  }
  
  // Forwarding simple controls
  Future<void> next() => _player.seekToNext();
  Future<void> previous() => _player.seekToPrevious();
  Future<void> seek(Duration pos) => _player.seek(pos);
}


