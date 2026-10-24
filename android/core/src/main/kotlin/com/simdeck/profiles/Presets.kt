package com.simdeck.profiles

import com.simdeck.model.Accent
import com.simdeck.model.Action
import com.simdeck.model.AxisRange
import com.simdeck.model.Control
import com.simdeck.model.ControlType
import com.simdeck.model.GameAction
import com.simdeck.model.GameId
import com.simdeck.model.KeyTrigger
import com.simdeck.model.Page
import com.simdeck.model.Position
import com.simdeck.model.Profile
import com.simdeck.model.Readings
import com.simdeck.model.VehicleFeature
import com.simdeck.model.VehicleFeaturePages

/**
 * A ready-made control: label, icon, type, action, lamp key and size already filled in, so adding a function is one tap
 * (like the first SimDeck's function presets). [control] is a template: [Presets.place] gives it a new id and a free spot.
 */
data class Preset(
    val id: String,
    val name: String,
    /** Group shown in the picker, e.g. "Lights & signals" or a built-in profile's page name. */
    val category: String,
    /** Where it comes from: "Any game", a built-in profile's name, "BeamNG game actions", one of your profiles. */
    val source: String,
    /** Games it suits; empty = any game. */
    val games: Set<GameId> = emptySet(),
    val description: String = "",
    val control: Control,
)

object Presets {
    /** Gamepad button number that [place] replaces with the next free one. */
    const val NEXT_BUTTON = 0
    /** Gamepad axis name that [place] replaces with the next free axis. */
    const val NEXT_AXIS = "next"

    private val AXES = listOf("X", "Y", "Z", "RX", "RY", "RZ", "SL0", "SL1")

    private fun t(type: ControlType, label: String, icon: String?, w: Int = 1, h: Int = 1, accent: Accent = Accent.NEUTRAL, action: Action = Action.None) =
        Control(id = "", type = type, label = label, icon = icon, w = w, h = h, accent = accent, action = action)

    private val nextBtn = Action.VJoyButton(1, NEXT_BUTTON)
    private fun key(vararg k: String, trigger: KeyTrigger = KeyTrigger.HOLD) = Action.Key(k.toList(), trigger)

