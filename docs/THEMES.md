# SimDeck equipment themes: design language

Status: **built in SimDeck 1.2** (see "What 1.2 built" at the end for what differs from this spec). It defines how SimDeck's play surface can look and behave like the equipment
being driven: an airliner flight deck, a general-aviation panel, an excavator cab, a farm tractor armrest, a truck dash,
a road car, an overland rig, a race car. A theme changes more than colour. It swaps materials, the shape and travel of
every control, how lamps and legends light, how gauges read, how the page is divided into panels, and how it all looks
at night.

[DESIGN.md](../DESIGN.md) stays the house style ("Race Front-End") for everything outside the play surface (profiles,
editor, connect, dialogs), and it remains the default theme of the play surface. This document adds the theme layer on top.

Contents: 1 Principles · 2 Invariants · 3 Anatomy of a theme · 4 The skin catalogue · 5 The themes · 6 Environment and
lighting · 7 Model changes · 8 Implementation · 9 Rollout · 10 Review checklist · 11 Sources

---

## 1. Principles

1. **Feel the machine, keep the tool.** A theme borrows the visual and motion language of real equipment and of the sim
   hardware people buy to imitate it (throttle quadrants, switch panels, button boxes, pilot joysticks). It never gives up
   SimDeck's job: big targets, glanceable state, nothing stuck pressed.
2. **Form follows the control's meaning.** A flap lever's knob is shaped like a wing section and a gear lever's like a
   wheel because pilots find them by touch. A hydraulic valve has a ball knob and springs to centre. A race car's TC
   setting is a numbered rotary. The skin is chosen for what the control *does*, not for decoration.
3. **Generic, not branded.** Inspired by categories of equipment, never a replica of a product. No manufacturer names,
   logos, trade colours or exact panel layouts (see Invariant 9).
4. **Procedural and scalable.** Every skin is drawn in code from tokens, so it works at any cell size, from a 1×1 phone
   cell to a 4×4 tablet block, and in any accent the user picks.
5. **Behaviour is the same in every theme.** A theme can change how a control *looks and feels* (visual travel, haptic
   detents, sounds) but never what it *sends*. A profile behaves identically in every theme.

## 2. Invariants (every theme, no exceptions)

1. **Touch targets.** Every interactive zone is at least 48dp, and skins keep their hit area to the full grid cell even
   when the drawn part is smaller (a toggle bat is drawn thin; it is touched as its whole cell).
2. **Signal colours keep their meaning.** Red: warning or danger. Amber: caution or degraded. Green: on, engaged, normal.
   Cyan or blue: advisory or selected. White: neutral status. This is the convention of flight decks and of vehicle
   tell-tales, so a theme may change the *shade* of each, never the meaning. A theme's decorative accent (machine yellow,
   anodised red) is never used for a lamp.
3. **State is never colour alone.** Lit means light *and* a second cue: a legend that appears, a lamp window that fills,
   a lever that has moved, a pointer that points. Pending (sent, game not yet confirmed) and unavailable (vehicle lacks
   it) have a visible treatment in every skin.
4. **Contrast.** Legends and readouts at least 4.5:1 against their surface in day lighting; lamp-on versus lamp-off at least
   3:1. Night lighting is checked separately (§6).
5. **Glanceable at arm's length.** Readouts at least 20sp; legends at least 11sp; icons at least 20dp. Detail (screws,
   texture, engraving) stays below the contrast of anything that carries information.
6. **Release safety.** Spring-return skins (levers, sticks, rockers, momentary keys) must visibly return to rest the
   moment the finger lifts or the gesture is cancelled, and send their release. No skin may keep a value that the
   behaviour would not.
7. **Guards stay guards.** Any control with `guard` shows a cover in every theme. The theme picks the cover's look (red
   flip cover, yellow-black hazard cover, clear plastic flap); the first tap always lifts it.
8. **Motion respects the system.** Honour Android's Remove animations: skins jump to their end state, haptics stay.
9. **No trade dress.** No logos, product names, manufacturer colour schemes (for example the green-and-yellow of one tractor
   maker or the yellow-and-black of one machine maker used as a brand pairing), or copies of a specific panel's layout.
   Generic standards (ISO symbols, regulation knob shapes, signal colours) are fine.
10. **The editor is honest.** The layout editor draws each control in its theme skin at rest, so what you place is what
    you get, and it stays in the house style around the canvas.

## 3. Anatomy of a theme

A theme is a set of tokens plus a default skin for each control type. Everything below is overridable per profile, per
page and (for skins) per control.

