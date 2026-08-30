# 🎵 Void Player

<div align="center">

<a href="https://f-droid.org/en/packages/com.tushar.voidplayer/">
  <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">
</a>

<br/><br/>

[![F-Droid Available](https://img.shields.io/badge/F--Droid-Available-3DDC84?style=for-the-badge&logo=fdroid&logoColor=white)](https://f-droid.org/en/packages/com.tushar.voidplayer/)
![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Compose](https://img.shields.io/badge/Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green.svg?style=for-the-badge)

**A modern, high-fidelity, privacy-first local music player built with Kotlin Multiplatform & Jetpack Compose.**  
*100% Free & Open Source (FOSS) • Zero Ads • Zero Trackers • Zero Telemetry • 100% Offline*

[**📱 Get it on F-Droid**](https://f-droid.org/en/packages/com.tushar.voidplayer/) • [**📦 Download APK (GitHub Releases)**](https://github.com/TUSHAR91316/Void-Player/releases/latest) • [**💻 Windows Setup (.msi)**](https://github.com/TUSHAR91316/Void-Player/releases/latest)

</div>

---

## ✨ Key Features

### 🧭 1. Modern Bottom Navigation & Mini-Player Pill
- **Persistent Bottom Navigation**: Seamlessly navigate between 4 dedicated spaces:
  - 🎵 **Library**: All tracks, favorites, search, sorting, and folder picker.
  - ✨ **AI Hub**: Void AI insights, AI DJ Flow, and smart mood collections.
  - 📁 **Playlists**: Custom playlist creation and management.
  - 💿 **Now Playing**: Dedicated full-screen player experience.
- **Floating Mini-Player Pill**: Sits smoothly above navigation with live thin progress indicator, transport controls, and tap-to-expand.

### 💿 2. Dedicated Full-Screen Now Playing Screen
- **Dynamic Palette Background**: Vertical immersive gradient extracted asynchronously from album artwork.
- **Gesture Controls**: Swipe left/right on cover art to skip tracks; swipe down or press back to minimize.
- **Interactive Multi-Mode Drawer**:
  - 💿 **Cover**: High-resolution album artwork view.
  - 📜 **Synced LRC Lyrics**: Real-time synchronized lyrics with active line highlighting and tap-to-seek.
  - 📋 **Live Queue**: Next-up track list with 1-tap jump.
  - ⚡ **Speed & Timer**: `0.5x` to `2.0x` pitch-corrected speed and Gentle Sleep Timer.

### 🧠 3. Void AI Hub & Smart Music Intelligence
- **📊 Void AI Insights ("My Vibe Wrapped")**:
  - Computes your library's musical personality (e.g. *"The Midnight Wanderer"*, *"The High-Drive Dynamo"*, *"The Deep Focus Architect"*).
  - Vibe breakdown percentages and total library listening duration.
  - AI-recommended hardware equalizer curves.
- **🎧 AI DJ Smart Flow**:
  - Intelligently analyzes acoustic energy and mood vectors of the active track to automatically queue the smoothest transitioning next song.
- **✨ AI Smart Categorization**:
  - Automatically groups local tracks into mood collections: *Night Vibes & Lo-Fi*, *High Energy*, *Deep Focus*, *Romance & Melodic*, *Quick Hits (<2.5m)*, *Extended Epics*, and *Artist Spotlights*.

### 🎚️ 4. Hi-Res Audio Codec Badge & Inspector
- **Lossless & Hi-Res Detection**: Automatically identifies `✨ Hi-Res FLAC` (24-bit/96kHz), `✨ Hi-Res WAV`, `🎵 AAC HD`, `🎵 OPUS HD`, and `🎵 320 kbps MP3`.
- **Interactive Codec Inspector**: Tap the badge to inspect sample rates, bitrates, lossless compression, and file details.

### 💾 5. Persistent Playlists & Favorites
- Custom playlists and favorite tracks are saved locally and restored automatically across app restarts.
- 1-tap **"Add to Playlist"** from any song's options menu.

### 🎧 6. Audiophile DSP Engine
- **Hardware Equalizer**: Multi-band EQ with custom levels.
- **Dynamic Normalization**: Real-time loudness normalization powered by Android `DynamicsProcessing` DSP.
- **Audio Focus & Auto-Pause**: Seamlessly pauses when headphones/Bluetooth disconnect or phone calls arrive.

### 🖥️ 7. Windows Desktop Native Support
- Native desktop experience with Windows Setup Wizard (`.msi`) and portable standalone executable (`VoidPlayer.exe`) with local folder scanning and audio engine.

---

## 🛠 Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin (100% Kotlin Multiplatform) |
| **UI Framework** | Jetpack Compose Multiplatform (Material 3) |
| **Audio Engine** | AndroidX Media3 (ExoPlayer) / Java Sound SPI |
| **State Architecture** | MVVM with `StateFlow` + `collectAsState` |
| **Build System** | Gradle (KMP) with JDK 21 |
| **Compatibility** | Android 8.0+ (API 26 to API 35) & Windows 10/11 |

---

## 📥 Installation

### 📱 Android

<a href="https://f-droid.org/en/packages/com.tushar.voidplayer/">
  <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="60">
</a>

- **F-Droid**: Available on the official F-Droid repository at [com.tushar.voidplayer](https://f-droid.org/en/packages/com.tushar.voidplayer/).
- **Direct APK**: Download the pre-built signed release APK from [GitHub Releases](https://github.com/TUSHAR91316/Void-Player/releases/latest).

### 🖥️ Windows Desktop
- **MSI Installer**: Download `VoidPlayer-2.2.0.msi` from GitHub Releases for seamless installation with Desktop and Start Menu shortcuts.
- **Portable App**: Download and run `VoidPlayer.exe` directly without installation.

---

## 🚀 Building From Source

```bash
# Clone the repository
git clone https://github.com/TUSHAR91316/Void-Player.git
cd Void-Player

# Build & Run Android (Debug)
./gradlew :composeApp:installDebug

# Build Android Signed Release APK
./gradlew :composeApp:assembleRelease

# Run Desktop Application
./gradlew :composeApp:run

# Package Desktop MSI Installer & Standalone Executable
./gradlew :composeApp:packageMsi :composeApp:createDistributable
```

---

## 🔒 Privacy & Freedom

Void Player is built on the philosophy of user freedom and privacy:
- **No Internet Required**: 100% of audio parsing, playback, metadata extraction, and AI categorization runs strictly locally on your device.
- **Zero Telemetry**: No crash reporting frameworks, no third-party trackers, no advertisements.
- **Zero Accounts**: No registration or login required.

---

## 📄 License
This project is open-source under the **MIT License**. See [LICENSE](./LICENSE) for details.
