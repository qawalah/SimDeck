# SimDeck: handover to GitHub + Claude Code

Snapshot of the complete project at: phone app 1.6.0, Windows companion 1.4.1, BeamNG mod 1.4.0, profile schema 6.
Also read `CLAUDE.md` (rules for working in this repo) and `docs/HANDOVER.md` (detailed architecture guide). This file is the short entry point.

## What it does
SimDeck turns an Android phone or tablet into an extra control surface for Windows games: buttons, toggles, selectors, sliders, knobs,
joysticks, rockers, gauges and lamps in per-game profiles. Input reaches the PC as a Bluetooth HID pad ("SimDeck Pad"), through a
Windows companion over Wi-Fi, or both. A BeamNG.drive mod reports vehicle state back (lamps, dimmed controls, live readings).

Main features
- Per-game profiles (Generic, Keyboard, BeamNG.drive x2, MudRunner, SnowRunner, Expeditions, ETS2, ATS, FS25, ACC, MSFS, Bulldozer, Utility vehicles).
- Equipment themes (airliner, aircraft, heavy equipment, farm, truck, road car, overland, motorsport), round buttons, dial gauges.
- Portrait or landscape (rotate button on the play bar), and stacks: two profiles played at once, top/bottom upright or side by side.
- Presets, copy/paste of controls across pages and profiles, layout editor on the phone and in the Windows companion (Layouts page).
- BeamNG: Vehicle page (every function of the current car, grouped, searchable, per-vehicle favourites), Gauges page (oil/coolant temp, boost, fuel, G, per-wheel brakes, any electrics signal), Tuning page (like the in-game Tuning menu), grouped searchable command/reading pickers.
- Companion installs/updates the BeamNG mod itself (while the game is closed), keyboard via scancodes, vJoy, optional ViGEm Xbox pad, macros.

