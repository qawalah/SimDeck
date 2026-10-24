package com.simdeck.model

import kotlin.math.abs
import kotlin.math.round

/**
 * One live value the phone can show (SimDeck 1.4): a game-state key with a readable title, a group for the picker, and how
 * to show it (unit after [scale], decimals, dial range and coloured bands). [lamp] values are on/off and suit a lamp.
 */
data class Reading(
    val key: String,
    val title: String,
    val group: String,
    val unit: String = "",
    val scale: Double = 1.0,
    val decimals: Int = 0,
    val min: Double? = null,
    val max: Double? = null,
    val bands: List<Band> = emptyList(),
    val lamp: Boolean = false,
    val icon: String? = null,
    /** Extra search words. */
    val keywords: String = "",
)

/** A reading the BeamNG mod says the current vehicle has ("beamng.readings"), in the game's own unit. */
data class ReadingInfo(val key: String, val group: String, val title: String, val unit: String = "")

object Readings {
    /** Picker group order. */
    val GROUPS = listOf(
        "Driving", "Engine", "Fuel & energy", "Drivetrain", "Driver inputs", "Motion", "Brakes", "Tyres", "Warnings",
        "Lights & signals", "Systems & locks", "Game", "Hydraulics", "Vehicle has", "All signals", "Other",
    )

    private fun r(key: String, title: String, group: String, unit: String = "", scale: Double = 1.0, decimals: Int = 0,
                  min: Double? = null, max: Double? = null, bands: List<Band> = emptyList(), icon: String? = null, keywords: String = "") =
        Reading(key, title, group, unit, scale, decimals, min, max, bands, false, icon, keywords)

    private fun lamp(key: String, title: String, group: String, icon: String? = null, keywords: String = "") =
        Reading(key, title, group, lamp = true, icon = icon, keywords = keywords)

    private val temp = listOf(Band(0.0, 60.0, "blue"), Band(60.0, 115.0, "green"), Band(130.0, 160.0, "red"))

