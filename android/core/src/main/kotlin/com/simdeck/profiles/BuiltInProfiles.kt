package com.simdeck.profiles

import com.simdeck.model.Accent
import com.simdeck.model.Action
import com.simdeck.model.AxisRange
import com.simdeck.model.Control
import com.simdeck.model.ControlType
import com.simdeck.model.GameId
import com.simdeck.model.KeyTrigger
import com.simdeck.model.OutputRoute
import com.simdeck.model.Page
import com.simdeck.model.Position
import com.simdeck.model.Profile
import com.simdeck.model.PanelGroup
import com.simdeck.model.Station
import com.simdeck.model.Band
import com.simdeck.model.Themes

/**
 * Profiles that ship with the app. Icon ids come from ui/icons/IconLibrary.kt; state keys and
 * BeamNG command names come from protocol/PROTOCOL.md.
 */
object BuiltInProfiles {
    const val GAMEPAD_ID = "builtin-gamepad"
    const val KEYBOARD_ID = "builtin-keyboard"
    const val BEAMNG_ID = "builtin-beamng"
    const val MUDRUNNER_ID = "builtin-mudrunner"
    const val BEAMNG_PAD_ID = "builtin-beamng-pad"
    const val BULLDOZER_ID = "builtin-bulldozer"
    const val UTILITY_ID = "builtin-utility"
    const val SNOWRUNNER_ID = "builtin-snowrunner"
    const val EXPEDITIONS_ID = "builtin-expeditions"
    const val ETS2_ID = "builtin-ets2"
    const val ATS_ID = "builtin-ats"
    const val FS25_ID = "builtin-fs25"
    const val ACC_ID = "builtin-acc"
    const val MSFS_ID = "builtin-msfs"

    val all: List<Profile> by lazy {
        listOf(
            gamepad(), keyboard(), beamng(), beamngPad(), mudrunner(), snowrunner(), expeditions(),
            scs(ETS2_ID, "Euro Truck Simulator 2", GameId.ETS2, "eurotrucks2.exe"),
            scs(ATS_ID, "American Truck Simulator", GameId.ATS, "amtrucks.exe"),
            fs25(), acc(), msfs(), bulldozer(), utility(),
        )
    }

    /** BeamNG executables, for automatic profile switching. */
    val BEAMNG_EXE = listOf("BeamNG.drive.x64.exe", "BeamNG.drive.exe", "BeamNG.tech.x64.exe")

    // ---- small builders --------------------------------------------------------------------

    private fun bng(cmd: String) = Action.BeamNG(cmd)
    private fun key(vararg k: String, trigger: KeyTrigger = KeyTrigger.HOLD) = Action.Key(k.toList(), trigger)
    private fun btn(n: Int) = Action.VJoyButton(1, n)

    private fun momentary(id: String, label: String, icon: String?, x: Int, y: Int, action: Action, accent: Accent = Accent.NEUTRAL, w: Int = 1, h: Int = 1, feedback: String? = null, visibleWhen: String? = null) =
        Control(id, ControlType.MOMENTARY, label, icon, x, y, w, h, accent, action, feedbackKey = feedback, visibleWhen = visibleWhen)

    private fun latching(id: String, label: String, icon: String?, x: Int, y: Int, action: Action, accent: Accent, feedback: String? = null, visibleWhen: String? = null, w: Int = 1, h: Int = 1) =
        Control(id, ControlType.LATCHING, label, icon, x, y, w, h, accent, action, feedbackKey = feedback, visibleWhen = visibleWhen)

    /** A hydraulic or drive lever: centred, springs back to stop when let go. */
    private fun lever(id: String, label: String, icon: String?, x: Int, y: Int, axis: String, accent: Accent = Accent.CYAN, w: Int = 1, h: Int = 4) =
        Control(id, ControlType.SLIDER, label, icon, x, y, w, h, accent, Action.VJoyAxis(1, axis),
            range = AxisRange.BIPOLAR, springReturn = true, vertical = h >= w)

    private fun rocker(id: String, label: String, icon: String?, x: Int, y: Int, up: Action, down: Action, accent: Accent = Accent.CYAN, w: Int = 1, h: Int = 2) =
        Control(id, ControlType.ROCKER, label, icon, x, y, w, h, accent, cw = up, ccw = down)

    private fun gauge(id: String, label: String, key: String, x: Int, y: Int, unit: String? = null, scale: Double = 1.0, decimals: Int = 0, w: Int = 1, h: Int = 1) =
        Control(id, ControlType.GAUGE, label, null, x, y, w, h, Accent.CYAN, feedbackKey = key, scale = scale, unit = unit, decimals = decimals)

    private fun lamp(id: String, label: String, icon: String, key: String, x: Int, y: Int, accent: Accent = Accent.AMBER) =
        Control(id, ControlType.INDICATOR, label, icon, x, y, 1, 1, accent, feedbackKey = key)

    /** An analog BeamNG input action on a slider: the mod runs the action's own code with the slider's value. */
    private fun bngAxis(id: String, label: String, icon: String?, x: Int, y: Int, action: String, bipolar: Boolean, accent: Accent, w: Int = 1, h: Int = 4) =
        Control(id, ControlType.SLIDER, label, icon, x, y, w, h, accent, Action.BeamNG("action.$action"),
            range = if (bipolar) AxisRange.BIPOLAR else AxisRange.UNIPOLAR, springReturn = true, vertical = h >= w)

    /** A throttle: stays where it is left, from idle (bottom) to full. */
    private fun throttle(id: String, x: Int, y: Int, axis: String = "SL0", h: Int = 4) =
        Control(id, ControlType.SLIDER, "Throttle", "speed", x, y, 1, h, Accent.GREEN,
            Action.VJoyAxis(1, axis, AxisRange.UNIPOLAR), range = AxisRange.UNIPOLAR)

    // ---- Generic gamepad: works with any game over Bluetooth HID or vJoy -------------------

    fun gamepad(): Profile {
        val buttons = (1..12).map { n ->
            val col = (n - 1) % 6
            val row = (n - 1) / 6
            momentary("gp_b$n", "B$n", null, 2 + col, row, btn(n), if (n <= 4) Accent.CYAN else Accent.NEUTRAL)
        }
        val toggles = (13..16).map { n ->
            latching("gp_t$n", "T${n - 12}", "power", 2 + (n - 13), 2, btn(n), Accent.AMBER)
        }
        val main = Page(
            id = "gp_main", name = "Pad", columns = 8, rows = 4,
            controls = buttons + toggles + listOf(
                Control("gp_throttle", ControlType.SLIDER, "Throttle", "speed", 0, 0, 1, 4, Accent.GREEN,
                    Action.VJoyAxis(1, "SL0", AxisRange.UNIPOLAR), range = AxisRange.UNIPOLAR),
                Control("gp_axis_z", ControlType.SLIDER, "Z axis", null, 1, 0, 1, 4, Accent.CYAN,
                    Action.VJoyAxis(1, "Z"), range = AxisRange.BIPOLAR, springReturn = true),
                Control("gp_mode", ControlType.SWITCH, "Mode", "settings", 2, 3, 3, 1, Accent.AMBER,
                    positions = listOf(Position("1", btn(17)), Position("2", btn(18)), Position("3", btn(19)))),
                Control("gp_dial", ControlType.ROTARY, "Dial", null, 6, 2, 2, 2, Accent.NEUTRAL,
                    cw = btn(20), ccw = btn(21)),
                momentary("gp_b22", "B22", null, 5, 3, btn(22)),
            ),
        )
        return Profile(
            id = GAMEPAD_ID, name = "Generic gamepad", game = GameId.CUSTOM, builtIn = true,
            pages = listOf(main), output = OutputRoute.AUTO,
            description = "Gamepad buttons, toggles, a throttle and an axis for any game. Bind them in the game's controls menu.",
        )
    }

    // ---- Generic keyboard macro pad -------------------------------------------------------

    fun keyboard(): Profile {
        val fkeys = (1..12).map { n ->
            momentary("kb_f$n", "F$n", null, (n - 1) % 6, (n - 1) / 6, key("F$n"), Accent.NEUTRAL)
        }
        val page = Page(
            id = "kb_main", name = "Keys", columns = 8, rows = 4,
            controls = fkeys + listOf(
                momentary("kb_esc", "Esc", "menu", 6, 0, key("ESC"), Accent.RED),
                momentary("kb_enter", "Enter", null, 7, 0, key("ENTER"), Accent.GREEN),
                momentary("kb_tab", "Tab", null, 6, 1, key("TAB")),
                momentary("kb_space", "Space", null, 7, 1, key("SPACE")),
                momentary("kb_up", "Up", "arrow_up", 1, 2, key("UP")),
                momentary("kb_left", "Left", "arrow_left", 0, 3, key("LEFT")),
                momentary("kb_down", "Down", "arrow_down", 1, 3, key("DOWN")),
                momentary("kb_right", "Right", "arrow_right", 2, 3, key("RIGHT")),
                momentary("kb_screenshot", "Screenshot", "screenshot", 3, 2, key("PRINTSCREEN", trigger = KeyTrigger.PRESS)),
                momentary("kb_mute", "Push to talk", "mic", 3, 3, key("F13"), Accent.AMBER),
                Control("kb_volume", ControlType.ROTARY, "Volume", "volume_up", 4, 2, 2, 2, Accent.CYAN,
                    cw = key("EQUALS", trigger = KeyTrigger.PRESS), ccw = key("MINUS", trigger = KeyTrigger.PRESS)),
                momentary("kb_copy", "Copy", null, 6, 2, key("LCTRL", "C", trigger = KeyTrigger.PRESS)),
                momentary("kb_paste", "Paste", null, 7, 2, key("LCTRL", "V", trigger = KeyTrigger.PRESS)),
                momentary("kb_quicksave", "Quick save", "star", 6, 3, key("F5", trigger = KeyTrigger.PRESS), Accent.GREEN),
                momentary("kb_quickload", "Quick load", "replay", 7, 3, key("F9", trigger = KeyTrigger.PRESS), Accent.AMBER),
            ),
        )
        return Profile(
            id = KEYBOARD_ID, name = "Keyboard keys", game = GameId.CUSTOM, builtIn = true,
            pages = listOf(page), output = OutputRoute.AUTO,
            description = "Function keys, arrows and common shortcuts. Works in any game or app.",
        )
    }

