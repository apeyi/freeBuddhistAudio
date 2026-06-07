# TODO / Deferred work

## Playback resumption from the media notification (after OS kill)

**Status:** deferred — do this together with the FBA API migration.

**Problem:** When Android kills the app process for memory, the media
notification becomes a dead shell. Tapping the notification body now reopens
the app (fixed via `setSessionActivity` in `PlaybackService`), but the
play/pause **transport buttons** do nothing, because the `MediaSession` and
player no longer exist. Spotify/YouTube Music/Pocket Casts handle this with
Media3 **playback resumption** (`MediaSession.Callback.onPlaybackResumption`),
which cold-starts the service and rebuilds playback without opening the UI.

**Why deferred:** The real work is making "catNum → audio URL" resolution
callable from `PlaybackService` (today it lives in `PlayerViewModel`, the UI
layer). That refactor is needed regardless of data source — but:
- The FBA owners will soon provide an **API** and **mux/lock down audio**. The
  current HTML-scraping resolution (`FBAScraper`, `document.__FBA__`) will
  likely break, so resumption code written against it now would be rewritten.
- Protected audio usually means **short-lived signed URLs** → the service must
  fetch a fresh URL at resume time (can't cache it). Build against the final
  API, not the scraper.
- The API may require **auth** (API key / Play Integrity attestation). The
  service needs to carry that; know the auth model first.

**When the API lands, implement:**
1. Move audio-URL resolution into a service-accessible component (repository
   method the service can call via Hilt), covering stream + downloaded-file
   cases and the new auth/signed-URL flow.
2. Override `onPlaybackResumption` in the `MediaSession.Callback` to return the
   last talk (from saved prefs: `last_cat_num`, `last_track_index_*`,
   `last_position_*`) as `MediaItemsWithStartPosition`.
3. Verify on a real device: play → force an OS kill (or `adb shell am kill`
   from background) → press play on the notification → resumes at saved
   position without opening the app.

**Note:** downloaded talks could support resumption today with no API
dependency (local file path), but shipping resume-for-downloads-only is
confusing UX — wait until streams can match.

**Context:** see the `setSessionActivity` fix in
`FBAudio-Android/app/src/main/java/com/fba/app/player/PlaybackService.kt`.