| Layer | Tokens | Examples |
|---|---|---|
| **Palette** | `environment`, `panel`, `panelEdge`, `bezel`, `cap`, `capHigh`, `capPressed`, `legend`, `legendDim`, `engrave`, `lampOff`, `lamp.red/amber/green/cyan/white`, `backlight`, `accent` | Airliner: grey panels, white legends, amber backlight-free annunciators |
| **Material** | `panelFinish` (flat, grained plastic, painted metal, brushed, carbon weave, powder coat), `fasteners` (none, Dzus, hex, Torx), `seams` (none, line, recess), `wear` (0..1) | Overland: black powder-coat plate with Torx screws |
| **Geometry** | `capShape` (cut-corner, rounded rect, square, round, rocker), `cornerRadius`, `bezelWidth`, `pressTravel` (dp of visual travel), `gateDepth` | Heavy: chunky 12dp radius rubber caps with 4dp travel |
| **Type** | `legendFont`, `legendCase`, `legendWeight`, `readoutFont` (7-segment, 14-segment, LCD dot, condensed italic, PFD sans), `engravingStyle` (filled, backlit, painted) | Motorsport: 7-segment dash, white silkscreen legends |
| **Iconography** | `iconSet` (house ISO-style set, plus per-theme additions), `iconStroke` | Heavy: ISO 6405-style machine symbols |
| **Lighting** | `modes` (day, dusk, night), `backlightColor`, `floodLevel`, `annunciatorStyle` (hidden-until-lit, always-visible), `darkCockpit` (bool) | Airliner night: dark panels, glowing white legends, lamps unchanged |
| **Feel** | `haptics` per event (press, detent, gate, centre, limit), `sounds` (optional, off by default: click, clack, thunk), `springCurve` | Truck: firm rocker click; flight: soft detent tick per lever notch |
| **Environment** | `background` (cockpit coaming, cab console, dash, carbon tub), `panelGroups` style (boundary lines, engraved headers, console bays) | Airliner: white boundary lines with engraved section titles |

## 4. The skin catalogue

Each skin is a drawing and a feel for one control type. Behaviour (what is sent, when) comes from the control type and is
shared. Skin ids are stable strings used in profiles.

### Momentary (button)

| Skin id | Looks like | Feel | Used in |
|---|---|---|---|
| `cap` | the house cut-corner cap | soft press | Race (default) |
| `lit-push` | square lighted pushbutton: dark lens, legend on one or two lines, lamp fills the lens when lit | short 4dp travel, crisp click | Airliner, Motorsport |
| `ring-push` | round metal pushbutton with an illuminated ring | click | Motorsport, Overland |
| `rubber-key` | chunky rubber keypad key with icon and a small lamp window in the top corner | deep travel, damped | Heavy, Agriculture |
| `rocker-mom` | rocker that springs back, icon on the upper half | rock and return | Commercial, Heavy |
| `glass-tile` | flat backlit tile behind glass, icon and label | tick, no travel | Road car |
| `mushroom` | large red palm button with a yellow collar (for one emergency stop or kill switch per page) | heavy press | Heavy, Agriculture |

### Latching (toggle)

| Skin id | Looks like | Feel | Used in |
|---|---|---|---|
| `toggle-bat` | metal bat toggle in a bezel, up = on; engraved ON / OFF above and below | snap over-centre, two clicks of travel | Airliner, GA, Motorsport, Overland |
| `lit-push-split` | lighted pushbutton with a split legend: lower half shows the state word (ON, OFF, AUTO), upper half a fault or status word | push-on, push-off | Airliner |
| `rocker-lamp` | automotive rocker with a laser-etched icon and two lamp windows (top lit when on, bottom shows a position or a fault) | rock with detent | Commercial, Heavy, Overland, Agriculture |
| `push-pull` | push-pull knob (pull = on) with a coloured collar | pull and click | GA (cabin heat style), Heavy (throttle hold) |
| `key` | ignition key in a lock with OFF / ON (and START if momentary half) | turn with detent | GA, Commercial, Road car |
| `glass-toggle` | glass tile with a lit bar under the icon | tick | Road car |

### Selector (switch) and button group (radio)

| Skin id | Looks like | Feel | Used in |
|---|---|---|---|
| `rotary-pointer` | bar-pointer knob with engraved positions around it (like a magneto or light selector) | one detent tick per position, end stops | Airliner, GA, Commercial, Heavy |
| `gated-lever` | lever in a slotted gate with labelled stations (P R N D, F N R, 1 2 3) | gate haptic at each station | Road car, Heavy, Agriculture |
| `slide-select` | the house segmented slider | tick | Race |
| `lit-row` | row of lit pushbuttons, one lit at a time (radio) | click | Airliner, Motorsport |
| `annunciator-row` | row of rubber keys with lamp windows (radio) | press | Heavy, Agriculture |

### Slider

The slider is where themes differ most, so its skin also carries an optional **station list** (named detents, see §7).

