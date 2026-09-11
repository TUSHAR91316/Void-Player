# Void Player Release Notes

---

## What's New in Version 2.3 — Convergence & SML Audio Intelligence Release

Welcome to **Void Player 2.3**! This release marks the convergence of the mobile and desktop platforms, featuring an on-device Small Machine Learning (SML) audio intelligence engine, upgraded Android 16 support, LRCLIB synchronized lyrics auto-fetching, hardware DSP audio controls, and the brand-new native Windows 11 WinUI 3 desktop client.

---

### Highlights & New Features

#### 1. Embedded Small Machine Learning (SML) Vector Engine
* **Multi-Feature Vector Space Modeling**: Categorizes local tracks across 12+ genre semantic centroids, acoustic energy vectors, tagged tempo (BPM) closeness, and lexical title/album token matching.
* **Continuous Acoustic Energy Extraction**: Computes normalized intensity scores ($0.08 \dots 0.98$) combining genre baselines, embedded ID3/FLAC tempo (BPM), and track duration dynamics.
* **Temperature-Scaled Softmax Calibration**: Normalizes feature distance scores through temperature-scaled softmax ($T=12.0$), yielding calibrated confidence ratings ($45\% \dots 99\%$) and eliminating unclassified fallback buckets.
* **100% On-Device & Private**: Runs entirely in local background coroutines. Zero cloud inference, zero network dependencies, and zero telemetry.

#### 2. Harmonic DJ Smart Flow Progression
* **Acoustic Transition Scoring**: Intelligently sequences continuous playback by evaluating mood continuity, acoustic energy delta, and tempo variance:
  $$\text{FlowScore}(A, B) = \text{MoodContinuity}(A, B) \times 0.45 + (1 - \Delta\text{Energy} \times 1.5) \times 0.35 + \left(1 - \frac{\Delta\text{BPM}}{60}\right) \times 0.20$$
* Prevents abrupt energy jolts or tempo collisions during continuous playback sessions.

#### 3. Void AI Insights ("My Vibe Wrapped")
* Computes your library's musical persona (*The Midnight Wanderer*, *The High-Drive Dynamo*, *The Deep Focus Architect*, *The Neon Cruiser*, *The Acoustic Vagabond*, *The Heavy Sonic Titan*).
* Detailed vibe breakdown percentages, library-wide acoustic energy averages, total listening duration, and recommended hardware equalizer presets.

#### 4. Dual-Engine Synchronized Lyrics (Local LRC + LRCLIB)
* In addition to local `.lrc` files, Void Player now queries the open-source, privacy-friendly **LRCLIB** database when local lyrics are absent.
* Fetched lyrics are permanently stored in local offline cache (`lyrics_cache/`) for seamless offline replay.

#### 5. Hardware Audio DSP & Equalizer
* Native integration with Android `android.media.audiofx.BassBoost` (0-1000 strength) and 5-band `Equalizer`.
* Full persistence for custom EQ presets (*Flat*, *Bass Boost*, *Vocal Pop*, *Electronic*, *Rock*, *Acoustic*), custom band levels, playback speed, and dynamics processing.

#### 6. Android 16 (API 36) Modernization
* Target and compile SDK upgraded to Android 16 (API Level 36, Baklava).
* Removed floating overlay `SYSTEM_ALERT_WINDOW` pill island in favor of native system media controls and notification pills.
* Asynchronous background artwork extraction on `Dispatchers.IO` with safe 512x512 downsampling to eliminate Android Binder IPC `TransactionTooLargeException`.
* Migrated AI Hub UI from decorative emojis to clean Material vector icons (`Bolt`, `Headphones`, `GraphicEq`, `Favorite`, `AutoAwesome`, `MusicNote`).
* Isolated release signing keystores into `local.properties` with expanded wildcard gitignores.

#### 7. Native Windows 11 WinUI 3 Desktop Client
* Built with Windows App SDK 2.4, WinUI 3, C#, and .NET 10 (Windows 11 SDK 10.0.26100.0).
* Modern Mica material backdrop, collapsible left navigation rail, Spotify-style bottom player bar, and split-pane 3-column layout.
* Full ID3/FLAC metadata parsing with TagLibSharp and native folder scanning.
* Distributed via Microsoft Store MSIX (`Byte-Labs.VoidPlayer`, Store ID: `9PCPXRSJ02RS`).