    /** Presets that work in any game: gamepad controls numbered for you, common keys, game readouts, page keys. */
    val generic: List<Preset> by lazy {
        val any = "Any game"
        fun p(id: String, cat: String, desc: String, c: Control) = Preset("generic.$id", c.label, cat, any, emptySet(), desc, c)
        listOf(
            // Gamepad: the next free button / axis is filled in when placed.
            p("gp_button", "Gamepad (bind in the game)", "On while held. Gets the next free gamepad button.", t(ControlType.MOMENTARY, "Button", "gamepad", action = nextBtn)),
            p("gp_toggle", "Gamepad (bind in the game)", "Tap on, tap off. Gets the next free gamepad button.", t(ControlType.LATCHING, "Toggle", "power", accent = Accent.AMBER, action = nextBtn)),
            p("gp_rocker", "Gamepad (bind in the game)", "Hold up or down. Two free gamepad buttons.",
                t(ControlType.ROCKER, "Rocker", "boom_extend", h = 2, accent = Accent.CYAN).copy(cw = nextBtn, ccw = nextBtn)),
            p("gp_dial", "Gamepad (bind in the game)", "Turn for steps. Two free gamepad buttons.",
                t(ControlType.ROTARY, "Dial", "cruise", 2, 2, Accent.CYAN).copy(cw = nextBtn, ccw = nextBtn)),
            p("gp_selector3", "Gamepad (bind in the game)", "Three positions, a free gamepad button each.",
                t(ControlType.SWITCH, "Selector", "gearbox", 3, 1, Accent.CYAN).copy(positions = listOf(Position("1", nextBtn), Position("2", nextBtn), Position("3", nextBtn)))),
            p("gp_throttle", "Gamepad (bind in the game)", "A lever that stays where you leave it. The next free axis.",
                t(ControlType.SLIDER, "Throttle", "speed", 1, 4, Accent.GREEN, Action.VJoyAxis(1, NEXT_AXIS, AxisRange.UNIPOLAR)).copy(range = AxisRange.UNIPOLAR)),
            p("gp_lever", "Gamepad (bind in the game)", "A spring-centred lever, like a hydraulic valve. The next free axis.",
                t(ControlType.SLIDER, "Lever", "boom", 1, 4, Accent.AMBER, Action.VJoyAxis(1, NEXT_AXIS)).copy(range = AxisRange.BIPOLAR, springReturn = true)),
            p("gp_stick", "Gamepad (bind in the game)", "Two axes on one stick. The next two free axes.",
                t(ControlType.JOYSTICK, "Joystick", "swing", 2, 2, Accent.AMBER, Action.VJoyAxis(1, NEXT_AXIS)).copy(actionY = Action.VJoyAxis(1, NEXT_AXIS))),
            // Keys most games share.
            p("key_esc", "Common keys", "Escape: menu or pause in most games.", t(ControlType.MOMENTARY, "Menu", "menu", action = key("ESC", trigger = KeyTrigger.PRESS))),
            p("key_enter", "Common keys", "Enter.", t(ControlType.MOMENTARY, "Enter", "arrow_right", action = key("ENTER", trigger = KeyTrigger.PRESS))),
            p("key_map", "Common keys", "M opens the map in many games.", t(ControlType.MOMENTARY, "Map", "map", accent = Accent.CYAN, action = key("M", trigger = KeyTrigger.PRESS))),
            p("key_screenshot", "Common keys", "F12 takes a screenshot in Steam games.", t(ControlType.MOMENTARY, "Screenshot", "screenshot", action = key("F12", trigger = KeyTrigger.PRESS))),
            p("key_ptt", "Common keys", "Push to talk on Caps Lock (change the key to yours).", t(ControlType.MOMENTARY, "Talk", "mic", accent = Accent.GREEN, action = key("CAPSLOCK"))),
            p("key_camera", "Common keys", "C changes the camera in many games.", t(ControlType.MOMENTARY, "Camera", "camera_cycle", action = key("C", trigger = KeyTrigger.PRESS))),
            p("key_arrows_ud", "Common keys", "Arrow up / down.", t(ControlType.ROCKER, "Up / down", "arrow_up", h = 2).copy(cw = key("UP"), ccw = key("DOWN"))),
            p("key_arrows_lr", "Common keys", "Arrow right / left.", t(ControlType.ROCKER, "Left / right", "arrow_right", w = 2).copy(cw = key("RIGHT"), ccw = key("LEFT"))),
            // Readouts from the game (BeamNG mod or OutGauge).
            p("g_speed", "Readouts and lamps", "Speed in km/h.", t(ControlType.GAUGE, "Speed", null, 2, 1, Accent.CYAN).copy(feedbackKey = "speed", scale = 3.6, unit = "km/h")),
            p("g_speed_mph", "Readouts and lamps", "Speed in mph.", t(ControlType.GAUGE, "Speed", null, 2, 1, Accent.CYAN).copy(feedbackKey = "speed", scale = 2.237, unit = "mph")),
            p("g_rpm", "Readouts and lamps", "Engine rpm.", t(ControlType.GAUGE, "RPM", null, 2, 1, Accent.CYAN).copy(feedbackKey = "rpm")),
            p("g_gear", "Readouts and lamps", "Current gear.", t(ControlType.GAUGE, "Gear", null, accent = Accent.CYAN).copy(feedbackKey = "gear")),
            p("g_fuel", "Readouts and lamps", "Fuel level in percent.", t(ControlType.GAUGE, "Fuel", null, accent = Accent.AMBER).copy(feedbackKey = "fuel", scale = 100.0, unit = "%")),
            p("g_shift", "Readouts and lamps", "Shift lights from the rpm.", t(ControlType.GAUGE, "Shift lights", null, 4, 1, Accent.RED).copy(feedbackKey = "rpm", skin = "shift-lights", max = 7000.0)),
            p("l_pbrake", "Readouts and lamps", "Lights while the parking brake is on.", t(ControlType.INDICATOR, "Park brake", "parking_brake", accent = Accent.RED).copy(feedbackKey = "parkingbrake")),
            p("l_high", "Readouts and lamps", "High beam tell-tale.", t(ControlType.INDICATOR, "High beam", "high_beam", accent = Accent.BLUE).copy(feedbackKey = "lights.high")),
            p("l_abs", "Readouts and lamps", "ABS working.", t(ControlType.INDICATOR, "ABS", "abs", accent = Accent.AMBER).copy(feedbackKey = "abs.active")),
            p("l_tc", "Readouts and lamps", "Traction control working.", t(ControlType.INDICATOR, "TC", "tcs", accent = Accent.AMBER).copy(feedbackKey = "tcs.intervening")),
            p("l_left", "Readouts and lamps", "Left signal tell-tale.", t(ControlType.INDICATOR, "Left", "signal_left", accent = Accent.GREEN).copy(feedbackKey = "signal.left")),
            p("l_right", "Readouts and lamps", "Right signal tell-tale.", t(ControlType.INDICATOR, "Right", "signal_right", accent = Accent.GREEN).copy(feedbackKey = "signal.right")),
            p("g_roll", "Readouts and lamps", "Side-to-side tilt, from the BeamNG mod.", t(ControlType.GAUGE, "Roll", null, 2, 2, Accent.AMBER).copy(feedbackKey = "roll", skin = "inclinometer", unit = "°", min = -45.0, max = 45.0)),
            p("g_pitch", "Readouts and lamps", "Nose up or down, from the BeamNG mod.", t(ControlType.GAUGE, "Pitch", null, 2, 2, Accent.AMBER).copy(feedbackKey = "pitch", skin = "inclinometer", unit = "°", min = -45.0, max = 45.0)),
            p("gp_hat", "Gamepad (bind in the game)", "A four-way hat for camera or trim: full deflection on two free axes.",
                t(ControlType.JOYSTICK, "Hat", "camera", 2, 2, Accent.CYAN, Action.VJoyAxis(1, NEXT_AXIS)).copy(actionY = Action.VJoyAxis(1, NEXT_AXIS), skin = "hat")),
            // Pages.
            p("page_next", "Pages", "Goes to the next page.", t(ControlType.PAGE, "Next page", "page_next").copy(pageTarget = "next")),
            p("page_prev", "Pages", "Goes to the previous page.", t(ControlType.PAGE, "Previous", "page_prev").copy(pageTarget = "prev")),
        )
    }

