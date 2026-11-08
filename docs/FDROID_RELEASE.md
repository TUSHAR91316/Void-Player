# F-Droid release guide

Void Player is published on F-Droid as `com.tushar.voidplayer`. F-Droid builds it from source and checks the result against the APK signed by the developer and attached to the GitHub release (reproducible build). The recipe lives in fdroiddata at [`metadata/com.tushar.voidplayer.yml`](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/com.tushar.voidplayer.yml).

## Why 2.4.0 had not reached F-Droid

As of 2026-11-08, F-Droid serves 2.3 (versionCode 6), which is also the newest `V*` tag. Four things stood between the source tree and an F-Droid update:

1. **No release tag.** The recipe uses `UpdateCheckMode: Tags`. F-Droid only sees a new version after a `V2.4.0` tag is pushed with `versionCode = 7` in `composeApp/build.gradle.kts`. The 2.4 work was uncommitted.
2. **Package name changed.** `defaultConfig.applicationId` had been set to the Play name `com.orbitwork.VoidPlayer`. F-Droid rejects a build whose package differs from the app ID, and installed users could not update. Fixed: `fdroid` (and the default) keep `com.tushar.voidplayer`; only the `full` (Play) flavor uses `com.orbitwork.VoidPlayer`.
3. **Product flavors vs. the recipe.** Builds 2.2 and 2.3 use `gradle: [yes]`. With the new `fdroid`/`full` flavors, that runs `assembleRelease`, which builds both flavors and leaves no APK where fdroidserver looks, so the build fails with "Failed to find output". `AutoUpdateMode: Version` copies the last build entry, so this has to be corrected once in fdroiddata (see below).
4. **Reproducible-build asset name.** `Binaries:` expects `V%v/VoidPlayer-%v-release.apk`. The `fdroidRelease` output is now named exactly `VoidPlayer-<version>-release.apk`.

## One-time fdroiddata change (needs a merge request on GitLab)

Add the 2.4.0 entry with the `fdroid` flavor and bump the current version. Later releases are then copied from this entry automatically.

```yaml
  - versionName: 2.4.0
    versionCode: 7
    commit: <full commit SHA that tag V2.4.0 points to>
    subdir: composeApp
    gradle:
      - fdroid

# …

CurrentVersion: 2.4.0
CurrentVersionCode: 7
```

Mention in the MR that 2.4.0 adds `fdroid`/`full` product flavors and that `fdroid` is the flavor to build.

## Release steps

1. Bump `versionCode` and `versionName` in `composeApp/build.gradle.kts`, and add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (500 characters max).
2. Commit, then tag that commit `V<versionName>` (capital V, for example `V2.4.0`) and push the branch and the tag.
3. On a clean checkout of the tag, with JDK 21, build the signed F-Droid APK:
   `./gradlew clean :composeApp:assembleFdroidRelease`
4. Attach `composeApp/build/outputs/apk/fdroid/release/VoidPlayer-<versionName>-release.apk` to the GitHub release for the tag without renaming it. Do **not** attach the `full` (Play) APK under that name. It has a different package and will not match.
5. The `fdroid-release` job in `.github/workflows/android.yml` checks the package name, that the tag matches `versionName`, and that the changelog exists. It must pass before F-Droid's checkupdates picks up the tag.

F-Droid's build server usually publishes a new version within a few days of the tag.