    // ---- BeamNG.drive: game commands through the mod, with feedback ----------------------

    private val GEARBOX_MODES = listOf("P", "R", "N", "D", "S", "M")

    fun beamng(): Profile {
        val drive = Page(
            id = "bng_drive", name = "Drive", columns = 8, rows = 4,
            controls = listOf(
                momentary("bng_ignition", "Ignition", "ignition", 0, 0, bng("ignition.toggle"), Accent.AMBER, feedback = "ignition"),
                momentary("bng_starter", "Start", "starter", 1, 0, bng("starter"), Accent.RED, feedback = "engine.running"),
                momentary("bng_horn", "Horn", "horn", 2, 0, bng("horn"), Accent.NEUTRAL),
                latching("bng_hazard", "Hazard", "hazard", 3, 0, bng("hazard"), Accent.RED, feedback = "hazard"),
                latching("bng_sig_l", "Left", "signal_left", 4, 0, bng("signal.left"), Accent.GREEN, feedback = "signal.left"),
                latching("bng_sig_r", "Right", "signal_right", 5, 0, bng("signal.right"), Accent.GREEN, feedback = "signal.right"),
                latching("bng_pbrake", "Park brake", "parking_brake", 6, 0, bng("parkingbrake"), Accent.RED, feedback = "parkingbrake"),
                momentary("bng_recover", "Recover", "recover", 7, 0, bng("recover"), Accent.AMBER),

                latching("bng_low", "Low beam", "low_beam", 0, 1, bng("lights.low"), Accent.GREEN, feedback = "lights.low"),
                latching("bng_high", "High beam", "high_beam", 1, 1, bng("lights.high"), Accent.BLUE, feedback = "lights.high"),
                latching("bng_fog", "Fog", "fog_light", 2, 1, bng("lights.fog"), Accent.GREEN, feedback = "lights.fog", visibleWhen = "has.fog"),
                latching("bng_lightbar", "Light bar", "light_bar", 3, 1, bng("lights.light_bar"), Accent.AMBER, feedback = "lights.light_bar", visibleWhen = "has.light_bar"),
                latching("bng_cruise", "Cruise", "cruise", 4, 1, bng("cruise"), Accent.GREEN, feedback = "cruise.active", visibleWhen = "has.cruise"),
                momentary("bng_cruise_set", "Set", "speed", 5, 1, bng("cruise.set"), Accent.NEUTRAL, visibleWhen = "has.cruise"),
                Control("bng_cruise_speed", ControlType.ROTARY, "Cruise speed", "cruise", 6, 1, 2, 2, Accent.CYAN,
                    cw = bng("cruise.up"), ccw = bng("cruise.down"), visibleWhen = "has.cruise", feedbackKey = "cruise.speed"),

                Control("bng_gearbox", ControlType.SWITCH, "Gearbox", "gearbox", 0, 2, 6, 1, Accent.CYAN,
                    positions = GEARBOX_MODES.map { Position(it, bng("gearbox.mode"), stateValue = it) },
                    positionKey = "gearbox.mode", visibleWhen = "has.gearbox.modes"),
                momentary("bng_gear_down", "Shift down", "gear_down", 4, 3, bng("gear.down"), Accent.NEUTRAL),
                momentary("bng_gear_up", "Shift up", "gear_up", 5, 3, bng("gear.up"), Accent.NEUTRAL, feedback = "gear"),

                momentary("bng_cam_prev", "Cam prev", "camera", 0, 3, bng("camera.prev")),
                momentary("bng_cam_next", "Cam next", "camera_cycle", 1, 3, bng("camera.next"), feedback = "camera.mode"),
                momentary("bng_look_back", "Look back", "look_back", 2, 3, bng("action.look_back")),
                latching("bng_slowmo", "Slow-mo", "slowmo", 3, 3, bng("slowmo"), Accent.CYAN, feedback = "game.slowmo"),
                latching("bng_pause", "Pause", "pause", 6, 3, bng("pause"), Accent.AMBER, feedback = "game.paused"),
                momentary("bng_reset", "Reset", "reset", 7, 3, bng("reset"), Accent.RED),
            ),
        )
        val drivetrain = Page(
            id = "bng_drivetrain", name = "4x4", columns = 8, rows = 4,
            controls = listOf(
                latching("bng_diff_f", "Front diff", "diff_lock", 0, 0, bng("diff.front"), Accent.AMBER, feedback = "diff.front.locked", visibleWhen = "has.diff.front", h = 2),
                latching("bng_diff_c", "Center diff", "differential", 1, 0, bng("diff.center"), Accent.AMBER, feedback = "diff.center.locked", visibleWhen = "has.diff.center", h = 2),
                latching("bng_diff_r", "Rear diff", "diff_lock", 2, 0, bng("diff.rear"), Accent.AMBER, feedback = "diff.rear.locked", visibleWhen = "has.diff.rear", h = 2),
                latching("bng_awd", "4WD", "awd", 3, 0, bng("awd"), Accent.GREEN, feedback = "awd.active", visibleWhen = "has.awd", h = 2),
                latching("bng_range", "Low range", "low_range", 4, 0, bng("range"), Accent.CYAN, feedback = "range.low", visibleWhen = "has.range", h = 2),
                latching("bng_esc2", "ESC / TC", "esc_off", 5, 0, bng("esc"), Accent.AMBER, feedback = "esc.active", visibleWhen = "has.esc", h = 2),
                latching("bng_pbrake2", "Park brake", "parking_brake", 6, 0, bng("parkingbrake"), Accent.RED, feedback = "parkingbrake", w = 2, h = 2),
                Control("bng_gearbox2", ControlType.RADIO, "Gearbox", "gearbox", 0, 2, 6, 1, Accent.CYAN,
                    positions = GEARBOX_MODES.map { Position(it, bng("gearbox.mode"), stateValue = it) },
                    positionKey = "gearbox.mode", visibleWhen = "has.gearbox.modes"),
                momentary("bng_gear_down2", "Shift down", "gear_down", 6, 2, bng("gear.down")),
                momentary("bng_gear_up2", "Shift up", "gear_up", 7, 2, bng("gear.up"), feedback = "gear"),
                momentary("bng_recover2", "Recover", "recover", 0, 3, bng("recover"), Accent.AMBER, w = 2),
                momentary("bng_reset2", "Reset", "reset", 2, 3, bng("reset"), Accent.RED),
                momentary("bng_reset_full", "Full reset", "reset", 3, 3, bng("reset.full"), Accent.RED),
                momentary("bng_horn2", "Horn", "horn", 4, 3, bng("horn")),
                latching("bng_hazard2", "Hazard", "hazard", 5, 3, bng("hazard"), Accent.RED, feedback = "hazard"),
                momentary("bng_cam_next2", "Camera", "camera_cycle", 6, 3, bng("camera.next")),
                momentary("bng_center_cam", "Center cam", "visibility", 7, 3, bng("action.center_camera")),
            ),
        )
        val vehicle = Page(id = "bng_vehicle", name = "Vehicle", columns = 8, rows = 4, auto = Page.AUTO_BEAMNG_VEHICLE)
        val more = Page(
            id = "bng_more", name = "Lights & assists", columns = 8, rows = 4,
            controls = listOf(
                latching("bng_work", "Work lights", "work_light", 0, 0, bng("action.toggle_work_lights"), Accent.AMBER),
                momentary("bng_flash", "Flash", "high_beam", 1, 0, bng("lights.high"), Accent.BLUE),
                momentary("bng_underglow", "Underglow", "underglow", 2, 0, bng("action.toggle_underglow"), Accent.CYAN),
                momentary("bng_siren", "Siren mode", "siren", 3, 0, bng("action.toggleLightbarMode"), Accent.RED, visibleWhen = "has.light_bar"),
                momentary("bng_abs", "ABS", "abs", 4, 0, bng("action.toggleABSBehavior"), Accent.AMBER, feedback = "abs.active"),
                rocker("bng_drive_mode", "Drive mode", "esc", 5, 0, bng("action.nextESCMode"), bng("action.previousESCMode"), h = 1),
                momentary("bng_nos", "Nitrous", "nitrous", 6, 0, bng("action.toggleNitrousOxide"), Accent.BLUE),
                momentary("bng_nos_hold", "NOS boost", "nitrous", 7, 0, bng("action.overrideNitrousOxide"), Accent.BLUE),
                momentary("bng_twostep", "Two-step", "flag", 6, 1, bng("action.toggleTwoStep"), Accent.GREEN),
                rocker("bng_twostep_rpm", "Launch rpm", "flag", 7, 1, bng("action.increaseTwoStepRPM"), bng("action.decreaseTwoStepRPM"), h = 2),
                momentary("bng_linelock", "Line lock", "line_lock", 0, 1, bng("action.toggleLineLock"), Accent.AMBER, w = 2),
                Control("bng_transbrake", ControlType.MOMENTARY, "Transbrake", "transbrake", 2, 1, 2, 1, Accent.AMBER, bng("action.toggleTransbrake"), guard = true),
                momentary("bng_cc_off", "Cruise off", "cruise", 4, 1, bng("action.disable_cc"), visibleWhen = "has.cruise"),
                rocker("bng_cc_fine", "Cruise ±1", "cruise", 5, 1, bng("action.increase_cc_speed"), bng("action.decrease_cc_speed"), h = 2, w = 1),
                gauge("bng_speed", "Speed", "speed", 0, 2, "km/h", scale = 3.6, w = 2),
                gauge("bng_rpm", "RPM", "rpm", 2, 2, w = 2),
                gauge("bng_gear", "Gear", "gear", 4, 2),
                gauge("bng_fuel", "Fuel", "fuel", 6, 3, "%", scale = 100.0),
                gauge("bng_cc_target", "Cruise", "cruise.speed", 7, 3, "km/h", scale = 3.6),
                lamp("bng_lamp_esc", "ESC", "esc", "esc.intervening", 0, 3),
                lamp("bng_lamp_tcs", "TC", "tcs", "tcs.intervening", 1, 3),
                lamp("bng_lamp_abs", "ABS", "abs", "abs.active", 2, 3),
                lamp("bng_lamp_pb", "Park brake", "parking_brake", "parkingbrake", 3, 3, Accent.RED),
                lamp("bng_lamp_hi", "High beam", "high_beam", "lights.high", 4, 3, Accent.BLUE),
                lamp("bng_lamp_ign", "Ignition", "ignition", "ignition", 5, 3, Accent.GREEN),
                lamp("bng_lamp_cc", "Cruise on", "cruise", "cruise.active", 6, 2, Accent.GREEN),
            ),
        )
        val world = Page(
            id = "bng_world", name = "World", columns = 8, rows = 4,
            controls = listOf(
                momentary("bng_map", "Map", "map", 0, 0, bng("action.toggleBigMap"), Accent.CYAN),
                momentary("bng_minimap", "Minimap", "minimap", 1, 0, bng("action.toggle_minimap")),
                momentary("bng_radial", "Radial menu", "radial_menu", 2, 0, bng("action.toggleRadialMenuMulti")),
                momentary("bng_walk", "Walk", "walk", 3, 0, bng("action.toggleWalkingMode")),
                rocker("bng_vehicle_sw", "Vehicle", "vehicle", 4, 0, bng("action.switch_next_vehicle"), bng("action.switch_previous_vehicle")),
                momentary("bng_couple", "Coupler", "coupler", 5, 0, bng("action.couplersToggle"), Accent.AMBER),
                momentary("bng_lock_all", "Lock all", "quick_coupler", 6, 0, bng("action.couplersLock")),
                momentary("bng_unlock_all", "Unlock all", "quick_coupler", 7, 0, bng("action.couplersUnlock")),
                momentary("bng_to_road", "To road", "recover", 5, 1, bng("action.recover_vehicle_to_last_road"), Accent.AMBER),
                momentary("bng_save_home", "Save spot", "home_save", 6, 1, bng("action.saveHome")),
                momentary("bng_load_home", "Load spot", "home_load", 7, 1, bng("action.loadHome")),
                momentary("bng_cam1", "Driver cam", "camera", 0, 1, bng("action.camera_1")),
                momentary("bng_cam2", "Orbit cam", "camera", 1, 1, bng("action.camera_2")),
                momentary("bng_free_cam", "Free cam", "camera_free", 2, 1, bng("action.toggleCamera")),
                rocker("bng_zoom", "Zoom", "zoom_in", 3, 1, bng("action.increase_zoom"), bng("action.decrease_zoom"), h = 2),
                momentary("bng_traffic", "Traffic", "traffic", 0, 2, bng("action.toggleTraffic")),
                momentary("bng_ai", "AI drive", "ai_drive", 1, 2, bng("action.toggleAITraffic")),
                momentary("bng_tod", "Time of day", "time_of_day", 2, 2, bng("action.cycleTimeOfDay")),
                rocker("bng_time", "Sim speed", "slowmo", 4, 2, bng("slowmo.faster"), bng("slowmo.slower"), h = 2),
                momentary("bng_hide_ui", "Hide UI", "hide_ui", 5, 2, bng("action.toggleUi")),
                momentary("bng_photo", "Photo mode", "photo_mode", 6, 2, bng("action.photomode")),
                momentary("bng_shot", "Screenshot", "screenshot", 7, 2, bng("action.doScreenShot")),
                momentary("bng_menu", "Menu", "menu", 0, 3, bng("action.toggleMenues")),
                momentary("bng_vehicles", "Vehicles", "vehicle", 1, 3, bng("action.vehicle_selector")),
                momentary("bng_parts", "Parts", "build", 2, 3, bng("action.parts_selector")),
                momentary("bng_interact", "Interact", "target", 3, 3, bng("action.gameplay_interact")),
                Control("bng_fire", ControlType.MOMENTARY, "Ignite", "fire", 5, 3, 1, 1, Accent.RED, bng("action.funFire"), guard = true),
                momentary("bng_extinguish", "Extinguish", "extinguish", 6, 3, bng("action.funExtinguish"), Accent.CYAN),
                Control("bng_reset_all", ControlType.MOMENTARY, "Reset all", "reset", 7, 3, 1, 1, Accent.RED, bng("reset.full"), guard = true),
            ),
        )
        val analog = Page(
            id = "bng_analog", name = "Pedals", columns = 8, rows = 4,
            controls = listOf(
                bngAxis("bng_ax_throttle", "Throttle", "speed", 0, 0, "accelerate", false, Accent.GREEN),
                bngAxis("bng_ax_brake", "Brake", "parking_brake", 1, 0, "brake", false, Accent.RED),
                bngAxis("bng_ax_clutch", "Clutch", "gearbox", 2, 0, "clutch", false, Accent.NEUTRAL),
                bngAxis("bng_ax_steer", "Steering", "steering", 3, 0, "steering", true, Accent.BLUE, w = 5, h = 2),
                momentary("bng_ax_down", "Shift down", "gear_down", 3, 2, bng("gear.down"), h = 2),
                momentary("bng_ax_up", "Shift up", "gear_up", 4, 2, bng("gear.up"), feedback = "gear", h = 2),
                gauge("bng_ax_speed", "Speed", "speed", 5, 2, "km/h", scale = 3.6, w = 2),
                gauge("bng_ax_gear", "Gear", "gear", 7, 2),
                momentary("bng_ax_hand", "Handbrake", "parking_brake", 5, 3, bng("action.parkingbrake_temporary"), Accent.RED, w = 2),
                momentary("bng_ax_horn", "Horn", "horn", 7, 3, bng("horn")),
            ),
        )
        return Profile(
            id = BEAMNG_ID, name = "BeamNG.drive", game = GameId.BEAMNG, builtIn = true, theme = null, themeFollowsVehicle = true,
            pages = listOf(drive, drivetrain, vehicle, more, world, analog), output = OutputRoute.COMPANION,
            processNames = BEAMNG_EXE,
            description = "Every vehicle function with live lights from the game, plus a Vehicle page that fills itself with what " +
                "the car you drive has (tailgate, doors, bed, crane, outriggers...). Needs the PC companion and the BeamNG mod.",
        )
    }