---

## What's New in Version 2.2 — The Flagship UI & AI Update

Welcome to **Void Player 2.2**! This milestone release completely redesigned the navigation architecture with a sleek **Bottom Navigation Bar**, introduced a **dedicated full-screen Now Playing screen**, and brought cutting-edge **AI music intelligence** directly to your local audio library with 100% offline privacy.

### Highlights & New Features

#### 1. Modern Bottom Navigation Bar & Mini-Player Pill
* 4 Dedicated Spaces: Library, AI Hub, Playlists, and Now Playing.
* Floating Mini-Player Pill: Sits above the bottom navigation bar with live progress line, transport controls, and tap-to-expand.

#### 2. Dedicated Full-Screen Now Playing Screen
* Dynamic Palette Background: Immersive vertical gradient dynamically extracted from album artwork.
* Hero Artwork & Gestures: Swipe left/right on cover art to skip tracks; swipe down or press back to minimize.
* Interactive Multi-Mode Drawer: Cover, Synced LRC Lyrics, Live Queue, and Speed & Timer controls.

#### 3. Void AI Hub & Music Intelligence
* Void AI Insights ("My Vibe Wrapped") listener persona profiling.
* AI DJ Smart Flow next-song queuing.
* Smart Mood Collections with dynamic clustering.

#### 4. Hi-Res Audio Codec Badge & Inspector
* Lossless & Hi-Res Detection: Automatically identifies FLAC (24-bit/96kHz), WAV, AAC HD, OPUS HD, and 320 kbps MP3.
* Interactive Codec Inspector: Inspect sample rates, bitrates, lossless status, and file details.

#### 5. Persistent Playlists & Favorites
* Custom playlists and favorite tracks saved to local persistent storage and auto-restored across app restarts.
* 1-tap "Add to Playlist" from any song's options menu.

#### 6. Smart Back Navigation Stack
* Cross-platform `PlatformBackHandler` closes dialogs, clears active search, or returns to previous tabs/Library rather than abruptly closing the app.

---

## What's New in Version 2.1

Welcome to **Void Player 2.1**! This update brought critical stability fixes, crash hardening across all components, and adaptive memory scaling to make the player run flawlessly on any device.

### Architecture & Performance
- Adaptive Memory Architecture: Intelligently queries your device's exact RAM capabilities (`com.tushar.voidplayer.utils.MemoryUtils`).
- Bounded Image Cache: A hardware-aware LRU cache for decoded album art bitmaps prevents memory exhaustion when scrolling large libraries.

### Audio Engine & DSP
- Audio Attributes: ExoPlayer is configured with `CONTENT_TYPE_MUSIC` and `USAGE_MEDIA` for correct audio focus and DAC routing.
- Auto-Pause on Headphone Disconnect: Full `AudioManager.ACTION_AUDIO_BECOMING_NOISY` integration.
- Real-Time Dynamic Normalization: Android's `DynamicsProcessing` DSP chip is used for multiband compression and normalization (API 28+ only).
- Equalizer: Hardware equalizer bands exposed in Settings, initialized safely after audio session is ready.

### Critical Crash Fixes
1. `ForegroundServiceStartNotAllowedException` (ANR on Android 12+) - fixed using proper startService lifecycle.
2. Native SIGSEGV crash in `libeffect` - fixed by gating DSP until non-zero audioSessionId.
3. DSP unavailable on custom ROMs - guarded with `catch(Throwable)`.
4. `SecurityException` from undeclared WAKE_LOCK - switched to `WAKE_MODE_NONE`.
5. `NullPointerException` from `BitmapFactory.decodeByteArray()` returning null - fixed with fallback bitmap and try-catch.
6. `OutOfMemoryError` in `loadArt()` - fixed with `catch(Throwable)` and safe retriever release.
7. OOM / Codec crash from aggressive ExoPlayer configuration - cleaned ExoPlayer builder.
