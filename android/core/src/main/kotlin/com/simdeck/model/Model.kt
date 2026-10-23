package com.simdeck.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Which game a profile targets. Drives built-in presets and the BeamNG integration. */
@Serializable
enum class GameId(val displayName: String) {
    @SerialName("beamng") BEAMNG("BeamNG.drive"),
    @SerialName("mudrunner") MUDRUNNER("MudRunner"),
    @SerialName("snowrunner") SNOWRUNNER("SnowRunner"),
    @SerialName("expeditions") EXPEDITIONS("Expeditions"),
    @SerialName("ets2") ETS2("Euro Truck Simulator 2"),
    @SerialName("ats") ATS("American Truck Simulator"),
    @SerialName("fs25") FS25("Farming Simulator 25"),
    @SerialName("acc") ACC("Assetto Corsa Competizione"),
    @SerialName("msfs") MSFS("Flight Simulator"),
    @SerialName("custom") CUSTOM("Custom"),
}

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val game: GameId = GameId.CUSTOM,
    val builtIn: Boolean = false,
    val pages: List<Page> = listOf(Page(id = "p1", name = "Main")),
    /** How this profile's inputs reach Windows. */
    val output: OutputRoute = OutputRoute.AUTO,
    /** Short description shown on the profile card. */
    val description: String = "",
    /** Schema version of the exported JSON, not the network protocol version. */
    val schema: Int = 1,
    /** Game executables (e.g. "BeamNG.drive.x64.exe"): the app switches to this profile when one is in front. */
    val processNames: List<String> = emptyList(),
)

/** Where a profile's inputs go. Game commands (BeamNG) and XInput always need the companion. */
@Serializable
enum class OutputRoute {
    /** Bluetooth HID when the phone is paired as "SimDeck Pad", otherwise the Wi-Fi companion. */
    @SerialName("auto") AUTO,
    /** Bluetooth HID only (no PC software). */
    @SerialName("hid") HID,
    /** Wi-Fi companion only (scancodes, vJoy, ViGEm). */
    @SerialName("companion") COMPANION,
}

/** A page is a grid. Controls are placed on cells and may span several. */
@Serializable
data class Page(
    val id: String,
    val name: String,
    val columns: Int = 8,
    val rows: Int = 4,
    val controls: List<Control> = emptyList(),
    /**
     * A page that fills itself while playing. [AUTO_BEAMNG_VEHICLE]: one control per feature of the BeamNG
     * vehicle being driven (its own actions, couplers, hydraulics), as reported by the mod. Its stored
     * controls are ignored.
     */
    val auto: String? = null,
) {
    companion object {
        const val AUTO_BEAMNG_VEHICLE = "beamng.vehicle"
    }
}

@Serializable
enum class ControlType {
    @SerialName("momentary") MOMENTARY,
    @SerialName("latching") LATCHING,
    @SerialName("switch") SWITCH,
    @SerialName("radio") RADIO,
    @SerialName("slider") SLIDER,
    @SerialName("rotary") ROTARY,
    /** Two spring-centred axes on one stick, like a machine's pilot joystick. See [Joystick]. */
    @SerialName("joystick") JOYSTICK,
    /** Up / down (or right / left) halves, each a momentary button: [Control.cw] up, [Control.ccw] down. See [Joystick]. */
    @SerialName("rocker") ROCKER,
    /** Display only: a number from the game ([Control.feedbackKey]) with scale, decimals and unit. */
    @SerialName("gauge") GAUGE,
    /** Display only: a lamp lit by [Control.feedbackKey]. */
    @SerialName("indicator") INDICATOR,
    /** Goes to another page of the profile ([Control.pageTarget]). Never sent to the PC. */
    @SerialName("page") PAGE;

    val wire: String get() = name.lowercase()

    /** Controls that only show something or act inside the app: they have no binding on the wire. */
    val displayOnly: Boolean get() = this == GAUGE || this == INDICATOR || this == PAGE
}

/** Accent colour role for a control. The UI theme maps each role to a colour. */
@Serializable
enum class Accent {
    @SerialName("neutral") NEUTRAL,
    @SerialName("amber") AMBER,
    @SerialName("green") GREEN,
    @SerialName("red") RED,
    @SerialName("blue") BLUE,
    @SerialName("cyan") CYAN,
}

@Serializable
data class Control(
    val id: String,
    val type: ControlType,
    val label: String,
    /** Id from the icon library (see IconIds), or null for a text-only control. */
    val icon: String? = null,
    val x: Int = 0,
    val y: Int = 0,
    val w: Int = 1,
    val h: Int = 1,
    val accent: Accent = Accent.NEUTRAL,
    /** Used by MOMENTARY, LATCHING and SLIDER; the left-right axis of a JOYSTICK. */
    val action: Action = Action.None,
    /** Used by SWITCH and RADIO: one entry per position, in order. */
    val positions: List<Position> = emptyList(),
    /** Used by ROTARY. */
    val cw: Action = Action.None,
    val ccw: Action = Action.None,
    /** SLIDER: return to the rest value when released. */
    val springReturn: Boolean = false,
    /** SLIDER: BIPOLAR sends -1..1 and rests at 0, UNIPOLAR sends 0..1 and rests at 0. */
    val range: AxisRange = AxisRange.UNIPOLAR,
    /** SLIDER: true when the slider is drawn vertically. */
    val vertical: Boolean = true,
    /** Game state key that lights the control's indicator, e.g. "lights.low". Null means local latch state. */
    val feedbackKey: String? = null,
    /** Game state availability key, e.g. "has.diff.front". The control is dimmed and disabled when it is false. */
    val visibleWhen: String? = null,
    /** SWITCH/RADIO: game state key whose string or number value selects the active position. */
    val positionKey: String? = null,
    /**
     * Game input action this control's gamepad button should be bound to inside the game,
     * e.g. BeamNG "toggle_headlights". Used by "Bind in game" (BeamNG mod bindings.apply).
     */
    val bindTo: String? = null,
    /** JOYSTICK: the forward-back axis (forward is +1). */
    val actionY: Action = Action.None,
    /** Safety cover: the first tap lifts the cover for a few seconds, the next one operates the control. */
    val guard: Boolean = false,
    /** SLIDER: snap to this many evenly spaced positions (0 or 1 = continuous). */
    val detents: Int = 0,
    /** GAUGE: shown value = game value x [scale]. */
    val scale: Double = 1.0,
    /** GAUGE: digits after the decimal point. */
    val decimals: Int = 0,
    /** GAUGE: unit printed under the value, e.g. "km/h". */
    val unit: String? = null,
    /** PAGE: "next", "prev" or a page id. */
    val pageTarget: String? = null,
)

