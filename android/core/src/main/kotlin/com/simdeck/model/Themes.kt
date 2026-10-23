package com.simdeck.model

/**
 * Equipment themes and control skins (docs/THEMES.md). Ids are stable strings stored in profiles; the app owns how
 * each one looks. A theme changes how controls look and feel, never what they send.
 */
object Themes {
    const val RACE = "race"
    const val AIRLINER = "airliner"
    const val GA = "ga"
    const val HEAVY = "heavy"
    const val AGRICULTURE = "agriculture"
    const val COMMERCIAL = "commercial"
    const val ROADCAR = "roadcar"
    const val OVERLAND = "overland"
    const val MOTORSPORT = "motorsport"

    val all = listOf(RACE, AIRLINER, GA, HEAVY, AGRICULTURE, COMMERCIAL, ROADCAR, OVERLAND, MOTORSPORT)

    fun displayName(id: String?): String = when (id) {
        AIRLINER -> "Airliner"
        GA -> "Light aircraft"
        HEAVY -> "Heavy equipment"
        AGRICULTURE -> "Agriculture"
        COMMERCIAL -> "Truck & bus"
        ROADCAR -> "Road car"
        OVERLAND -> "Overland"
        MOTORSPORT -> "Motorsport"
        else -> "Race (house)"
    }

    /** The theme a BeamNG vehicle category asks for (the mod reports the category with the vehicle's features). */
    fun forVehicle(category: String?): String? = when (category?.lowercase()) {
        "car" -> ROADCAR
        "truck", "bus" -> COMMERCIAL
        "machine" -> HEAVY
        "tractor" -> AGRICULTURE
        "offroad" -> OVERLAND
        "race" -> MOTORSPORT
        "plane", "aircraft" -> GA
        else -> null
    }

    /** The theme in effect on [page] of [profile], given the vehicle category when the profile follows the vehicle. */
    fun resolve(profile: Profile, page: Page?, vehicleCategory: String? = null): String {
        page?.theme?.let { if (it in all) return it }
        if (profile.themeFollowsVehicle) forVehicle(vehicleCategory)?.let { return it }
        return profile.theme?.takeIf { it in all } ?: RACE
    }
}

/** Skin ids per control type (docs/THEMES.md section 4). The first of each list is the house look. */
object Skins {
    val byType: Map<ControlType, List<String>> = mapOf(
        ControlType.MOMENTARY to listOf("cap", "lit-push", "ring-push", "rubber-key", "rocker-mom", "glass-tile", "mushroom"),
        ControlType.LATCHING to listOf("cap", "toggle-bat", "lit-push-split", "rocker-lamp", "push-pull", "key", "glass-toggle", "ring-push", "rubber-key", "lit-push", "gear-lever"),
        ControlType.SWITCH to listOf("slide-select", "rotary-pointer", "gated-lever"),
        ControlType.RADIO to listOf("cap", "lit-row", "annunciator-row", "glass-tile"),
        ControlType.SLIDER to listOf("fader", "hydraulic-lever", "thrust-lever", "quadrant-knob", "hand-throttle", "pedal", "stalk"),
        ControlType.ROTARY to listOf("encoder", "concentric", "multi-pos", "trim-wheel", "thumb-roller", "chrome-dial"),
        ControlType.JOYSTICK to listOf("gate", "pilot-grip", "sidestick", "yoke", "hat"),
        ControlType.ROCKER to listOf("rocker-3", "toggle-3", "window-switch"),
        ControlType.GAUGE to listOf("readout", "steam", "segment", "bar", "tape", "shift-lights", "inclinometer"),
        ControlType.INDICATOR to listOf("telltale", "annunciator", "master", "master-warning", "led-dot"),
        ControlType.PAGE to listOf("cap", "page-tab"),
    )

