# Changelog

All notable changes to GhostUser are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

> Versions 1.0.0 through 1.0.5 were released before this file existed. Their
> entries below were reconstructed from the commit history and the shipped code
> on 2026-08-04, so they describe what actually went out rather than what was
> planned.

## [Unreleased]

### Documentation

- Backfilled this changelog for every release from 1.0.0 onward.
- README: corrected the build requirements. It claimed this machine had no JDK,
  Android SDK or Gradle and that you had to build elsewhere — both variants build
  here. Also corrected the Gradle wrapper version (8.11.1, not 8.5).
- README: documented three features that shipped undocumented — the on-screen
  **gesture recorder**, the panel's **⟳ loop-override** toggle, and **macro
  import/export**. The floating panel is now described as it actually is: a
  draggable bubble that expands into a seven-control row.

## [1.0.5] - 2026-07-11

### Fixed

- **The floating panel no longer appears on its own.** Visibility was bound to
  "the accessibility service is enabled" rather than "the user wants the
  controls", and `onServiceConnected()` showed the panel unconditionally. Because
  the system binds an enabled accessibility service on boot, after an app update,
  and whenever it restarts the process under memory pressure, the panel turned up
  over other apps with the user having done nothing.
- The panel's ✕ now persists the dismissal instead of only removing the view in
  memory, which the next service rebind undid.

### Added

- `OverlayPrefs` — a persisted panel-visible flag, default off. Deliberately
  SharedPreferences rather than the `SettingsStore` DataStore: the service reads
  it synchronously while connecting, and an async read would let the panel flash
  on screen before the stored "hidden" value arrived.

### Changed

- Constructing `OverlayController` no longer draws anything, so the editor's
  point picker still works while the panel is hidden.
- Home's top-bar control is a real toggle, tinted while the panel is on screen.
- Narrowed `accessibilityEventTypes` to `typeWindowStateChanged`.
  `onAccessibilityEvent()` is an empty stub, so `typeAllMask` had the system
  marshalling every event in the OS to a listener that discards them.

## [1.0.4] - 2026-07-01

### Fixed

- Settings is scrollable. The content was a plain `Column`, so once the Backup
  section was added it overflowed and clipped with no way to reach the bottom.

## [1.0.3] - 2026-07-01

### Added

- **Macro import/export.** Settings ▸ Backup & sharing exports all macros to a
  JSON file and imports from one through the system document picker. Imports are
  appended with fresh unique ids, so an import can never overwrite an existing
  macro — even one exported from the same device.
- Macro editor: a Share action that sends a single macro as JSON.
- `MacroTransfer` — a versioned `MacroBundle` envelope with lenient parsing that
  accepts a bundle, a bare list of macros, or a single macro object.

## [1.0.2] - 2026-07-01

### Fixed

- Enabling the accessibility service is harder to break on strict OEM ROMs.
  Overlay setup in `onServiceConnected()` is now deferred to the main looper and
  wrapped in a guard, so a window failure can't crash the service and flip the
  accessibility toggle back off. The service is marked connected before any
  optional overlay work.

### Changed

- `showOverlayPanel()` lazily creates the overlay if it wasn't set up at connect.
- Dropped the unused `flagRetrieveInteractiveWindows` from the service config.

## [1.0.1] - 2026-07-01

### Added

- A distinct **✕** on the expanded control row that fully removes the overlay
  window (reopen it from the app's top bar), alongside **▽** which minimises to
  the bubble. Previously the panel could be collapsed but never dismissed.

## [1.0.0] - 2026-07-01

Initial release.

### Added

- **Two injection engines** behind one `GestureEngine` interface, selectable per
  preference: *Accessibility* (`dispatchGesture()`, no root), *Root* (pipes
  `input tap/swipe` into a long-lived `su` shell), and *Auto* (prefers root when
  confirmed, else falls back to accessibility).
- **Floating control panel** hosted by the accessibility service itself as a
  `TYPE_ACCESSIBILITY_OVERLAY` window — no draw-over-apps permission prompt and
  no foreground-service notification. Drag to move; a bubble expands into a
  control row.
- **On-screen gesture recorder** — captures live taps, long-presses and swipes
  along with the real pauses between them, and saves the result as a macro.
- **On-screen point picker** — tap targets directly on the screen to add them to
  a macro as taps, with numbered markers.
- **Macro editor** — reorderable steps (tap, long-press, swipe, delay), per-step
  duration and repeat, loop count and interval.
- **Auto-clicker loop controls** — repeat N times or forever, with a configurable
  interval.
- Macros persisted as JSON in app-private storage; settings in DataStore.
- House dark theme: near-black surface, gold accent, light/dark/system selection.

[Unreleased]: https://github.com/PortableDiag/GhostUser/compare/v1.0.5...HEAD
[1.0.5]: https://github.com/PortableDiag/GhostUser/compare/v1.0.4...v1.0.5
[1.0.4]: https://github.com/PortableDiag/GhostUser/compare/v1.0.3...v1.0.4
[1.0.3]: https://github.com/PortableDiag/GhostUser/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/PortableDiag/GhostUser/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/PortableDiag/GhostUser/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/PortableDiag/GhostUser/releases/tag/v1.0.0
