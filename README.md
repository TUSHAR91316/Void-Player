# 🎵 Void Player

<div align="center">

<a href="https://f-droid.org/en/packages/com.tushar.voidplayer/">
  <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">
</a>

<br/><br/>

[![F-Droid Available](https://img.shields.io/badge/F--Droid-Available-3DDC84?style=for-the-badge&logo=fdroid&logoColor=white)](https://f-droid.org/en/packages/com.tushar.voidplayer/)
[![GitHub Release](https://img.shields.io/github/v/release/TUSHAR91316/Void-Player?style=for-the-badge&logo=github&logoColor=white)](https://github.com/TUSHAR91316/Void-Player/releases/latest)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Compose_Multiplatform-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Android](https://img.shields.io/badge/Android_8.0+-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Windows](https://img.shields.io/badge/Windows_10/11-0078D4?style=for-the-badge&logo=windows&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green.svg?style=for-the-badge)

**A modern, high-fidelity, privacy-first local music player built with Kotlin Multiplatform & Jetpack Compose.**  
*100% Free & Open Source (FOSS) • Zero Ads • Zero Trackers • Zero Telemetry • 100% Offline*

[**📱 Get it on F-Droid**](https://f-droid.org/en/packages/com.tushar.voidplayer/) &nbsp;•&nbsp; [**📦 Download APK**](https://github.com/TUSHAR91316/Void-Player/releases/latest) &nbsp;•&nbsp; [**💻 Windows Installer**](https://github.com/TUSHAR91316/Void-Player/releases/latest)

</div>

---

## ✨ Key Features

### 🧭 1. Modern Bottom Navigation & Mini-Player Pill
- **Persistent 4-Tab Navigation**: Seamlessly navigate between dedicated spaces:
  - 🎵 **Library** — All tracks, favorites, search, sorting, and folder picker.
  - ✨ **AI Hub** — Void AI insights, AI DJ Flow, and smart mood collections.
  - 📁 **Playlists** — Custom playlist creation and management.
  - 💿 **Now Playing** — Dedicated full-screen immersive player.
- **Floating Mini-Player Pill**: Sits smoothly above navigation with a live thin progress indicator, play/pause, next track controls, and tap-to-expand into full-screen.

### 💿 2. Dedicated Full-Screen Now Playing Screen
- **Dynamic Palette Background**: Vertical immersive gradient extracted asynchronously from album artwork using Android Palette API.
- **Gesture Controls**: Swipe left/right on cover art to skip tracks; swipe down or press back to minimize.
- **Interactive Multi-Mode Drawer**:
  - 💿 **Cover** — High-resolution album artwork view.
  - 📜 **Synced LRC Lyrics** — Real-time synchronized lyrics with active line highlighting, auto-scroll, and tap-to-seek.
  - 📋 **Live Queue** — Next-up track list with 1-tap jump.
  - ⚡ **Speed & Timer** — `0.5x`–`2.0x` pitch-corrected speed controls and Gentle Sleep Timer.

### 🧠 3. Void AI Hub & Smart Music Intelligence
- **📊 Void AI Insights ("My Vibe Wrapped")**:
  - Computes your library's musical personality (e.g. *"The Midnight Wanderer"*, *"The High-Drive Dynamo"*, *"The Deep Focus Architect"*).
  - Vibe breakdown percentages and total library listening duration.
  - AI-recommended hardware equalizer curves.
- **🎧 AI DJ Smart Flow**: Analyzes acoustic energy and mood vectors to automatically queue the smoothest transitioning next song.
- **✨ AI Smart Categorization**: Auto-groups local tracks into: *Night Vibes & Lo-Fi*, *High Energy*, *Deep Focus*, *Romance & Melodic*, *Quick Hits (<2.5m)*, *Extended Epics*, and *Artist Spotlights*.

### 🎚️ 4. Hi-Res Audio Codec Badge & Inspector
- **Lossless & Hi-Res Detection**: Automatically identifies `✨ Hi-Res FLAC` (24-bit/96kHz), `✨ Hi-Res WAV`, `🎵 AAC HD`, `🎵 OPUS HD`, and `🎵 320 kbps MP3`.
- **Interactive Codec Inspector**: Tap the badge to inspect sample rates, bitrates, and file details.

### 💾 5. Persistent Playlists & Favorites
- Custom playlists and favorite tracks are saved locally and restored automatically across app restarts.
- 1-tap **"Add to Playlist"** from any song's options menu (`⋮`).

### 🎧 6. Audiophile DSP Engine
- **Hardware Equalizer**: Multi-band EQ with custom levels.
- **Dynamic Normalization**: Real-time loudness normalization powered by Android `DynamicsProcessing` DSP.
- **Audio Focus & Auto-Pause**: Pauses on headphone disconnect or incoming calls.
- **Pitch-Corrected Speed Control**: `0.5x`–`2.0x` via native ExoPlayer pitch correction.
- **Gentle Sleep Timer**: Gradual volume fade-out before auto-pause.

### 🔙 7. Smart Back Navigation
- Cross-platform `PlatformBackHandler` closes dialogs, clears search, or navigates back through tabs before exiting.

### 🖥️ 8. Windows Desktop Native Support
- Native Windows Setup Wizard (`.msi`) and portable standalone executable (`VoidPlayer.exe`).
- Native folder picker (`JFileChooser`) with recursive audio scanning and last-used folder restoration.
- Desktop audio engine using Java Sound (`AudioInputStream` / `Clip`) with seek, volume, and queue support.
- Multi-resolution `.ico` embedded in the setup wizard and executable.
- `perUserInstall = true` for seamless in-place updates without admin prompts.

---

## 🛠 Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin 2.x (100% Kotlin Multiplatform) |
| **UI Framework** | Compose Multiplatform (Material 3) |
| **Android Audio** | AndroidX Media3 (ExoPlayer 1.x) |
| **Desktop Audio** | Java Sound SPI (`AudioInputStream` / `Clip`) |
| **State Architecture** | MVVM with `StateFlow` + `collectAsState` |
| **Build System** | Gradle KMP with JDK 21 |
| **Packaging** | WiX Toolset (Windows `.msi`) |
| **Compatibility** | Android 8.0+ (API 26–35) & Windows 10/11 |

---

## 📥 Installation

### 📱 Android

<a href="https://f-droid.org/en/packages/com.tushar.voidplayer/">
  <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="60">
</a>

- **F-Droid** *(Recommended)*: Install from the official F-Droid repository at [com.tushar.voidplayer](https://f-droid.org/en/packages/com.tushar.voidplayer/) — fully open-source, no proprietary stores.
- **Direct APK**: Download the pre-built signed release APK from [GitHub Releases](https://github.com/TUSHAR91316/Void-Player/releases/latest).

### 🖥️ Windows Desktop
- **MSI Installer** *(Recommended)*: Download `VoidPlayer-2.2.0.msi` from [GitHub Releases](https://github.com/TUSHAR91316/Void-Player/releases/latest) — installs with Desktop & Start Menu shortcuts. Re-run anytime to update in-place.
- **Portable App**: Download and run `VoidPlayer.exe` directly — no installation required.

---

## 🚀 Building From Source

```bash
# Clone the repository (v2 branch)
git clone -b v2 https://github.com/TUSHAR91316/Void-Player.git
cd Void-Player

# Android — debug build on device/emulator
./gradlew :composeApp:installDebug

# Android — signed release APK
./gradlew :composeApp:assembleRelease

# Desktop — run locally
./gradlew :composeApp:run

# Desktop — package MSI installer & portable EXE
./gradlew :composeApp:packageMsi :composeApp:createDistributable

# Run unit tests
./gradlew check
```

> **Requirements**: JDK 21, Android SDK API 35, Android Studio Ladybug or newer.

---

## 🗂️ Repository Structure

```
composeApp/
├── commonMain/        # Shared Compose UI, models, player interface, and utils
│   ├── ui/            # Screens, components (BottomNavBar, NowPlayingScreen, MiniPlayerPill…)
│   ├── model/         # Song, Playlist, AiCategory data classes
│   ├── player/        # AudioPlayer interface & PlayerState
│   ├── data/          # SongRepository interface
│   └── utils/         # AiEngine, AiCategorizer, LrcParser, AudioMetadataUtils, SleepTimer
├── androidMain/       # Android implementations (ExoPlayer Media3, SAF, Palette, Overlay)
└── desktopMain/       # Desktop JVM implementations (Java Sound, JFileChooser, persistence)
```

---

## 🔒 Privacy & Freedom

Void Player is built on the philosophy of user freedom and radical privacy:
- **No Internet Required**: 100% of audio parsing, playback, metadata extraction, and AI categorization runs entirely on your device.
- **Zero Telemetry**: No crash reporting frameworks, no third-party trackers, no advertisements.
- **Zero Accounts**: No registration, sign-in, or cloud sync required — ever.
- **FOSS Forever**: MIT licensed and built entirely on open-source libraries.

---

## 🤝 Contributing

We welcome contributions of all kinds! Please read:
- [**CONTRIBUTING.md**](./CONTRIBUTING.md) — Development setup, architecture guide, and PR workflow.
- [**CODE_OF_CONDUCT.md**](./CODE_OF_CONDUCT.md) — Community standards and enforcement guidelines.
- [**SECURITY.md**](./SECURITY.md) — How to responsibly disclose security vulnerabilities.

---

## 🗺️ Roadmap

See [**ROADMAP.md**](./ROADMAP.md) for planned features including:
- 🖥️ Dedicated Desktop multi-pane UI (Left Nav Rail + Persistent Player Bar + 3-Column Layout)
- 🔄 Dual-channel in-app update checker (GitHub & F-Droid)
- 📜 Online LRC lyrics auto-fetcher (LRCLIB)
- 🎚️ Custom EQ presets & bass booster
- 🏷️ Built-in ID3 tag editor
- ⌨️ Desktop hotkeys & Windows SMTC media key integration

---

## 📄 License
This project is open-source under the **MIT License**. See [LICENSE](./LICENSE) for details.
