package com.simdeck.model

/**
 * Screen-facing state. Screens are stateless composables that take these plus lambdas;
 * ViewModels (com.simdeck.app) produce them.
 */

/** Where an indicator's state comes from. */
enum class FeedbackSource {
    /** Local latch state only; no game feedback key or no game connected. */
    LOCAL,
    /** The user just changed the control and the game has not echoed it yet (times out after ~1.5 s). */
    PENDING,
    /** Confirmed by game state from the mod or telemetry. */
    GAME,
}

/** Lit/unlit state of a control's indicator lamp, with optional readout text (e.g. gear "D"). */
data class IndicatorState(
    val lit: Boolean,
    val text: String? = null,
    val source: FeedbackSource = FeedbackSource.LOCAL,
)

enum class ConnectionStatus { DISCONNECTED, SEARCHING, CONNECTING, PAIRING, CONNECTED, ERROR }

data class Capabilities(
    val keyboard: Boolean = false,
    val vjoy: Boolean = false,
    val vigem: Boolean = false,
    val beamng: Boolean = false,
    val outgauge: Boolean = false,
    /** How each vJoy device is set up in "Configure vJoy" (button count, enabled axes), when the companion can tell. */
    val vjoyInfo: List<VJoyInfo> = emptyList(),
)

data class VJoyInfo(val device: Int, val buttons: Int, val axes: List<String>)

data class ConnectionUi(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val serverName: String? = null,
    val host: String? = null,
    val latencyMs: Int? = null,
    val capabilities: Capabilities = Capabilities(),
    /** Human-readable problem, e.g. "Wrong PIN". */
    val message: String? = null,
)

/** Bluetooth HID link: the phone acting as a "SimDeck Pad" keyboard + gamepad for Windows. */
enum class HidStatus { UNSUPPORTED, OFF, REGISTERING, READY, CONNECTED }

data class BtHost(val name: String, val address: String)

data class HidUi(
    val status: HidStatus = HidStatus.OFF,
    /** Name of the connected PC, when CONNECTED. */
    val hostName: String? = null,
    /** Paired PCs the user can connect to. */
    val bondedHosts: List<BtHost> = emptyList(),
    val message: String? = null,
)

data class DiscoveredServer(val name: String, val host: String, val wsPort: Int)

/** Everything the play surface needs. */
data class PlayState(
    val profile: Profile,
    val pageIndex: Int = 0,
    /** Current value per control id: 0/1 for buttons, position index for switch/radio, -1..1 or 0..1 for sliders. */
    val values: Map<String, Float> = emptyMap(),
    /** Indicator per control id, already resolved from local latch state or game feedback. */
    val indicators: Map<String, IndicatorState> = emptyMap(),
    /** Control ids whose visibleWhen key is false for the current vehicle. Draw dimmed and ignore input. */
    val unavailable: Set<String> = emptySet(),
    /** Raw game state for HUD readouts (speed, gear, rpm...). */
    val game: Map<String, Any> = emptyMap(),
    val connection: ConnectionUi = ConnectionUi(),
    val hid: HidUi = HidUi(),
    /** Incremented every time a press is dropped because no link is up; the HUD flashes "NO LINK" on change. */
    val linkDrops: Int = 0,
    /** Problems that stop this profile from working (vJoy set up with too few buttons...), shown until dismissed. */
    val warnings: List<String> = emptyList(),
    /** What the BeamNG mod found on the current vehicle, for auto pages and their diagnostics. */
    val vehicle: VehicleFeatures = VehicleFeatures(),
    /** Bind helper: joysticks move one axis at a time, so a game's "move the axis" capture picks the right one. */
    val bindHelper: Boolean = false,
    /**
     * Split view (1.6): the page shown in the second half, below the first upright or beside it when turned.
     * Null when the screen shows one page. Both halves are the same profile, so they share [values] and [indicators].
     */
    val splitPageIndex: Int? = null,
)

/** Outcome of the last "Bind in game" request. */
data class BindResult(
    val ok: Boolean,
    val message: String,
    val device: String,
    val requested: Int,
    val count: Int,
    val unknown: List<String> = emptyList(),
    val written: Boolean = false,
    val busy: Boolean = false,
    val alreadyBound: Boolean = false,
    val atMillis: Long = 0L,
)

data class EditorState(
    val profile: Profile,
    val pageIndex: Int = 0,
    val selectedControlId: String? = null,
    val dirty: Boolean = false,
    /** Game state keys seen from the companion so far (for the feedback/visibleWhen/position pickers). */
    val stateKeys: List<String> = emptyList(),
    /** BeamNG commands reported by the mod, for the command picker. */
    val beamngCommands: List<String> = emptyList(),
    /** Every BeamNG input action the mod can run (action.<name>), for the action browser. */
    val beamngActions: List<GameAction> = emptyList(),
    /** Features of the vehicle being driven (vehicle actions, couplers, hydraulics). */
    val vehicleFeatures: List<VehicleFeature> = emptyList(),
    /** Bind helper switch, mirrored here for the profile panel. */
    val bindHelper: Boolean = false,
    /** Result of the last "Bind in game", null until one was requested. */
    val bindResult: BindResult? = null,
    /** Whether "Bind in game" can be sent right now (companion connected and the mod is up). */
    val canBindInGame: Boolean = false,
    /** The editor clipboard, null when nothing was copied. */
    val clip: Clip? = null,
)

/** What was copied in the editor: controls or a whole page, from any profile. Kept across profiles and restarts. */
@kotlinx.serialization.Serializable
data class Clip(
    val controls: List<Control> = emptyList(),
    val page: Page? = null,
    /** Where it came from, e.g. "BeamNG.drive / Drive", for the Paste button. */
    val from: String = "",
) {
    val label: String get() = page?.let { "page ${it.name}" } ?: if (controls.size == 1) controls[0].label else "${controls.size} controls"
}
