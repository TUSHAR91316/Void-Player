import 'dart:io';
import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:window_manager/window_manager.dart';

// Internal Service Imports
import 'theme/cyber_theme.dart';
import 'package:just_audio_background/just_audio_background.dart';
import 'services/crash_logger.dart';
import 'providers/player_provider.dart';

// Screens
import 'screens/home_screen.dart';
import 'widgets/system_dynamic_island.dart';

void main() async {
  // Global Error Trap (The Black Box)
  runZonedGuarded(() async {
    WidgetsFlutterBinding.ensureInitialized();
    
    // Initialize JustAudioBackground (Replaces AudioHandler)
    try {
      await JustAudioBackground.init(
        androidNotificationChannelId: 'com.void.player.channel.audio',
        androidNotificationChannelName: 'Void Player Playback',
        androidNotificationOngoing: true,
        notificationColor: const Color(0xFF00FFFF),
      ).timeout(const Duration(seconds: 3));
    } catch (e) {
      debugPrint("⚠️ Audio Init Timed Out or Failed: $e");
    }

    // Desktop: Window Management
    if (Platform.isWindows || Platform.isLinux || Platform.isMacOS) {
      // window_manager 0.6+: native API (nativeapi); synchronous, no ensureInitialized.
      final window = WindowManager.instance.getCurrent();
      if (window != null) {
        window.setSize(const Size(1200, 800).toNative(), false);
        window.backgroundColor = Colors.transparent.toNative();
        window.titleBarStyle = TitleBarStyle.hidden;
        window.center();
        window.show();
        window.focus();
      }
    }

    // Launch Application
    runApp(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => PlayerProvider()),
        ],
        child: const CyberMusicApp(),
      ),
    );

  }, (error, stack) {
    // Catch-All for Fatal Errors
    debugPrint("🔥 FATAL ERROR: $error");
    CrashLogger.log(error, stack);
  });
}

// Overlay Entry Point (Background/PiP)
@pragma("vm:entry-point")
void overlayMain() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      home: const SystemDynamicIsland(),
    ),
  );
}

class CyberMusicApp extends StatelessWidget {
  const CyberMusicApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Void Player',
      debugShowCheckedModeBanner: false,
      theme: CyberTheme.theme,
      home: const HomeScreen(),
    );
  }
}