    /** Every control of [p] as a preset, grouped by page; repeats (same label, type and action) are listed once. */
    fun fromProfile(p: Profile, source: String = p.name): List<Preset> {
        val seen = HashSet<String>()
        return p.pages.filter { it.auto == null }.flatMap { pg ->
            pg.controls.mapNotNull { c ->
                val sig = listOf(c.type, c.label, c.action, c.cw, c.ccw, c.positions.map { it.action }, c.feedbackKey).toString()
                if (!seen.add(sig)) return@mapNotNull null
                Preset("${p.id}/${pg.id}/${c.id}", c.label, pg.name, source, if (p.game == GameId.CUSTOM) emptySet() else setOf(p.game), "", c)
            }
        }
    }

    /** Every live reading from the BeamNG mod as a gauge (or a lamp for on/off values), grouped like the reading picker. */
    val readings: List<Preset> by lazy {
        Readings.known.map { r ->
            Preset("reading.${r.key}", r.title, "Gauges: ${r.group}", "BeamNG readings", setOf(GameId.BEAMNG),
                "Shows ${r.key}" + if (r.unit.isNotBlank()) " in ${r.unit}" else "", Readings.control(r))
        }
    }

    /** The built-in library: generic presets, readings, then every built-in profile's controls. */
    val builtIn: List<Preset> by lazy { generic + readings + BuiltInProfiles.all.flatMap { fromProfile(it) } }

    /** A BeamNG input action as a one-tap button (axis actions become a spring-centred slider). */
    fun gameAction(a: GameAction): Preset {
        val c = if (a.axis) t(ControlType.SLIDER, a.title.take(24), null, 2, 1, Accent.CYAN, Action.BeamNG("action." + a.name))
            .copy(range = AxisRange.BIPOLAR, springReturn = true, vertical = false)
        else t(ControlType.MOMENTARY, a.title.take(24), VehicleFeaturePages.iconFor(VehicleFeature("action." + a.name, a.title)), action = Action.BeamNG("action." + a.name))
        return Preset("bng.action.${a.name}", c.label, if (a.vehicle) "This vehicle" else a.category.ifBlank { "Game" }, "BeamNG game actions",
            setOf(GameId.BEAMNG), "action.${a.name}", c)
    }

    /** A function of the BeamNG vehicle being driven, as a control. */
    fun vehicleFeature(f: VehicleFeature): Preset {
        val (pg, _) = VehicleFeaturePages.controls(Page("v", "v", columns = 4, rows = 2), listOf(f))
        val c = pg.controls.single().copy(id = "", x = 0, y = 0)
        return Preset("bng.feature.${f.id}", f.title, "This vehicle", "BeamNG game actions", setOf(GameId.BEAMNG), f.id, c)
    }

