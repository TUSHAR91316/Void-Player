# 🔒 Security Policy — Void Player

## Supported Versions

Only the latest stable release receives active security maintenance.

| Version | Supported          |
|---------|-------------------|
| 2.2.x   | ✅ Actively maintained |
| 2.1.x   | ⚠️ Critical fixes only |
| < 2.0   | ❌ End of life |

---

## Privacy Architecture

Void Player is designed from the ground up for privacy:

- **100% Offline**: All audio parsing, playback, metadata extraction, and AI categorization runs strictly on-device. No internet connection is required or used.
- **Zero Telemetry**: No crash reporters, analytics SDKs, or ad networks are included.
- **No Accounts**: No user registration, sign-in, or cloud sync.
- **Local Storage Only**: Playlists, favorites, EQ settings, and folder history are stored in local app storage (`SharedPreferences` on Android, `.properties` files on Desktop) — never uploaded anywhere.

---

## Reporting a Vulnerability

We take security and user privacy seriously. If you discover a vulnerability in Void Player, please report it **responsibly**:

### 1. Do NOT disclose publicly first
Do not open a public GitHub issue for sensitive security vulnerabilities. This gives us time to investigate and patch before exposure.

### 2. Submit a Private Security Advisory
Open a **private** security advisory directly on GitHub:  
👉 [**New Security Advisory**](https://github.com/TUSHAR91316/Void-Player/security/advisories/new)

### 3. Include these details:
- **Description**: Clear explanation of the vulnerability.
- **Steps to Reproduce**: Numbered steps or a proof-of-concept.
- **Affected Platform(s)**: Android / Windows Desktop / Both.
- **Potential Impact**: What an attacker could do if this were exploited.
- **Suggested Fix** (optional): If you have ideas on how to fix it.

---

## Response Timeline

| Action | Target Time |
|---|---|
| Initial acknowledgment | Within 48 hours |
| Severity assessment | Within 5 business days |
| Patch release (critical) | Within 14 days |
| Patch release (moderate) | Within 30 days |
| Public disclosure | After patch is released |

---

## Scope

The following are **in scope** for this security policy:
- APK / Desktop binary integrity issues.
- File path traversal or unauthorized filesystem access.
- Memory safety issues in native audio DSP components.
- Overlay / window manager security issues (`OverlayService`).

The following are **out of scope**:
- Issues in third-party libraries (report to the respective upstream project).
- Bugs that require physical device access to exploit.
- UI/UX issues that are not security-relevant.