    /** Everything the BeamNG mod reports by name (protocol/PROTOCOL.md, State keys and Readings). */
    val known: List<Reading> = listOf(
        r("speed", "Speed", "Driving", "km/h", 3.6, 0, 0.0, 240.0, icon = "speed", keywords = "kmh velocity"),
        r("rpm", "Engine speed", "Driving", "rpm", 1.0, 0, 0.0, 8000.0, icon = "engine", keywords = "revs tachometer"),
        r("gear", "Gear", "Driving", icon = "gearbox"),
        r("gearbox.mode", "Gearbox mode (P R N D S M)", "Driving", icon = "gear_auto"),
        r("cruise.speed", "Cruise set speed", "Driving", "km/h", 3.6, 0, icon = "cruise"),
        r("oil.temp", "Oil temperature", "Engine", "°C", 1.0, 0, 40.0, 160.0, temp, "temp", "oil temp"),
        r("coolant.temp", "Coolant temperature", "Engine", "°C", 1.0, 0, 40.0, 140.0,
            listOf(Band(0.0, 60.0, "blue"), Band(60.0, 105.0, "green"), Band(115.0, 140.0, "red")), "temp", "water radiator temp"),
        r("boost", "Boost", "Engine", "psi", 1.0, 1, -15.0, 30.0, icon = "bolt", keywords = "turbo supercharger pressure"),
        r("boost.max", "Boost maximum", "Engine", "psi", 1.0, 1, icon = "bolt", keywords = "turbo"),
        r("engine.load", "Engine load", "Engine", "%", 100.0, 0, 0.0, 100.0, icon = "engine"),
        r("engine.throttle", "Engine throttle", "Engine", "%", 100.0, 0, 0.0, 100.0, icon = "throttle"),
        r("rpm.tacho", "Tachometer (smoothed rpm)", "Engine", "rpm", 1.0, 0, 0.0, 8000.0, icon = "engine"),
        r("fuel", "Fuel level", "Fuel & energy", "%", 100.0, 0, 0.0, 100.0, listOf(Band(0.0, 10.0, "red")), "fuel"),
        r("fuel.volume", "Fuel left", "Fuel & energy", "L", 1.0, 1, icon = "fuel", keywords = "litres liters tank"),
        r("fuel.capacity", "Tank size", "Fuel & energy", "L", 1.0, 0, icon = "fuel", keywords = "litres liters"),
        r("battery", "Battery charge", "Fuel & energy", "%", 100.0, 0, 0.0, 100.0, listOf(Band(0.0, 10.0, "red")), "battery", "electric ev soc"),
        r("electrical.load", "Electrical load", "Fuel & energy", "%", 100.0, 0, 0.0, 100.0, icon = "bolt"),
        r("gear.index", "Gear number", "Drivetrain", icon = "gearbox"),
        r("clutch", "Clutch engagement", "Drivetrain", "%", 100.0, 0, 0.0, 100.0, icon = "clutch"),
        r("wheelspeed", "Wheel speed", "Drivetrain", "km/h", 3.6, 0, 0.0, 240.0, icon = "speed"),
        lamp("shifting", "Shifting", "Drivetrain", "gearbox"),
        r("input.throttle", "Throttle pedal", "Driver inputs", "%", 100.0, 0, 0.0, 100.0, icon = "throttle", keywords = "accelerator gas"),
        r("input.brake", "Brake pedal", "Driver inputs", "%", 100.0, 0, 0.0, 100.0, icon = "brake"),
        r("input.clutch", "Clutch pedal", "Driver inputs", "%", 100.0, 0, 0.0, 100.0, icon = "clutch"),
        r("input.parkingbrake", "Hand brake lever", "Driver inputs", "%", 100.0, 0, 0.0, 100.0, icon = "parking_brake"),
        r("input.steering", "Steering input", "Driver inputs", "%", 100.0, 0, -100.0, 100.0, icon = "steering"),
        r("steering.angle", "Steering wheel angle", "Driver inputs", "°", 1.0, 0, -540.0, 540.0, icon = "steering"),
        r("airspeed", "Speed over ground", "Motion", "km/h", 3.6, 0, 0.0, 240.0, icon = "speed"),
        r("altitude", "Altitude", "Motion", "m", 1.0, 0, icon = "navigation", keywords = "height elevation"),
        r("g.x", "G-force sideways", "Motion", "g", 1.0, 2, -2.0, 2.0, icon = "target", keywords = "lateral acceleration"),
        r("g.y", "G-force forward / back", "Motion", "g", 1.0, 2, -2.0, 2.0, icon = "target", keywords = "longitudinal acceleration braking"),
        r("g.z", "G-force vertical", "Motion", "g", 1.0, 2, -2.0, 2.0, icon = "target", keywords = "acceleration bump"),
        r("pitch", "Pitch (nose up)", "Motion", "°", 1.0, 0, -45.0, 45.0, icon = "vehicle", keywords = "inclinometer tilt"),
        r("roll", "Roll (side tilt)", "Motion", "°", 1.0, 0, -45.0, 45.0, icon = "vehicle", keywords = "inclinometer tilt"),
        r("odometer", "Odometer", "Motion", "km", 0.001, 1, icon = "navigation", keywords = "distance mileage"),
        r("trip", "Trip", "Motion", "km", 0.001, 1, icon = "navigation", keywords = "distance"),
        lamp("warning.checkengine", "Check engine", "Warnings", "check_engine", "malfunction"),
        lamp("warning.oil", "Oil overheating", "Warnings", "temp", "oil temperature warning"),
        lamp("warning.lowfuel", "Low fuel", "Warnings", "fuel"),
        lamp("warning.lowpressure", "Tyre deflated", "Warnings", "tire_pressure", "puncture flat tire tyre"),
        lamp("abs.active", "ABS working", "Warnings", "abs"),
        lamp("esc.intervening", "ESC working", "Warnings", "esc", "stability"),
        lamp("tcs.intervening", "Traction control working", "Warnings", "tcs"),
        lamp("lights.low", "Headlights", "Lights & signals", "low_beam", "low beam"),
        lamp("lights.high", "High beam", "Lights & signals", "high_beam"),
        lamp("lights.fog", "Fog lights", "Lights & signals", "fog_light"),
        lamp("lights.light_bar", "Light bar", "Lights & signals", "light_bar"),
        lamp("signal.left", "Left signal", "Lights & signals", "signal_left", "indicator blinker"),
        lamp("signal.right", "Right signal", "Lights & signals", "signal_right", "indicator blinker"),
        lamp("hazard", "Hazard lights", "Lights & signals", "hazard"),
        lamp("horn", "Horn", "Lights & signals", "horn"),
        lamp("parkingbrake", "Parking brake", "Systems & locks", "parking_brake", "handbrake"),
        lamp("ignition", "Ignition on", "Systems & locks", "ignition"),
        r("ignition.level", "Ignition position (0-3)", "Systems & locks", icon = "ignition"),
        lamp("engine.running", "Engine running", "Systems & locks", "engine"),
        lamp("esc.active", "ESC on", "Systems & locks", "esc", "stability"),
        lamp("tcs.active", "Traction control on", "Systems & locks", "tcs"),
        lamp("cruise.active", "Cruise control on", "Systems & locks", "cruise"),
        lamp("diff.front.locked", "Front diff locked", "Systems & locks", "diff_lock", "differential"),
        lamp("diff.center.locked", "Centre diff locked", "Systems & locks", "diff_lock", "differential center"),
        lamp("diff.rear.locked", "Rear diff locked", "Systems & locks", "diff_lock", "differential"),
        lamp("awd.active", "4WD engaged", "Systems & locks", "awd", "4x4 all wheel drive"),
        lamp("range.low", "Low range", "Systems & locks", "low_range", "transfer case"),
        lamp("game.paused", "Game paused", "Game", "pause"),
        lamp("game.slowmo", "Slow motion", "Game", "slowmo"),
        r("camera.mode", "Camera", "Game", icon = "camera"),
    )