    // ---- BeamNG over Bluetooth: gamepad buttons bound in-game by "Bind in game" -------------

    /**
     * Same functions as [beamng], but every control is a gamepad button that BeamNG reads directly
     * (Bluetooth "SimDeck Pad" or vJoy). "Bind in game" asks the mod to map each button to its
     * BeamNG action once. Lamps still light from game state when the companion and mod are running.
     */
    fun beamngPad(): Profile {
        var n = 0
        fun pad(id: String, label: String, icon: String, x: Int, y: Int, bind: String, accent: Accent = Accent.NEUTRAL, feedback: String? = null, visibleWhen: String? = null, w: Int = 1) =
            Control("bpad_$id", ControlType.MOMENTARY, label, icon, x, y, w, 1, accent, btn(++n), feedbackKey = feedback, visibleWhen = visibleWhen, bindTo = bind)
        val page = Page(
            id = "bpad_main", name = "Vehicle", columns = 8, rows = 4,
            controls = listOf(
                pad("starter", "Start", "starter", 0, 0, "activateStarterMotor", Accent.RED, "engine.running"),
                pad("horn", "Horn", "horn", 1, 0, "horn"),
                pad("hazard", "Hazard", "hazard", 2, 0, "toggle_hazard_signal", Accent.RED, "hazard"),
                pad("sig_l", "Left", "signal_left", 3, 0, "toggle_left_signal", Accent.GREEN, "signal.left"),
                pad("sig_r", "Right", "signal_right", 4, 0, "toggle_right_signal", Accent.GREEN, "signal.right"),
                pad("pbrake", "Park brake", "parking_brake", 5, 0, "parkingbrake_toggle", Accent.RED, "parkingbrake"),
                pad("lights", "Headlights", "low_beam", 6, 0, "toggle_headlights", Accent.GREEN, "lights.low"),
                pad("fog", "Fog", "fog_light", 7, 0, "toggle_foglights", Accent.GREEN, "lights.fog", "has.fog"),
                pad("esc", "ESC / TC", "esc", 0, 1, "toggleESCMode", Accent.AMBER, "esc.active", "has.esc"),
                pad("diff", "Diff mode", "diff_lock", 1, 1, "toggleDiffMode", Accent.AMBER, "diff.rear.locked"),
                pad("awd", "4WD", "awd", 2, 1, "toggle4WDStatus", Accent.GREEN, "awd.active", "has.awd"),
                pad("range", "Low range", "low_range", 3, 1, "toggleRangeStatus", Accent.CYAN, "range.low", "has.range"),
                pad("shifter", "Shift mode", "gearbox", 4, 1, "toggleShifterMode", Accent.CYAN, "gearbox.mode"),
                pad("gear_down", "Shift down", "gear_down", 5, 1, "shiftDown"),
                pad("gear_up", "Shift up", "gear_up", 6, 1, "shiftUp", feedback = "gear"),
                pad("lightbar", "Light bar", "light_bar", 7, 1, "toggle_lightbar_signal", Accent.AMBER, "lights.light_bar", "has.light_bar"),
                pad("cam", "Camera", "camera_cycle", 0, 2, "switch_camera_next", feedback = "camera.mode"),
                pad("look_back", "Look back", "look_back", 1, 2, "look_back"),
                pad("center_cam", "Center cam", "visibility", 2, 2, "center_camera"),
                pad("recover", "Recover", "recover", 3, 2, "recover_vehicle", Accent.AMBER),
                pad("reset", "Reset", "reset", 4, 2, "reset_physics", Accent.RED),
                pad("home", "Load home", "home", 5, 2, "loadHome"),
                pad("save_home", "Save home", "place", 6, 2, "saveHome"),
                pad("reload", "Reload", "build", 7, 2, "reload_vehicle"),
                pad("slowmo", "Slow-mo", "slowmo", 0, 3, "toggle_slow_motion", Accent.CYAN, "game.slowmo"),
                pad("slower", "Slower", "minus", 1, 3, "slower_motion"),
                pad("faster", "Faster", "plus", 2, 3, "faster_motion"),
                pad("pause", "Pause", "pause", 3, 3, "pause", Accent.AMBER, "game.paused"),
                pad("full_reset", "Full reset", "reset", 4, 3, "reset_all_physics", Accent.RED),
            ) + listOf(
                Control("bpad_info_gear", ControlType.MOMENTARY, "Gear", "gearbox", 5, 3, 3, 1, Accent.CYAN,
                    Action.None, feedbackKey = "gear"),
            ),
        )
        return Profile(
            id = BEAMNG_PAD_ID, name = "BeamNG.drive (Bluetooth pad)", game = GameId.BEAMNG, builtIn = true, theme = null, themeFollowsVehicle = true,
            pages = listOf(page), output = OutputRoute.AUTO, processNames = BEAMNG_EXE,
            description = "Every control is a gamepad button. Tap \"Bind in game\" in the editor once (needs the mod) or bind them in BeamNG's Controls menu.",
        )
    }

