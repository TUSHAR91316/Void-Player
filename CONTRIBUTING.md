# 🤝 Contributing to Void Player

Thank you for your interest in contributing to **Void Player**! We welcome all contributions — from bug reports and UI polish to audio DSP improvements and new feature implementations.

---

## 📋 Table of Contents
1. [Code of Conduct](#code-of-conduct)
2. [How Can I Contribute?](#how-can-i-contribute)
   - [Reporting Bugs](#-reporting-bugs)
   - [Suggesting Features](#-suggesting-features)
   - [Pull Requests](#-pull-requests)
3. [Development Setup](#️-development-setup)
   - [Prerequisites](#prerequisites)
   - [Building & Running](#building--running)
4. [Architecture & Guidelines](#-architecture--guidelines)
5. [Git & Commit Conventions](#-git--commit-conventions)
6. [F-Droid Compatibility Policy](#️-f-droid-compatibility-policy)

---

## 📜 Code of Conduct

By participating in this project, you agree to abide by our [Code of Conduct](./CODE_OF_CONDUCT.md). Please treat fellow contributors with respect and kindness.

---

## 💡 How Can I Contribute?

### 🐞 Reporting Bugs
Before filing an issue, please search existing [GitHub Issues](https://github.com/TUSHAR91316/Void-Player/issues) to avoid duplicates. Use the **Bug Report** template and include:

- **Platform** — Android or Windows Desktop
- **App Version** — e.g. `v2.2`
- **Device & OS** — e.g. Pixel 8, Android 14 / Windows 11
- **Steps to Reproduce** — clear numbered steps
- **Expected vs Actual Behavior**
- **Logcat / Console Logs** — if applicable

### 💡 Suggesting Features
Feature requests are always welcome! Open an issue using the **Feature Request** template describing:
- The problem you are trying to solve.
- Your proposed solution or user experience (mockups are great!).
- The target platform (Android / Desktop / Both).
- Any alternatives you have considered.

### 🔀 Pull Requests
1. **Fork** the repository on GitHub.
2. **Clone your fork**:
   ```bash
   git clone https://github.com/<your-username>/Void-Player.git
   cd Void-Player
   ```
3. **Check out the `v2` branch** (the active development branch):
   ```bash
   git checkout v2
   ```
4. **Create a descriptive feature branch**:
   ```bash
   git checkout -b feat/my-feature-name
   ```
5. **Make your changes** and test on both Android and Desktop where applicable.
6. **Commit** following our [Commit Conventions](#-git--commit-conventions).
7. **Push** to your fork and open a **Pull Request** targeting the `v2` branch.
8. Fill out the **PR template** completely — this helps reviewers understand your change quickly.

---

## 🛠️ Development Setup

### Prerequisites

| Tool | Version |
|---|---|
| **JDK** | 21 (OpenJDK 21 / Temurin 21) |
| **Android Studio** | Ladybug or newer |
| **Android SDK** | API 35 (Android 15) |
| **Git** | Any recent version |

### Building & Running

**Android — debug build on device/emulator:**
```bash
./gradlew :composeApp:installDebug
```

**Android — signed release APK:**
```bash
./gradlew :composeApp:assembleRelease
```

**Desktop — run locally:**
```bash
./gradlew :composeApp:run
```

**Desktop — package MSI installer & portable EXE:**
```bash
./gradlew :composeApp:packageMsi :composeApp:createDistributable
```

**Run all unit tests:**
```bash
./gradlew check
```

---

## 📐 Architecture & Guidelines

Void Player uses **Kotlin Multiplatform (KMP)** and **Compose Multiplatform** with a clean separation of concerns:

```
composeApp/
├── commonMain/          # Shared Compose UI, interfaces, models, and utils
│   ├── App.kt           # Root Compose container & navigation orchestration
│   ├── ui/
│   │   ├── components/  # BottomNavBar, MiniPlayerPill, NowPlayingScreen, AiHubScreen…
│   │   └── theme/       # Material 3 tokens, SurfaceBackground, dynamic accents
│   ├── model/           # Song, Playlist, AiCategory — immutable data classes
│   ├── player/          # AudioPlayer interface & PlayerState definitions
│   ├── data/            # SongRepository interface
│   └── utils/           # AiEngine, AiCategorizer, LrcParser, AudioMetadataUtils, MemoryUtils
├── androidMain/         # Android implementations (ExoPlayer, MediaSession, SAF, Palette)
└── desktopMain/         # Desktop implementations (Java Sound, JFileChooser, properties storage)
```

### Key Development Rules

1. **Platform isolation**: Platform-specific code lives in `androidMain` / `desktopMain`. All shared UI and business logic belongs in `commonMain`.
2. **Material 3 design**: Use Material 3 tokens, dark palettes, and spring animations. Match the existing glassmorphism aesthetic.
3. **No main-thread blocking**: Always use `Dispatchers.Default` for bitmap decoding, color extraction, and heavy computation.
4. **Minimize recomposition**: Use `derivedStateOf` or lambda references (e.g., `() -> Long`) for rapidly changing state like `currentPosition`.
5. **Privacy first**: Void Player is 100% offline. Never add network dependencies that send user data, analytics, or telemetry.
6. **FOSS only**: Do not introduce proprietary SDKs, binary blobs, or non-free libraries.

---

## 📝 Git & Commit Conventions

We follow [Conventional Commits](https://www.conventionalcommits.org/):

| Prefix | Purpose | Example |
|---|---|---|
| `feat:` | New feature | `feat(ui): add dedicated now playing screen` |
| `fix:` | Bug fix | `fix(nav): keep bottom nav visible during playback` |
| `docs:` | Documentation only | `docs: update README with F-Droid badge` |
| `style:` | Formatting, whitespace | `style: reformat NowPlayingScreen.kt` |
| `refactor:` | Code restructuring | `refactor(player): extract DSP lifecycle into helper` |
| `perf:` | Performance improvement | `perf(cache): reduce LRU bitmap memory pressure` |
| `build:` | Build / CI / tooling | `build: bump compose-multiplatform to 1.7.0` |
| `test:` | Tests only | `test: add TimeFormatter unit tests` |
| `chore:` | Housekeeping | `chore: remove unused temp files` |

Use **scopes** in parentheses to clarify what was changed: `feat(ai):`, `fix(desktop):`, `build(android):`.

---

## 🛡️ F-Droid Compatibility Policy

Void Player is distributed on [F-Droid](https://f-droid.org/en/packages/com.tushar.voidplayer/). To maintain F-Droid inclusion, **all contributions must comply** with:

1. **No Proprietary Dependencies**: All libraries must be 100% open-source (FOSS) with no proprietary binary blobs.
2. **No Tracking or Ads**: Zero ad networks, tracking SDKs, crash reporters, or proprietary analytics.
3. **Reproducible Builds**: The release APK must compile cleanly in F-Droid's sandboxed build environment.
4. **No Self-Updating APK Downloads**: F-Droid builds must not download and install APKs directly (they redirect to the F-Droid store page instead).

Any PR introducing a dependency that violates these rules will be rejected.

---

Thank you for helping make Void Player awesome! 🎧🚀
