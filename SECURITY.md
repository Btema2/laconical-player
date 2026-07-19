# Security Policy

Laconical Player is a privacy-focused, offline-first music player. We take security seriously, even though the app has no network access by default.

## Reporting a Vulnerability

If you discover a potential security bug or vulnerability in Laconical Player, please report it via [GitHub Issues](https://github.com/btema2/laconical-player/issues). 

Please provide:
- A clear description of the issue.
- Steps to reproduce the vulnerability (if applicable).
- Potential impact.

We will review all reports and address significant security issues as quickly as possible.

## Our Security Commitment

- **Privacy First**: Laconical Player contains no trackers and no analytics. The only network call the app can make is an opt-in lyrics lookup against [LRCLIB](https://lrclib.net) — off by default, and even when enabled it sends only track title/artist/album/duration, never audio or listening history.
- **Local Data**: All your data (track metadata, playlists, settings, cached lyrics) is stored locally on your device.
- **Permissions**: We only request the minimum permissions necessary for the app to function (e.g., `READ_MEDIA_AUDIO`).

Thank you for helping keep Laconical Player secure!
