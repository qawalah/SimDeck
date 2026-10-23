package com.simdeck.model

/**
 * One thing the BeamNG vehicle being driven can do beyond the standard car functions, as the mod reports it
 * ("beamng.features", PROTOCOL.md): its own input actions, its interaction actions, a coupler group, or a
 * hydraulic input. [id] is the BeamNG command that drives it.
 */
data class VehicleFeature(
    val id: String,
    val title: String,
    /** "button" (press / release), "hold" (on while held), "axis" (-1..1, springs back) or "toggle". */
    val kind: String = "button",
    val centered: Boolean = false,
    /** input_actions, interaction, game, coupler or hydro. */
    val source: String = "",
    /** State key that lights the control, e.g. "hydro.dump_bed". */
    val stateKey: String? = null,
)

/** What the mod reported for the current vehicle. */
data class VehicleFeatures(
    val vehicle: String = "",
    val list: List<VehicleFeature> = emptyList(),
    /** The mod's diagnostics block as one readable line per entry (which source found what). */
    val diagnostics: List<String> = emptyList(),
)

/** A BeamNG input action the mod can run, for the editor's action browser. */
data class GameAction(val name: String, val title: String, val category: String = "", val vehicle: Boolean = false, val axis: Boolean = false)

/** Builds the controls of auto pages from the vehicle's features. */
object VehicleFeaturePages {
    const val ID_PREFIX = "auto-"

    /** A short icon guess from the feature's name, so the page reads at a glance. */
    fun iconFor(f: VehicleFeature): String {
        val n = (f.id + " " + f.title).lowercase()
        return when {
            "tailgate" in n -> "tailgate"
            "door" in n || "hood" in n || "trunk" in n || "hatch" in n -> "door"
            "ramp" in n || "tilt" in n -> "ramp"
            "jack" in n || "feet" in n || "leg" in n -> "jack"
            "outrigger" in n || "stabil" in n -> "outriggers"
            "axle" in n -> "lift_axle"
            "height" in n || "kneel" in n || "suspension" in n || "air" in n -> "suspension"
            "bed" in n || "dump" in n -> "ramp"
            "blade" in n -> "blade"
            "bucket" in n -> "bucket"
            "boom" in n || "arm" in n || "crane" in n -> "boom"
            "hook" in n || "winch" in n -> "winch"
            "swing" in n || "slew" in n || "rotat" in n -> "swing"
            "light" in n || "lamp" in n || "beacon" in n -> "beacon"
            "siren" in n || "horn" in n -> "horn"
            "lock" in n || "pin" in n || "latch" in n || "coupler" in n -> "quick_coupler"
            "plow" in n || "plough" in n -> "plough"
            f.kind == "axis" -> "suspension"
            else -> "build"
        }
    }

    /**
     * Lays the features out on [page]'s grid: buttons one cell each, axes two cells wide. The grid grows up to
     * the editor's limits when there are many; anything that still doesn't fit is left out.
     */
    fun controls(page: Page, features: List<VehicleFeature>): Pair<Page, Int> {
        if (features.isEmpty()) return page.copy(controls = emptyList()) to 0
        val cells = features.fold(0) { acc, f -> acc + if (f.kind == "axis") 2 else 1 }
        var cols = page.columns
        var rows = page.rows
        while (cols * rows < cells && (cols < MAX_COLS || rows < MAX_ROWS)) {
            if (cols < MAX_COLS && cols <= rows * 2) cols++ else if (rows < MAX_ROWS) rows++ else cols++
        }
        val taken = Array(rows) { BooleanArray(cols) }
        val out = ArrayList<Control>()
        var dropped = 0
        for (f in features) {
            val w = if (f.kind == "axis") 2 else 1
            val spot = (0 until rows).asSequence().flatMap { y -> (0..cols - w).asSequence().map { x -> x to y } }
                .firstOrNull { (x, y) -> (x until x + w).all { !taken[y][it] } }
            if (spot == null) { dropped++; continue }
            val (x, y) = spot
            for (i in x until x + w) taken[y][i] = true
            val action = Action.BeamNG(f.id)
            val c = if (f.kind == "axis") Control(
                id = ID_PREFIX + f.id, type = ControlType.SLIDER, label = f.title, icon = iconFor(f), x = x, y = y, w = w, h = 1,
                accent = Accent.CYAN, action = action, range = AxisRange.BIPOLAR, springReturn = true, vertical = false,
                feedbackKey = f.stateKey,
            ) else Control(
                id = ID_PREFIX + f.id, type = ControlType.MOMENTARY, label = f.title, icon = iconFor(f), x = x, y = y, w = 1, h = 1,
                accent = if (f.source == "coupler") Accent.AMBER else Accent.NEUTRAL, action = action, feedbackKey = f.stateKey,
            )
            out += c
        }
        return page.copy(columns = cols, rows = rows, controls = out) to dropped
    }

    /** [p] with every auto page filled for the current vehicle. */
    fun fill(p: Profile, features: VehicleFeatures): Profile {
        if (p.pages.none { it.auto == Page.AUTO_BEAMNG_VEHICLE }) return p
        return p.copy(pages = p.pages.map { pg ->
            if (pg.auto == Page.AUTO_BEAMNG_VEHICLE) controls(pg, features.list).first else pg
        })
    }

    private const val MAX_COLS = 12
    private const val MAX_ROWS = 8
}