| Skin id | Looks like | Feel | Used in |
|---|---|---|---|
| `fader` | the house T-handle fader | smooth | Race |
| `thrust-lever` | airliner thrust lever in a quadrant with an engraved scale and named stations (IDLE, CL, FLX/MCT, TOGA); a reverse latch below idle | detent tick at each station, heavier tick into TOGA | Airliner |
| `quadrant-knob` | GA quadrant lever with a shaped knob: throttle (smooth, black), propeller (scalloped, blue), mixture (fluted, red), flaps (airfoil), gear (wheel) | smooth, with end stops | GA, Airliner (flap and gear) |
| `hydraulic-lever` | valve lever with a ball knob in a slot, centre detent notches, end labels (RAISE / LOWER) | springs to centre, tick through centre | Heavy, Agriculture (exists today as the spring-centred bipolar look) |
| `hand-throttle` | ribbed rotary-style hand throttle or a short throttle lever with a range bar (idle to high idle) | stays where left | Heavy, Agriculture |
| `pedal` | vertical pedal pad with tread, travel shown as a fill | spring return | Motorsport, Road car, Commercial |
| `stalk` | a steering-column stalk seen end-on with numbered positions (retarder 0-5, wipers) | detents | Commercial, Road car |

### Rotary

| Skin id | Looks like | Feel | Used in |
|---|---|---|---|
| `encoder` | the house knurled knob | step ticks | Race |
| `concentric` | dual concentric knob with a value window above it (managed / selected style dot) | inner and outer rings step separately | Airliner |
| `multi-pos` | numbered multi-position rotary with a skirt (1-12), the motorsport setup knob | firm detent per number, readout of the current number | Motorsport |
| `trim-wheel` | vertical wheel edge-on with ribs and a position pointer | free spin with ticks | GA, Airliner |
| `thumb-roller` | small roller set into a grip or console | fine ticks | Heavy, Agriculture |
| `chrome-dial` | satin knob with a light ring | soft ticks | Road car |

### Joystick

| Skin id | Looks like | Feel | Used in |
|---|---|---|---|
| `gate` | the house round gate | centre tick | Race |
| `pilot-grip` | top view of a machine pilot joystick: grip with buttons and thumb-roller indicators, square gate, arrows labelled with the motions (SWING, BOOM, STICK, BUCKET) | springs back, tick leaving the dead zone | Heavy, Agriculture |
| `sidestick` | airliner sidestick top view with a round gate | springs back | Airliner |
| `yoke` | yoke horns with a horizontal travel bar and fore-aft travel bar | springs back | GA |
| `hat` | four-way hat on a grip (for camera or trim) | 4 detents | Airliner, Motorsport |

### Rocker

| Skin id | Looks like | Used in |
|---|---|---|
| `rocker-3` | ON-OFF-ON spring-centred rocker with up and down icons | Commercial, Heavy, Overland |
| `toggle-3` | three-position spring-centred bat toggle | Airliner, GA, Motorsport |
| `window-switch` | the car window switch: pull up, push down | Road car |

### Gauge

| Skin id | Looks like | Used in |
|---|---|---|
| `readout` | the house italic number with unit | Race |
| `steam` | round analogue gauge: needle, scale, coloured range arcs (green normal, amber caution, red limit) | GA, Commercial, Heavy, Overland |
| `segment` | 7- or 14-segment LCD window with unlit segments faintly visible | Airliner (value windows), Motorsport, Heavy |
| `tape` | vertical moving tape with a pointer box | Airliner |
| `bar` | segmented bar graph (fuel, temperature, boost) | Motorsport, Heavy |
| `shift-lights` | LED strip that fills green, amber, red and flashes blue at the shift point | Motorsport |
| `inclinometer` | vehicle silhouette that tilts with roll or pitch | Overland |

Round gauges and round buttons are on the roadmap ([ROADMAP.md](ROADMAP.md)); `steam`, `ring-push` and `chrome-dial`
are where they arrive.

### Lamp (indicator) and page key

| Skin id | Looks like | Used in |
|---|---|---|
| `telltale` | ISO-style tell-tale icon that lights in its signal colour | Road car, Commercial, Overland (default) |
| `annunciator` | rectangular lens, legend hidden until lit (dark-cockpit style), or engraved and dim | Airliner, GA |
| `master` | large MASTER CAUTION / MASTER WARNING lens that flashes; tap to acknowledge (acknowledge is local only) | Airliner, GA |
| `led-dot` | small LED with a silkscreen label | Motorsport, Heavy |
| `page-tab` (page key) | engraved panel-section label (OVHD, PEDESTAL, LOADER) or a console hot key with a house icon | per theme |

### Guard covers

| Cover | Looks like | Used in |
|---|---|---|
| `flip-red` | red hinged cover, striped edge | Airliner, GA, Motorsport (the "missile switch") |
| `flip-clear` | clear plastic flap with a red edge | Commercial, Road car |
| `hazard` | yellow-and-black striped cover (generic hazard pattern) | Heavy, Agriculture, Overland |

## 5. The themes

Each theme lists its character, what it draws on, its environment, palette, type, default skins and its signature details.
Hex values are starting points for the implementation; the review in §10 decides the final ones.

### 5.1 Race Front-End (house default)

Unchanged from [DESIGN.md](../DESIGN.md): night-garage blue-black, one electric magenta, cut-corner caps, racing stripe
for lit state. Default for custom profiles.

### 5.2 Airliner flight deck

