# TODO / Deferred work

## Playback resumption from the media notification (after OS kill)

**Status:** done on Android (v0.8.0, together with Android Auto). iOS has no
equivalent problem (AVAudioSession + Now Playing keep working until the app is
killed, and iOS relaunches the app for remote commands).

`PlaybackResolver` (service-side, Hilt) resolves catNum → chapter queue for both
streams and downloads; `PlaybackService` overrides `onPlaybackResumption` to
rebuild the last talk from the saved prefs (`last_cat_num`, `last_track_index_*`,
`last_position_*`). Progress is written by `PlaybackPersistence` inside the
service, so it keeps working when the UI process part is gone.

**Still open:** when the FBA API lands with signed/short-lived audio URLs, only
`PlaybackResolver.resolveTrackUri` needs to change (fetch a fresh URL at resume
time; carry the API auth). Re-verify on a device: play → `adb shell am kill` →
press play on the notification → resumes at the saved position.

## Website links opening in the app (App Links / Universal Links)

**Status:** Android side done (`autoVerify` on the https filters). Waiting on
two external steps; files are ready in `docs/well-known/`.

1. **FBA** hosts `/.well-known/assetlinks.json` (both hosts) and
   `/.well-known/apple-app-site-association` — asked in `docs/api-v2-review.md` §1.2.
2. **Play app-signing cert:** read its SHA-256 from Play Console → Setup → App
   signing and add it as a second fingerprint in `assetlinks.json` (Play
   installs are signed by Google, not by our upload key).
3. **Apple:** Account Holder enables *Associated Domains* on the
   `com.dharmachakra.fba` App ID, then add the entitlement to `project.yml`
   (snippet in `docs/well-known/README.md`) and rebuild via `ios-testflight`.
4. Verify: `adb shell pm get-app-links com.dharmachakra.fba_android` →
   `verified`; on iOS long-press a talk link in Notes → "Open in FBAudio".