    private val byKey = known.associateBy { it.key }

    /** How to show a value the mod reported in its own unit: °C stays, ratios become %, m/s becomes km/h. */
    private fun fromModUnit(i: ReadingInfo): Reading {
        val wheelBrake = i.key.startsWith("brake.") && i.key.endsWith(".temp")
        val base = when (i.unit) {
            "C" -> Reading(i.key, i.title, i.group, "°C", 1.0, 0, 0.0, if (i.key.startsWith("brake.")) 900.0 else 160.0,
                if (wheelBrake) listOf(Band(0.0, 80.0, "blue"), Band(80.0, 550.0, "green"), Band(650.0, 900.0, "red")) else emptyList(), icon = "temp")
            "ratio" -> Reading(i.key, i.title, i.group, "%", 100.0, 0, 0.0, 100.0)
            "m/s" -> Reading(i.key, i.title, i.group, "km/h", 3.6, 0)
            "deg" -> Reading(i.key, i.title, i.group, "°", 1.0, 0)
            "psi", "L", "g", "m", "rpm" -> Reading(i.key, i.title, i.group, i.unit, 1.0, if (i.unit == "g") 2 else 1)
            else -> Reading(i.key, i.title, i.group, i.unit, 1.0, if (i.key.startsWith("e.")) 2 else 0)
        }
        val isLamp = i.key.endsWith(".deflated") || i.group == "Warnings"
        return base.copy(lamp = isLamp, icon = base.icon ?: if (i.key.startsWith("tire.")) "tire_pressure" else null)
    }

    /** The reading for [key], from the known list or the vehicle's own catalogue, or a plain one. */
    fun forKey(key: String, info: List<ReadingInfo> = emptyList()): Reading =
        byKey[key] ?: info.firstOrNull { it.key == key }?.let { fromModUnit(it) } ?: plain(key)

    private fun plain(key: String): Reading = when {
        key.startsWith("has.") -> lamp(key, "Has " + key.removePrefix("has.").replace('.', ' ').replace('_', ' '), "Vehicle has")
        key.startsWith("hydro.") -> Reading(key, key.removePrefix("hydro.").replace('_', ' ').replaceFirstChar { it.uppercase() }, "Hydraulics",
            "%", 100.0, 0, -100.0, 100.0, icon = "boom")
        key.startsWith("e.") -> Reading(key, key.removePrefix("e."), "All signals", decimals = 2)
        else -> Reading(key, key, "Other")
    }

