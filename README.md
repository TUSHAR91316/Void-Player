# Void Player

<div align="center">

<a href="https://f-droid.org/en/packages/com.tushar.voidplayer/">
  <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="75">
</a>

<br/><br/>

[![F-Droid](https://img.shields.io/badge/F--Droid-Available-3DDC84?style=flat-square&logo=fdroid&logoColor=white)](https://f-droid.org/en/packages/com.tushar.voidplayer/)
[![GitHub Release](https://img.shields.io/github/v/release/TUSHAR91316/Void-Player?style=flat-square&logo=github)](https://github.com/TUSHAR91316/Void-Player/releases/latest)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.7-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green?style=flat-square)](./LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0+-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/)
[![Windows](https://img.shields.io/badge/Windows-10%2F11-0078D4?style=flat-square&logo=windows&logoColor=white)](https://github.com/TUSHAR91316/Void-Player/releases/latest)

**A modern, high-fidelity, privacy-first local music player built with Kotlin Multiplatform and Compose Multiplatform.**

100% Free and Open Source (FOSS) — Zero Ads — Zero Trackers — Zero Telemetry — 100% Offline

[Get it on F-Droid](https://f-droid.org/en/packages/com.tushar.voidplayer/) &nbsp;·&nbsp; [Download APK](https://github.com/TUSHAR91316/Void-Player/releases/latest) &nbsp;·&nbsp; [Windows Installer](https://github.com/TUSHAR91316/Void-Player/releases/latest)

</div>

---

## Features

### Navigation and Playback Controls

Void Player uses a persistent four-tab bottom navigation bar so that the active tab is always reachable without interrupting playback:

- **Library** — Full song list with favorites filter, text search across title, artist, and album, column sorting (Title / Artist / Duration), and folder picker.
- **AI Hub** — Void AI Insights ("My Vibe Wrapped"), AI DJ Smart Flow, and automatic mood-based collections.
- **Playlists** — Create, manage, and play custom playlists backed by local persistent storage.
- **Now Playing** — Dedicated full-screen player with immersive artwork gradient, gesture controls, lyrics, queue, and speed controls.

A floating Mini-Player Pill sits above the navigation bar, displaying the current track title, artist, a live progress line, quick play/pause and next-track buttons, and expanding to the full Now Playing screen on tap.

### Now Playing Screen

- Dynamic background gradient extracted from album artwork using the Android Palette API.
- Swipe left or right on the album artwork to skip tracks; swipe down or press the system back button to minimize to the Mini-Player Pill.
- Multi-mode slide-up drawer with four panels:
  - **Cover** — Full-resolution album artwork.
  - **Lyrics** — Real-time synchronized LRC lyrics with active-line accent, smooth auto-scroll, and tap-to-seek.
  - **Queue** — Live upcoming track list with one-tap jump-to-song.
  - **Controls** — Playback speed (0.5x to 2.0x, pitch-corrected) and Gentle Sleep Timer with volume fade-out.

### AI Music Intelligence (Fully Offline)

All AI features run on-device using metadata heuristics. No internet connection or external API is used.

- **Void AI Insights** — Analyzes library composition to produce a musical personality profile (e.g., "The Midnight Wanderer", "The High-Drive Dynamo"), vibe breakdown percentages, total library duration, top artist, and a recommended hardware EQ curve.
- **AI DJ Smart Flow** — Scores candidate tracks by keyword similarity, artist affinity, and duration proximity to the current song, then selects the next track from the top-scoring matches.
- **AI Smart Categorization** — Automatically groups tracks into mood and tempo collections: Night Vibes and Lo-Fi, High Energy and Workout, Deep Focus and Study, Romance and Melodic, Quick Hits (under 2.5 minutes), Extended Masterpieces, and Artist Spotlights for artists with two or more tracks.

### Audio Quality

- **Hi-Res Codec Detection** — Identifies FLAC (lossless), WAV (uncompressed PCM), AAC/M4A, OGG/Opus, and MP3 from the file extension and displays an appropriate quality badge.
- **Codec Inspector** — Tap the badge to view the detected format, estimated sample rate, and estimated bitrate.
- **Synchronized LRC Lyrics** — Parses `.lrc` files in the song's folder, timestamps each line, and auto-scrolls in real time. Tapping a lyric line seeks to that position.
- **Sleep Timer** — Set a countdown timer; the last 20 seconds fade the volume to zero before pausing.

### Persistence and Data

- Custom playlists and favorite tracks are stored in `SharedPreferences` (Android) or `~/.voidplayer/` property files (Desktop).
- The last selected folder is remembered and reloaded automatically on next launch.

### Back Navigation

A cross-platform `PlatformBackHandler` intercepts the system back gesture or key and navigates in priority order: close open dialogs, clear the active search query, return to the previous tab in the back stack, and finally return to the Library tab before allowing the app to exit.

### Windows Desktop

- Native directory chooser (`JFileChooser`) for selecting the music folder.
- Java Sound (`AudioInputStream` / `Clip`) audio engine with seek, volume control, shuffle, and repeat.
- Multi-resolution application icon embedded in the setup wizard and executable.
- MSI installer with `perUserInstall = true` for in-place updates without administrator prompts.

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.x (Kotlin Multiplatform) |
| UI Framework | Compose Multiplatform 1.7 (Material 3) |
| Android Audio Engine | AndroidX Media3 / ExoPlayer |
| Desktop Audio Engine | Java Sound SPI (AudioInputStream / Clip) |
| State Management | MVVM with StateFlow and collectAsState |
| Build System | Gradle KMP, JDK 21 |
| Windows Packaging | WiX Toolset (MSI) |
| Minimum Android SDK | API 26 (Android 8.0) |
| Target Android SDK | API 35 (Android 15) |
| Desktop Platform | Windows 10 / 11 (JVM 21) |

---

## Installation

### Android

<a href="https://f-droid.org/en/packages/com.tushar.voidplayer/">
  <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="55">
</a>

- **F-Droid** (recommended): Install from the official F-Droid repository at [com.tushar.voidplayer](https://f-droid.org/en/packages/com.tushar.voidplayer/). No proprietary store, no tracking.
- **Direct APK**: Download the signed release APK from [GitHub Releases](https://github.com/TUSHAR91316/Void-Player/releases/latest) and install manually.

### Windows Desktop

- **MSI Installer** (recommended): Download `VoidPlayer-2.2.0.msi` from [GitHub Releases](https://github.com/TUSHAR91316/Void-Player/releases/latest). Creates Desktop and Start Menu shortcuts. Re-run the installer at any time to update in place.
- **Portable**: Download `VoidPlayer.exe` and run directly without installation.

---

## Building from Source

Prerequisites: JDK 21, Android SDK API 35, Android Studio Ladybug or newer.

```bash
# Clone the v2 branch
git clone -b v2 https://github.com/TUSHAR91316/Void-Player.git
cd Void-Player

# Android — debug build on connected device or emulator
./gradlew :composeApp:installDebug

# Android — signed release APK
./gradlew :composeApp:assembleRelease

# Desktop — run locally
./gradlew :composeApp:run

# Desktop — produce MSI installer and portable executable
./gradlew :composeApp:packageMsi :composeApp:createDistributable

# Run unit tests
./gradlew check
```

---

## Repository Structure

```
composeApp/
├── commonMain/        # Shared Compose UI, player interface, data models, and utilities
│   ├── ui/
│   │   ├── components/    # BottomNavBar, MiniPlayerPill, NowPlayingScreen, AiHubScreen,
│   │   │                  # PlaylistsScreen, SongList, Header, SettingsDialog
│   │   └── theme/         # Material 3 color scheme and surface definitions
│   ├── model/             # Song, Playlist, AiCategory data classes
│   ├── player/            # AudioPlayer interface and RepeatMode, PlayerState definitions
│   ├── data/              # SongRepository interface
│   └── utils/             # AiEngine, AiCategorizer, LrcParser, AudioMetadataUtils,
│                          # SleepTimerManager, ImageCache, MemoryUtils
├── androidMain/       # Android: ExoPlayer, MediaSession, SAF scanning, Palette, OverlayService
└── desktopMain/       # Desktop: Java Sound, JFileChooser, properties-based persistence
```

---

## Privacy

Void Player is designed to operate without any internet connection or server dependency:

- All audio parsing, playback, metadata extraction, and AI categorization runs on the user's device.
- No crash reporters, analytics SDKs, advertising networks, or telemetry are included.
- No user account, registration, or cloud service is required.
- All user data (playlists, favorites, settings, folder history) is stored locally in app-private storage.

---

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md) for development setup, architecture guidelines, commit conventions, and the pull request process.

See [CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md) for community standards.

See [SECURITY.md](./SECURITY.md) for the responsible disclosure policy.

---

## Roadmap

See [ROADMAP.md](./ROADMAP.md) for planned features in Version 2.3 and beyond.

---

## License

This project is licensed under the MIT License. See [LICENSE](./LICENSE) for details.