    // ---- MudRunner: default keyboard layout -------------------------------------------------

    fun mudrunner(): Profile {
        // Fills the whole 8x4 grid: pedals and steering on the left, the three big lit toggles on top,
        // gear / handbrake / lights / horn in the middle row, map / camera / menu along the bottom.
        val page = Page(
            id = "mr_main", name = "Truck", columns = 8, rows = 4,
            controls = listOf(
                momentary("mr_up", "Gas", "arrow_up", 0, 0, key("W"), Accent.GREEN, h = 2),
                momentary("mr_down", "Brake", "arrow_down", 0, 2, key("S"), Accent.RED, h = 2),
                momentary("mr_left", "Left", "arrow_left", 1, 0, key("A"), h = 2),
                momentary("mr_right", "Right", "arrow_right", 1, 2, key("D"), h = 2),
                latching("mr_diff", "Diff lock", "diff_lock", 2, 0, key("Q", trigger = KeyTrigger.TAP), Accent.AMBER, w = 2, h = 2),
                latching("mr_awd", "AWD", "awd", 4, 0, key("E", trigger = KeyTrigger.TAP), Accent.GREEN, w = 2, h = 2),
                momentary("mr_winch", "Winch (hold)", "winch", 6, 0, key("F"), Accent.AMBER, w = 2, h = 2),
                momentary("mr_gear", "Gear (hold)", "gearbox", 2, 2, key("R"), Accent.CYAN, w = 2),
                latching("mr_handbrake", "Handbrake", "parking_brake", 4, 2, key("T", trigger = KeyTrigger.TAP), Accent.RED, w = 2),
                latching("mr_lights", "Lights", "low_beam", 6, 2, key("H", trigger = KeyTrigger.TAP), Accent.GREEN),
                momentary("mr_horn", "Horn", "horn", 7, 2, key("G")),
                momentary("mr_map", "Map", "map", 2, 3, key("M", trigger = KeyTrigger.PRESS), Accent.CYAN, w = 2),
                momentary("mr_camera", "Camera", "camera_cycle", 4, 3, key("C", trigger = KeyTrigger.PRESS), w = 2),
                momentary("mr_escape", "Menu", "menu", 6, 3, key("ESC", trigger = KeyTrigger.PRESS), w = 2),
            ),
        )
        // The crane (log loader) on MudRunner's default keys: hold a rocker half to move that way.
        val crane = Page(
            id = "mr_crane", name = "Crane", columns = 8, rows = 4,
            controls = listOf(
                rocker("mr_cr_extend", "Extend / retract", "boom_extend", 0, 0, key("W"), key("S"), Accent.AMBER, w = 2, h = 4),
                rocker("mr_cr_slew", "Slew R / L", "swing", 2, 0, key("D"), key("A"), Accent.AMBER, w = 2, h = 4),
                rocker("mr_cr_lift", "Lift / lower", "boom", 4, 0, key("E"), key("Q"), Accent.CYAN, w = 2, h = 4),
                rocker("mr_cr_rot", "Rotate grapple", "grapple", 6, 0, key("T"), key("R"), Accent.NEUTRAL, w = 2, h = 2),
                latching("mr_cr_grab", "Grapple", "grapple", 6, 2, key("D4", trigger = KeyTrigger.TAP), Accent.GREEN, w = 2, h = 2),
            ),
        )
        return Profile(
            id = MUDRUNNER_ID, name = "MudRunner", game = GameId.MUDRUNNER, builtIn = true, theme = Themes.OVERLAND,
            pages = listOf(page, crane), output = OutputRoute.AUTO, processNames = listOf("MudRunner.exe"),
            description = "Diff lock, AWD, winch, gears, handbrake, map and camera on MudRunner's default keys. Edit any key to match your bindings.",
        )
    }

    // ---- Heavy machines: pilot joysticks, levers and buttons, bound once in the game ----------
    //
    // Laid out like a real cab: a pilot joystick under each thumb (two spring-centred axes each,
    // ISO pattern on the excavator), extra hydraulics as spring-centred levers, switchgear in the
    // middle. Every axis and button is the Bluetooth pad (or vJoy device 1), so these work in any
    // game with machines once bound in its controls menu. Buttons keep one number per job across
    // machines (1 start, 2 horn, 3 beacon, 4 lights, 5 work lights, 6 park brake, 7/8 gear down/up,
    // 11/12 cameras), so one set of bindings covers several machines. Jobs only one machine has use
    // 17-32 and may share a number with another machine's own job (24 is excavator travel speed and the
    // plough's spreader), since each machine is bound on its own. Every button is 32 or below,
    // the most many DirectInput games and the Windows SimDecks panel can see.

    private fun stick(id: String, label: String, icon: String?, x: Int, y: Int, xAxis: String, yAxis: String, accent: Accent = Accent.AMBER, ends: List<String> = emptyList()) =
        Control(id, ControlType.JOYSTICK, label, icon, x, y, 2, 2, accent, Action.VJoyAxis(1, xAxis), actionY = Action.VJoyAxis(1, yAxis), ends = ends)

    private fun direction(id: String, x: Int, y: Int) =
        Control(id, ControlType.SWITCH, "Direction", "direction", x, y, 3, 1, Accent.CYAN,
            positions = listOf(Position("F", btn(13)), Position("N", btn(14)), Position("R", btn(15))))