    /**
     * Everything the picker offers: the known readings, then what the vehicle reported that isn't known (brake
     * temperatures per wheel, every raw electrics signal), then any other key seen in game state. Grouped in [GROUPS] order.
     */
    fun catalog(info: List<ReadingInfo>, stateKeys: Collection<String> = emptyList()): List<Reading> {
        val out = LinkedHashMap<String, Reading>()
        known.forEach { out[it.key] = it }
        info.forEach { if (it.key !in out) out[it.key] = fromModUnit(it) }
        stateKeys.forEach { if (it !in out) out[it] = plain(it) }
        val order = GROUPS.withIndex().associate { (i, g) -> g to i }
        return out.values.withIndex().sortedWith(compareBy({ order[it.value.group] ?: (GROUPS.size - 1) }, { it.index })).map { it.value }
    }

    /** Readings matching every word of [query] in the title, key, group, unit or keywords. */
    fun search(list: List<Reading>, query: String): List<Reading> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return list
        return list.filter { r ->
            val hay = "${r.title} ${r.key} ${r.group} ${r.unit} ${r.keywords}".lowercase()
            words.all { it in hay }
        }
    }

    /** A gauge (or a lamp for on/off values) that shows [r], as a template for the editor and presets. */
    fun control(r: Reading): Control =
        if (r.lamp) Control(id = "", type = ControlType.INDICATOR, label = r.title.take(18), icon = r.icon, accent = lampAccent(r), feedbackKey = r.key)
        else Control(
            id = "", type = ControlType.GAUGE, label = shortTitle(r), icon = null, w = 2, h = if (r.min != null && r.max != null) 2 else 1,
            accent = Accent.CYAN, feedbackKey = r.key, scale = r.scale, decimals = r.decimals, unit = r.unit.ifBlank { null },
            min = r.min, max = r.max, bands = r.bands, skin = if (r.min != null && r.max != null) "steam" else null,
        )

    private fun lampAccent(r: Reading): Accent = when {
        r.group == "Warnings" || r.key.endsWith(".deflated") || r.key == "parkingbrake" || r.key == "hazard" -> Accent.RED
        r.key.startsWith("signal.") || r.key == "lights.low" || r.key == "engine.running" -> Accent.GREEN
        r.key == "lights.high" -> Accent.BLUE
        else -> Accent.AMBER
    }

    private fun shortTitle(r: Reading): String = when (r.key) {
        "oil.temp" -> "Oil temp"
        "coolant.temp" -> "Coolant"
        "engine.load" -> "Load"
        "fuel.volume" -> "Fuel"
        "g.x" -> "Lateral G"
        "g.y" -> "Long. G"
        "g.z" -> "Vertical G"
        else -> r.title.replace("temperature", "temp").take(18)
    }

    /** [value] as the reading shows it: scaled, rounded, with the unit; on/off for lamps. */
    fun format(r: Reading, value: Any?): String = when (value) {
        null -> "–"
        is Boolean -> if (value) "On" else "Off"
        is Number -> {
            val v = value.toDouble() * r.scale
            val s = if (r.decimals == 0) round(v).toLong().toString() else String.format(java.util.Locale.ROOT, "%.${r.decimals}f", v)
            if (r.unit.isBlank()) s else "$s ${r.unit}"
        }
        else -> value.toString()
    }

    /**
     * The keys a profile shows, to send as "readings.watch": every lamp, gauge, position and availability key. Keys the
     * mod always reports cost nothing extra.
     */
    fun watchKeys(p: Profile): List<String> = p.pages.flatMap { it.controls }
        .flatMap { listOfNotNull(it.feedbackKey, it.positionKey, it.visibleWhen) }
        .filter { it.isNotBlank() }.distinct().sorted()

    internal fun close(a: Double, b: Double) = abs(a - b) < 1e-9
}
