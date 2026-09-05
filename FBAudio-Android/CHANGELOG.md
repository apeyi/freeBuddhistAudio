# Changelog

## v0.7.0 — spec v1.0 (pre-API)

<details>
<summary>Changes</summary>

**New**
- Five tabs: Home · Search · Downloads · Donate · My FBA. Recently listened moved to My FBA (with Donate and Settings)
- Home redesigned per the v1.0 spec: header with Log in, Sangharakshita (Year / Series with images), Digital Legacy card and page, Collections grid, Introductions / Meditations / Latest / Themes / Series / People / Places, Support FBA, Connect links
- Collections, Themes, People and Places come from FBA's curated website menu; tiles use FBA cover images or stable generated artwork
- Remastered audio: Remastered | Original toggle in the player (remembered per talk), badges on talks and series, downloads record the version
- Series pages show image, blurb and a Donate button; Donate button on every talk page
- Search: All | Audio, with Series and Talks grouped; speaker mode removed (People replaces it)
- Downloads: All | Talks | Transcripts filter, "Audio · Transcript · Remastered" labels, save a transcript on its own
- Settings: English only (default on, using FBA's language markers), Prefer remastered audio
- Log in with the FBA / Triratna account (native login screen); listening history and resume position sync with the website
- Join page (subscriptions open at launch); download gating behind a feature flag

**Changed**
- Light-mode text is black instead of brown

</details>

## v0.6.0

<details>
<summary>Changes</summary>

**Improvements**
- Delete confirmations — deleting a download now shows a confirmation dialog
- Delete All button in the downloads screen toolbar
- After finishing a downloaded talk, prompts to delete the offline files
- Color scheme now matches the FBA website brand color (#A85D21) with warm brown tones throughout
- Removed generic Browse screen — Sangharakshita, Mitra Study, etc. are accessed directly from home

**Bug fixes**
- Fixed Mitra Study back navigation — pressing back from a module's talk list now goes back through the hierarchy instead of jumping to home
- Fixed black squares in Sangharakshita section (dark mode) by replacing ListItem with transparent Row layouts

</details>

## v0.5.1

<details>
<summary>Changes</summary>

- Auto-advance to next chapter when a chapter finishes playing
- Fixed progress bar dots introduced by Material3 update (removed stop indicators and track gaps)
- Fixed player seek bar styling (thin track, no visible thumb)
- Disabled HTTP logging in release builds to avoid leaking URLs to logcat
- Sanitized catNum in download filenames to prevent path traversal
- Extracted hardcoded Sangharakshita and Mitra Study data into shared JSON files with build-time codegen

</details>

## v0.5.0

<details>
<summary>Changes</summary>

- Android 16 (API 36) support: bumped compileSdk/targetSdk to 36
- Updated Media3 (ExoPlayer) 1.2.1 → 1.5.1 to fix foreground service type crash on Android 16
- Updated AGP, Gradle, Compose, AndroidX dependencies for API 36 compatibility
- Fixed all deprecation warnings (ArrowBack, MenuBook, QueueMusic, Divider, progress indicator lambdas)
- Replaced Sangharakshita home screen image with the FBA default portrait

</details>

## v0.4.0

<details>
<summary>Changes</summary>

**Improvements**
- Sangharakshita and FBA logo images are now bundled in the app — the home screen loads instantly without network
- Recently listened talks that are finished now show a checkmark and "Completed" label instead of a near-full progress bar
- Failed downloads show a retry button so you can re-download without navigating back to the talk
- Failed downloads preserve their progress ("Failed at 33%") instead of resetting to 0%

**Bug fixes**
- Fixed downloads not stopping when internet is lost — the worker now checks for cancellation during each chunk read and between tracks, so it fails fast instead of hanging
- Fixed position polling running continuously even when nothing is playing, wasting battery

**Code quality**
- Extracted shared transcript parsing logic into TranscriptParser utility, removing duplication between the scraper and download worker

</details>

## v0.3.0

<details>
<summary>Changes</summary>

- Recently listened section on home screen with progress tracking
- Redesigned home screen with Sangharakshita hero card and Mitra Study section
- Multi-chapter support with chapter list and navigation
- Offline transcript viewing for downloaded talks
- Bug fixes

</details>

## v0.2.0

<details>
<summary>Changes</summary>

- Move keystore passwords to local.properties

</details>

## v0.1.0

<details>
<summary>Changes</summary>

- Initial release

</details>