    fun bulldozer(): Profile {
        val dozer = Page(
            id = "dz_main", name = "Dozer", columns = 8, rows = 4,
            panels = listOf(PanelGroup("LEFT CONSOLE", 0, 0, 2, 4), PanelGroup("DASH", 2, 0, 4, 4), PanelGroup("RIGHT CONSOLE", 6, 0, 2, 4)),
            controls = listOf(
                stick("dz_drive", "Drive / steer", "tracks", 0, 0, "X", "Y", Accent.GREEN, ends = listOf("LEFT", "RIGHT", "REV", "FWD")),
                throttle("dz_throttle", 0, 2, h = 2),
                momentary("dz_gear_up", "Gear up", "gear_up", 1, 2, btn(8)),
                momentary("dz_gear_down", "Gear down", "gear_down", 1, 3, btn(7)),
                momentary("dz_start", "Start", "starter", 2, 0, btn(1), Accent.RED),
                momentary("dz_horn", "Horn", "horn", 3, 0, btn(2)),
                latching("dz_beacon", "Beacon", "beacon", 4, 0, btn(3), Accent.AMBER),
                latching("dz_lights", "Lights", "low_beam", 2, 1, btn(4), Accent.GREEN),
                latching("dz_work", "Work lights", "work_light", 3, 1, btn(5), Accent.AMBER),
                latching("dz_pbrake", "Park brake", "parking_brake", 4, 1, btn(6), Accent.RED),
                direction("dz_direction", 2, 2),
                latching("dz_float", "Blade float", "float_mode", 2, 3, btn(9), Accent.CYAN),
                latching("dz_autoshift", "Auto shift", "gearbox", 3, 3, btn(16), Accent.GREEN),
                momentary("dz_cam", "Camera", "camera_cycle", 4, 3, btn(11)),
                lever("dz_ripper", "Ripper", "ripper", 5, 0, "SL1", Accent.AMBER),
                stick("dz_blade", "Blade lift / tilt", "blade", 6, 0, "RY", "RX", ends = listOf("TILT L", "TILT R", "RAISE", "LOWER")),
                lever("dz_angle", "Blade angle", "blade_angle", 6, 2, "RZ", w = 2, h = 1),
                momentary("dz_shank", "Ripper pin", "ripper", 6, 3, btn(10), Accent.AMBER),
                momentary("dz_rear_cam", "Rear view", "look_back", 7, 3, btn(12)),
            ),
        )
        return Profile(
            id = BULLDOZER_ID, name = "Bulldozer", game = GameId.CUSTOM, builtIn = true, theme = Themes.HEAVY,
            pages = listOf(dozer), output = OutputRoute.AUTO,
            description = "A dozer cab: drive joystick on the left, blade joystick on the right, ripper and blade-angle levers, throttle and switchgear. " +
                "Bind once in the game's controls menu (turn on the Bind helper in the editor so each stick axis is captured on its own). " +
                "With vJoy, set device 1 to 32 buttons and all 8 axes in Configure vJoy. In BeamNG, use the BeamNG profile's Vehicle page instead: no binding needed.",
        )
    }

    fun utility(): Profile {
        val loader = Page(
            id = "ut_loader", name = "Loader", columns = 8, rows = 4,
            panels = listOf(PanelGroup("LEFT CONSOLE", 0, 0, 2, 4), PanelGroup("DASH", 2, 0, 4, 4), PanelGroup("RIGHT CONSOLE", 6, 0, 2, 4)),
            controls = listOf(
                stick("ld_drive", "Drive / steer", "arrow_up", 0, 0, "X", "Y", Accent.GREEN),
                throttle("ld_throttle", 0, 2, h = 2),
                momentary("ld_gear_up", "Gear up", "gear_up", 1, 2, btn(8)),
                momentary("ld_gear_down", "Gear down", "gear_down", 1, 3, btn(7)),
                momentary("ld_start", "Start", "starter", 2, 0, btn(1), Accent.RED),
                momentary("ld_horn", "Horn", "horn", 3, 0, btn(2)),
                latching("ld_beacon", "Beacon", "beacon", 4, 0, btn(3), Accent.AMBER),
                latching("ld_lights", "Lights", "low_beam", 5, 0, btn(4), Accent.GREEN),
                latching("ld_pbrake", "Park brake", "parking_brake", 2, 1, btn(6), Accent.RED),
                latching("ld_diff", "Diff lock", "diff_lock", 3, 1, btn(32), Accent.AMBER),
                latching("ld_float", "Float", "float_mode", 4, 1, btn(9), Accent.CYAN),
                latching("ld_coupler", "Coupler", "quick_coupler", 5, 1, btn(19), Accent.AMBER),
                momentary("ld_return", "Return to dig", "bucket", 2, 2, btn(17), Accent.CYAN),
                momentary("ld_cam", "Camera", "camera_cycle", 3, 2, btn(11)),
                momentary("ld_rear_cam", "Rear view", "look_back", 4, 2, btn(12)),
                latching("ld_work", "Work lights", "work_light", 5, 2, btn(5), Accent.AMBER),
                direction("ld_direction", 2, 3),
                momentary("ld_kickdown", "Kickdown", "gear_down", 5, 3, btn(18)),
                stick("ld_bucket", "Boom / bucket", "bucket", 6, 0, "RY", "RX", ends = listOf("CURL", "DUMP", "RAISE", "LOWER")),
                stick("ld_aux", "Attachment", "quick_coupler", 6, 2, "RZ", "SL1", Accent.CYAN),
            ),
        )
        val excavator = Page(
            id = "ut_excavator", name = "Excavator", columns = 8, rows = 4,
            panels = listOf(PanelGroup("LEFT CONSOLE", 0, 0, 4, 4), PanelGroup("DASH", 4, 0, 2, 4), PanelGroup("RIGHT CONSOLE", 6, 0, 2, 4)),
            controls = listOf(
                lever("ex_left", "Left track", "tracks", 0, 0, "Y", Accent.GREEN),
                lever("ex_right", "Right track", "tracks", 1, 0, "Z", Accent.GREEN),
                stick("ex_left_stick", "Swing / stick", "swing", 2, 0, "X", "RY", ends = listOf("SWING L", "SWING R", "STICK IN", "STICK OUT")),
                momentary("ex_start", "Start", "starter", 4, 0, btn(1), Accent.RED),
                momentary("ex_horn", "Horn", "horn", 5, 0, btn(2)),
                latching("ex_beacon", "Beacon", "beacon", 4, 1, btn(3), Accent.AMBER),
                latching("ex_lights", "Lights", "low_beam", 5, 1, btn(4), Accent.GREEN),
                stick("ex_right_stick", "Boom / bucket", "boom", 6, 0, "SL1", "RX", ends = listOf("CURL", "DUMP", "BOOM UP", "BOOM DOWN")),
                latching("ex_work", "Work lights", "work_light", 2, 2, btn(5), Accent.AMBER),
                momentary("ex_hammer", "Hammer", "bucket", 3, 2, btn(20), Accent.RED),
                latching("ex_coupler", "Coupler", "quick_coupler", 4, 2, btn(19), Accent.AMBER),
                momentary("ex_cam", "Camera", "camera_cycle", 5, 2, btn(11)),
                lever("ex_aux", "Aux flow", "stick", 2, 3, "RZ", Accent.CYAN, w = 2, h = 1),
                latching("ex_hispeed", "Travel speed", "tracks", 4, 3, btn(24), Accent.GREEN),
                momentary("ex_rear_cam", "Rear view", "look_back", 5, 3, btn(12)),
                Control("ex_rpm", ControlType.ROTARY, "Engine speed", "speed", 6, 2, 2, 2, Accent.CYAN, cw = btn(25), ccw = btn(26)),
            ),
        )
        val crane = Page(
            id = "ut_crane", name = "Crane", columns = 8, rows = 4,
            panels = listOf(PanelGroup("LEFT CONSOLE", 0, 0, 2, 4), PanelGroup("DASH", 2, 0, 4, 4), PanelGroup("RIGHT CONSOLE", 6, 0, 2, 4)),
            controls = listOf(
                stick("cr_left_stick", "Slew / hoist", "hook", 0, 0, "X", "SL1", ends = listOf("SLEW L", "SLEW R", "HOIST UP", "LOWER")),
                stick("cr_drive", "Drive / steer", "arrow_up", 0, 2, "Z", "Y", Accent.GREEN),
                momentary("cr_out_out", "Outriggers out", "outriggers", 2, 0, btn(21), Accent.AMBER),
                momentary("cr_out_in", "Outriggers in", "outriggers", 3, 0, btn(22)),
                latching("cr_lock", "Load lock", "hook", 4, 0, btn(23), Accent.RED),
                momentary("cr_start", "Start", "starter", 5, 0, btn(1), Accent.RED),
                momentary("cr_horn", "Horn", "horn", 2, 1, btn(2)),
                latching("cr_beacon", "Beacon", "beacon", 3, 1, btn(3), Accent.AMBER),
                latching("cr_lights", "Lights", "low_beam", 4, 1, btn(4), Accent.GREEN),
                latching("cr_pbrake", "Park brake", "parking_brake", 5, 1, btn(6), Accent.RED),
                momentary("cr_gear_down", "Gear down", "gear_down", 2, 2, btn(7)),
                momentary("cr_gear_up", "Gear up", "gear_up", 3, 2, btn(8)),
                momentary("cr_cam", "Camera", "camera_cycle", 4, 2, btn(11)),
                momentary("cr_hook_cam", "Hook view", "camera", 5, 2, btn(10)),
                lever("cr_rotate", "Hook rotate", "hook", 2, 3, "RZ", Accent.NEUTRAL, w = 4, h = 1),
                stick("cr_right_stick", "Boom / extend", "boom", 6, 0, "RY", "RX", ends = listOf("RETRACT", "EXTEND", "BOOM UP", "BOOM DOWN")),
                throttle("cr_throttle", 6, 2, h = 2),
                latching("cr_work", "Work lights", "work_light", 7, 2, btn(5), Accent.AMBER),
                momentary("cr_rear_cam", "Rear view", "look_back", 7, 3, btn(12)),
            ),
        )
        val plough = Page(
            id = "ut_plough", name = "Plough & tow", columns = 8, rows = 4,
            panels = listOf(PanelGroup("LEFT CONSOLE", 0, 0, 2, 4), PanelGroup("DASH", 2, 0, 4, 4), PanelGroup("RIGHT CONSOLE", 6, 0, 2, 4)),
            controls = listOf(
                stick("pl_plough", "Plough lift / angle", "plough", 0, 0, "Z", "RX"),
                Control("pl_rate", ControlType.ROTARY, "Spread rate", "spreader", 0, 2, 2, 2, Accent.CYAN, cw = btn(25), ccw = btn(26)),
                latching("pl_beacon", "Beacon", "beacon", 2, 0, btn(3), Accent.AMBER),
                latching("pl_lights", "Lights", "low_beam", 3, 0, btn(4), Accent.GREEN),
                latching("pl_work", "Work lights", "work_light", 4, 0, btn(5), Accent.AMBER),
                momentary("pl_horn", "Horn", "horn", 5, 0, btn(2)),
                latching("pl_spreader", "Spreader", "spreader", 2, 1, btn(24), Accent.CYAN),
                latching("pl_float", "Plough float", "float_mode", 3, 1, btn(31), Accent.CYAN),
                momentary("pl_wheel_up", "Wheel lift up", "tow", 4, 1, btn(27), Accent.AMBER),
                momentary("pl_wheel_down", "Wheel lift down", "tow", 5, 1, btn(28)),
                momentary("pl_winch_in", "Winch in", "winch", 2, 2, btn(29), Accent.AMBER),
                momentary("pl_winch_out", "Winch out", "winch", 3, 2, btn(30)),
                latching("pl_pbrake", "Park brake", "parking_brake", 4, 2, btn(6), Accent.RED),
                momentary("pl_cam", "Camera", "camera_cycle", 5, 2, btn(11)),
                momentary("pl_rear_cam", "Rear view", "look_back", 2, 3, btn(12)),
                stick("pl_tow", "Tow boom / extend", "tow", 6, 0, "RY", "SL1"),
                stick("pl_wing", "Wing plough", "plough", 6, 2, "RZ", "X", Accent.CYAN),
            ),
        )
        return Profile(
            id = UTILITY_ID, name = "Utility vehicles", game = GameId.CUSTOM, builtIn = true, theme = Themes.HEAVY,
            pages = listOf(loader, excavator, crane, plough), output = OutputRoute.AUTO,
            description = "Wheel loader, excavator (ISO joysticks), mobile crane, snow plough and tow truck, a page each. Bind once in the game's controls menu " +
                "(Bind helper on, so each stick axis is captured on its own). With vJoy, set device 1 to 32 buttons and all 8 axes in Configure vJoy. " +
                "In BeamNG, use the BeamNG profile's Vehicle page instead: no binding needed.",
        )
    }
    // ---- More games: default keys where they are known, gamepad buttons (Bluetooth pad or vJoy) where not ----