**Character.** Calm, grey, engineered. A panel of grey lightplates with white engraved legends that glow at night, square
lighted pushbuttons, guarded switches, a glareshield strip of value windows, and a pedestal with thrust levers.
Annunciation follows a dark-cockpit philosophy: when everything is normal, little is lit.

**Draws on.** Airliner overhead, glareshield and pedestal panels; airline-style sim hardware (throttle quadrants with
IDLE / CL / FLX / TOGA detents and a reverse latch, Airbus-style FCU and EFIS replicas with segment value windows,
Boeing-style MCP panels). Lightplates on real 737s are painted grey (Boeing's standard is cited as FS 36440 light gull
grey, with other suppliers using darker greys); legends are backlit through the plate.

**Environment.** Panel groups are drawn as separate lightplates with a thin white boundary line around each functional
group and an engraved group title (ELEC, FUEL, LIGHTS, AUTOFLIGHT). Above the grid, an optional glareshield band holds the
page's segment readouts.

| Token | Day | Night |
|---|---|---|
| environment | #1E2226 (coaming) | #0B0D0F |
| panel | #8E9396 (light grey plate, a little darker than the real paint so phone screens stay comfortable) | #2A2E31 |
| legend | #FFFFFF engraved fill | #F4F1E6 backlit glow |
| lampOff | #2B2F33 | #15181A |
| lamps | red #FF3B30, amber #FFB000, green #3DDC84, cyan #36C5F0, white #F5F5F5 | same, never dimmed below 70% |

**Type.** Legends in B612 (an open-source font designed for cockpit displays), uppercase, bold for group titles.
Value windows in a 7-segment face (DSEG7) in amber or white on black. PFD-style tapes in B612.

**Default skins.** Momentary `lit-push`; latching `lit-push-split` (or `toggle-bat` for lights and probes); selector
`rotary-pointer`; radio `lit-row`; slider `thrust-lever` (and `quadrant-knob` with airfoil knob when the control's icon is
flaps, wheel knob when it is landing gear); rotary `concentric`; joystick `sidestick`; rocker `toggle-3`; gauge `segment`
or `tape`; lamp `annunciator`; guard `flip-red`.

**Signature details.**
- Split legends: the lower half of a lit pushbutton carries the state (ON, OFF, AUTO, ALTN) in white or blue, the upper
  half the problem (FAULT, LOW) in amber.
- Managed versus selected: a value window shows dashes and a dot when the game reports "managed", the number when
  "selected" (from a game state key; optional).
- Master caution and warning lenses on any page that has an annunciator.
- Flap and gear knobs are shape-coded per the certification rules for cockpit control knobs (flap knob like an airfoil,
  gear knob like a wheel).

**Avoid.** Bright magenta (it means "managed / FMS" in real flight decks), decorative glow, rounded consumer shapes.

### 5.3 General aviation panel

**Character.** A light aircraft: black or dark-grey panel, round steam gauges, a row of bat toggles and rocker breakers,
a key magneto switch, and push-pull or quadrant levers colour-coded by function.

**Draws on.** Light-aircraft panels and GA sim hardware (switch panels with a gear lever, gear lamps and a five-position
magneto rotary OFF / R / L / BOTH / START; GA throttle quadrants with black, blue and red levers; trim wheels).

**Palette.** environment #121212, panel #1B1C1E with satin texture, legend #F2F2F2 painted, engrave #9A9A9A. Lever
knobs: throttle #1A1A1A, propeller #2F6FD6, mixture #D23A2E (the light-aircraft convention: black, blue, red).

**Type.** Legends in Barlow Semi Condensed uppercase, gauge numerals in a clean sans (B612). Steam gauges with white
numerals and coloured arcs.

**Default skins.** Momentary `ring-push`; latching `toggle-bat`; selector `rotary-pointer` (magneto `key` when the icon is
ignition); slider `quadrant-knob`; rotary `trim-wheel` for trim, otherwise `encoder`; joystick `yoke`; gauge `steam`; lamp
`annunciator`; guard `flip-red`.

**Signature details.** Gear lever with three green gear lamps above it (lit from game state keys); a magneto key; red
mixture knob always rightmost in a quadrant group.

### 5.4 Heavy equipment cab (excavator, dozer, loader, crane)

**Character.** Built for gloves and vibration. Dark grained plastic consoles, big rubber keys with lamp windows, rocker
banks with ISO machine symbols, pilot joysticks with thumb rollers, hydraulic levers with ball knobs, a rugged colour
display, and safety yellow only where something can hurt you.

**Draws on.** Machine cab consoles and armrests; pilot joysticks with several proportional thumb and finger rollers and
up to seven buttons; the ISO 10968 excavator control pattern (left stick: swing and stick; right stick: boom and bucket);
ISO 6405 operator-control symbols.

**Environment.** Pages read as consoles: a left armrest bay and a right armrest bay (joystick plus key cluster) and a front
dash bay. Bays are recessed panels with rounded ends, drawn from page panel groups (§7).

