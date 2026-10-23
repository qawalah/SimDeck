# SimDeck 1.1.0

SimDeck is now one app: the GameControl codebase continues under the SimDeck name, with what the earlier SimDeck prototype
had that GameControl lacked, and fixes for vehicle-specific BeamNG functions and the machine (utility) profiles.

## Upgrading

- The app's package is now `com.simdeck.app`, so it installs **next to** GameControl or the earlier SimDeck instead of over
  them. Export the profiles you made, uninstall the old app, install `SimDeck.apk`, import them, and pair once with the PIN.
- BeamNG: delete the old mod zips (`gamecontroller.zip`, `simdeck_bridge.zip` or an older `simdeck.zip`) from the `mods`
  folder and copy in the new `simdeck.zip` (version 1.1.0). Two copies of the mod fight over the same port.
- Replace the PC companion with the new one.

## Vehicle-specific BeamNG functions (fixed)

Tailgates, doors, dump beds, cranes, outriggers, ramps, lift axles and similar did not work because the mod only looked in one
place for them. BeamNG keeps them in four: the vehicle's `input_actions*.json`, its `*.interaction.json` files (the newer
"Triggers2" actions, switched on per vehicle in its jbeam), the shared `vehicles/common` interaction maps, and the game's
own list of active actions. The mod now reads all four, and also drives couplers (tailgate latches, fifth wheels) and
hydraulics directly in vehicle Lua, so a function works even when it has no input action.

- **Vehicle page**: a new page in the BeamNG.drive profile that fills itself with the functions of the vehicle you are
  driving: buttons for actions and couplers, spring-centred sliders for hydraulics. Nothing to bind. If it stays empty, it
  says why and shows what the mod found where.
- In the editor, the BeamNG command field now offers **This vehicle** and **Game actions** by name, and sliders can drive a
  BeamNG analog action or hydraulic directly.
- In the BeamNG console, `simdeckLink.report()` lists everything the mod found for the current vehicle.

## Utility and machine profiles (fixed)

They failed silently in two ways, both now caught:
- vJoy ignores buttons above the number set in *Configure vJoy* (8 by default) and axes that aren't ticked, so most of a
  machine cab did nothing. The companion now checks each profile against the vJoy setup and the phone shows a warning
  strip saying exactly what to change.
- Pushing a joystick or bipolar lever below centre was never released when the phone went away. Fixed in the companion.
- **Bind helper** (editor > Binding): while you assign controls in a game's settings, each joystick moves one axis at a
  time, so the game captures the axis you mean instead of whichever moved first.
- In BeamNG you don't need them at all: the Vehicle page drives a machine's hydraulics directly.

## New from SimDeck

- **Controls**: rocker switch (hold up / down), gauge (any game value with scale and unit), tell-tale lamp, page key,
  a safety cover for any button or selector, and notched sliders.
- **Profiles**: SnowRunner, Expeditions: A MudRunner Game, Euro Truck Simulator 2, American Truck Simulator, Farming
  Simulator 25, Assetto Corsa Competizione and Microsoft Flight Simulator; a crane page for MudRunner; and a much larger
  BeamNG.drive profile (Vehicle, Lights & assists with tell-tales and gauges, World, and analog Pedals pages).
- **Automatic profile switching**: the companion tells the phone which game is in front, and the phone opens the profile
  made for it (set the game program in the editor; on by default, off in editor > Binding).
- **Icons**: 65 more, including tailgate, door, ramp, lift axle, crane, PTO, landing gear, flaps and autopilot.

## Not tested in game

This round was built and tested with the automated suites only (207 mod, 139 companion and 41 phone-core tests). The new
BeamNG discovery and the vJoy checks follow BeamNG's documentation and vJoy's API but have not been run in BeamNG or on a
PC with vJoy. If the Vehicle page stays empty for a vehicle, send the `simdeckLink.report()` output from the console.

# SimDeck 1.0.0

The first full release of SimDeck: an Android phone or tablet as an extra control surface for Windows games.

## Download

| File | For |
|---|---|
| `SimDeck.apk` | The phone app (Android 9 or newer) |
| `SimDeck-companion-win-x64.zip` | The PC companion (Windows 10/11, 64-bit, no .NET install needed) |
| `simdeck.zip` | The BeamNG.drive mod |
| `INSTALL.md` | How to install and connect |
| `SMOKE_TEST.md` | One end-to-end test of everything |
| `SHA256SUMS.txt` | Checksums of the files above |

## Upgrading from a beta

1.0 is signed with a test key, like the betas, and that key differs between builds, so Android won't install 1.0 over a
beta. Export any profiles you made (card menu > **Export to file**), uninstall the beta, install 1.0, import them again,
and enter the companion's PIN once. A later version signed with a permanent release key will need the same steps once.

## What's in 1.0

**Two ways to connect, together or apart**
- **Bluetooth controller**: the phone pairs with Windows as "SimDeck Pad", a keyboard plus a 64-button, 8-axis gamepad. No PC
  software needed.
- **PC companion over Wi-Fi**: scancode keyboard input, vJoy buttons and axes, an optional virtual Xbox pad, macros, BeamNG
  commands, and live game state back to the phone. Found automatically on the network, paired once with a PIN.
- With both up, each control uses the best route, nothing stays pressed when a link drops, and the companion shows the phone
  on both links.

**Profiles and controls**
- Seven built-in profiles: Generic gamepad, Keyboard keys, BeamNG.drive, BeamNG.drive (Bluetooth pad), MudRunner, Bulldozer,
  and Utility vehicles (loader, excavator, crane, plough & tow).
- Controls: buttons, toggles, selectors, button groups, sliders and hydraulic levers, knobs, and pilot joysticks with two
  spring-centred axes. Lamps follow the game where it reports state.
- A layout editor with pages, drag to move and resize, an icon library, and export and import as `.simdeck.json`.

**BeamNG.drive**
- Every vehicle function from the phone: lights, signals, hazards, horn, ignition and starter, gearbox modes, ESC, diff
  locks, 4WD and range, park brake, cruise control, cameras, recover and reset, slow motion and pause.
- Lamps follow the real vehicle, and controls the car doesn't have are dimmed.
- **Bind in game** maps the Bluetooth pad's buttons inside BeamNG in one tap.
- No mod? OutGauge telemetry still lights the basic lamps.

## Known limits

- Keep SimDeck on screen while playing. Leaving it lets go of held buttons and sliders; toggles stay on.
- Some phone makers remove Bluetooth HID support; on those phones use the companion over Wi-Fi.
- When the phone pairs over Bluetooth, Windows may switch the PC's sound to the phone. INSTALL explains how to turn that off.
- BeamNG: a held `action.*` command that targets the player's car is released in the new car after a vehicle switch
  (details in `beamng-mod/README.md`, Known limits).
- The app's text is English only.