    fun snowrunner(): Profile {
        val drive = Page(
            id = "sr_drive", name = "Truck", columns = 8, rows = 4,
            controls = listOf(
                latching("sr_awd", "AWD", "awd", 0, 0, key("E", trigger = KeyTrigger.TAP), Accent.GREEN, w = 2, h = 2),
                latching("sr_diff", "Diff lock", "diff_lock", 2, 0, key("Q", trigger = KeyTrigger.TAP), Accent.AMBER, w = 2, h = 2),
                momentary("sr_winch", "Quick winch", "winch", 4, 0, key("F", trigger = KeyTrigger.PRESS), Accent.AMBER),
                momentary("sr_winch_rel", "Release winch", "winch_release", 5, 0, key("R", trigger = KeyTrigger.PRESS)),
                latching("sr_engine", "Engine", "starter", 6, 0, key("B", trigger = KeyTrigger.TAP), Accent.RED),
                latching("sr_lights", "Lights", "low_beam", 7, 0, key("L", trigger = KeyTrigger.TAP), Accent.GREEN),
                momentary("sr_horn", "Horn", "horn", 4, 1, key("G")),
                latching("sr_hb", "Handbrake", "parking_brake", 5, 1, key("SPACE", trigger = KeyTrigger.TAP), Accent.RED),
                momentary("sr_clutch", "Clutch", "clutch", 6, 1, key("LSHIFT")),
                momentary("sr_map", "Map", "map", 7, 1, key("M", trigger = KeyTrigger.PRESS), Accent.CYAN),
                Control("sr_gearbox", ControlType.RADIO, "Gearbox", "gearbox", 0, 2, 6, 1, Accent.CYAN, positions = listOf(
                    Position("R", btn(1)), Position("N", btn(2)), Position("A", btn(3)), Position("H", btn(4)), Position("L", btn(5)), Position("L+", btn(6)),
                )),
                momentary("sr_gear_down", "Gear down", "gear_down", 6, 2, btn(7)),
                momentary("sr_gear_up", "Gear up", "gear_up", 7, 2, btn(8)),
                momentary("sr_functions", "Functions", "menu", 0, 3, key("V", trigger = KeyTrigger.PRESS)),
                momentary("sr_trailer", "Trailer", "trailer", 1, 3, btn(9)),
                momentary("sr_cargo", "Load cargo", "inventory", 2, 3, btn(10)),
                momentary("sr_cam", "Camera", "camera_cycle", 3, 3, btn(11)),
                momentary("sr_profile", "Profile", "star", 4, 3, key("F4", trigger = KeyTrigger.PRESS)),
                momentary("sr_menu", "Menu", "menu", 5, 3, key("ESC", trigger = KeyTrigger.PRESS), w = 3),
            ),
        )
        val addons = Page(
            id = "sr_addons", name = "Add-ons", columns = 8, rows = 4,
            controls = (1..8).map { n ->
                val k = listOf("D4", "D5", "D6", "D7", "D8", "D9", "D0", "MINUS")[n - 1]
                momentary("sr_addon$n", "Add-on $n", "pto", ((n - 1) % 4) * 2, ((n - 1) / 4) * 2, key(k, trigger = KeyTrigger.PRESS), Accent.AMBER, w = 2, h = 2)
            },
        )
        return Profile(
            id = SNOWRUNNER_ID, name = "SnowRunner", game = GameId.SNOWRUNNER, builtIn = true, theme = Themes.OVERLAND,
            pages = listOf(drive, addons), output = OutputRoute.AUTO, processNames = listOf("SnowRunner.exe"),
            description = "AWD, diff lock, winch, engine, lights and the 8 add-on actions on SnowRunner's default keys. Gearbox positions are gamepad buttons 1-11: bind them once in Options > Controls.",
        )
    }

    fun expeditions(): Profile {
        val p = Page(
            id = "ex_main", name = "Truck", columns = 8, rows = 4,
            controls = listOf(
                latching("exp_awd", "AWD", "awd", 0, 0, btn(1), Accent.GREEN, w = 2, h = 2),
                latching("exp_diff", "Diff lock", "diff_lock", 2, 0, btn(2), Accent.AMBER, w = 2, h = 2),
                momentary("exp_low", "Low gear", "low_range", 4, 0, btn(3), Accent.CYAN),
                momentary("exp_high", "High gear", "high_range", 5, 0, btn(4), Accent.CYAN),
                momentary("exp_rev", "Reverse", "gear_r", 6, 0, btn(5)),
                momentary("exp_gear_up", "Gear up", "gear_up", 7, 0, btn(6)),
                latching("exp_hb", "Handbrake", "parking_brake", 4, 1, btn(7), Accent.RED),
                latching("exp_lights", "Lights", "low_beam", 5, 1, btn(8), Accent.GREEN),
                momentary("exp_horn", "Horn", "horn", 6, 1, btn(9)),
                momentary("exp_gear_down", "Gear down", "gear_down", 7, 1, btn(10)),
                momentary("exp_winch", "Winch", "winch", 0, 2, btn(11), Accent.AMBER, w = 2),
                momentary("exp_winch_rel", "Release", "winch_release", 2, 2, btn(12), w = 2),
                rocker("exp_tyres", "Tyre pressure", "tire_pressure", 4, 2, btn(13), btn(14)),
                momentary("exp_anchor", "Anchor", "coupler", 5, 2, btn(15)),
                momentary("exp_map", "Map", "map", 6, 2, btn(16), Accent.CYAN),
                momentary("exp_drone", "Drone", "plane", 7, 2, btn(17)),
                momentary("exp_scout", "Scout", "navigation", 0, 3, btn(18)),
                momentary("exp_binoc", "Binoculars", "zoom_in", 1, 3, btn(19)),
                momentary("exp_tools", "Tools", "build", 2, 3, btn(20)),
                momentary("exp_cam", "Camera", "camera_cycle", 3, 3, btn(21)),
                momentary("exp_radio", "Radio", "radio", 5, 3, btn(22)),
                latching("exp_engine", "Engine", "starter", 6, 3, btn(23), Accent.RED, w = 2),
            ),
        )
        return Profile(
            id = EXPEDITIONS_ID, name = "Expeditions: A MudRunner Game", game = GameId.EXPEDITIONS, builtIn = true, theme = Themes.OVERLAND,
            pages = listOf(p), output = OutputRoute.AUTO, processNames = listOf("Expeditions.exe", "ExpeditionsMudRunner.exe"),
            description = "Every control is a gamepad button (1-23), so it works with any in-game layout: in Options > Controls pick an action and press the SimDeck button once.",
        )
    }