    private val names = mapOf(
        "cap" to "Cap", "lit-push" to "Lit pushbutton", "ring-push" to "Round push, lit ring", "rubber-key" to "Rubber key",
        "rocker-mom" to "Rocker, springs back", "glass-tile" to "Glass tile", "mushroom" to "Mushroom (emergency)",
        "toggle-bat" to "Toggle switch", "lit-push-split" to "Lit pushbutton, split legend", "rocker-lamp" to "Rocker with lamps",
        "push-pull" to "Push-pull knob", "gear-lever" to "Gear lever (wheel knob)", "key" to "Key switch", "glass-toggle" to "Glass toggle",
        "slide-select" to "Slide selector", "rotary-pointer" to "Rotary pointer", "gated-lever" to "Gated lever",
        "lit-row" to "Lit buttons", "annunciator-row" to "Keys with lamps",
        "fader" to "Fader", "hydraulic-lever" to "Hydraulic lever", "thrust-lever" to "Thrust lever", "quadrant-knob" to "Quadrant lever",
        "hand-throttle" to "Hand throttle", "pedal" to "Pedal", "stalk" to "Column stalk",
        "encoder" to "Encoder knob", "concentric" to "Concentric knob", "multi-pos" to "Numbered rotary", "trim-wheel" to "Trim wheel",
        "thumb-roller" to "Thumb roller", "chrome-dial" to "Chrome dial",
        "gate" to "Round gate", "pilot-grip" to "Pilot joystick", "sidestick" to "Sidestick", "yoke" to "Yoke",
        "rocker-3" to "Rocker, 3 position", "toggle-3" to "Toggle, 3 position", "window-switch" to "Window switch",
        "readout" to "Number", "steam" to "Dial", "segment" to "Segment display", "bar" to "Bar graph", "tape" to "Tape",
        "shift-lights" to "Shift lights", "telltale" to "Tell-tale", "annunciator" to "Annunciator", "master" to "Master caution",
        "master-warning" to "Master warning", "inclinometer" to "Inclinometer", "hat" to "Four-way hat",
        "led-dot" to "LED", "page-tab" to "Panel tab",
    )

    fun displayName(id: String): String = names[id] ?: id

