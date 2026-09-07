# Security Policy

## Supported Versions

| Version | Status |
|---|---|
| 2.2.x | Actively maintained |
| 2.1.x | Critical security fixes only |
| 2.0.x and earlier | End of life — no further updates |

---

## Privacy Architecture

Void Player is designed to operate entirely without internet connectivity:

- All audio parsing, playback, metadata extraction, and AI categorization runs on the user's device.
- No telemetry, crash reporters, analytics SDKs, or advertising networks are included.
- No user account or authentication is required.
- User data (playlists, favorites, folder history, EQ settings) is stored in local app-private storage (`SharedPreferences` on Android, property files under `~/.voidplayer/` on Desktop) and is never transmitted.

---

## Reporting a Vulnerability

If you discover a security vulnerability or privacy issue in Void Player, please report it responsibly before any public disclosure.

### Step 1: Do not disclose publicly first

Do not open a public GitHub issue for security vulnerabilities. Public disclosure before a patch is available may expose users to risk.

### Step 2: Submit a private security advisory

Open a private advisory through GitHub's Security Advisories feature:

[Submit a Security Advisory](https://github.com/TUSHAR91316/Void-Player/security/advisories/new)

### Step 3: Include the following details

- A clear description of the vulnerability.
- Numbered steps or a proof-of-concept to reproduce the issue.
- The affected platform or platforms (Android, Windows Desktop, or both).
- The potential impact if the vulnerability were exploited.
- A suggested remediation, if one is known.

---

## Response Timeline

| Action | Target |
|---|---|
| Initial acknowledgment | Within 48 hours of receipt |
| Severity assessment and triage | Within 5 business days |
| Patch release for critical severity | Within 14 days |
| Patch release for moderate severity | Within 30 days |
| Public disclosure | After the patch is released and distributed |

---

## Scope

The following are within scope for this security policy:

- APK or binary integrity issues.
- Unauthorized filesystem access or path traversal vulnerabilities.
- Memory safety issues in native audio components (DSP, effects processing).
- Window manager or overlay service security issues (`OverlayService`).
- Issues that allow one app to access another app's private storage.

The following are outside scope:

- Vulnerabilities in upstream third-party libraries. Please report those to the respective project.
- Issues that require physical access to an unlocked device.
- UI or usability issues that have no security impact.
- Denial-of-service issues that affect only the reporting user's own device.