| Token | Value |
|---|---|
| environment | #16181A |
| panel | #24272A grained |
| cap | #2E3236 rubber, cap-high #3A3F44 |
| legend | #E9ECEF |
| accent (decorative, never a lamp) | machine yellow #F2B400, used for guards, joystick collars, bay edges |
| lamps | amber #FFB300, green #3EDC81, red #FF453A, white #F1F3F5 |

**Type.** Barlow Semi Condensed Bold uppercase for legends; readouts in a 14-segment face for the rugged-display look,
or bold condensed on the colour display panel.

**Default skins.** Momentary `rubber-key`; latching `rocker-lamp`; selector `gated-lever` (forward / neutral / reverse);
radio `annunciator-row`; slider `hydraulic-lever` (bipolar spring) or `hand-throttle` (unipolar); rotary `thumb-roller`;
joystick `pilot-grip`; rocker `rocker-3`; gauge `steam` or `bar`; lamp `led-dot`; guard `hazard`.

**Signature details.**
- Joystick arrows labelled with the motion, not the axis: the profile's `ends` labels (§7) print SWING L / SWING R,
  BOOM DOWN / BOOM UP. Motion follows ISO 10968 in the built-in excavator page.
- Function colour-coding on joystick collars and lever knobs matches the colour of the part on the machine diagram
  (boom, stick, bucket, swing), as ISO-pattern illustrations do.
- One `mushroom` emergency key allowed per page, always red on a yellow collar.
- Haptics are heavier (longer pulse) and visual travel deeper.

**Avoid.** A single maker's yellow-and-black body livery; small text; fine detail that reads as noise on a vibrating mount.

### 5.5 Agriculture (tractor armrest)

A variant of Heavy for tractors and farm machines.

**Character.** A multifunction armrest: a joystick-style drive lever, a hand throttle, a row of colour-coded hydraulic
remote levers, hitch controls, and a terminal screen with a scroll roller and hot keys.

**Specifics.** Hydraulic remote (SCV) levers each get a different collar colour so they can be matched to colour-coded
hoses at the rear. Agricultural standards define colour coding for remote hydraulic services: take the colours from
the standard's table (ISO 15077 Annex C and the BS / ISO remote-hydraulics colour code) rather than inventing a set. A `gated-lever` for F / N / R, a
`hand-throttle`, `thumb-roller` for the terminal. Neutral dark palette; accents come only from the remote colours.

**Avoid.** Any one maker's green, red or blue body colour as the theme accent.

### 5.6 Commercial vehicle (truck and bus)

**Character.** A long working day. Matte charcoal dash, rows of rocker switches with icons and an LED in each, stalks for
retarder and wipers, an analogue cluster with tell-tales, and a calm amber or white backlight at night.

**Draws on.** Truck dash switch panels with rows of rocker keytops grouped by function (lighting, safety, bodywork,
driver assistance), two- and three-position switches that are either fixed or spring-loaded, symbol keytops; the retarder
stalk with numbered positions.

| Token | Value |
|---|---|
| environment | #121416 |
| panel | #1F2225 fine grain |
| cap (rocker) | #2A2D31, cap-high #34383D |
| legend | #EDEFF2 |
| backlight (night) | warm amber #FFB347 or white, user choice |
| lamps | ISO tell-tale colours |

**Type.** Overpass (an open font in the spirit of road-sign lettering) for labels; cluster numerals in Overpass Mono.

**Default skins.** Momentary `rocker-mom`; latching `rocker-lamp`; selector `rotary-pointer` (lights) or `stalk`; slider
`stalk` (retarder) or `pedal`; rotary `encoder`; rocker `rocker-3`; gauge `steam`; lamp `telltale`; guard `flip-clear`.

**Signature details.** Rocker banks aligned in rows of 5 to 10 with a thin divider every group; each rocker's lamp
window on top; trailer and axle icons from the house set; a tachograph-style segment readout for time and speed.

### 5.7 Road car

**Character.** A modern interior: piano black and satin chrome, glass tiles with white backlight, a few satisfying
physical knobs, ISO tell-tales. Quiet, premium, uncluttered.

**Draws on.** Current car interiors, with one deliberate rule from safety testing: Euro NCAP's 2026 protocols expect
indicators, wipers, hazard lights, horn and the emergency call to work through direct physical inputs. In this theme those
functions are always drawn as physical controls (`ring-push`, stalk or rocker skins), never as glass tiles.

| Token | Value |
|---|---|
| environment | #0E0F11 |
| panel | #141518 gloss (piano black) |
| bezel | satin chrome #A7ABB0 |
| legend | #F5F6F7 |
| backlight | white #FFFFFF at night, dimmed |

**Type.** Inter for labels (sentence case), readouts in Inter tabular figures.

**Default skins.** Momentary `glass-tile`; latching `glass-toggle`; selector `gated-lever` (P R N D) or `chrome-dial`;
slider `pedal`; rotary `chrome-dial`; rocker `window-switch`; gauge `steam` with a clean modern dial; lamp `telltale`.

### 5.8 Overland (off-road)

