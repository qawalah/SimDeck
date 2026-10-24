# SimDeck developer handover

SimDeck turns an Android phone or tablet into an extra control surface for Windows games. This page is the starting point for a developer taking the project over. For users, read [INSTALL.md](INSTALL.md). For the 1.0 feature list, read [RELEASE_NOTES.md](RELEASE_NOTES.md).

## The three parts

| Part | Folder | Stack | Tests |
|---|---|---|---|
| Android app | `android/` | Kotlin 2.0, Jetpack Compose, AGP 8.7, Gradle 8.10, minSdk 28, JDK 17 | `./gradlew :core:test` (JUnit, pure JVM) |
| Windows companion | `windows/` | C# / .NET 8, Avalonia UI, self-contained single exe | `dotnet test SimDeck.Companion.sln -c Release` |
| BeamNG mod | `beamng-mod/` | Lua (BeamNG GE and vehicle extensions) | `cd beamng-mod && luajit tests/run.lua` (needs `lua-socket`) |

All three speak one protocol, [protocol/PROTOCOL.md](../protocol/PROTOCOL.md) (v1). Every message carries `"v":1`. Change the protocol there first, then in all three parts.

### How input reaches the game

The app has two routes, and it can use both at once:

1. **Bluetooth HID.** The phone registers as "SimDeck Pad" (`android/app/.../hid/BluetoothHidLink.kt`). Its report descriptor (`android/core/.../hid/`) has a keyboard (report id 1) and a gamepad with 64 buttons and 8 axes (report id 2: X Y Z RX RY RZ SL0 SL1). No PC software is needed.
2. **Wi-Fi companion.** The app finds the PC by mDNS, with a UDP broadcast fallback, and pairs with a 6-digit PIN. It then uses a WebSocket on port 47800 for control and state, and UDP on 47801/47802 for low-latency values. The companion injects scancode keyboard input (SendInput), vJoy buttons and axes, an optional ViGEm Xbox pad, and macros. It talks to the BeamNG mod on 47803 and reads OutGauge on 4444.

`InputEngine` (`android/core/.../engine/InputEngine.kt`) picks a route for each control and tracks what each link is holding. It releases everything a link held when that link drops, and replays latches when the link comes back. Most of the review history is about these edge cases, and `CoreTest.kt` pins them. Run the tests after any change to it.

### Android module layout

