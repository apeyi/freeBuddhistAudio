# FBAudio Project

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
- `mitra_study.json` — 22 modules, 56 talks
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

Release signing uses `local.properties` (RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, etc.).

## iOS Builds (Codemagic)

The `CM_API_TOKEN` env variable is available for Codemagic API calls.

### Trigger a build

```bash
curl -s -X POST https://api.codemagic.io/builds \
  -H "Content-Type: application/json" \
  -H "x-auth-token: $CM_API_TOKEN" \
  -d '{"appId": "69b57cbdee625882cf704b1b", "workflowId": "ios-build", "branch": "main"}'
```

### Check build status

```bash
curl -s "https://api.codemagic.io/builds/BUILD_ID" \
  -H "x-auth-token: $CM_API_TOKEN" | python3 -c "
import json, sys
b = json.load(sys.stdin).get('build', {})
print('Status:', b.get('status'))
print('Message:', b.get('message', ''))
for a in b.get('buildActions', []):
    print(f'[{a.get(\"status\",\"?\")}] {a.get(\"name\",\"?\")}')
"
```

### Download build log artifact

Build logs are saved as artifacts. To download after a failed build:

```bash
# 1. Get artifact path from build response
ART_URL=$(curl -s "https://api.codemagic.io/builds/BUILD_ID" \
  -H "x-auth-token: $CM_API_TOKEN" | python3 -c "
import json, sys
for a in json.load(sys.stdin).get('build',{}).get('artefacts',[]):
    print(a.get('url','')); break
")

# 2. Extract path portion (remove https://api.codemagic.io/artifacts/ prefix)
ART_PATH=$(echo "$ART_URL" | sed 's|https://api.codemagic.io/artifacts/||')

# 3. Get temporary download URL
EXPIRES_AT=$(($(date +%s) + 3600))
DL_URL=$(curl -s -X POST "https://api.codemagic.io/artifacts/${ART_PATH}/public-url" \
  -H "Content-Type: application/json" \
  -H "x-auth-token: $CM_API_TOKEN" \
  -d "{\"expiresAt\": $EXPIRES_AT}" | python3 -c "import json,sys; print(json.load(sys.stdin).get('url',''))")

# 4. Download and extract
curl -sL "$DL_URL" -o /tmp/artifacts.zip
unzip -o /tmp/artifacts.zip -d /tmp/artifacts/
grep "error:" /tmp/artifacts/build.log
```

### iOS build/fix cycle

1. Make iOS code changes in `FBAudio-iOS/`
2. `git add . && git commit && git push`
3. Trigger build via API
4. Wait ~3 min, check status
5. If failed, download build.log artifact, find errors, fix, repeat

## Key Architecture Notes

- Android theme: hardcoded FBA brand color `#A85D21`, no dynamic/Material You colors
- iOS theme: `Color.saffronOrange` = `#A85D21`, used as `.tint()` throughout
- Browse screen is NOT a standalone destination — Sangharakshita, Mitra Study, etc. are navigated to directly from home
- Shared data uses `fixTitle()` to move "The/A/An" from end to front of Sangharakshita talk titles
- Download filenames are sanitized (alphanumeric + `_-` only) to prevent path traversal
- HTTP logging disabled in release builds (Android)