    fun scs(id: String, name: String, game: GameId, exe: String): Profile {
        val g = game.name.lowercase()
        val cab = Page(
            id = "${game.name.lowercase()}_cab", name = "Cab", columns = 8, rows = 4,
            controls = listOf(
                latching("${g}_left", "Left", "signal_left", 0, 0, key("LBRACKET", trigger = KeyTrigger.TAP), Accent.GREEN),
                latching("${g}_hazard", "Hazard", "hazard", 1, 0, key("F", trigger = KeyTrigger.TAP), Accent.RED),
                latching("${g}_right", "Right", "signal_right", 2, 0, key("RBRACKET", trigger = KeyTrigger.TAP), Accent.GREEN),
                momentary("${g}_lights", "Lights", "low_beam", 3, 0, key("L", trigger = KeyTrigger.PRESS), Accent.GREEN),
                latching("${g}_high", "High beam", "high_beam", 4, 0, key("K", trigger = KeyTrigger.TAP), Accent.BLUE),
                momentary("${g}_flash", "Flash", "high_beam", 5, 0, key("J")),
                latching("${g}_beacon", "Beacon", "beacon", 6, 0, key("O", trigger = KeyTrigger.TAP), Accent.AMBER),
                momentary("${g}_horn", "Horn", "horn", 7, 0, key("H")),
                Control("${g}_engine", ControlType.LATCHING, "Engine", "starter", 0, 1, 1, 1, Accent.RED, key("E", trigger = KeyTrigger.TAP), guard = true),
                latching("${g}_pbrake", "Park brake", "parking_brake", 1, 1, key("SPACE", trigger = KeyTrigger.TAP), Accent.RED),
                latching("${g}_ebrake", "Engine brake", "engine_brake", 2, 1, key("B", trigger = KeyTrigger.TAP), Accent.AMBER),
                latching("${g}_cruise", "Cruise", "cruise", 3, 1, key("C", trigger = KeyTrigger.TAP), Accent.GREEN),
                rocker("${g}_cc", "Cruise ±", "cruise", 4, 1, btn(1), btn(2), h = 1),
                momentary("${g}_cc_resume", "Resume", "replay", 5, 1, btn(3)),
                momentary("${g}_wipers", "Wipers", "wiper", 6, 1, key("P", trigger = KeyTrigger.PRESS)),
                momentary("${g}_trailer", "Trailer", "trailer", 7, 1, key("T", trigger = KeyTrigger.PRESS), Accent.AMBER),
                Control("${g}_retarder", ControlType.ROTARY, "Retarder", "retarder", 0, 2, 2, 2, Accent.CYAN, cw = btn(4), ccw = btn(5)),
                latching("${g}_diff", "Diff lock", "diff_lock", 2, 2, btn(6), Accent.AMBER),
                latching("${g}_lift", "Lift axle", "lift_axle", 3, 2, btn(7), Accent.CYAN),
                latching("${g}_tlift", "Trailer axle", "lift_axle", 4, 2, btn(8), Accent.CYAN),
                momentary("${g}_range", "Range", "low_range", 5, 2, btn(9)),
                momentary("${g}_splitter", "Splitter", "gearbox", 6, 2, btn(10)),
                momentary("${g}_dash", "Dashboard", "speed", 7, 2, key("I", trigger = KeyTrigger.PRESS)),
                momentary("${g}_down", "Shift down", "gear_down", 2, 3, key("LCTRL", trigger = KeyTrigger.PRESS)),
                momentary("${g}_up", "Shift up", "gear_up", 3, 3, key("LSHIFT", trigger = KeyTrigger.PRESS)),
                momentary("${g}_map", "Map", "map", 4, 3, key("M", trigger = KeyTrigger.PRESS), Accent.CYAN),
                momentary("${g}_mirrors", "Mirrors", "mirror", 5, 3, key("F2", trigger = KeyTrigger.PRESS)),
                momentary("${g}_advisor", "Advisor", "navigation", 6, 3, key("F3", trigger = KeyTrigger.PRESS)),
                momentary("${g}_radio", "Radio", "radio", 7, 3, key("R", trigger = KeyTrigger.PRESS)),
            ),
        )
        val cams = Page(
            id = "${game.name.lowercase()}_cams", name = "Cameras", columns = 8, rows = 4,
            controls = listOf("Interior", "Chase", "Top", "Roof", "Lean", "Bumper", "Wheel", "Cinematic").mapIndexed { i, label ->
                momentary("${g}_cam${i + 1}", label, "camera", (i % 4) * 2, (i / 4) * 2, key("D${i + 1}", trigger = KeyTrigger.PRESS), w = 2, h = 2)
            },
        )
        return Profile(
            id = id, name = name, game = game, builtIn = true, theme = Themes.COMMERCIAL, pages = listOf(cab, cams), output = OutputRoute.AUTO, processNames = listOf(exe),
            description = "Lights, signals, beacon, cruise, engine and trailer on the game's default keys. Retarder, diff lock, lift axles, range and splitter are gamepad buttons 1-10: bind them once in Options > Controls.",
        )
    }

    fun fs25(): Profile {
        val tractor = Page(
            id = "fs_tractor", name = "Tractor", columns = 8, rows = 4,
            controls = listOf(
                latching("fs_engine", "Engine", "starter", 0, 0, btn(1), Accent.RED),
                momentary("fs_lights", "Lights", "low_beam", 1, 0, btn(2), Accent.GREEN),
                latching("fs_beacon", "Beacon", "beacon", 2, 0, btn(3), Accent.AMBER),
                latching("fs_left", "Left", "signal_left", 3, 0, btn(4), Accent.GREEN),
                latching("fs_hazard", "Hazard", "hazard", 4, 0, btn(5), Accent.RED),
                latching("fs_right", "Right", "signal_right", 5, 0, btn(6), Accent.GREEN),
                momentary("fs_horn", "Horn", "horn", 6, 0, btn(7)),
                latching("fs_pbrake", "Park brake", "parking_brake", 7, 0, btn(8), Accent.RED),
                latching("fs_cruise", "Cruise", "cruise", 0, 1, btn(9), Accent.GREEN),
                rocker("fs_cc", "Cruise ±", "cruise", 1, 1, btn(10), btn(11), h = 1),
                momentary("fs_dir", "Direction", "direction", 2, 1, btn(12)),
                latching("fs_diff", "Diff lock", "diff_lock", 3, 1, btn(13), Accent.AMBER),
                latching("fs_awd", "4WD", "awd", 4, 1, btn(14), Accent.GREEN),
                latching("fs_gps", "Steering assist", "steering", 5, 1, btn(15), Accent.CYAN),
                latching("fs_worker", "Worker", "ai_drive", 6, 1, btn(16), Accent.CYAN),
                momentary("fs_enter", "Enter / exit", "walk", 7, 1, btn(17)),
                momentary("fs_lower_back", "Rear lower", "implement_down", 0, 2, btn(18), Accent.AMBER, w = 2),
                momentary("fs_lower_front", "Front lower", "implement_down", 2, 2, btn(19), Accent.AMBER, w = 2),
                latching("fs_onoff", "Turn on", "pto", 4, 2, btn(20), Accent.GREEN, w = 2),
                momentary("fs_fold", "Fold", "crane", 6, 2, btn(21)),
                momentary("fs_attach", "Attach", "coupler", 7, 2, btn(22), Accent.AMBER),
                momentary("fs_select", "Select tool", "swing", 0, 3, btn(23)),
                momentary("fs_next_veh", "Next vehicle", "vehicle", 1, 3, btn(24)),
                momentary("fs_cam", "Camera", "camera_cycle", 2, 3, btn(25)),
                momentary("fs_map", "Map", "map", 3, 3, btn(26), Accent.CYAN),
                momentary("fs_tip", "Tip", "ramp", 4, 3, btn(27)),
                momentary("fs_pipe", "Pipe", "crane", 5, 3, btn(28)),
                momentary("fs_cover", "Cover", "tailgate", 6, 3, btn(29)),
                momentary("fs_ridge", "Ridge markers", "flag", 7, 3, btn(30)),
            ),
        )
        val loader = Page(
            id = "fs_loader", name = "Loader", columns = 8, rows = 4,
            controls = listOf(
                stick("fs_arm", "Front loader", "bucket", 0, 0, "RX", "RY", ends = listOf("CURL", "DUMP", "RAISE", "LOWER")),
                stick("fs_drive", "Drive / steer", "steering", 0, 2, "X", "Y", Accent.GREEN),
                throttle("fs_throttle", 2, 0),
                lever("fs_aux", "3rd function", "quick_coupler", 3, 0, "RZ"),
                momentary("fs_l_lower_front", "Front lower", "implement_down", 4, 0, btn(19), Accent.AMBER, w = 2, h = 2),
                latching("fs_l_onoff", "Turn on", "pto", 6, 0, btn(20), Accent.GREEN, w = 2, h = 2),
                momentary("fs_l_attach", "Attach", "coupler", 4, 2, btn(22), Accent.AMBER, w = 2, h = 2),
                momentary("fs_l_tip", "Tip", "ramp", 6, 2, btn(27), w = 2, h = 2),
            ),
        )
        return Profile(
            id = FS25_ID, name = "Farming Simulator 25", game = GameId.FS25, builtIn = true, theme = Themes.AGRICULTURE,
            pages = listOf(tractor, loader), output = OutputRoute.AUTO,
            processNames = listOf("FarmingSimulator2025.exe", "FarmingSimulator2025Game.exe"),
            description = "Tractor and implement controls as gamepad buttons 1-30 and axes. Bind them once in Options > Controls (Bind helper on for the loader stick).",
        )
    }