- `:core` is plain JVM with no Android dependency: the model (`Profile`, `Page`, `Control`, `Action`), `InputEngine`, `Feedback`, `Protocol`, the HID descriptor, `BuiltInProfiles`, `LayoutOps`, `Presets` (the preset library, next-free gamepad numbering and placing or pasting controls and pages), and `ProfileJson` (import and export as `.simdeck.json`, schema 6), and `VehicleFeaturePages` (fills auto pages from the mod's `beamng.features`). `-PcoreOnly=true` builds and tests it without the Android SDK.
- `:app` holds the Compose UI (`ui/screens`, `ui/components`, `ui/theme`, `ui/icons`, and `ui/sim` for equipment themes: `SimTheme` tokens per theme, generated textures and panel groups, and the skin painters for every control type; `Themes` and `Skins` ids live in `:core`), `AppViewModel`, `BluetoothHidLink`, `CompanionClient`, and storage.

Control types are momentary, latching, switch, button group, slider, rotary, joystick, rocker, gauge, lamp (indicator) and page key. A joystick is two spring-centred slider axes on the wire: `<id>.x` and `<id>.y`; a rocker is two momentary buttons: `<id>.up` and `<id>.down`. Gauges, lamps and page keys never go on the wire (`ControlType.displayOnly`).

`AppViewModel` keeps the stored profile (`baseProfile`) apart from the one being played (`activeProfile`), which is the stored one with its auto pages filled for the current vehicle; every `beamng.features` refills it and re-syncs the companion. It also handles `notice` (warning strip), `foreground` (automatic profile switching by `Profile.processNames`) and the bind helper (joystick axis lock).

### Readings and tuning (1.4)

`core/.../model/Readings.kt` is the catalogue of live values (title, group, unit, scale, dial range) the pickers, presets
and the Gauges page use; `Commands.kt` groups the BeamNG commands for the command picker; `Tuning.kt` holds the tuning
settings and the game's display rules (display range, percent for unit-less values, steps). The phone sends
`readings.watch` with the keys its profiles show (all of them while a picker is open); the companion merges the phones and
forwards it; the mod's vehicle Lua only reports watched readings. Tuning is read in vehicle Lua (`v.data.variables`) and
applied in game-engine Lua by merging the values into the vehicle's config and respawning it, as the Tuning menu does.
The UI is `ui/components/GroupedPicker.kt`, `ui/screens/Pickers.kt` and `ui/screens/TuningPanel.kt`.

### Installing the mod (1.4.1)

`windows/src/Companion.Core/BeamNG/ModInstaller.cs`: the mod's files are embedded in Companion.Core (see its csproj),
zipped at run time like `beamng-mod/build.sh` does. The host checks the mods folder every 10 s and, with
`AutoInstallBeamNgMod` on, installs while BeamNG is closed. Only the first instance on Windows gets an installer (App.axaml.cs).

### Editing from the PC (1.3)

The companion's **Layouts** page edits the phone's profiles over the existing WebSocket (`profiles.*` / `profile.*` messages
in PROTOCOL.md). `windows/src/Companion.Core/Layout/LayoutDoc.cs` edits the profile as a JSON tree (so unknown fields
survive) with the phone's layout rules (minimum spans, grid limits, free-spot search, new ids) and undo; `LayoutCatalog.cs`
mirrors the phone's theme colours, skin ids and skin resolve rules. The UI is `LayoutsViewModel` and `LayoutCanvas` in
`Companion.App`. The phone is the owner: `AppViewModel.saveFromPc` imports the JSON like a file and refuses while its own
editor has that profile open. `tools/UiSnap` renders the companion headlessly with a simulated phone serving two built-in
profiles, for design review.

### Built-in profiles

`BuiltInProfiles.kt` has fourteen: Generic gamepad, Keyboard keys, BeamNG.drive (with the auto Vehicle page), BeamNG.drive (Bluetooth pad), MudRunner, SnowRunner, Expeditions, Euro Truck Simulator 2, American Truck Simulator, Farming Simulator 25, Assetto Corsa Competizione, Microsoft Flight Simulator, Bulldozer, and Utility vehicles (loader, excavator, crane, plough & tow). Every game profile lists its executables in `processNames` (a test checks it). The machine profiles keep button numbers at 32 or below, and common jobs share one number across machines. The comment above `bulldozer()` lists them.

### Design

The visual language ("racing-game front end") is set out in [DESIGN.md](../DESIGN.md) and [PRODUCT.md](../PRODUCT.md). The companion follows the same tokens.

## Building and releasing

Everything is built by GitHub Actions ([.github/workflows/ci.yml](../.github/workflows/ci.yml)) on every push. Each part is uploaded as an artifact.

To publish a release, run the workflow by hand (**Actions > CI > Run workflow**) and set `release_tag`:

- A tag such as `beta-3` creates a prerelease.
- A tag such as `v1.0.1` creates the latest release, with `docs/RELEASE_NOTES.md` as its notes.

The release contains `SimDeck.apk`, the companion zip, the mod zip, `INSTALL.md`, `SMOKE_TEST.md`, and `SHA256SUMS.txt`.

### Signing (important)

- A `v*` release must be signed with the SimDeck release key. The workflow checks the APK's SHA-256 certificate fingerprint against the one pinned in `ci.yml` and refuses anything else.
- The key comes from three repository secrets: `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, and `ANDROID_KEY_ALIAS` (`simdeck`). Gradle reads them as `SIMDECK_KEYSTORE`, `SIMDECK_KEYSTORE_PASSWORD`, `SIMDECK_KEY_ALIAS`, and optionally `SIMDECK_KEY_PASSWORD`. Without them, it falls back to the debug key.
- Android only installs an update over an app signed with the same key. Every future version must use this key. If the key is lost, users have to uninstall and reinstall.
- The keystore file and its password are never in this repository. The owner (ALI) holds them.
- **1.0.0 shipped with a test key**, by ALI's choice (`allow_test_key` was ticked), because the secrets were not set yet.
  A release key exists: ALI holds `simdeck-release.jks` and its password, and the workflow pins its fingerprint.
  For the first properly signed release, add the three secrets and don't tick `allow_test_key`. Users then uninstall
  and reinstall once, as RELEASE_NOTES explains. After that, updates install normally.

Before each release:

1. Bump `versionCode` and `versionName` in `android/app/build.gradle.kts`.
2. Bump `Version` in `windows/Directory.Build.props`.
3. Bump the version in `beamng-mod/simdeck/mod_info/simdeck/info.json`.

## Testing on real hardware

Unit tests cannot cover Bluetooth pairing, vJoy, ViGEm, SendInput, or BeamNG in game. [SMOKE_TEST.md](SMOKE_TEST.md) is the end-to-end script for these, and takes about an hour. Run it before every release. `beamng-mod/EXPECTED.md` lists the log lines the mod should print in game.

## History and known limits

- 1.4.0 added live readings, the Gauges and Tuning pages and the grouped pickers; not yet run in BeamNG either.
- 1.2.0 added equipment themes ([THEMES.md](THEMES.md)); 1.3.0 added presets, copy and paste, the PC layout editor and the
  theme extras. Neither has been run on a real phone, in BeamNG or with vJoy yet.
- 1.1.0 merged the earlier SimDeck prototype into this codebase (which was called GameControl until then) and renamed it SimDeck throughout, including the Android package (`com.simdeck.app`). It added the multi-source vehicle feature discovery in the mod, vJoy configuration checks in the companion, and the controls, profiles and icons the prototype had. None of 1.1.0 has been run in BeamNG or with vJoy yet: run SMOKE_TEST plus the Vehicle page and utility checks in RELEASE_NOTES before calling it released.

- `docs/review/` holds every review round, round 1 to rc3, with each part's replies. When a comment in the code cites an item id (A19, M1, B-3, U28, and so on), you can trace it back there. The 1.0 release candidate was signed off with 0 open comments in `rc3.md`.
- Known limits are listed in RELEASE_NOTES (Known limits) and `beamng-mod/README.md` (Known limits). They include:
  - There is no foreground service: leaving the app lets go of held controls.
  - The app's text is English only.
  - Some phones lack Bluetooth HID.
- Planned features are in [ROADMAP.md](ROADMAP.md); the equipment-theme design language is [THEMES.md](THEMES.md).
- Ideas that came up during review but are not built:
  - A foreground service, so play can continue in the background.
  - Localisation.
  - The `AutoMirrored` icon deprecations shown in the build log.
  - Moving to `actions/setup-java@v5` and newer Node-24 action versions.

## Where to look first

| Task | Start in |
|---|---|
| A new control type | `core/.../model/Model.kt`, then `InputEngine`, `Feedback`, `Profiles.kt` (defaults), the UI palette and inspector, and `PROTOCOL.md` |
| A new built-in profile | `core/.../profiles/BuiltInProfiles.kt`; the layout and round-trip tests check it automatically |
| A new BeamNG command or lamp | `beamng-mod/simdeck/lua/...`, the `beamng.*` ids in `PROTOCOL.md`, and the BeamNG profiles |
| A new companion output | `windows/src/Companion.Core/Input` and `Engine` |
| A new preset | `core/.../profiles/Presets.kt` (generic ones), or the built-in profiles, whose controls become presets |
| A new skin or theme | `core/.../model/Themes.kt`, `app/.../ui/sim`, THEMES.md, and `LayoutCatalog.cs` for the PC editor |
| Connection or pairing bugs | `android/app/.../net/CompanionClient.kt` and `windows/src/Companion.Core/Net` |