/**
 * A JOYSTICK is two spring-centred bipolar sliders that share one stick. Everything past the UI
 * (engine, routes, protocol, companion) sees only those two sliders, with ids "<id>.x" and "<id>.y",
 * so the wire protocol and the companion need nothing new.
 */
object Joystick {
    const val X = ".x"
    const val Y = ".y"
    /** A ROCKER is two momentary buttons on the wire: "<id>.up" ([Control.cw]) and "<id>.down" ([Control.ccw]). */
    const val UP = ".up"
    const val DOWN = ".down"

    fun axes(c: Control): List<Control> = listOf(axis(c, X, c.action), axis(c, Y, c.actionY))

    private fun axis(c: Control, suffix: String, a: Action) = c.copy(
        id = c.id + suffix, type = ControlType.SLIDER, action = a, actionY = Action.None,
        range = AxisRange.BIPOLAR, springReturn = true, feedbackKey = null,
        label = c.label + if (suffix == X) " (left-right)" else " (forward-back)",
    )

    fun halves(c: Control): List<Control> = listOf(half(c, UP, c.cw), half(c, DOWN, c.ccw))

    private fun half(c: Control, suffix: String, a: Action) = c.copy(
        id = c.id + suffix, type = ControlType.MOMENTARY, action = a, cw = Action.None, ccw = Action.None,
        feedbackKey = null, label = c.label + if (suffix == UP) " (up)" else " (down)",
    )

    /** The ids a control drives: its own, its two axes, or its two halves. */
    fun ids(c: Control): Pair<String, String?> = when (c.type) {
        ControlType.JOYSTICK -> (c.id + X) to (c.id + Y)
        ControlType.ROCKER -> (c.id + UP) to (c.id + DOWN)
        else -> c.id to null
    }

    /**
     * The page's controls as the engine sees them: each joystick replaced by its two axes, each rocker by
     * its two halves, and display-only controls (gauges, lamps, page switches) left out.
     */
    fun expand(controls: List<Control>): List<Control> = controls.flatMap {
        when {
            it.type == ControlType.JOYSTICK -> axes(it)
            it.type == ControlType.ROCKER -> halves(it)
            it.type.displayOnly -> emptyList()
            else -> listOf(it)
        }
    }

    /** Finds [id] among [controls], including a joystick axis id such as "stick.x" or a rocker half "zoom.up". */
    fun find(controls: List<Control>, id: String): Control? {
        controls.firstOrNull { it.id == id }?.let { return it }
        for ((suffix, type) in listOf(X to ControlType.JOYSTICK, Y to ControlType.JOYSTICK, UP to ControlType.ROCKER, DOWN to ControlType.ROCKER)) {
            if (!id.endsWith(suffix)) continue
            val parent = controls.firstOrNull { it.id == id.removeSuffix(suffix) && it.type == type } ?: continue
            return (if (type == ControlType.JOYSTICK) axes(parent) else halves(parent)).first { it.id == id }
        }
        return null
    }
}

@Serializable
data class Position(
    val label: String,
    val action: Action = Action.None,
    val icon: String? = null,
    /** Value of positionKey that means this position is active, e.g. "D". */
    val stateValue: String? = null,
)

@Serializable
enum class AxisRange {
    @SerialName("bipolar") BIPOLAR,
    @SerialName("unipolar") UNIPOLAR,
}

@Serializable
enum class KeyTrigger {
    @SerialName("hold") HOLD,
    @SerialName("tap") TAP,
    @SerialName("press") PRESS,
}

@Serializable
data class MacroStep(
    val keys: List<String> = emptyList(),
    val holdMs: Int = 0,
    val delayMs: Int = 0,
)

/** What a control does. Serialized exactly as the protocol's Action object. */
@Serializable
sealed interface Action {
    @Serializable @SerialName("none")
    data object None : Action

    @Serializable @SerialName("key")
    data class Key(val keys: List<String>, val trigger: KeyTrigger = KeyTrigger.HOLD) : Action

    @Serializable @SerialName("vjoy_button")
    data class VJoyButton(val device: Int = 1, val button: Int) : Action

    @Serializable @SerialName("vjoy_axis")
    data class VJoyAxis(val device: Int = 1, val axis: String, val range: AxisRange = AxisRange.BIPOLAR) : Action

    @Serializable @SerialName("macro")
    data class Macro(val steps: List<MacroStep>) : Action

    @Serializable @SerialName("beamng")
    data class BeamNG(val command: String) : Action

    @Serializable @SerialName("xinput_button")
    data class XInputButton(val button: String) : Action

    @Serializable @SerialName("xinput_axis")
    data class XInputAxis(val axis: String) : Action
}
