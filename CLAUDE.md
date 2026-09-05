# FBAudio Project

A mobile app for the [Free Buddhist Audio](https://www.freebuddhistaudio.com/) archive — Android (Kotlin/Jetpack Compose) and iOS (SwiftUI). Provides streaming and offline playback of dharma talks, with hardcoded data for Sangharakshita talks so they work offline (the Mitra Study JSON is still bundled but no longer surfaced).

## Repo Structure (Monorepo)

```
/workspace/
├── FBAudio-Android/   — Android app (Kotlin/Jetpack Compose)
├── FBAudio-iOS/       — iOS app (SwiftUI, uses XcodeGen)
├── fbaudio-shared/    — Shared JSON data + images (both platforms read from here)
├── codemagic.yaml     — Codemagic CI config for iOS builds
└── .github/workflows/ — GitHub Actions for Android + iOS CI
```

## Shared Data (`fbaudio-shared/`)

- `sangharakshita.json` — 340 talks, 23 series
- `mitra_study.json` — 22 modules, 76 talks
- `images/` — sangharakshita.jpg, fba_logo.jpg

Android reads these at **build time** via a Gradle codegen task (`buildSrc/GenerateSharedData.kt`) that generates Kotlin source files and copies images into drawable resources.

iOS bundles the `fbaudio-shared/` folder as a resource directory and parses JSON at runtime via `SharedDataLoader.swift`.

## Development conventions

**Apply every change to both platforms.** This is a dual-platform app — Android
(`FBAudio-Android/`) and iOS (`FBAudio-iOS/`) are meant to stay at feature
parity. Any fix, feature, or behaviour change to one platform should be made to
the other in the same change, unless it's genuinely platform-specific (and then
say why). Recent examples done on both: the NaN slider/progress guard
(`safeFraction`), playback-error auto-retry, and the `PlaybackMath` extraction.

**Write tests as part of the work, not after.** When fixing a bug or adding
logic, ask "is there a pure function at the core?" — if so, add a unit test for
it. Pure logic lives in testable units on purpose:
- Android: JVM unit tests in `FBAudio-Android/app/src/test/java/com/fba/app/`,
  run with `./gradlew testDebugUnitTest`. See `HelpersTest`, `PlaybackMathTest`.
- iOS: XCTest in `FBAudio-iOS/FBAudioTests/`, run via `xcodebuild test` (CI).
  See `HelpersTests`, `PlaybackMathTests`.

Both test suites run in CI (GitHub Actions / Codemagic), no device needed.
Prefer extracting fiddly logic (progress math, parsing, formatting) into pure
functions over testing framework-coupled code (Media3/AVFoundation lifecycle,
Compose/SwiftUI) which needs instrumented/device tests and isn't worth the cost.

## Android Builds

```bash
# Build debug
cd FBAudio-Android && ./gradlew assembleDebug

# Run tests
./gradlew testDebugUnitTest

# Install on connected phone (use -s SERIAL for specific device)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Build release
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

Release signing uses `local.properties` (RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, etc.). The `.keystore` file lives at the repo root and is gitignored.

## iOS Builds

iOS is built via Codemagic CI (`codemagic.yaml`). The workflow generates an Xcode project with XcodeGen, builds for the iOS Simulator, and exports a `FBAudio-simulator.zip` artifact. Source-of-truth project is defined in `FBAudio-iOS/project.yml`.

Local build (requires macOS + Xcode):
```bash
cd FBAudio-iOS
brew install xcodegen
xcodegen generate
open FBAudio.xcodeproj
```

## GitHub Releases

Release descriptions use collapsible `<details>` tags:

```markdown
<details>
<summary>What's new</summary>

- Feature 1
- Bug fix 1
</details>
```

APK is attached as a release asset.

## Key Architecture Notes

- **Brand color**: hardcoded `#A85D21` on both platforms, used for controls/accents only — body text is black (spec v1.0). Android: no dynamic/Material You colors. iOS: `Color.saffronOrange` used via `.tint()`.
- **Tabs**: Home · Search · Downloads · Donate · My FBA (Donate opens the web donation page; the Join page exists but is only reached via download gating). Recently listened lives on My FBA. Home is the spec v1.0 layout (header, Sangharakshita, Digital Legacy, Collections, Support, Connect); Collections is the hub for Introductions/Meditations/Latest/Themes/Series/People/Places plus FBA's curated collections. See `docs/spec-v1.0-pre-api.md`.
- **Website content layer**: `ContentRepository` (Android `data/repository`, iOS `Data/`) serves the curated menu (`SiteMenuParser` on `document.__FBA__.sidebar_menu`), API collections (`/api/v1/collections/{type}?page=&limit=24`), named `/collection/<slug>` pages (`?pageNo=`), series pages and the Digital Legacy page, cached on disk for 24 h (`ContentCache`). Lists are addressed by `ContentSource` (api | named | browse | series), which is string-encoded into routes.
- **Language filter** (`LanguageFilter`, pure, unit-tested): "English only" setting hides entries/talks using FBA's own markers (menu label suffixes, Languages section, Places country labels). The search API's `lang_code` is unreliable and not used.
- **Remastered audio**: `Track.remasterAudioUrl` / `remasterDurationSeconds`; the player has a Remastered | Original toggle (per-talk choice in `AppSettings`), downloads record `audioVersion`. Talks cached before this existed are refetched once (`TalkRepository.TALK_CACHE_EPOCH` / iOS `isCurrentSchema`).
- **Login**: Triratna SAML SSO performed natively (`SsoLogin`: load the SSO form, post credentials, relay the SAML response; native `LoginScreen`), capturing `PHPSESSID`, `SimpleSAMLAuthToken`, `fba` cookies (`SessionCookieStore` → OkHttp CookieJar; iOS `HTTPCookieStorage`). `AuthRepository` verifies with `/api/v1/my-details` and reads `document.__FBA__.user`. `HistoryRepository` syncs `/api/v1/history` and `/api/v1/checkpoints/` (resume position; the account's checkpoint wins). The site rotates the `fba` cookie on every response, so session-carrying requests are serialized while logged in (`SessionCookieStore.serializingInterceptor` / iOS `FbaSession`) and audio/image requests carry no cookies. Feature switches live in `FeatureFlags` (both platforms).
- **Downloads**: transcript-only downloads are rows with `filePath == ""` (Android) / `transcriptOnly` (iOS); the Downloads screen filters All | Talks | Transcripts and shows what's stored.
- **Title fixup**: `fixTitle()` moves "The/A/An" from end to front of Sangharakshita talk titles.
- **Download filenames** are sanitized (alphanumeric + `_-` only) to prevent path traversal.
- **HTTP logging** disabled in release builds (Android).
- **Crash logs** (debug builds only) saved to `filesDir/crash_logs/` — last 10 kept.
- **Deep links**:
  - `https://www.freebuddhistaudio.com/audio/details?num=…`
  - `fbaudio://talk/CATNUM`, `fbaudio://series/ID`, `fbaudio://speaker/NAME`
