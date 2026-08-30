# Void Player Release Notes

---

## What's New in Version 2.2 — The Flagship UI & AI Update 🚀

Welcome to **Void Player 2.2**! This milestone release completely redesigns the navigation architecture with a sleek **Bottom Navigation Bar**, introduces a **dedicated full-screen Now Playing screen**, and brings cutting-edge **AI music intelligence** directly to your local audio library with 100% offline privacy.

---

### 🌟 Highlights & New Features

#### 🧭 1. Modern Bottom Navigation Bar & Mini-Player Pill
* **4 Dedicated Spaces**:
  * 🎵 **Library**: All tracks, favorites, search, quick sorting, and folder picker.
  * ✨ **AI Hub**: Void AI insights, AI DJ Flow, and smart mood collections.
  * 📁 **Playlists**: Custom playlist creation and management.
  * 💿 **Now Playing**: Dedicated full-screen player experience.
* **Floating Mini-Player Pill**: Sits gracefully above the bottom navigation bar with a live thin progress line, quick play/pause, next track, and smooth expansion to the dedicated player on tap.

---

#### 💿 2. Dedicated Full-Screen Now Playing Screen
* **Dynamic Palette Background**: Immersive vertical gradient dynamically extracted from album artwork.
* **Hero Artwork & Gestures**: Swipe left/right on cover art to skip tracks; swipe down or press back to minimize.
* **Interactive Multi-Mode Drawer**:
  * 💿 **Cover**: High-resolution album artwork view.
  * 📜 **Synced LRC Lyrics**: Real-time synchronized lyrics with active line highlighting, smooth auto-scroll, and tap-to-seek.
  * 📋 **Live Queue**: Next-up track list with 1-tap jump.
  * ⚡ **Speed & Timer**: `0.5x` to `2.0x` pitch-corrected speed controls and Gentle Sleep Timer.

---

#### 🧠 3. Void AI Hub & Music Intelligence
* **📊 Void AI Insights ("My Vibe Wrapped")**:
  * Computes your library's musical personality (e.g. *"The Midnight Wanderer"*, *"The High-Drive Dynamo"*, *"The Deep Focus Architect"*).
  * Vibe breakdown percentages and total library listening duration.
  * AI-recommended hardware equalizer curves.
* **🎧 AI DJ Smart Flow**:
  * Intelligently calculates acoustic energy and mood vectors of the active track to automatically queue the smoothest transitioning next song.
* **✨ AI Smart Categorization**:
  * Auto-groups your songs into mood/vibe collections: *Night Vibes & Lo-Fi*, *High Energy & Workout*, *Deep Focus*, *Romance & Melodic*, *Quick Hits (<2.5m)*, *Extended Epics*, and *Artist Spotlights*.

---

#### 🎚️ 4. Hi-Res Audio Codec Badge & Inspector
* **Lossless & Hi-Res Detection**: Automatically identifies `✨ Hi-Res FLAC` (24-bit/96kHz), `✨ Hi-Res WAV`, `🎵 AAC HD`, `🎵 OPUS HD`, and `🎵 320 kbps MP3`.
* **Interactive Codec Inspector**: Tap the badge to inspect sample rates, bitrates, lossless status, and file details.

---

#### 💾 5. Persistent Playlists & Favorites
* Custom playlists and favorite tracks are saved to local persistent storage and auto-restored across app restarts.
* 1-tap **"Add to Playlist"** from any song's options menu (`⋮`).

---

#### 🔙 6. Smart Back Navigation Stack
* Cross-platform `PlatformBackHandler` intelligently closes open dialogs, clears active search, or returns to previous tabs/Library rather than abruptly closing the app.

---

#### 🖥️ 7. Windows Desktop Native Support
* Native Windows directory chooser with recursive audio file scanning and last-used folder auto-restoration.
* Native Desktop Audio Engine with real-time timeline seek, volume slider, and playlist queue.
* Multi-resolution `.ico` embedded into setup wizard, desktop shortcuts, and executable header.
* `perUserInstall = true` enabled for seamless in-place updates and overwrite installation without "already installed" errors.

---

## What's New in Version 2.1 🛡️

Welcome to **Void Player 2.1**! This update brought critical stability fixes, crash hardening across all components, and adaptive memory scaling to make the player run flawlessly on any device.

### 🧠 Architecture & Performance
- **Adaptive Memory Architecture**: Intelligently queries your device's exact RAM capabilities (`com.tushar.voidplayer.utils.MemoryUtils`).
- **Bounded Image Cache**: A hardware-aware LRU cache for decoded album art bitmaps prevents memory exhaustion when scrolling large libraries.

### 🎧 Audio Engine & DSP
- **Audio Attributes**: ExoPlayer is configured with `CONTENT_TYPE_MUSIC` and `USAGE_MEDIA` for correct audio focus and DAC routing.
- **Auto-Pause on Headphone Disconnect**: Full `AudioManager.ACTION_AUDIO_BECOMING_NOISY` integration.
- **Real-Time Dynamic Normalization**: Android's `DynamicsProcessing` DSP chip is used for multiband compression and normalization (API 28+ only).
- **Equalizer**: Hardware equalizer bands exposed in Settings, initialized safely after audio session is ready.

### 🐛 Critical Crash Fixes
1. `ForegroundServiceStartNotAllowedException` (ANR on Android 12+) - fixed using proper startService lifecycle.
2. Native SIGSEGV crash in `libeffect` - fixed by gating DSP until non-zero audioSessionId.
3. DSP unavailable on custom ROMs - guarded with `catch(Throwable)`.
4. `SecurityException` from undeclared WAKE_LOCK - switched to `WAKE_MODE_NONE`.
5. `NullPointerException` from `BitmapFactory.decodeByteArray()` returning null - fixed with fallback bitmap and try-catch.
6. `OutOfMemoryError` in `loadArt()` - fixed with `catch(Throwable)` and safe retriever release.
7. OOM / Codec crash from aggressive ExoPlayer configuration - cleaned ExoPlayer builder.
