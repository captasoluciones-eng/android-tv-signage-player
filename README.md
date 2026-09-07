# Android TV Signage Player

A self-contained Android TV kiosk app that plays a remotely-managed playlist of
video / image / web-link items in a fullscreen, unattended loop — a functional
equivalent of OptiSigns/Yodeck-style signage players, with no third-party branding
or SaaS lock-in. Package: `com.captasoluciones.signage`.

## 1. Directory tree

```
android-tv-signage-player/
├── README.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── keystore.properties.example
├── .gitignore
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── kotlin/com/captasoluciones/signage/
        │   ├── SignageApplication.kt
        │   ├── data/
        │   │   ├── AppContainer.kt
        │   │   ├── model/
        │   │   │   ├── PlaylistModels.kt        (PlaylistResponse, OverlayConfig, PlaylistSettings, PlaylistItem, ContentTypes, RemoteCommands)
        │   │   │   └── HeartbeatPayload.kt
        │   │   ├── remote/
        │   │   │   ├── PlaylistApi.kt
        │   │   │   └── ETagCacheInterceptor.kt
        │   │   ├── local/
        │   │   │   ├── DeviceDataStore.kt
        │   │   │   └── EventLogBuffer.kt
        │   │   └── repository/
        │   │       └── PlaylistRepository.kt
        │   ├── player/
        │   │   ├── PlayerState.kt               (Screen, PlayerUiState, PlayerEvent)
        │   │   ├── PlayerViewModel.kt
        │   │   ├── PlayerViewModelFactory.kt
        │   │   └── ExoPlayerHolder.kt
        │   ├── service/
        │   │   ├── BootReceiver.kt
        │   │   ├── WatchdogService.kt
        │   │   └── HeartbeatManager.kt
        │   ├── ui/
        │   │   ├── MainActivity.kt
        │   │   ├── PlayerScreen.kt
        │   │   ├── VideoRenderer.kt
        │   │   ├── ImageRenderer.kt
        │   │   ├── WebRenderer.kt
        │   │   ├── OverlayComposable.kt
        │   │   ├── WaitingScreen.kt
        │   │   ├── SetupScreen.kt
        │   │   ├── StatusScreen.kt
        │   │   └── theme/
        │   │       └── Theme.kt
        │   └── util/
        │       ├── KioskModeHelper.kt
        │       └── NetworkUtils.kt
        └── res/
            ├── values/{strings,colors,themes}.xml
            ├── drawable/{banner,ic_launcher_background,ic_launcher_foreground}.xml
            ├── mipmap-anydpi-v26/{ic_launcher,ic_launcher_round}.xml
            └── mipmap-mdpi/{ic_launcher,ic_launcher_round}.xml   (legacy API 22-25 fallback)
```

## 2. Architecture rationale

**MVVM**, one Activity, no navigation library:

- `PlaylistRepository` (Retrofit + OkHttp, with `ETagCacheInterceptor` for conditional
  GET) owns the last successfully-parsed playlist in memory and knows how to fetch a
  new one.
- `PlayerViewModel` is the single brain of the app: it owns three independent
  coroutine loops (polling, playback sequencing, heartbeat), exposes one
  `StateFlow<PlayerUiState>`, and computes which of the three screens is visible from
  plain boolean flags rather than a stored, racy "current screen" field (see
  `PlayerState.kt` doc comment).
- `MainActivity` is the only Activity. It hosts a Compose tree that swaps between
  `SetupScreen` and `PlayerScreen` based on `PlayerUiState.screen`. Handling
  everything in one Activity means the single shared `ExoPlayer` instance never has
  to be torn down and rebuilt across Activity transitions.

**Compose (Material3) + Views interop, not `androidx.tv:tv-material`.** The spec
allows either; this project deliberately picked the plain Compose + Views/AndroidView
route because:

1. The player screen needs a `PlayerView` (Media3) and a `WebView` embedded via
   `AndroidView` regardless of which Compose flavor is used — `tv-material` buys
   nothing there.
2. D-pad navigation is only required on the Setup screen, which is a handful of
   `OutlinedTextField`/`Button`/`Switch` controls. Standard Compose focusable
   components already respond correctly to D-pad up/down/left/right (focus
   traversal) and DPAD_CENTER/ENTER (activation) on Android TV out of the box —
   `tv-material`'s main value-add (scale-on-focus `Card`/`Button` styling, TV-specific
   `LazyRow`/`Carousel`) is not needed for a simple settings form.
