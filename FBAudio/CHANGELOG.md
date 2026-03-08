# Changelog

## v0.4.1

- Replaced Sangharakshita home screen image with the FBA default portrait

## v0.4.0

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

## v0.3.0

- Recently listened section on home screen with progress tracking
- Redesigned home screen with Sangharakshita hero card and Mitra Study section
- Multi-chapter support with chapter list and navigation
- Offline transcript viewing for downloaded talks
- Bug fixes

## v0.2.0

- Move keystore passwords to local.properties

## v0.1.0

- Initial release
