# FBAudio Project

A mobile app for the [Free Buddhist Audio](https://www.freebuddhistaudio.com/) archive — Android (Kotlin/Jetpack Compose) and iOS (SwiftUI). Provides streaming and offline playback of dharma talks, with hardcoded data for Sangharakshita talks and the Mitra Study curriculum so they work offline.

## Repo Structure (Monorepo)

```
/workspace/
├── FBAudio/           — Android app (Kotlin/Jetpack Compose)
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

## Android Builds

```bash
# Build debug
cd FBAudio && ./gradlew assembleDebug

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

- **Brand color**: hardcoded `#A85D21` on both platforms. Android: no dynamic/Material You colors. iOS: `Color.saffronOrange` used via `.tint()`.
- **Home navigation**: Sangharakshita, Mitra Study, etc. are navigated to directly from home — Browse is not a standalone destination.
- **Title fixup**: `fixTitle()` moves "The/A/An" from end to front of Sangharakshita talk titles.
- **Download filenames** are sanitized (alphanumeric + `_-` only) to prevent path traversal.
- **HTTP logging** disabled in release builds (Android).
- **Crash logs** (debug builds only) saved to `filesDir/crash_logs/` — last 10 kept.
- **Deep links**:
  - `https://www.freebuddhistaudio.com/audio/details?num=…`
  - `fbaudio://talk/CATNUM`, `fbaudio://series/ID`, `fbaudio://speaker/NAME`
