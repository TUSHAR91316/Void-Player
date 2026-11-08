# Changelog

Notable changes to Void Player are recorded here. This file covers source changes; an entry does not imply that a release has been published.

## [2.4.0] — In preparation

### Added

- Android F-Droid and full product flavors, plus CI workflows for Android and Windows builds.
- Optional online lyrics on Android and Windows. Network lyrics are off by default; F-Droid update messaging leaves updates to the F-Droid client.
- Secure content-URI artwork delivery for Android lock-screen and notification metadata, with bounded artwork caching.
- Custom equalizer preset management on Android and Windows, including save, rename, delete, and JSON import/export.
- Audio tag editing on Windows and Android. Both support title, artist, album, year, genre, track, BPM, and key; Windows also replaces the cover and Android also edits lyrics. Android MediaStore writes request runtime consent.
- SLM v2 scoring on Android/common Kotlin and Windows, with Camelot key parsing, harmonic and half/double-tempo-aware scoring, time-of-day energy targets, and persisted listening preference history.
- Windows media transport controls, audio-file activation, and local crash-log viewing.
- Android queue controls to add, remove, and reorder tracks.
- M3U/M3U8 playlist import and export on Android and Windows, with UTF-8 support and matching against the current library.
- Android cached SAF/MediaStore audio tags and BPM/key extraction for the library.
- Shared Kotlin/C# SLM fixtures covering key conversion, scoring, bias, and radio deduplication.
- Paused queue and position restoration after the music library loads on Android and Windows.
- Accent-insensitive debounced library search on both platforms.
- A v2.4 progress report documenting implementation status, open work, and the planned completion sequence: [V2.4_PROGRESS.md](V2.4_PROGRESS.md).

### Changed

- SLM v2.1 on Android and Windows: wider key-tag support (note names with major/minor, Open Key), a stricter harmonic and tempo score, whole-word mood matching, and a texture term in the flow score. Radio picks avoid tempo jumps over 25 BPM and only vary among near-equal candidates.
- Android Graphite theme matching Windows, with Settings → Appearance to switch back to the Classic (2.3) theme at any time.
- Windows interface redesign: neutral graphite theme with one accent, search in the navigation pane, a segmented Songs filter, a quieter player bar with inline volume, and no glow effects. The album-art accent is kept readable against the background.

- Android Compose Multiplatform dependencies are pinned to a version compatible with the project's Android Gradle Plugin and API level.
- Android release builds use R8 minification, resource shrinking, and release lint checks.
- The F-Droid and GitHub builds keep the package `com.tushar.voidplayer`; only the Play (`full`) flavor uses `com.orbitwork.VoidPlayer`. CI builds the F-Droid release and verifies its package, tag, and changelog.
- Android playlists use JSON storage, with migration from the legacy delimiter format.
- Android SAF file identifiers use negative 64-bit SHA-256-derived IDs, with migration for existing favorites, playlists, and cached lyrics.
- Android SAF folder enumeration uses `DocumentsContract` child-document queries; queried names and modification times are carried through the scan.
- Android UI identifies SLM feature values as metadata-based estimates, not waveform analysis.
- Release and privacy metadata now describe v2.4.0 preparation and optional online requests.

### Fixed

- Favorite changes, lazily loaded artwork, and refined song metadata now propagate through observable state.
- Android sleep-timer actions run on the player-safe thread, and manual track skips no longer trigger natural-end behavior.
- Android playback queues follow the visible library list when starting a track.
- Android equalizer band levels and presets persist across restarts.
- Granting Android library permission reloads the library without requiring an app restart.
- Android artwork loading is downsampled and cached; notification artwork uses readable content URIs instead of private `file://` paths.
- Android seeking before media preparation is guarded, and lyrics timestamp parsing and online search matching are more robust.
- Windows library rescans preserve favorites and stable track metadata; playback advances past missing or corrupt files with a failure limit.
- Windows equalizer resources are reused and disposed correctly; shuffle uses a random order.
- Windows library scans skip unchanged files, tolerate inaccessible directories, and serialize scan requests.
- Windows library persistence is debounced and written atomically.
- Android tag reading and editing work for MediaStore (default library) songs. Before this fix the missing file extension made every read and write fail.
- Saving tags on Android no longer erases the existing year, track number, and embedded lyrics. The editor reads the file's current tags before it allows saving.
- Removing a migrated SAF track from favorites now persists across library reloads.
- Android listening statistics count skips and completions for the track that actually played.
- Removing the playing song from the Android queue updates the current track; "Play next" on the playing song no longer interrupts it.
- LRC `[offset:]` tags are applied in the documented direction on both platforms, and Windows keeps empty timestamped lines as instrumental gaps.
- Android M3U export keeps dots in playlist names, and import uses the real file name and matches percent-encoded SAF names and "Artist - Title" entries.

### Validation completed during preparation

- Android F-Droid debug unit tests pass (54 tests); F-Droid and full minified Release APKs assemble with release lint enabled.
- Windows Release build succeeds with no warnings; 32 desktop unit tests pass.
- The reproducible clean-build APK hash comparison has not been repeated after the latest changes.

### Still pending before a full 2.4.x scope is complete

- Physical-device checks for Android permissions, lock-screen artwork, sleep timer, tag-write consent, and long-running radio behavior.
- Manual Windows checks for media keys, Explorer file activation, favorites after rescan, and playback resume.
- ReplayGain, auto-vibe EQ, accessibility audit/fixes, localization scaffold, folder view, visualizer, and Android Auto.
- Physical-device and interactive desktop acceptance, including Android permission and artwork behavior, tag-write consent, sleep timers, queue restoration, Windows media keys/file activation, and clean-build reproducibility.

## [2.3.0]

See [release.md](release.md) for the published 2.3 release notes.
