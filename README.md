# GhostUser

A Clickmate-style automation app for Android: build **auto-clickers** and
multi-step **gesture macros** (taps, long-presses, swipes, delays) and play them
back over other apps. Works on **stock devices** (via an AccessibilityService)
and on **rooted devices** (via a persistent `su` shell), selectable per your
preference.

## Highlights

- **Two injection engines** behind one interface:
  - *Accessibility* — `dispatchGesture()`, no root required.
  - *Root* — pipes `input tap/swipe` to a long-lived `su` shell; higher tap rate,
    and works over apps that block synthetic gestures.
  - *Auto* — prefers root when granted, otherwise falls back to accessibility.
- **Floating control panel** drawn by the accessibility service itself using a
  `TYPE_ACCESSIBILITY_OVERLAY` window — so there's **no draw-over-apps permission
  prompt and no foreground-service notification**. It rides as a small draggable
  bubble; tap it to expand a control row:

  | | |
  |---|---|
  | ⠿ | grip — drag to move the panel |
  | ● | start/stop **recording** a gesture sequence |
  | ▶ / ■ | play/stop the selected macro |
  | ⟳ | loop override — on, ▶ repeats forever; off, it runs once |
  | ■ | stop playback (and stop recording) |
  | ☰ | open the GhostUser app |
  | ▽ | collapse back to the bubble |
  | ✕ | dismiss the panel entirely |

  The panel is shown **only when you ask for it** (the floating-controls icon in
  the app's top bar) and stays gone once you dismiss it with ✕ — that choice is
  remembered across reboots and app restarts.
- **On-screen gesture recorder** — hit ● and use the screen normally. Taps, long
  presses and swipes are captured with the real pauses between them and saved as
  a macro you can replay or edit. If a macro is playing when you start
  recording, playback stops first — otherwise the recorder would capture the
  app's own injected taps as if you had made them.
- **On-screen point picker** — tap targets directly on the screen to add them as
  taps to a macro. No pixel-coordinate guesswork.
- **Loop controls** — repeat N times or forever, with a configurable interval
  (the core auto-clicker knob).
- **Import / export** — Settings ▸ *Backup & sharing* writes every macro to a
  JSON file and reads them back through the system document picker; the editor
  can share a single macro. Imports are always appended with fresh ids, so they
  can never overwrite what you already have.
- Material 3 **DayNight** theme with a blue primary (`#2D6BFF` light,
  `#AEC6FF` dark). Follows the system by default; forceable to light or dark in
  Settings. The floating panel always draws on a dark translucent pill, so it
  keeps its own brighter accent (`#4C8DFF`) for contrast on any wallpaper.

## Requirements to build

- JDK 17, and the Android SDK (Android Studio Koala or newer, or the
  command-line tools).
- Compile SDK 34, min SDK 26, target 34. Gradle wrapper 8.11.1, AGP 8.2.2,
  Kotlin 1.9.24, Compose compiler 1.5.14.

### Command line

```bash
git clone https://github.com/PortableDiag/GhostUser.git
cd GhostUser

# Point at your SDK (once):
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew assembleDebug          # -> app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A release build reads signing creds from env vars (no hardcoded keys):
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, with the keystore at
`ghostuser-release.jks` in the project root. There is deliberately no fallback —
a forgotten `export` fails the build loudly rather than signing with a weak key.

```bash
source ./signing.env               # or export the three vars yourself
./gradlew assembleRelease          # -> app/build/outputs/apk/release/app-release.apk
```

## First run

1. Launch **GhostUser**.
2. If the banner says the service is off, tap **Open accessibility settings** and
   enable *GhostUser Gesture Engine*.
3. Tap the **floating-controls icon** in the top bar to put the panel on screen.
   Enabling the service only grants the capability — the panel never appears on
   its own, and ✕ on the panel puts it away for good until you ask again.
4. (Optional, rooted) Settings ▸ **Test root access**, then pick engine *Root* or
   *Auto*.
5. Build a macro either way:
   - **Record it** — expand the panel, tap **●**, perform the gesture on screen,
     tap **Done**. It's saved and selected, ready for ▶.
   - **Build it** — **New macro** ▸ **Pick tap points on screen** ▸ tap your
     targets ▸ **Done**. Set an interval, **Save**, then ▶ from the list or the
     floating panel.

## Architecture

```
model/        Macro + Step (flat, @Serializable)
data/         MacroRepository (JSON file), SettingsStore (DataStore),
              MacroTransfer (import/export envelope)
engine/       GestureEngine + Accessibility/Root impls, RootShell,
              EngineProvider, PlaybackController (the global play/stop loop)
service/      GhostAccessibilityService (instance + overlay host),
              OverlayController (panel + recorder + point picker), OverlayBus,
              OverlayPrefs (is the panel wanted on screen?), ServiceUtils
ui/           Compose screens (Home / Editor / Settings) + house theme
```

The accessibility service, floating overlay, and app UI all share the same
process-wide singletons (`MacroRepository`, `PlaybackController`, `OverlayBus`),
so state stays in sync without any IPC.

**Panel visibility is a user decision, not a side effect of the service running.**
The system binds an enabled accessibility service on boot, after an app update,
and whenever it restarts the process — so `onServiceConnected()` must never show a
window on its own. It restores the panel only if `OverlayPrefs.visible` says the
user left it showing (SharedPreferences, so the service can read it synchronously
while connecting).

## Notes & limits (v1)

- Coordinates are absolute screen pixels — a macro recorded in portrait won't
  line up in landscape or on a different resolution.
- Non-root playback cannot inject into apps that flag secure/`FLAG_SECURE`
  surfaces or explicitly reject synthetic gestures; use the root engine there.
- The point picker and the recorder capture touches over whatever is behind them,
  and the app underneath receives nothing while they are up. Navigate to your
  target app/screen first, then start picking or recording.
- The service declares `canRetrieveWindowContent="false"` and
  `onAccessibilityEvent()` is an empty stub. GhostUser never reads what is on
  your screen; it only dispatches gestures and hosts its own windows.
- There are no automated tests and no CI.