    fun acc(): Profile {
        val box = Page(
            id = "acc_box", name = "Wheel box", columns = 8, rows = 4,
            controls = listOf(
                Control("acc_ign", ControlType.LATCHING, "Ignition", "ignition", 0, 0, 1, 1, Accent.AMBER, btn(1), guard = true),
                momentary("acc_start", "Starter", "starter", 1, 0, btn(2), Accent.RED),
                latching("acc_pit", "Pit limiter", "speed_limiter", 2, 0, btn(3), Accent.AMBER, w = 2),
                momentary("acc_lights", "Lights", "low_beam", 4, 0, btn(4), Accent.GREEN),
                momentary("acc_flash", "Flash", "high_beam", 5, 0, btn(5), Accent.BLUE),
                latching("acc_rain", "Rain light", "fog_rear", 6, 0, btn(6), Accent.RED),
                momentary("acc_wipers", "Wipers", "wiper", 7, 0, btn(7)),
                Control("acc_tc", ControlType.ROTARY, "TC", "tcs", 0, 1, 2, 2, Accent.CYAN, cw = btn(8), ccw = btn(9)),
                Control("acc_abs", ControlType.ROTARY, "ABS", "abs", 2, 1, 2, 2, Accent.CYAN, cw = btn(10), ccw = btn(11)),
                Control("acc_map", ControlType.ROTARY, "Engine map", "engine", 4, 1, 2, 2, Accent.AMBER, cw = btn(12), ccw = btn(13)),
                Control("acc_bias", ControlType.ROTARY, "Brake bias", "parking_brake", 6, 1, 2, 2, Accent.RED, cw = btn(14), ccw = btn(15)),
                momentary("acc_mfd", "MFD page", "settings", 0, 3, btn(16)),
                rocker("acc_mfd_ud", "MFD ↑↓", "arrow_up", 1, 3, btn(17), btn(18), h = 1),
                rocker("acc_mfd_lr", "MFD +/-", "plus", 2, 3, btn(19), btn(20), h = 1),
                momentary("acc_cam", "Camera", "camera_cycle", 3, 3, btn(21)),
                momentary("acc_hud", "HUD", "hide_ui", 4, 3, btn(22)),
                momentary("acc_left", "Left", "signal_left", 5, 3, btn(23), Accent.GREEN),
                momentary("acc_right", "Right", "signal_right", 6, 3, btn(24), Accent.GREEN),
                momentary("acc_talk", "Talk", "mic", 7, 3, btn(25)),
            ),
        )
        return Profile(
            id = ACC_ID, name = "Assetto Corsa Competizione", game = GameId.ACC, builtIn = true, theme = Themes.MOTORSPORT,
            pages = listOf(box), output = OutputRoute.AUTO, processNames = listOf("AC2-Win64-Shipping.exe"),
            description = "A race button box: ignition (covered), starter, pit limiter, lights, and TC / ABS / engine map / brake bias rotaries. Gamepad buttons 1-25: bind them once in Controls.",
        )
    }

    fun msfs(): Profile {
        val cockpit = Page(
            id = "msfs_cockpit", name = "Cockpit", columns = 8, rows = 4,
            panels = listOf(
                PanelGroup("GEAR", 0, 0, 1, 2), PanelGroup("ENGINE & ELEC", 2, 0, 6, 1), PanelGroup("EXT LIGHTS", 2, 1, 5, 1),
                PanelGroup("AUTOPILOT", 0, 2, 7, 1), PanelGroup("VIEWS", 0, 3, 5, 1),
            ),
            controls = listOf(
                Control("msfs_gear", ControlType.LATCHING, "Gear", "landing_gear", 0, 0, 1, 2, Accent.GREEN, btn(1), skin = "gear-lever", legends = listOf("UP", "DN")),
                rocker("msfs_flaps", "Flaps", "flaps", 1, 0, btn(2), btn(3)),
                latching("msfs_spoilers", "Spoilers", "flaps", 2, 0, btn(4), Accent.AMBER),
                latching("msfs_pbrake", "Park brake", "parking_brake", 3, 0, btn(5), Accent.RED),
                latching("msfs_battery", "Battery", "battery", 4, 0, btn(6), Accent.CYAN).copy(legends = listOf("FAULT", "OFF")),
                latching("msfs_avionics", "Avionics", "power", 5, 0, btn(7), Accent.GREEN),
                latching("msfs_fuelpump", "Fuel pump", "fuel", 6, 0, btn(8), Accent.CYAN).copy(legends = listOf("LO PR", "ON")),
                momentary("msfs_starter", "Starter", "starter", 7, 0, btn(9), Accent.RED),
                latching("msfs_nav", "Nav", "beacon_air", 2, 1, btn(10), Accent.GREEN),
                latching("msfs_beacon", "Beacon", "beacon", 3, 1, btn(11), Accent.RED),
                latching("msfs_strobe", "Strobe", "beacon_air", 4, 1, btn(12), Accent.CYAN),
                latching("msfs_taxi", "Taxi", "low_beam", 5, 1, btn(13), Accent.GREEN),
                latching("msfs_landing", "Landing", "high_beam", 6, 1, btn(14), Accent.BLUE),
                rocker("msfs_trim", "Trim", "swing", 7, 1, btn(15), btn(16)),
                latching("msfs_ap", "AP master", "autopilot", 0, 2, btn(17), Accent.GREEN, w = 2).copy(legends = listOf("", "ON")),
                latching("msfs_hdg", "HDG", "navigation", 2, 2, btn(18), Accent.CYAN),
                latching("msfs_alt", "ALT", "arrow_up", 3, 2, btn(19), Accent.CYAN),
                latching("msfs_navm", "NAV", "map", 4, 2, btn(20), Accent.CYAN),
                latching("msfs_appr", "APPR", "plane", 5, 2, btn(21), Accent.CYAN),
                latching("msfs_vs", "VS", "arrow_down", 6, 2, btn(22), Accent.CYAN),
                momentary("msfs_cam", "Camera", "camera_cycle", 0, 3, btn(23)),
                momentary("msfs_cockpit_view", "Cockpit", "visibility", 1, 3, btn(24)),
                momentary("msfs_ext", "External", "plane", 2, 3, btn(25)),
                momentary("msfs_atc", "ATC", "chat", 3, 3, btn(26)),
                latching("msfs_pause", "Pause", "pause", 4, 3, btn(27), Accent.AMBER),
            ),
        )
        val levers = Page(
            id = "msfs_levers", name = "Levers", columns = 8, rows = 4,
            panels = listOf(PanelGroup("PEDESTAL", 0, 0, 4, 4), PanelGroup("FLIGHT CONTROLS", 4, 0, 4, 3)),
            controls = listOf(
                throttle("msfs_throttle", 0, 0, "Z").copy(stations = listOf(Station(0f, "IDLE"), Station(0.62f, "CL"), Station(0.82f, "FLX"), Station(1f, "TOGA"))),
                Control("msfs_prop", ControlType.SLIDER, "Prop", "swing", 1, 0, 1, 4, Accent.BLUE, Action.VJoyAxis(1, "SL0", AxisRange.UNIPOLAR), range = AxisRange.UNIPOLAR),
                Control("msfs_mixture", ControlType.SLIDER, "Mixture", "fuel", 2, 0, 1, 4, Accent.RED, Action.VJoyAxis(1, "SL1", AxisRange.UNIPOLAR), range = AxisRange.UNIPOLAR),
                Control("msfs_flaps_ax", ControlType.SLIDER, "Flaps", "flaps", 3, 0, 1, 4, Accent.CYAN, Action.VJoyAxis(1, "RZ", AxisRange.UNIPOLAR), range = AxisRange.UNIPOLAR, detents = 4),
                stick("msfs_yoke", "Yoke", "plane", 4, 0, "X", "Y", Accent.AMBER),
                lever("msfs_rudder", "Rudder", "swing", 4, 2, "RX", w = 4, h = 1),
                rocker("msfs_trim2", "Trim", "swing", 6, 0, btn(15), btn(16), w = 2, h = 2),
            ),
        )
        return Profile(
            id = MSFS_ID, name = "Microsoft Flight Simulator", game = GameId.MSFS, builtIn = true, theme = Themes.AIRLINER,
            pages = listOf(cockpit, levers), output = OutputRoute.AUTO, processNames = listOf("FlightSimulator.exe", "FlightSimulator2024.exe"),
            description = "Gear (covered), flaps, lights, autopilot and engine switches as gamepad buttons 1-27; throttle, prop, mixture and flap levers on axes. Bind them once in Controls Options.",
        )
    }
}
