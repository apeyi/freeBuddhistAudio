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

## Appetize.io (iOS Simulator Preview)

Upload simulator .app zip to Appetize for browser-based preview:

```bash
# Upload new app
curl -X POST https://api.appetize.io/v1/apps \
  -H "X-API-KEY: $APPETIZE_API_TOKEN" \
  -F "file=@/workspace/FBAudio-simulator.zip" \
  -F "platform=ios"

# Update existing app
curl -X POST https://api.appetize.io/v1/apps/PUBLIC_KEY \
  -H "X-API-KEY: $APPETIZE_API_TOKEN" \
  -F "file=@/workspace/FBAudio-simulator.zip" \
  -F "platform=ios"
```

Current public key: `qlilkuml5qqe2xwfjhn5xwwv3e`
Public URL: https://appetize.io/app/qlilkuml5qqe2xwfjhn5xwwv3e

The simulator .app zip is exported as a Codemagic build artifact (`FBAudio-simulator.zip`).

## Vast.ai (GPU Compute)

The `VASTAI_API_KEY` env variable is available.

### Search for GPU offers

```bash
curl -s -X POST "https://console.vast.ai/api/v0/bundles/" \
  -H "Authorization: Bearer $VASTAI_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "limit": 5,
    "type": "on-demand",
    "verified": {"eq": true},
    "rentable": {"eq": true},
    "rented": {"eq": false},
    "gpu_name": {"eq": "RTX 2080 Ti"},
    "order": [["dph_total", "asc"]]
  }'
```

### Create an instance

```bash
curl -s -X PUT "https://console.vast.ai/api/v0/asks/OFFER_ID/" \
  -H "Authorization: Bearer $VASTAI_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "image": "pytorch/pytorch:2.1.0-cuda12.1-cudnn8-runtime",
    "disk": 20,
    "runtype": "ssh",
    "onstart": "#!/bin/bash\nYOUR_SCRIPT_HERE",
    "label": "my-instance"
  }'
```

**Important**: Use `runtype: "ssh"` with `onstart` as a bash script (starting with `#!/bin/bash`). Do NOT use `runtype: "args"` — it passes the onstart as entrypoint args, not a shell command.

### Check instance status

```bash
curl -s "https://console.vast.ai/api/v0/instances/" \
  -H "Authorization: Bearer $VASTAI_API_KEY"
```

### Get instance logs

```bash
# Request logs (returns S3 URL)
curl -s -X PUT "https://console.vast.ai/api/v0/instances/request_logs/INSTANCE_ID/" \
  -H "Authorization: Bearer $VASTAI_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"tail": "100"}'

# For system/daemon logs, add: "daemon_logs": "true"
# The result_url may take a few seconds to become available on S3
```

### Destroy an instance

```bash
curl -s -X DELETE "https://console.vast.ai/api/v0/instances/INSTANCE_ID/" \
  -H "Authorization: Bearer $VASTAI_API_KEY"
```

### Pricing notes

- RTX 2080 Ti: ~$0.06/hr (cheapest, good for Whisper transcription)
- Instance boot + docker pull takes 1-2 min
- Container logs (from onstart) may take a minute to appear; daemon logs are available sooner

## GitHub Releases

Release descriptions must use collapsible `<details>` tags:

```markdown
<details>
<summary>What's new</summary>

- Feature 1
- Bug fix 1
</details>
```

## Telegram Bridge

Env vars: `TELEGRAM_BOT_TOKEN`, `TELEGRAM_USER_ID`

Scripts in `scripts/`:
- `telegram_poll.py` — long-polls Telegram, prints message and exits when one arrives
- `telegram_send.py` — sends a message to the user: `python3 scripts/telegram_send.py "text"`

### How it works (background poll in active session)

1. Run `python3 scripts/telegram_poll.py` as a background bash command
2. When a message arrives, the command completes and wakes Claude
3. Read the message from the task output, do the work, reply via `telegram_send.py`
4. Display both the user's message and the reply as normal text in the terminal
5. Start polling again (goto 1)

### After session restart

Run `claude --resume SESSION_ID` then say "start listening on Telegram".

Session ID is saved in `/workspace/.claude_session_id`.

### Standalone bot (auto-start, survives session crashes)

A standalone Python bot runs as a daemon and invokes `claude --print --resume SESSION_ID --dangerously-skip-permissions` for each incoming message. This provides Telegram access even when no interactive session is running. Set up via cron `@reboot`.

## Key Architecture Notes

- Android theme: hardcoded FBA brand color `#A85D21`, no dynamic/Material You colors
- iOS theme: `Color.saffronOrange` = `#A85D21`, used as `.tint()` throughout
- Browse screen is NOT a standalone destination — Sangharakshita, Mitra Study, etc. are navigated to directly from home
- Shared data uses `fixTitle()` to move "The/A/An" from end to front of Sangharakshita talk titles
- Download filenames are sanitized (alphanumeric + `_-` only) to prevent path traversal
- HTTP logging disabled in release builds (Android)

# currentDate
Today's date is 2026-03-15.