## Platform and type (three parts)
| Part | Folder | Type |
|---|---|---|
| Android app | `android/` | Android app (Kotlin, Compose; `:core` pure JVM + `:app`) |
| Windows companion | `windows/` | Windows window + system-tray app (C#/.NET 8, Avalonia), also a headless CLI (`Companion.Cli`) |
| BeamNG mod | `beamng-mod/` | Lua mod (game-engine + vehicle extensions), also embedded inside the companion exe |

## Identity
- Android: `applicationId` = `com.simdeck.app`, `versionCode` = 16, `versionName` = 1.6.0 (`android/app/build.gradle.kts`), minSdk 28.
- Windows: assembly `SimDeckCompanion`, version 1.4.1 (`windows/Directory.Build.props`).
- BeamNG mod: 1.4.0 (`beamng-mod/simdeck/mod_info/simdeck/info.json` and `MOD_VERSION` in `simdeckLink.lua`).
- `ProfileJson.SCHEMA` = 6. Protocol v1 (`protocol/PROTOCOL.md`).
- The three version numbers are independent; a release bumps only the parts that changed.

## Build and run
- Android core tests: `cd android && ./gradlew :core:test` (add `-PcoreOnly=true` without an SDK). APK: `./gradlew :app:assembleRelease` (needs JDK 17, Android SDK; set `ANDROID_HOME` or `android/local.properties`; use `--no-daemon` if the Gradle daemon crashes).
- Windows: `cd windows && dotnet test SimDeck.Companion.sln -c Release`; publish single exe: `dotnet publish src/Companion.App/Companion.App.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o <dir>` (or `windows/build.ps1`).
- Mod: `cd beamng-mod && luajit tests/run.lua` (needs lua-socket; 234 checks); zip with `sh beamng-mod/build.sh`.
- CI: `.github/workflows/ci.yml` runs all suites on every push; run it by hand with `release_tag` to publish a release (details in `docs/HANDOVER.md`).
- Manual setup for players: Windows firewall allows the companion; optional vJoy/ViGEm drivers; the phone and PC on the same Wi-Fi; BeamNG mod is auto-installed by the companion (Settings > BeamNG mod).
- Before each release bump versions (Android gradle, `Directory.Build.props`, mod `info.json`) and run `docs/SMOKE_TEST.md`.

## Data and services
- Android: profiles and settings in app-private storage (`ProfileRepository.kt`, `CompanionClient.kt` settings: paired PC, favourites per vehicle model, tuning auto-apply, orientation, stacks).
- Windows: `CompanionConfig` JSON under `%APPDATA%\SimDeck`; mod written to `...\BeamNG.drive\current\mods\simdeck.zip` (old copies renamed `*.disabled-by-simdeck`).
- Network (no cloud, no accounts): WebSocket TCP 47800 (`/ws`, 6-digit PIN pairing), UDP 47801 (low-latency events), UDP 47802 (discovery broadcast; mDNS first), TCP 47803 on 127.0.0.1 (companion <-> BeamNG mod), UDP 4444 (OutGauge fallback).

## Secrets (names only)
- Release signing, provided as GitHub repository secrets: `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` (value `simdeck`). Gradle reads env vars `SIMDECK_KEYSTORE` (path), `SIMDECK_KEYSTORE_PASSWORD`, `SIMDECK_KEY_ALIAS`, optional `SIMDECK_KEY_PASSWORD`.
- The keystore file (`simdeck-release.jks`) is held by the owner, not in the repo. CI pins the release certificate SHA-256 (`9011afbf...` in `ci.yml`); every release must use that key or users cannot update.
- Without these env vars local builds are signed with the debug key. No API keys are used.

## Known bugs, unfinished work, next ideas
- None of 1.1 to 1.6 has been tested in a real game / on real hardware (BeamNG, vJoy, Bluetooth HID, a real phone). Run `docs/SMOKE_TEST.md`.
- **Stale built-in profiles (reported by the owner, not fixed):** `ProfileRepository` lets a stored copy of a built-in profile override the shipped one, so a phone that saved BeamNG.drive earlier does not get the new Gauges/Tuning pages. Workaround: Profiles > BeamNG.drive > Restore original layout. Fix idea: merge missing built-in pages by id (`bng_gauges`, `bng_tuning`) into stored built-ins.
- Release APK is not minified (`isMinifyEnabled = false`, ~30+ MB); enabling R8 would shrink it (needs testing).
- Mod version vs companion version confusion: consider one unified version number.
- Roadmap (`docs/ROADMAP.md`): other games via SimHub Property Server; built-in connections (MSFS SimConnect, ETS2/ATS telemetry, ACC/AC shared memory, FS25 script mod, read-only iRacing/rF2/LMU/F1/Forza).
- Other ideas: foreground service to keep play running in background, localisation (English only), AutoMirrored icon deprecations, newer GitHub Action versions.

## Decisions to keep
- Keyboard output uses scancodes, not virtual keys: DirectInput games ignore VK-only events.
- Machine profiles keep joystick buttons at 32 or below, one number per shared job (many games/vJoy cap at 32).
- Every message carries the protocol version; change `protocol/PROTOCOL.md` first, bump `ProfileJson.SCHEMA` when the profile format changes.
- Phone is the owner of profiles; the PC editor sends JSON the phone imports, and refuses while the phone's editor has that profile open.
- Mod only sends readings the phones watch (keeps traffic low); tuning is applied by merging into the vehicle config and respawning, like the game's menu.
- Companion installs the mod only while BeamNG is closed (the game keeps mod zips open) and disables old copies that would fight over port 47803.
- A stack plays as one merged profile (`Stacks.combine` in core): bottom control ids get the `b~` prefix, each half keeps its own output route (`InputEngine.outputFor`), so the protocol and companion are unchanged.
- Vehicle page uses one uniform compact button style with a label for what it does, groups + search + favourites keyed by vehicle model; no vehicle function is ever dropped.
- Signing keys/passwords never go in the repo; keep review-cited regression tests (comments cite ids like A19, M1; see `docs/review/`).
- Tooling note: `device_commit_files`-style size limits were why APK/zips were split earlier; irrelevant in GitHub, release assets come from CI.