    /** Presets matching [query] (name, category, source, description, action), case-insensitive. */
    fun search(list: List<Preset>, query: String): List<Preset> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return list
        return list.filter { p ->
            q in p.name.lowercase() || q in p.category.lowercase() || q in p.source.lowercase() || q in p.description.lowercase() ||
                q in (p.control.action as? Action.BeamNG)?.command.orEmpty().lowercase()
        }
    }

    // ---- placing a template on a page ------------------------------------------------------------

    private fun buttons(a: Action): List<Int> = (a as? Action.VJoyButton)?.takeIf { it.device == 1 }?.let { listOf(it.button) } ?: emptyList()
    private fun usedButtons(p: Profile): MutableSet<Int> = p.pages.flatMap { it.controls }.flatMap { c ->
        buttons(c.action) + buttons(c.cw) + buttons(c.ccw) + c.positions.flatMap { buttons(it.action) }
    }.toMutableSet()

    private fun usedAxes(p: Profile): MutableSet<String> = p.pages.flatMap { it.controls }.flatMap { c ->
        listOf(c.action, c.actionY).mapNotNull { (it as? Action.VJoyAxis)?.takeIf { a -> a.device == 1 }?.axis }
    }.toMutableSet()

    /** Replaces the "next free" placeholders of [c] with real gamepad buttons and axes not used in [p]. */
    fun number(c: Control, p: Profile): Control {
        val btns = usedButtons(p)
        val axes = usedAxes(p)
        fun nb(): Int { val n = (1..128).first { it !in btns }; btns += n; return n }
        fun na(): String { val a = AXES.firstOrNull { it !in axes } ?: "SL0"; axes += a; return a }
        fun fix(a: Action): Action = when {
            a is Action.VJoyButton && a.button == NEXT_BUTTON -> a.copy(button = nb())
            a is Action.VJoyAxis && a.axis == NEXT_AXIS -> a.copy(axis = na())
            else -> a
        }
        return c.copy(action = fix(c.action), actionY = fix(c.actionY), cw = fix(c.cw), ccw = fix(c.ccw), positions = c.positions.map { it.copy(action = fix(it.action)) })
    }

    /** First spot on [page] where a [w] x [h] control fits, scanning rows; null when none. */
    fun freeSpot(page: Page, w: Int, h: Int): Pair<Int, Int>? {
        if (w > page.columns || h > page.rows) return null
        for (y in 0..page.rows - h) for (x in 0..page.columns - w) {
            val clash = page.controls.any { c -> x < c.x + c.w && x + w > c.x && y < c.y + c.h && y + h > c.y }
            if (!clash) return x to y
        }
        return null
    }

    /**
     * Places a copy of [template] on page [pageIndex] of [p]: new id, gamepad numbers filled in, at the first free spot in
     * its own size, else in the smallest size that still works. Null when the page has no room.
     */
    fun place(p: Profile, pageIndex: Int, template: Control): Pair<Profile, String>? {
        val page = p.pages.getOrNull(pageIndex) ?: return null
        if (page.auto != null) return null
        val numbered = number(template, p)
        val (minW, minH) = LayoutOps.minSpan(numbered)
        val sizes = listOf(numbered.w to numbered.h, minOf(numbered.w, page.columns) to minOf(numbered.h, page.rows), minW to minH).distinct()
        for ((w, h) in sizes) {
            val spot = freeSpot(page, w, h) ?: continue
            val c = numbered.copy(id = LayoutOps.newControlId(), x = spot.first, y = spot.second, w = w, h = h)
            val placed = LayoutOps.clamp(c, page)
            return p.copy(pages = p.pages.mapIndexed { i, pg -> if (i == pageIndex) pg.copy(controls = pg.controls + placed) else pg }) to placed.id
        }
        return null
    }

    /** Adds [page] (copied from anywhere) as a new last page of [p], with fresh page and control ids. */
    fun pastePage(p: Profile, page: Page): Profile {
        val copy = page.copy(
            id = LayoutOps.newPageId(),
            name = page.name.let { n -> if (p.pages.any { it.name == n }) "$n copy" else n },
            controls = page.controls.map { it.copy(id = LayoutOps.newControlId()) },
        )
        return LayoutOps.sanitize(p.copy(pages = p.pages + copy)).copy(id = p.id, builtIn = p.builtIn)
    }
}
