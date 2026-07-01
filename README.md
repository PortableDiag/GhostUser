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
  prompt and no foreground-service notification**. Drag it, tap ▶/■ to play/stop.
- **On-screen point picker** — tap targets directly on the screen to add them as
  taps to a macro. No pixel-coordinate guesswork.
- **Loop controls** — repeat N times or forever, with a configurable interval
  (the core auto-clicker knob).
- House dark theme: near-black surface, gold accent, selectable accent palette.

## Requirements to build

This machine has `adb` but **no JDK / Android SDK / Gradle**, so build on a
machine that has them (or open in Android Studio):

- Android Studio (Koala or newer), **or** command-line SDK + JDK 17.
- Compile SDK 34, min SDK 26, target 34. Gradle wrapper 8.5, AGP 8.2.2,
  Kotlin 1.9.24.

### Command line

```bash
# Point at your SDK (once):
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew assembleDebug          # -> app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A release build reads signing creds from env vars (no hardcoded keys):
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, with the keystore at
`ghostuser-release.jks` in the project root.

## First run

1. Launch **GhostUser**.
2. If the banner says the service is off, tap **Open accessibility settings** and
   enable *GhostUser Gesture Engine*. The floating panel appears once enabled.
3. (Optional, rooted) Settings ▸ **Test root access**, then pick engine *Root* or
   *Auto*.
4. **New macro** ▸ **Pick tap points on screen** ▸ tap your targets ▸ **Done**.
   Set an interval, **Save**, then ▶ from the list or the floating panel.

## Architecture

```
model/        Macro + Step (flat, @Serializable)
data/         MacroRepository (JSON file), SettingsStore (DataStore)
engine/       GestureEngine + Accessibility/Root impls, EngineProvider,
              PlaybackController (the global play/stop loop)
service/      GhostAccessibilityService (instance + overlay host),
              OverlayController (panel + point picker), OverlayBus
ui/           Compose screens (Home / Editor / Settings) + house theme
```

The accessibility service, floating overlay, and app UI all share the same
process-wide singletons (`MacroRepository`, `PlaybackController`, `OverlayBus`),
so state stays in sync without any IPC.

## Notes & limits (v1)

- Coordinates are absolute screen pixels — a macro recorded in portrait won't
  line up in landscape or on a different resolution.
- Non-root playback cannot inject into apps that flag secure/`FLAG_SECURE`
  surfaces or explicitly reject synthetic gestures; use the root engine there.
- The point picker captures taps over whatever is behind it; navigate to your
  target app/screen first, then start picking.
