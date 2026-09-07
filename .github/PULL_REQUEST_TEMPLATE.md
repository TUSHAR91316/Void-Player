## Summary

Provide a concise description of the change and the motivation behind it.

Resolves: #(issue number)

---

## Type of Change

- [ ] Bug fix (non-breaking, resolves an existing issue)
- [ ] New feature (non-breaking, adds new behavior)
- [ ] Breaking change (alters existing behavior or public API)
- [ ] Performance improvement
- [ ] UI / UX improvement
- [ ] Documentation update
- [ ] Build or tooling change
- [ ] Refactor (no behavior change)

---

## Testing

Describe how the change was tested. Check all that apply.

- [ ] Android debug build verified (`./gradlew :composeApp:installDebug`)
- [ ] Android release build verified (`./gradlew :composeApp:assembleRelease`)
- [ ] Desktop run verified (`./gradlew :composeApp:run`)
- [ ] Unit tests pass (`./gradlew check`)
- [ ] Tested on physical device — model and OS version:
- [ ] Tested on emulator — API level:

---

## Screenshots

If this change affects the UI, provide before and after screenshots.

| Before | After |
|:---:|:---:|
| | |

---

## Checklist

- [ ] Code follows the platform-isolation model (`commonMain` / `androidMain` / `desktopMain`).
- [ ] Self-reviewed the diff before opening this pull request.
- [ ] Added inline comments where logic is non-obvious.
- [ ] No new compiler warnings or lint errors introduced.
- [ ] No proprietary libraries, ad SDKs, telemetry, or network data-collection added.
- [ ] Documentation or `ROADMAP.md` updated if the change introduces a significant new capability.
- [ ] Pull request targets the `v2` branch.
