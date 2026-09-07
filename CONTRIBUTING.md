# Contributing to Void Player

Thank you for your interest in contributing to Void Player. Contributions of all kinds are welcome, including bug reports, documentation improvements, UI polish, performance work, and new features.

---

## Table of Contents

1. [Code of Conduct](#code-of-conduct)
2. [How to Contribute](#how-to-contribute)
   - [Reporting Bugs](#reporting-bugs)
   - [Suggesting Features](#suggesting-features)
   - [Pull Requests](#pull-requests)
3. [Development Setup](#development-setup)
4. [Architecture and Guidelines](#architecture-and-guidelines)
5. [Commit Conventions](#commit-conventions)
6. [F-Droid Compatibility Requirements](#f-droid-compatibility-requirements)

---

## Code of Conduct

All participants are expected to follow the [Code of Conduct](./CODE_OF_CONDUCT.md). Please treat contributors and maintainers with respect.

---

## How to Contribute

### Reporting Bugs

Before filing a new issue, search [existing issues](https://github.com/TUSHAR91316/Void-Player/issues) to avoid duplicates. Use the **Bug Report** template and include:

- Platform: Android or Windows Desktop.
- App version (e.g., `2.2`).
- Device model and OS version (e.g., Pixel 8, Android 14 / Windows 11).
- Numbered steps to reproduce the issue.
- Expected behavior vs. actual behavior.
- Relevant logcat output or console logs, if available.

### Suggesting Features

Open a new issue using the **Feature Request** template. Describe the problem you are trying to solve, your proposed solution, the target platform, and any alternative approaches you considered. If the feature requires internet access or a third-party service, confirm that it is privacy-preserving and open source.

### Pull Requests

1. Fork the repository on GitHub.
2. Clone your fork and check out the `v2` branch:
   ```bash
   git clone https://github.com/<your-username>/Void-Player.git
   cd Void-Player
   git checkout v2
   ```
3. Create a descriptive branch:
   ```bash
   git checkout -b feat/my-feature-name
   ```
4. Implement your changes and verify on both Android and Desktop where applicable.
5. Commit following the [Commit Conventions](#commit-conventions) below.
6. Push to your fork and open a pull request targeting the `v2` branch.
7. Complete the pull request template in full.

---

## Development Setup

### Prerequisites

| Requirement | Version |
|---|---|
| JDK | 21 (OpenJDK 21 / Temurin 21) |
| Android Studio | Ladybug or newer |
| Android SDK | API 35 (Android 15) |
| Git | Any current release |

### Build Commands

```bash
# Android — debug build on device or emulator
./gradlew :composeApp:installDebug

# Android — signed release APK
./gradlew :composeApp:assembleRelease

# Desktop — run locally
./gradlew :composeApp:run

# Desktop — produce MSI installer and portable executable
./gradlew :composeApp:packageMsi :composeApp:createDistributable

# Run all unit tests
./gradlew check
```

---

## Architecture and Guidelines

Void Player uses Kotlin Multiplatform (KMP) and Compose Multiplatform with a strict platform-isolation model:

```
composeApp/
├── commonMain/    Shared Compose UI, player interface, data models, and utilities.
│                  All UI components and business logic live here.
├── androidMain/   Android implementations only: ExoPlayer, MediaSession, SAF, Palette.
└── desktopMain/   Desktop JVM implementations only: Java Sound, JFileChooser, file persistence.
```

**Key rules:**

1. **Platform isolation.** All platform-specific code must reside in `androidMain` or `desktopMain`. Do not add Android or JVM-only imports to `commonMain`.
2. **No main-thread blocking.** Use `Dispatchers.Default` for bitmap decoding, color extraction, AI categorization, and any file I/O inside a coroutine. Use `withContext(Dispatchers.IO)` for all file and network operations in repository implementations.
3. **Minimize recomposition.** Use `derivedStateOf` or pass lambdas (`() -> Long`) for state that changes at high frequency, such as the playback position. Avoid computing derived values directly inside the composition tree.
4. **Exception safety.** Use `catch(Throwable)`, not `catch(Exception)`, for all media, DSP, bitmap, and service code paths. `OutOfMemoryError`, `Error`, and native exceptions are not caught by `catch(Exception)`.
5. **Material 3 design.** Match the existing dark color scheme, glassmorphism surfaces, and spring-based animations. Use Material 3 tokens rather than hard-coded colors.
6. **Privacy and FOSS.** Void Player operates 100% offline. Do not introduce any network requests that transmit user data, analytics, or telemetry.

---

## Commit Conventions

Void Player follows [Conventional Commits](https://www.conventionalcommits.org/):

| Prefix | Purpose |
|---|---|
| `feat:` | New feature |
| `fix:` | Bug fix |
| `docs:` | Documentation changes only |
| `style:` | Formatting and whitespace with no logic changes |
| `refactor:` | Code restructuring with no feature or fix |
| `perf:` | Performance improvement |
| `build:` | Build configuration, dependencies, or CI |
| `test:` | Adding or updating tests |
| `chore:` | Housekeeping tasks |

Use a scope in parentheses to narrow the context. Examples:

```
feat(ai): add acoustic energy scoring to AI DJ Flow
fix(desktop): close AudioInputStream after duration estimation
docs: rewrite CONTRIBUTING.md in professional style
build(android): bump targetSdk from 34 to 35
```

---

## F-Droid Compatibility Requirements

Void Player is distributed on [F-Droid](https://f-droid.org/en/packages/com.tushar.voidplayer/). All contributions must conform to the following constraints, which are enforced by the F-Droid inclusion policy:

1. **No proprietary dependencies.** All libraries must be fully open source (FOSS). Binary blobs and proprietary SDKs are prohibited.
2. **No tracking or advertising.** Ad networks, analytics SDKs, and proprietary crash reporters are prohibited.
3. **Reproducible builds.** The release APK must compile cleanly in the F-Droid sandboxed build environment.
4. **No self-downloading APK updates.** F-Droid builds must not download or install APK files at runtime. In-app update notifications for F-Droid builds must redirect to the F-Droid store page.

Any pull request that introduces a dependency or behavior that violates these requirements will be rejected.

---

Thank you for contributing to Void Player.