3. Dropping the extra library reduces version-compatibility surface area in a project
   that cannot be test-built against a real Compose-for-TV artifact graph here.

**Single shared `ExoPlayer`** (`ExoPlayerHolder`), created once in `MainActivity` and
released on dispose; never recreated per video item. For back-to-back video items, the
*next* video's `MediaItem` is appended as a second queue entry so ExoPlayer's loader
can start buffering it while the current one plays — a pragmatic preload strategy that
respects the "single instance" constraint. Image preloading uses Coil's `ImageLoader.enqueue`
to warm the memory/disk cache for the *next* item while the current one is showing.
`WebView` instances are created per "link" item and destroyed via `AndroidView`'s
`onRelease` callback when composition leaves that item, avoiding leaks.

**Event log**: kept as a 200-entry in-memory ring buffer (`EventLogBuffer`,
`StateFlow<List<LogEntry>>`) rather than a Room table. It is explicitly a
diagnostics/status aid, not data that must survive a process restart, so a database
would add persistence-layer complexity (migrations, DAO boilerplate) for no real
benefit — the watchdog + heartbeat already give a central panel operator durable
visibility into device health.

**"Never a permanent black screen" semantics** (see `PlayerState.kt`): the *waiting*
screen is shown only when there is no content to loop at all (first boot before any
successful sync, or a currently-held playlist with zero playable items). Any other
network failure while a previously-good playlist is already held in memory does **not**
interrupt playback — the app keeps looping the last good playlist silently, and only
surfaces the failure in `lastError` / the status log, exactly as the spec's fault
tolerance section describes ("continuing to loop the last successfully-loaded
playlist").

**Blackout command semantics**: `command: "blackout"` sets a persistent black overlay
that stays on top of playback until a *different* `commandId` arrives with any command
other than `"blackout"` (including `"none"`) — this follows from the "each command
executes exactly once per commandId" rule combined with blackout needing to be a
level, not an edge.

**Heartbeat endpoint**: since only a single `baseUrl` is configured on-device, the
heartbeat is POSTed to `{baseUrl}/heartbeat` (trailing slash on `baseUrl` is trimmed
first). Adjust `HeartbeatManager.buildHeartbeatUrl` if your backend uses a different
convention.

**Type safety in the playlist contract**: `PlaylistResponse` and all nested classes
are `@Serializable` data classes where every field has a default, parsed with a
`Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }`
instance (`AppContainer.json`). An unsupported/misspelled `type` value, or an
`activo=false` item, is filtered out of the playback sequence before it ever reaches
a renderer — it is never a crash, just silently skipped (see
`PlayerViewModel.startPlaybackLoop`).

## 3. Gradle / dependency versions (all fully pinned)

| Component | Version |
|---|---|
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.1.0 |
| Gradle wrapper | 8.10.2 |
| Compose BOM | 2024.12.01 |
| Media3 (ExoPlayer/HLS/UI/common) | 1.5.0 |
| Coil (coil-compose) | 2.7.0 |
| Retrofit | 2.11.0 |
| OkHttp | 4.12.0 |
| kotlinx.serialization-json | 1.7.3 |
| kotlinx-coroutines-android | 1.9.0 |
| AndroidX DataStore (preferences) | 1.1.1 |
| AndroidX core-ktx | 1.15.0 |
| AndroidX activity-compose / activity-ktx | 1.9.3 |
| AndroidX lifecycle-* | 2.8.7 |

`minSdk = 22`, `targetSdk = 35`, `compileSdk = 35`.

> Note on the Gradle wrapper: this project ships `gradle/wrapper/gradle-wrapper.properties`
> (a plain text file) but **not** the binary `gradle-wrapper.jar` or the `gradlew` /
> `gradlew.bat` launcher scripts, since those can't be produced as text. Opening the
> project in Android Studio will regenerate them automatically on first sync, or run
> `gradle wrapper --gradle-version 8.10.2` once (with any local Gradle install) from the
> project root to materialize them yourself.

## 4. Playlist contract implemented

`GET {baseUrl}?deviceId={id}` — see `data/model/PlaylistModels.kt` for the exact
Kotlin shape, which mirrors the JSON in the spec 1:1 including defaults for every
field. Content types: `video` (Media3 ExoPlayer, no controls, `muteVideo`
configurable, advances on natural end OR `durationSec` — whichever first; `null`
`durationSec` = play to natural end), `imagen` (Coil, `durationSec` with a default of
10s if null, `scale: "fill"→Crop / anything else→Fit`), `link` (fullscreen WebView, JS
enabled, scrollbars off, `durationSec` with a default of 20s if null, `scale` as an
integer WebView text-zoom percentage). Any other `type` is filtered out before
playback, never crashes.

Commands (`command` + `commandId`, executed once per unique `commandId`, persisted in
DataStore): `reload` (fetch immediately instead of waiting for the next poll tick),
`restart` (kill and relaunch the whole app process via `AlarmManager` + `Process.killProcess`),
`clearWebCache` (bumps a counter that the next/currently-showing `WebView` observes and
clears its cache off), `blackout` (see semantics above), `none` (no-op, also clears an
active blackout).

Conditional GET: `ETagCacheInterceptor` adds `If-None-Match`/`If-Modified-Since` from
the previous 200 response's `ETag`/`Last-Modified` headers; a `304` is treated as "no
change, keep playing what's loaded."

## 5. Building a signed release APK / AAB

### 5.1 Generate a release keystore

```bash
keytool -genkeypair -v \
  -keystore signage-release.jks \
  -alias signage-release \
  -keyalg RSA -keysize 2048 -validity 10000
```

Answer the prompts (name, org, etc.) and choose a strong store/key password. Keep
`signage-release.jks` **outside** version control (see `.gitignore`).

### 5.2 Wire up signing

```bash
cp keystore.properties.example keystore.properties
```

Edit `keystore.properties` and set:

```properties
storeFile=../signage-release.jks
storePassword=<your store password>
keyAlias=signage-release
keyPassword=<your key password>
```

`app/build.gradle.kts` reads this file automatically (if present) and wires it into
the `release` signing config — no other edits needed. If `keystore.properties` is
absent, `release` builds still compile, they simply come out unsigned.

### 5.3 Build

```bash
# From the project root, after `gradle wrapper` has materialized gradlew (see §3 note):
./gradlew assembleRelease      # -> app/build/outputs/apk/release/app-release.apk
./gradlew bundleRelease        # -> app/build/outputs/bundle/release/app-release.aab
```

(Windows: `gradlew.bat assembleRelease`.)

## 6. Installing on a TV box via adb

```bash
# Connect to the box (find its IP in Settings > Device Preferences > About > Status, or via USB)
adb connect 192.168.1.50:5555

# Install (or upgrade) the signed release APK
adb -s 192.168.1.50:5555 install -r app/build/outputs/apk/release/app-release.apk

# Launch it
adb -s 192.168.1.50:5555 shell am start -n com.captasoluciones.signage/.ui.MainActivity
```

### 6.1 Make it the default home/launcher (recommended for true kiosk deployments)

The app already declares the `LEANBACK_LAUNCHER` category, so it shows up as a
regular app tile on the Android TV home screen. To make it take over as the actual
home app (so the launcher is never reachable):

```bash
adb -s 192.168.1.50:5555 shell cmd package set-home-activity com.captasoluciones.signage/.ui.MainActivity
```

If that command isn't available on the target OS build, use
**Settings > Apps > Default apps > Home app** on the device itself and pick
"Signage Player" (or whatever the OEM's home-app picker is called).

## 7. On-device configuration

- First launch with an empty base URL auto-opens the Setup screen.
- Reach Setup at any time by holding **OK/DPAD_CENTER for 5 seconds**, or pressing
  **MENU**.
- Fields: base URL, device name, poll minutes, mute video — persisted to DataStore
  immediately on Save, which also triggers an immediate re-poll.
- The **Estado** tab shows deviceId, pairing code, linked status, last sync time,
  item count, last error, app version, and the last 200 log lines.

## 8. Known limitations (honest notes)

- No real device/emulator was available to run an actual Gradle build in this
  environment; the code has been written for correctness against the documented
  Media3 1.5.0 / Compose BOM 2024.12.01 / Retrofit 2.11.0 / Coil 2.7.0 APIs, but a
  first `./gradlew assembleDebug` after opening the project is the way to catch any
  last mismatch (e.g. a future minor API rename in a dependency you might already
  have cached locally under a different patch version).
- `gradle-wrapper.jar` and `gradlew`/`gradlew.bat` are not included (binary/generated
  files) — see the note in §3.
- The status screen's event log is in-memory only and resets on process restart by
  design (see architecture rationale above).
