package com.simdeck.model

/**
 * One BeamNG command in the grouped picker (SimDeck 1.4): what to put in [Action.BeamNG.command], a readable title, the
 * group it belongs to and a line saying how it behaves.
 */
data class CommandInfo(
    val id: String,
    val title: String,
    val group: String,
    val detail: String = "",
    /** Drives a value (slider, joystick): hydraulics and analog game actions. */
    val axis: Boolean = false,
    /** Game-state key that tells whether it is on, for the control's lamp. */
    val stateKey: String? = null,
    val keywords: String = "",
)

object BeamNGCommands {
    val GROUPS = listOf(
        "This vehicle", "Engine", "Lights & signals", "Assists & brakes", "Cruise control", "Drivetrain", "Gears", "Camera",
        "Recovery & game", "Other commands",
    )

    private const val FLIP = "Flips it on each press"
    private const val FOLLOW = "On while the control is on (for toggles and switches)"
    private const val HOLD = "Active while held"
    private const val ONCE = "Once per press"

    private fun onOff(feature: String, title: String, group: String, stateKey: String?, keywords: String = "") = listOf(
        CommandInfo("$feature.toggle", title, group, FLIP, stateKey = stateKey, keywords = keywords),
        CommandInfo(feature, "$title (on / off)", group, FOLLOW, stateKey = stateKey, keywords = keywords),
    )

    /** The mod's own commands (protocol/PROTOCOL.md, BeamNG command names), grouped. */
    val builtIn: List<CommandInfo> = buildList {
        addAll(onOff("ignition", "Ignition", "Engine", "ignition", "key power"))
        add(CommandInfo("starter", "Starter", "Engine", HOLD, stateKey = "engine.running", keywords = "start crank"))
        addAll(onOff("lights.low", "Headlights", "Lights & signals", "lights.low", "low beam"))
        addAll(onOff("lights.high", "High beam", "Lights & signals", "lights.high"))
        addAll(onOff("lights.fog", "Fog lights", "Lights & signals", "lights.fog"))
        addAll(onOff("lights.light_bar", "Light bar", "Lights & signals", "lights.light_bar", "police siren"))
        addAll(onOff("hazard", "Hazard lights", "Lights & signals", "hazard", "warning"))
        addAll(onOff("signal.left", "Left signal", "Lights & signals", "signal.left", "indicator blinker"))
        addAll(onOff("signal.right", "Right signal", "Lights & signals", "signal.right", "indicator blinker"))
        add(CommandInfo("horn", "Horn", "Lights & signals", HOLD, stateKey = "horn"))
        addAll(onOff("parkingbrake", "Parking brake", "Assists & brakes", "parkingbrake", "handbrake"))
        addAll(onOff("esc", "ESC", "Assists & brakes", "esc.active", "stability"))
        addAll(onOff("tcs", "Traction control", "Assists & brakes", "tcs.active", "tc"))
        addAll(onOff("cruise", "Cruise control", "Cruise control", "cruise.active"))
        add(CommandInfo("cruise.set", "Set cruise to current speed", "Cruise control", ONCE))
        add(CommandInfo("cruise.up", "Cruise speed up 5 km/h", "Cruise control", ONCE))
        add(CommandInfo("cruise.down", "Cruise speed down 5 km/h", "Cruise control", ONCE))
        addAll(onOff("diff.front", "Front diff lock", "Drivetrain", "diff.front.locked", "differential"))
        addAll(onOff("diff.center", "Centre diff lock", "Drivetrain", "diff.center.locked", "differential center"))
        addAll(onOff("diff.rear", "Rear diff lock", "Drivetrain", "diff.rear.locked", "differential"))
        addAll(onOff("awd", "4WD", "Drivetrain", "awd.active", "4x4 all wheel drive"))
        addAll(onOff("range", "Low range", "Drivetrain", "range.low", "transfer case"))
        add(CommandInfo("gear.up", "Shift up", "Gears", ONCE))
        add(CommandInfo("gear.down", "Shift down", "Gears", ONCE))
        add(CommandInfo("gearbox.mode", "Gearbox mode (P R N D S M)", "Gears", "Selector positions send 0 = P ... 5 = M", stateKey = "gearbox.mode"))
        add(CommandInfo("camera.next", "Next camera", "Camera", ONCE))
        add(CommandInfo("camera.prev", "Previous camera", "Camera", ONCE))
        add(CommandInfo("recover", "Recover (rewind)", "Recovery & game", HOLD))
        add(CommandInfo("reset", "Reset vehicle", "Recovery & game", ONCE))
        add(CommandInfo("reset.full", "Reset all vehicles", "Recovery & game", ONCE))
        addAll(onOff("slowmo", "Slow motion", "Recovery & game", "game.slowmo"))
        add(CommandInfo("slowmo.faster", "Slow motion faster", "Recovery & game", ONCE))
        add(CommandInfo("slowmo.slower", "Slow motion slower", "Recovery & game", ONCE))
        addAll(onOff("pause", "Pause", "Recovery & game", "game.paused"))
    }

