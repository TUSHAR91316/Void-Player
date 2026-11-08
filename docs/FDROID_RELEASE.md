# F-Droid release guide

Void Player is published on F-Droid as `com.tushar.voidplayer`. F-Droid builds it from source and checks the result against the APK signed by the developer and attached to the GitHub release (reproducible build). The recipe lives in fdroiddata at [`metadata/com.tushar.voidplayer.yml`](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/com.tushar.voidplayer.yml).

## Automatic Updates via F-Droid Checkupdates

The recipe in fdroiddata uses:
```yaml
AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: '2.3'
CurrentVersionCode: 6
```

When a tag matching `V%v` (e.g. `V2.4.0`) is pushed to GitHub, F-Droid's checkupdates bot automatically:
1. Detects the new tag `V2.4.0`.
2. Duplicates the previous build entry with `gradle: [yes]` and `subdir: composeApp`.
3. Runs `./gradlew assembleRelease`, which produces a single APK:
   `composeApp/build/outputs/apk/release/VoidPlayer-2.4.0-release.apk`
   with `package='com.tushar.voidplayer'`, `versionCode=7`, and `versionName='2.4.0'`.
4. Verifies the APK against the GitHub release asset at:
   `https://github.com/TUSHAR91316/Void-Player/releases/download/V2.4.0/VoidPlayer-2.4.0-release.apk`
5. Signs and distributes the update automatically to all users without requiring any manual GitLab MR.

## Release steps

1. Ensure `versionCode` and `versionName` in `composeApp/build.gradle.kts` match the release, and ensure `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` exists.
2. Commit and tag that commit `V<versionName>` (capital V, e.g. `V2.4.0`), then push the branch and tag.
3. Build the signed release APK:
   `./gradlew clean :composeApp:assembleRelease`
4. Attach `composeApp/build/outputs/apk/release/VoidPlayer-<versionName>-release.apk` to the GitHub release for tag `V<versionName>`.
5. The `fdroid-release` job in `.github/workflows/android.yml` validates the package name, tag name, and changelog.
6. F-Droid's checkupdates bot will pick up the new tag automatically.

