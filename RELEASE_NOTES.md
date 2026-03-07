# FBAudio v0.3.0 Release Notes

## New Features

### Redesigned Home Screen
- **Sangharakshita featured section** — Large hero card with image, talk and series counts, and quick access to browse by year or by series
- **Sangharakshita series** — 23 lecture series listed and browsable directly from the home screen
- **Mitra Study section** — Quick access to structured study courses from the home screen
- **Recently Listened** — Shows talks you've recently played with position/duration timestamps and a progress bar showing how far through each talk you are
- **Download indicators** — Downloaded talks show a checkmark icon in the recently listened list
- **Donate card** — "Support Free Buddhist Audio" card links to the FBA donation page

### Chapters (renamed from Tracks)
- "Tracks" renamed to "Chapters" throughout the app (player, detail screen, bottom sheet)
- **Chapters on detail page** — Chapters now appear below the description on the talk detail screen, styled as a tappable list matching the player's chapter view
- **Clickable chapters** — Tap any chapter on the detail page to start playing it directly
- **Current chapter indicator** — The currently playing chapter is highlighted with bold blue text in both the player's chapter list and the detail page

### Offline & Downloads
- **Transcripts included in downloads** — When downloading a talk, the transcript (if available) is automatically saved for offline reading
- **Offline transcript viewing** — Transcripts load from local storage when available, falling back to the network
- **Improved offline playback** — Downloaded talks play reliably without an internet connection, including correct chapter resume

## Bug Fixes

### Search
- **Fixed search performance** — Search results are now parsed entirely on the IO thread, preventing UI freezes on large result sets (some queries returned 1000+ results / 1.5MB+ of JSON)
- **Capped search results** — Limited to 200 results to prevent memory issues on mobile
- **Deduplicated results** — Search results are deduplicated by catalogue number to prevent list rendering crashes
- **Keyboard dismissal** — The keyboard now automatically hides when search results arrive or when the search button is pressed

### Playback Resume
- **Fixed multi-chapter resume** — Previously, resuming only worked for chapter 1. Now correctly resumes the last played chapter at the saved position for all talks
- **Fixed chapter highlight** — The correct chapter is now highlighted when resuming a multi-chapter talk
- **Fixed single-track playback** — Single-track downloaded talks now play correctly offline

### Navigation
- **Fixed back navigation from series** — Pressing back from a series talk list (accessed via the home screen) now correctly returns to the previous screen instead of showing the full browse category list
- **Fixed speaker/series browse back** — The back button on speaker and series browse screens always returns to the previous screen

### Data & Display
- **Fixed negative durations** — Some talks on the FBA website have invalid negative duration values (e.g. -1772841600 seconds). These are now clamped to zero throughout the app (parser, cache, and display)
- **Duration fallback** — For talks with missing duration data, the app uses the player's reported duration for progress tracking
- **Friendly error messages** — Network errors now show "Connection error" instead of raw exception messages like "failed to connect to..."
- **Offline error handling** — `getTalkDetail` no longer crashes when the network is unavailable; gracefully returns cached data or null

## Technical Changes
- Database version 4 → 5 with migrations for `recently_listened` table
- New `RecentlyListenedEntity` and `RecentlyListenedDao` for Room persistence
- New `ErrorUtil.kt` with `friendlyError()` helper for user-friendly error messages
- `DownloadWorker` now downloads and parses transcript HTML alongside audio files
- App version: 0.2.0 → 0.3.0 (versionCode 2 → 3)