    /** Default skin per control type for each theme. */
    val defaults: Map<String, Map<ControlType, String>> = mapOf(
        Themes.RACE to emptyMap(),
        Themes.AIRLINER to mapOf(
            ControlType.MOMENTARY to "lit-push", ControlType.LATCHING to "lit-push-split", ControlType.SWITCH to "rotary-pointer",
            ControlType.RADIO to "lit-row", ControlType.SLIDER to "thrust-lever", ControlType.ROTARY to "concentric",
            ControlType.JOYSTICK to "sidestick", ControlType.ROCKER to "toggle-3", ControlType.GAUGE to "segment",
            ControlType.INDICATOR to "annunciator", ControlType.PAGE to "page-tab",
        ),
        Themes.GA to mapOf(
            ControlType.MOMENTARY to "ring-push", ControlType.LATCHING to "toggle-bat", ControlType.SWITCH to "rotary-pointer",
            ControlType.RADIO to "lit-row", ControlType.SLIDER to "quadrant-knob", ControlType.ROTARY to "encoder",
            ControlType.JOYSTICK to "yoke", ControlType.ROCKER to "toggle-3", ControlType.GAUGE to "steam",
            ControlType.INDICATOR to "annunciator", ControlType.PAGE to "page-tab",
        ),
        Themes.HEAVY to mapOf(
            ControlType.MOMENTARY to "rubber-key", ControlType.LATCHING to "rocker-lamp", ControlType.SWITCH to "gated-lever",
            ControlType.RADIO to "annunciator-row", ControlType.SLIDER to "hydraulic-lever", ControlType.ROTARY to "thumb-roller",
            ControlType.JOYSTICK to "pilot-grip", ControlType.ROCKER to "rocker-3", ControlType.GAUGE to "steam",
            ControlType.INDICATOR to "led-dot", ControlType.PAGE to "page-tab",
        ),
        Themes.AGRICULTURE to mapOf(
            ControlType.MOMENTARY to "rubber-key", ControlType.LATCHING to "rocker-lamp", ControlType.SWITCH to "gated-lever",
            ControlType.RADIO to "annunciator-row", ControlType.SLIDER to "hydraulic-lever", ControlType.ROTARY to "thumb-roller",
            ControlType.JOYSTICK to "pilot-grip", ControlType.ROCKER to "rocker-3", ControlType.GAUGE to "bar",
            ControlType.INDICATOR to "led-dot", ControlType.PAGE to "page-tab",
        ),
        Themes.COMMERCIAL to mapOf(
            ControlType.MOMENTARY to "rocker-mom", ControlType.LATCHING to "rocker-lamp", ControlType.SWITCH to "rotary-pointer",
            ControlType.RADIO to "lit-row", ControlType.SLIDER to "stalk", ControlType.ROTARY to "encoder",
            ControlType.JOYSTICK to "gate", ControlType.ROCKER to "rocker-3", ControlType.GAUGE to "steam",
            ControlType.INDICATOR to "telltale", ControlType.PAGE to "page-tab",
        ),
        Themes.ROADCAR to mapOf(
            ControlType.MOMENTARY to "glass-tile", ControlType.LATCHING to "glass-toggle", ControlType.SWITCH to "gated-lever",
            ControlType.RADIO to "glass-tile", ControlType.SLIDER to "pedal", ControlType.ROTARY to "chrome-dial",
            ControlType.JOYSTICK to "gate", ControlType.ROCKER to "window-switch", ControlType.GAUGE to "steam",
            ControlType.INDICATOR to "telltale", ControlType.PAGE to "page-tab",
        ),
        Themes.OVERLAND to mapOf(
            ControlType.MOMENTARY to "ring-push", ControlType.LATCHING to "rocker-lamp", ControlType.SWITCH to "rotary-pointer",
            ControlType.RADIO to "annunciator-row", ControlType.SLIDER to "hand-throttle", ControlType.ROTARY to "encoder",
            ControlType.JOYSTICK to "gate", ControlType.ROCKER to "rocker-3", ControlType.GAUGE to "steam",
            ControlType.INDICATOR to "telltale", ControlType.PAGE to "page-tab",
        ),
        Themes.MOTORSPORT to mapOf(
            ControlType.MOMENTARY to "ring-push", ControlType.LATCHING to "toggle-bat", ControlType.SWITCH to "rotary-pointer",
            ControlType.RADIO to "lit-row", ControlType.SLIDER to "pedal", ControlType.ROTARY to "multi-pos",
            ControlType.JOYSTICK to "gate", ControlType.ROCKER to "toggle-3", ControlType.GAUGE to "segment",
            ControlType.INDICATOR to "led-dot", ControlType.PAGE to "page-tab",
        ),
    )

    /** Functions Euro NCAP expects on physical controls: the road-car theme never draws these as glass. */
    private val physicalInRoadCar = setOf("signal_left", "signal_right", "hazard", "horn", "wiper")

    /** The skin [c] is drawn with under [theme]. */
    fun resolve(c: Control, theme: String): String {
        val valid = byType[c.type] ?: return "cap"
        c.skin?.let { if (it in valid) return it }
        // A spring-centred bipolar slider is a valve lever in every theme that has no lever of its own.
        if (c.type == ControlType.SLIDER && c.range == AxisRange.BIPOLAR && c.springReturn && theme in setOf(Themes.RACE, Themes.HEAVY, Themes.AGRICULTURE, Themes.OVERLAND, Themes.COMMERCIAL)) {
            return "hydraulic-lever"
        }
        if (theme == Themes.ROADCAR && c.icon in physicalInRoadCar && c.type in setOf(ControlType.MOMENTARY, ControlType.LATCHING)) return "ring-push"
        // Shape-coded levers on a flight deck: flaps, gear, propeller and mixture get their quadrant knobs.
        if (theme == Themes.AIRLINER && c.type == ControlType.SLIDER && c.range == AxisRange.UNIPOLAR) {
            val n = (c.label + " " + (c.icon ?: "")).lowercase()
            if (listOf("flap", "gear", "prop", "mix").any { it in n }) return "quadrant-knob"
        }
        // Sliders on a bipolar spring axis in a flight deck are trim/rudder-like: a quadrant lever reads wrong.
        if (theme in setOf(Themes.AIRLINER, Themes.GA) && c.type == ControlType.SLIDER && c.range == AxisRange.BIPOLAR) return "fader"
        return defaults[theme]?.get(c.type) ?: valid.first()
    }
}