    private val builtInIds = builtIn.associateBy { it.id }

    private fun pretty(s: String) = s.replace('_', ' ').replace(Regex("([a-z])([A-Z])"), "$1 $2").trim()
        .replaceFirstChar { it.uppercase() }

    /**
     * Everything the picker offers for a BeamNG command: the vehicle's own functions first, then the mod's commands, then
     * every game action grouped by the game's category. [reported] are the command names the mod sent (anything new
     * lands in "Other commands"). With [axisOnly], only what takes a value.
     */
    fun catalog(
        vehicle: List<VehicleFeature> = emptyList(),
        actions: List<GameAction> = emptyList(),
        reported: List<String> = emptyList(),
        axisOnly: Boolean = false,
    ): List<CommandInfo> {
        val out = LinkedHashMap<String, CommandInfo>()
        for (f in vehicle) {
            val axis = f.kind == "axis"
            out[f.id] = CommandInfo(f.id, f.title, "This vehicle",
                when { axis -> "Lever: follows the slider"; f.kind == "hold" -> HOLD; else -> ONCE } + sourceNote(f.source),
                axis = axis, stateKey = f.stateKey)
        }
        if (!axisOnly) {
            builtIn.forEach { out.putIfAbsent(it.id, it) }
            reported.filter { it !in builtInIds }.forEach { out.putIfAbsent(it, CommandInfo(it, pretty(it.replace('.', ' ')), "Other commands", it)) }
        }
        for (a in actions) {
            val id = "action." + a.name
            val group = if (a.vehicle) "This vehicle" else "Game actions: " + (a.category.ifBlank { "general" }.let { pretty(it) })
            out.putIfAbsent(id, CommandInfo(id, a.title, group, if (a.axis) "Analog game action" else "Game action $id", axis = a.axis))
        }
        val list = out.values.filter { !axisOnly || it.axis }
        val order = GROUPS.withIndex().associate { (i, g) -> g to i }
        return list.withIndex().sortedWith(compareBy({ order[it.value.group] ?: GROUPS.size }, { it.value.group }, { it.index })).map { it.value }
    }

    private fun sourceNote(source: String) = when (source) {
        "coupler" -> " · coupler"
        "hydro" -> " · hydraulic"
        "interaction" -> " · vehicle interaction"
        else -> ""
    }

    /** Commands matching every word of [query] in title, id, group, detail or keywords. */
    fun search(list: List<CommandInfo>, query: String): List<CommandInfo> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return list
        return list.filter { c ->
            val hay = "${c.title} ${c.id} ${c.group} ${c.detail} ${c.keywords}".lowercase()
            words.all { it in hay }
        }
    }

    /** A readable name for a command id, for summaries ("Hazard lights" for hazard.toggle). */
    fun title(id: String): String? = builtInIds[id]?.title
}