**Character.** Rugged accessory panel bolted into a 4×4: black powder-coated aluminium plate with Torx heads, backlit
laser-etched rocker switches, toggle bats under covers for the winch, an inclinometer and an air-pressure gauge. Sand and
olive are the only decorative tones.

**Draws on.** Off-road switch panels built from automotive rocker switches with laser-etched legends and two lens windows
(top and bottom) for dual LED illumination, available in several backlight colours; winch controls; on-board air.

| Token | Value |
|---|---|
| environment | #121310 |
| panel | #1B1C1A powder coat |
| fasteners | Torx, #5A5C58 |
| legend | backlight colour (user choice: amber #FFB000, red #FF3B30, blue #2F80ED, green #27AE60, white) |
| decorative | sand #C8B48A, olive #5B6B3A (frames and headers only) |

**Type.** Barlow Condensed for legends; Big Shoulders Stencil for page and panel titles only.

**Default skins.** Momentary `ring-push`; latching `rocker-lamp`; selector `rotary-pointer`; slider `hand-throttle`
(winch speed) or `fader`; rotary `encoder`; rocker `rocker-3` (winch in / out); gauge `steam`, `inclinometer`; lamp
`telltale`; guard `hazard` for the winch freespool, `flip-red` for kill switches.

**Signature details.** Every rocker has two lamp windows: top shows the function icon in the backlight colour, bottom
lights when the function is on. Diff locks and air compressor get the bottom lamp in amber.

### 5.9 Motorsport (race car)

**Character.** Purpose-built and loud. Carbon-weave tub, anodised panels, aircraft-style toggles with red missile covers,
illuminated pushbuttons with coloured rings, numbered multi-position rotaries for TC, ABS, map and brake bias, a shift-light
strip and a segment dash.

**Draws on.** GT steering wheels and sim button boxes: rotaries for brake bias, traction control and ABS; buttons for pit
limiter, flash, wipers and engine maps; encoders for dash pages; telemetry-driven LED strips; carbon and aluminium
housings; switches engineered with resistance, detent and travel so they are not hit by accident.

| Token | Value |
|---|---|
| environment | #0B0B0C carbon weave |
| panel | #17181A anodised black, edge #2C2E31 |
| legend | #FFFFFF silkscreen |
| ring lamps | user accent per button (red, amber, green, blue, white) |
| shift lights | green #2EE66B → amber #FFB000 → red #FF2D2D → blue flash #2F7BFF |

**Type.** Titillium Web semibold uppercase for legends; dash in DSEG7 / DSEG14.

**Default skins.** Momentary `ring-push`; latching `toggle-bat`; selector `multi-pos`; radio `lit-row`; slider `pedal`
or `fader`; rotary `multi-pos`; joystick `hat`; rocker `toggle-3`; gauge `segment`, `bar`, `shift-lights`; lamp
`led-dot`; guard `flip-red`.

**Signature details.** Pit limiter `ring-push` flashes its ring while active; multi-position rotaries show their number
in a small window; a shift-light strip can sit above any page (uses `rpm` and a configured shift rpm).

### Future candidates

Rail (driver's desk, notched master controller, AWS / TPWS lamps), marine (helm, twin throttles, nav light panel),
emergency services (light bar and siren control head), space sims (backlit switch matrices). Same anatomy, new skins only
where the catalogue lacks one.

## 6. Environment and lighting

**Panel groups.** A page can carry named rectangles in grid units (`panels`, §7). Each theme draws them its own way:
lightplates with white boundary lines and engraved titles (Airliner), recessed console bays (Heavy, Agriculture), rocker
rows with dividers (Commercial), a single bolted plate (Overland), carbon frames (Motorsport). Controls never overlap a
group's title strip.

**Lighting modes.** Day, dusk and night, chosen automatically (the game's headlights state or time of day when available,
otherwise the phone's dark mode or a manual toggle in the HUD).
- Night darkens panels and lights legends with the theme's backlight; lamps keep their full signal colours and never
  dim below 70% (warning lamps stay conspicuous when panel lighting is low, as on real flight decks).
- Airliner night uses integral lighting (legend glow) plus a soft flood; a "dark cockpit" switch hides unlit
  annunciator legends entirely.
- Contrast rules in §2 are checked in day and night separately.

**Background.** Behind the grid each theme draws its environment: the coaming and glareshield edge (Airliner), cab console
plastic (Heavy), dash grain (Commercial), piano black with a chrome bead (Road car), powder-coat plate (Overland), carbon
(Motorsport). Backgrounds are low-contrast and static: no motion, no parallax.

**Theme follows vehicle (BeamNG).** With the option on, the BeamNG profile picks a theme from the vehicle the mod reports:
cars → Road car, trucks and buses → Commercial, machines → Heavy, off-road builds → Overland, race cars → Motorsport.
This needs a vehicle category in `beamng.features` (the mod can read the vehicle's type and tags from its info file).

## 7. Model changes (profile schema 4)

All optional; a profile without them looks exactly as today.

| Where | Field | Meaning |
|---|---|---|
| Profile | `theme` | theme id (`race`, `airliner`, `ga`, `heavy`, `agriculture`, `commercial`, `roadcar`, `overland`, `motorsport`) |
| Profile | `themeFollowsVehicle` | BeamNG only: pick the theme from the vehicle category |
| Page | `theme` | per-page override (an overhead page and a pedestal page can differ in lighting group) |
| Page | `panels` | list of `{title, x, y, w, h}` panel groups in grid units |
| Control | `skin` | skin id override; null = the theme's default for this control type |
| Control | `legends` | up to two strings for split-legend skins and rocker lamp windows, for example `["FAULT", "ON"]` |
| Control | `ends` | two labels for lever and stick ends, for example `["LOWER", "RAISE"]`; joysticks take four |
| Control | `stations` | named positions for sliders, for example `[{"at":0,"label":"IDLE"},{"at":0.6,"label":"CL"},{"at":1,"label":"TOGA"}]`; drawn and given haptic detents, values unchanged |
| Control | `bands` | gauge range arcs: `[{"from":0,"to":0.8,"color":"green"},...]` in the gauge's displayed units |
| Control | `min`, `max` | gauge scale for round and bar gauges |

`ProfileJson.SCHEMA` goes to 4 when any of these is present. Built-in profiles get themes: MSFS → Airliner (with a GA
variant page), Bulldozer and Utility → Heavy, FS25 → Agriculture, ETS2 and ATS → Commercial, SnowRunner, MudRunner and
Expeditions → Overland, ACC → Motorsport, BeamNG → Race with theme-follows-vehicle on.

## 8. Implementation

- **Theme as data.** `SimTheme` (Kotlin data class in `:app`, ids in `:core`) holds the tokens of §3. Provided through a
  `CompositionLocal` around the play surface only; the house `Race` tokens keep driving everything else.
- **Skins as painters, behaviour shared.** Today each control type has one composable that both handles input and draws.
  Split them: one input layer per control type (the current gesture code: press and release, drag, steps, spring-back,
  guard) and a `Skin` interface that draws from `(control, value, indicator, pressed, enabled, theme, lighting)`. The
  registry maps `(theme, type, skin id)` to a skin. This keeps Invariants 1, 5 and 6 in one place.
- **Drawing.** Compose `Canvas` and `Path` only, with gradients for depth and generated textures (grain, carbon weave,
  brushed metal) rendered once per size into an `ImageBitmap` cache; a `RuntimeShader` path on Android 13+ is optional.
  No raster artwork shipped.
- **Fonts.** All under open font licences (SIL OFL, B612 also EPL): B612, DSEG7 / DSEG14, Overpass, Inter, Titillium Web, Big Shoulders Stencil, Barlow (already in).
  Load per theme on demand to keep the APK small.
- **Haptics.** A small vocabulary per theme: `press`, `release`, `detent`, `gate`, `centre`, `limit`, mapped to
  `HapticFeedbackConstants` with per-theme strength. Sounds are opt-in and short (under 60 ms).
- **Editor.** The canvas draws skins at rest; the inspector gets a Theme section (profile and page) and, per control,
  Skin, Legends, End labels, Stations and Bands. The Windows layout editor on the roadmap uses the same tokens and skin ids.
- **Screenshots.** Extend the Paparazzi shots (one per theme, day and night, phone and tablet) and keep them as review
  references in `docs/images/themes/`.

## 9. Rollout

1. **Foundation.** Split input from drawing; `SimTheme`, the skin registry, schema 4 fields; Race re-expressed as a theme
   with no visual change. Lighting modes.
2. **Three anchor themes.** Heavy (pilot grip, hydraulic lever, rubber key, rocker), Airliner (lit push, split legend,
   thrust lever with stations, segment window, annunciator, panel groups), Motorsport (ring push, toggle bat with missile
   cover, multi-pos, shift lights). These cover most of the skin catalogue.
3. **Round controls and gauges** (roadmap): `steam`, `ring-push`, `chrome-dial`, `rotary-pointer`, `trim-wheel`.
4. **Remaining themes:** GA, Commercial, Road car, Overland, Agriculture. Built-in profiles adopt their themes.
5. **Theme follows vehicle** in BeamNG (mod reports the vehicle category), and the Windows layout editor.

## 10. Review checklist (per theme)

- [ ] Every control type renders in its default skin at 1×1, 2×2 and 4×4 on a phone and a tablet.
- [ ] Rest, pressed, lit, pending, unavailable and guarded states are distinguishable without colour.
- [ ] Contrast checks pass in day and night (§2.4).
- [ ] Spring-return skins return on release and on a cancelled gesture.
- [ ] No logo, product name, maker livery or copied panel layout.
- [ ] A user who knows the real equipment recognises the controls (ask one).
- [ ] Remove animations honoured; haptics still fire.

## 11. Sources

Research for this spec (accessed October 2026). Facts used are paraphrased in the sections above.

- 14 CFR 25.781, cockpit control knob shape: https://www.law.cornell.edu/cfr/text/14/25.781 and 14 CFR 23.781:
  https://www.govinfo.gov/content/pkg/CFR-2002-title14-vol1/pdf/CFR-2002-title14-vol1-sec23-781.pdf (the shapes are in
  the regulations' figures).
- Annunciator colours, dark cockpit, dimming: https://flyawaysimulation.com/ask/answers/annunciator-lights-cockpit-backlighting/
- 737 lightplate colours (FS 36440 and supplier greys): https://www.flaps2approach.com/journal/2018/2/1/differences-in-colour-manufacturer-and-layout-in-the-center.html
- Airbus FCU knobs, managed and selected display: https://docs.flybywiresim.com/pilots-corner/a32nx/a32nx-briefing/flight-deck/glareshield/fcu/
- Lighted pushbutton legends (hidden until lit, colours, night matching): https://www.korry.com/Content/SiteDocuments/389%20Switch_Large%20Cap_Brochure.pdf
- Airliner throttle quadrant detents and reverse: https://aerosoft.com/en/shop/hardware/throttles/3123/thrustmaster-tca-quadrant-airbus-edition
- GA throttle quadrant, autopilot, annunciators, gear and flap levers, trim wheel: https://fselite.net/content/honeycomb-aeronautical-bravo-throttle-quadrant-first-look/
- GA switch panel, gear lever and magneto rotary: https://secure.logitech.com/en-ca/shop/p/flight-simulator-switch-panel
- Airbus-style EFIS sim panel (metal caps, segment LCD, pull-up detent): https://msfsaddons.com/2024/10/01/winwing-announces-full-size-airbus-efis-panel-for-home-cockpit-enthusiasts/
- Excavator control patterns (ISO 10968 / SAE J1177): https://en.wikipedia.org/wiki/Excavator_controls
- Machine joystick rollers and buttons, hand support: https://engcon.com/en/optional-extras/joysticks-mig2.html
- ISO 6405 symbols for earth-moving machinery: https://shop-checkout.bsigroup.com/products/earth-moving-machinery-symbols-for-operator-controls-and-other-displays-common-symbols-2
- ISO 15077 agricultural operator controls (colour coding annex): https://webstore.ansi.org/preview-pages/ISO/preview_ISO+15077-2020.pdf
- Colour coding of tractor remote hydraulics (BS / ISO): https://knowledge.bsigroup.com/products/hydraulic-equipment-for-agricultural-machinery-method-for-colour-coding-of-remote-hydraulic-power-services-and-controls-on-agricultural-tractors
- Tractor armrest console (display, roller, hot keys): https://www.fwi.co.uk/machinery/video-john-deere-s-commandarm-control-console
- Truck dash switch keytops and switch types: https://bodybuilder.scania.com/content/dam/bodybuilder/tbb-files/scania-parts-for-bodybuilding/Switches_and_driver_information.pdf
- Off-road rocker switches, laser etching, dual lens windows, backlight colours: https://rockerswitchpros.com/?p=131031
- GT wheel function placement: https://www.fanatec.com/us/en/explorer/games/gaming-tips/configure-your-steering-wheel-for-gt-racing/
- Button box design (detent, travel, materials, LED telemetry): https://www.apexsimracing.com/blogs/sim-racing-blog/makes-great-button-box
- Euro NCAP 2026 physical controls: https://www.automotiveworld.com/news/euro-ncap-results-reward-physical-buttons-in-2026-tests/

## What 1.2 built

Everything above, with these differences:

- **Not built yet:** optional sounds (§3, Feel), the `hat` joystick skin, the `inclinometer` gauge (the BeamNG mod does not
  report roll and pitch yet), the master-warning variant (only `master` caution exists), and a per-control backlight colour
  choice for Overland and Commercial (each theme has one backlight).
- **Seven-segment readouts are drawn**, not set in the DSEG font: the app draws the segments itself (digits and the letters a
  seven-segment display can show), so no font file is needed.
- **Road car labels** use the system sans-serif (Roboto on most phones) instead of Inter.
- **Haptics** use three weights per theme (light, firm, heavy) instead of six separate events; slider stations, notches and
  the bipolar centre still tick.
- **Night lighting** is chosen in the play screen's header (automatic, day, night). Automatic follows the game's headlights
  when the game reports them; otherwise it stays on day.
- **Theme follows vehicle**: the mod classifies the vehicle as car, truck, bus, machine, tractor, offroad, race, trailer or prop
  from the words in its info files (`Type`, `Body Style`, `Config Type`, names) and sends it with the vehicle's features.
- Fonts shipped: B612 (airliner, light-aircraft readouts), Barlow Semi Condensed (machines, light aircraft, overland),
  Overpass (trucks), Titillium Web (motorsport), Big Shoulders Stencil (overland panel titles), Barlow Condensed (house). All
  under the SIL Open Font License; notices in `android/FONT_LICENSE_THEMES_OFL.txt`.

Reference screenshots are in [images/themes](images/themes).

