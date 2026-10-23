package com.simdeck.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Generated from the icon sources in the UI designer's icon sheet: 24dp grid, 2dp round stroke,
// shapes after the ISO 2575 tell-tales drivers already know. Edit the paths here directly.

data class IconEntry(val id: String, val name: String, val category: String, val vector: ImageVector)

private fun rig(name: String, vararg parts: Pair<String, Boolean>): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            for ((d, filled) in parts) {
                if (filled) {
                    addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))
                } else {
                    addPath(
                        pathData = addPathNodes(d),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
        }
        .build()

/** A path with its own stroke width, for the icons ported from SimDeck. */
private class P(val d: String, val filled: Boolean, val width: Float = 2f)

private fun rigW(name: String, vararg parts: P): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            for (p in parts) {
                if (p.filled) addPath(pathData = addPathNodes(p.d), fill = SolidColor(Color.Black))
                else addPath(
                    pathData = addPathNodes(p.d), stroke = SolidColor(Color.Black), strokeLineWidth = p.width,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        .build()

private const val S = false
private const val F = true

/** Every icon a control can show, keyed by the string id stored in profiles. */
object IconLibrary {
    val categories: List<String> = listOf("Lights", "Driving", "Drivetrain", "Camera & tools", "Machines", "Game", "Aviation", "General")

    val entries: List<IconEntry> = listOf(
        // Lights
        IconEntry("low_beam", "Low beam", "Lights", rig("low_beam", "M13 5.5c4 0 7 2.9 7 6.5s-3 6.5-7 6.5c-.8 0-1.5-.2-1.5-1V6.5c0-.8.7-1 1.5-1z" to S, "M8 7.5 3 9M8 11 3 12.5M8 14.5 3 16" to S)),
        IconEntry("high_beam", "High beam", "Lights", rig("high_beam", "M13 5.5c4 0 7 2.9 7 6.5s-3 6.5-7 6.5c-.8 0-1.5-.2-1.5-1V6.5c0-.8.7-1 1.5-1z" to S, "M8 7.5H3M8 12H3M8 16.5H3" to S)),
        IconEntry("fog_light", "Fog light", "Lights", rig("fog_light", "M13 5.5c4 0 7 2.9 7 6.5s-3 6.5-7 6.5c-.8 0-1.5-.2-1.5-1V6.5c0-.8.7-1 1.5-1z" to S, "M8.5 7.5 3 7.5M8.5 12H3M8.5 16.5H3" to S, "M6 4.5c-1 2.5 1 4.5 0 7.5s1 5 0 8" to S)),
        IconEntry("light_bar", "Light bar", "Lights", rig("light_bar", "M4 14.5h16a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-2a1 1 0 0 1 1-1z" to S, "M7 16.5h.01M12 16.5h.01M17 16.5h.01" to S, "M12 4v6M5.5 6l2 4M18.5 6l-2 4" to S)),
        IconEntry("hazard", "Hazard", "Lights", rig("hazard", "M12 3.5 21.5 20h-19z" to S, "M12 9.2l4.3 7.3H7.7z" to S)),
        IconEntry("signal_left", "Signal left", "Lights", rig("signal_left", "M10 5 3 12l7 7v-4h5.5a2 2 0 0 0 2-2v-2a2 2 0 0 0-2-2H10z" to S, "M21 9v6" to S)),
        IconEntry("signal_right", "Signal right", "Lights", rig("signal_right", "M14 5l7 7-7 7v-4H8.5a2 2 0 0 1-2-2v-2a2 2 0 0 1 2-2H14z" to S, "M3 9v6" to S)),
        IconEntry("interior_light", "Interior light", "Lights", rig("interior_light", "M6 15h12l-2-7H8z" to S, "M12 8V4" to S, "M8 18.5l-1 2M12 18.5v2.5M16 18.5l1 2" to S)),
        // Driving
        IconEntry("ignition", "Ignition", "Driving", rig("ignition", "M8 5.5a4.5 4.5 0 1 0 0 9 4.5 4.5 0 0 0 0-9z" to S, "M12.5 10H21M18 10v3M21 10v2.5" to S, "M8 8.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z" to F)),
        IconEntry("starter", "Starter", "Driving", rig("starter", "M12 3.5v7" to S, "M7.2 6.3a7.5 7.5 0 1 0 9.6 0" to S)),
        IconEntry("horn", "Horn", "Driving", rig("horn", "M3 10v4h3l7 4.5v-13L6 10z" to S, "M16.5 9a4 4 0 0 1 0 6M19 6.5a7.5 7.5 0 0 1 0 11" to S)),
        IconEntry("cruise", "Cruise", "Driving", rig("cruise", "M4.5 17.5a8.5 8.5 0 1 1 15 0" to S, "M12 15l3.5-5" to S, "M12 14a1.4 1.4 0 1 0 0 2.8 1.4 1.4 0 0 0 0-2.8z" to F, "M7 8l1 1M12 5.5V7M17 8l-1 1" to S, "M8.5 20.5h7" to S)),
        IconEntry("parking_brake", "Parking brake", "Driving", rig("parking_brake", "M12 5a7 7 0 1 0 0 14 7 7 0 0 0 0-14z" to S, "M10 15.5v-7h2.5a2.2 2.2 0 0 1 0 4.4H10" to S, "M4.2 6.5a10 10 0 0 0 0 11M19.8 6.5a10 10 0 0 1 0 11" to S)),
        IconEntry("esc", "ESC", "Driving", rig("esc", "M5 13.5V11l1.6-3.6A2 2 0 0 1 8.4 6.2h7.2a2 2 0 0 1 1.8 1.2L19 11v2.5a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1z" to S, "M5 11h14" to S, "M7.5 14.5v1.5M16.5 14.5v1.5" to S, "M5 20c1-1.5 2-1.5 3 0M10.5 20c1-1.5 2-1.5 3 0M16 20c1-1.5 2-1.5 3 0" to S)),
        IconEntry("esc_off", "ESC off", "Driving", rig("esc_off", "M5 13.5V11l1.6-3.6A2 2 0 0 1 8.4 6.2h7.2a2 2 0 0 1 1.8 1.2L19 11v2.5a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1z" to S, "M5 20c1-1.5 2-1.5 3 0M10.5 20c1-1.5 2-1.5 3 0M16 20c1-1.5 2-1.5 3 0" to S, "M3 3l18 18" to S)),
        IconEntry("tcs", "Traction control", "Driving", rig("tcs", "M12 4.5a7.5 7.5 0 1 0 0 15 7.5 7.5 0 0 0 0-15z" to S, "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z" to S, "M12 4.5V9M12 15v4.5M4.5 12H9M15 12h4.5" to S, "M1.5 8.5c.8 2.3.8 4.7 0 7M22.5 8.5c-.8 2.3-.8 4.7 0 7" to S)),
        IconEntry("abs", "ABS", "Driving", rig("abs", "M12 6a6 6 0 1 0 0 12 6 6 0 0 0 0-12z" to S, "M12 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z" to F, "M4.2 6.5a10 10 0 0 0 0 11M19.8 6.5a10 10 0 0 1 0 11" to S)),
        IconEntry("wiper", "Wiper", "Driving", rig("wiper", "M3 17a12 12 0 0 1 18 0" to S, "M12 18.5 6.5 9" to S, "M12 17.5a1.2 1.2 0 1 0 0 2.4 1.2 1.2 0 0 0 0-2.4z" to F)),
        IconEntry("steering", "Steering", "Driving", rig("steering", "M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0-17z" to S, "M12 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4z" to S, "M3.8 11 10 12M20.2 11 14 12M12 14v6.3" to S)),
        IconEntry("fuel", "Fuel", "Driving", rig("fuel", "M5 20.5V5a1.5 1.5 0 0 1 1.5-1.5h6A1.5 1.5 0 0 1 14 5v15.5M3.5 20.5h12" to S, "M7 7h5v4H7z" to S, "M14 9h2a1.5 1.5 0 0 1 1.5 1.5v6a1.5 1.5 0 0 0 3 0V8.5L18 6" to S)),
        IconEntry("engine", "Engine", "Driving", rig("engine", "M7 8h6l2 2h2.5v2.5H20V9.5h1.5v7H20v-3h-2.5V16H15l-2 2H8.5L6 15.5H4.5v-5H6V9z" to S, "M9 8V5.5M7 5.5h4" to S)),
        // Drivetrain
        IconEntry("gearbox", "Gearbox", "Drivetrain", rig("gearbox", "M5 5v14M12 5v14M19 5v7H5" to S, "M5 3.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zM12 3.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zM19 3.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zM5 17.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zM12 17.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z" to F)),
        IconEntry("gear_up", "Shift up", "Drivetrain", rig("gear_up", "M6 14l6-6 6 6" to S, "M6 19l6-6 6 6" to S)),
        IconEntry("gear_down", "Shift down", "Drivetrain", rig("gear_down", "M6 10l6 6 6-6" to S, "M6 5l6 6 6-6" to S)),
        IconEntry("diff_lock", "Diff lock", "Drivetrain", rig("diff_lock", "M3 6.5v11M5.5 6.5v11M18.5 6.5v11M21 6.5v11" to S, "M5.5 12H9M15 12h3.5" to S, "M12 8.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7z" to S, "M9.5 9.5l5 5M14.5 9.5l-5 5" to S)),
        IconEntry("differential", "Differential", "Drivetrain", rig("differential", "M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10z" to S, "M3 12h4M17 12h4M12 3v4" to S, "M3 8.5v7M21 8.5v7" to S)),
        IconEntry("awd", "4WD / AWD", "Drivetrain", rig("awd", "M8 4.5h8M8 19.5h8M12 4.5v15" to S, "M4.5 2.5h3v5h-3zM16.5 2.5h3v5h-3zM4.5 16.5h3v5h-3zM16.5 16.5h3v5h-3z" to F, "M7.5 5h1M15.5 5h1M7.5 19h1M15.5 19h1" to S)),
        IconEntry("low_range", "Low range", "Drivetrain", rig("low_range", "M4 17a8 8 0 0 1 16 0" to S, "M12 17 7.5 12" to S, "M12 16a1 1 0 1 0 0 2 1 1 0 0 0 0-2z" to F, "M4 20.5h16" to S)),
        IconEntry("suspension", "Suspension", "Drivetrain", rig("suspension", "M12 2.5v3M12 18.5v3" to S, "M8 5.5h8M8 18.5h8" to S, "M9 7.5l6 1.7-6 1.8 6 1.7-6 1.8 6 1.7" to S)),
        IconEntry("winch", "Winch", "Drivetrain", rig("winch", "M5 7.5h8v9H5z" to S, "M3 7.5v9M15 7.5v9" to S, "M5 10.5h8M5 13.5h8" to S, "M13 9.5h5.5v4.5" to S, "M18.5 14a2 2 0 1 0 2 2" to S)),
        IconEntry("trailer", "Trailer", "Drivetrain", rig("trailer", "M2.5 6.5h13v9h-13z" to S, "M15.5 13.5h6" to S, "M7 15.5a2 2 0 1 0 0 4 2 2 0 0 0 0-4z" to S, "M21.5 12v3" to S)),
        IconEntry("jack", "Jack", "Drivetrain", rig("jack", "M4 20.5h16" to S, "M8 20.5 12 10l4 10.5" to S, "M9.5 7h5v3h-5z" to S, "M12 7V3.5M9.5 3.5h5" to S)),
        // Camera & tools
        IconEntry("camera", "Camera", "Camera & tools", rig("camera", "M3.5 7.5h11a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1v-7a1 1 0 0 1 1-1z" to S, "M15.5 11l5-3v8l-5-3" to S)),
        IconEntry("camera_cycle", "Change camera", "Camera & tools", rig("camera_cycle", "M3.5 9h9a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1v-6a1 1 0 0 1 1-1z" to S, "M13.5 12l4-2.5v7l-4-2.5" to S, "M8 5.5a7 7 0 0 1 11.5 1.5" to S, "M19.8 3.8l-.3 3.2-3.1-.6" to S)),
        IconEntry("look_back", "Look back", "Camera & tools", rig("look_back", "M12 6c-5 0-8.5 4.5-9.5 6 1 1.5 4.5 6 9.5 6s8.5-4.5 9.5-6c-1-1.5-4.5-6-9.5-6z" to S, "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z" to F)),
        IconEntry("photo", "Photo", "Camera & tools", rig("photo", "M4 7.5h3l1.5-2h7l1.5 2h3a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-10a1 1 0 0 1 1-1z" to S, "M12 10a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7z" to S)),
        IconEntry("map", "Map", "Camera & tools", rig("map", "M3.5 6.5 9 4.5l6 2 5.5-2v13l-5.5 2-6-2-5.5 2z" to S, "M9 4.5v13M15 6.5v13" to S)),
        IconEntry("recover", "Recover vehicle", "Camera & tools", rig("recover", "M4 13.5V12l1.4-3A1.6 1.6 0 0 1 6.8 8h6.4a1.6 1.6 0 0 1 1.4 1l1.4 3v1.5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z" to S, "M6.5 14.5v1.5M13.5 14.5v1.5" to S, "M18.5 4.5a5 5 0 0 1 1.5 8" to S, "M17.5 2.5l1 2.2-2.2 1" to S, "M3 19.5h17" to S)),
        IconEntry("reset", "Reset", "Camera & tools", rig("reset", "M5 12a7 7 0 1 0 2-4.9" to S, "M4.5 3.5v4.2h4.2" to S)),
        IconEntry("replay", "Replay", "Camera & tools", rig("replay", "M4 12a8 8 0 1 0 2.3-5.6" to S, "M5.5 3v4h4" to S, "M10.5 9v6l5-3z" to F)),
        IconEntry("slowmo", "Slow motion", "Camera & tools", rig("slowmo", "M7 3.5h10M7 20.5h10" to S, "M8 3.5c0 4 4 5 4 8.5s-4 4.5-4 8.5M16 3.5c0 4-4 5-4 8.5s4 4.5 4 8.5" to S, "M9.5 18.5h5L12 16z" to F)),
        IconEntry("pause", "Pause", "Camera & tools", rig("pause", "M7 5h3.5v14H7zM13.5 5H17v14h-3.5z" to F)),
        // Machines
        IconEntry("blade", "Dozer blade", "Machines", rig("blade", "M8 3.5c2.5 2 3.5 5 3.5 8.5S10.5 18.5 8 20.5" to S, "M4.5 20.5H8" to S, "M11.5 12H21M10.5 6.5 20 9.5" to S)),
        IconEntry("blade_tilt", "Blade tilt", "Machines", rig("blade_tilt", "M3.5 14.5 20.5 10v3.5L3.5 18z" to S, "M7 8a7 7 0 0 1 10.5-1.5" to S, "M14.8 6.2l2.7.3.3-2.7" to S)),
        IconEntry("blade_angle", "Blade angle", "Machines", rig("blade_angle", "M4 5l15 5-.8 2.4-15-5z" to S, "M11.5 7.7a1.2 1.2 0 1 0 0 2.4 1.2 1.2 0 0 0 0-2.4z" to F, "M6 19a8.5 8.5 0 0 0 12 0" to S, "M6 19l.3-2.6M6 19l2.6.4M18 19l-.3-2.6M18 19l-2.6.4" to S)),
        IconEntry("ripper", "Ripper", "Machines", rig("ripper", "M6 4h12" to S, "M10 4v3M14 4v3" to S, "M12 7v6.5c0 2.8 1.8 4.8 4.5 5.5l2 .5-1.5 2" to S)),
        IconEntry("float_mode", "Float mode", "Machines", rig("float_mode", "M5.5 10h13" to S, "M12 3v4.5M9.8 5.2 12 3l2.2 2.2" to S, "M3 16.5c1.5-1.5 3-1.5 4.5 0s3 1.5 4.5 0 3-1.5 4.5 0 3 1.5 4.5 0" to S, "M3 20.5c1.5-1.5 3-1.5 4.5 0s3 1.5 4.5 0 3-1.5 4.5 0 3 1.5 4.5 0" to S)),
        IconEntry("bucket", "Bucket", "Machines", rig("bucket", "M5 5h9.5L19 9.5l-3 9H10.5L5 10z" to S, "M10.5 18.5 9.5 21M13.5 18.5 13 21" to S)),
        IconEntry("quick_coupler", "Quick coupler", "Machines", rig("quick_coupler", "M5 4.5h14v4.5H5z" to S, "M8.5 9v4a2.5 2.5 0 0 0 5 0V9" to S, "M16 9v7.5" to S, "M11 18.5h8" to S)),
        IconEntry("boom", "Boom", "Machines", rig("boom", "M4 19 10.5 6l9 3" to S, "M6.5 15.5l6-6.5" to S, "M4 17.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zM10.5 4.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z" to F)),
        IconEntry("stick", "Arm (stick)", "Machines", rig("stick", "M6 6.5l8.5 12M9 4.5l8.5 12" to S, "M7.5 3.5a2 2 0 1 0 0 4 2 2 0 0 0 0-4zM16 16.5a2 2 0 1 0 0 4 2 2 0 0 0 0-4z" to S)),
        IconEntry("swing", "Swing / slew", "Machines", rig("swing", "M5 12a7 7 0 0 1 12-4.9" to S, "M17.4 3.8l-.4 3.3-3.3-.4" to S, "M19 12a7 7 0 0 1-12 4.9" to S, "M6.6 20.2l.4-3.3 3.3.4" to S, "M12 10.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z" to F)),
        IconEntry("tracks", "Tracks", "Machines", rig("tracks", "M6 8.5h12a4 4 0 0 1 0 8H6a4 4 0 0 1 0-8z" to S, "M6 11a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zM12 11a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zM18 11a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z" to F, "M6 19.5h1M11.5 19.5h1M17 19.5h1" to S)),
        IconEntry("hook", "Crane hook", "Machines", rig("hook", "M12 3v5M9.5 3h5" to S, "M12 8v5a4 4 0 1 1-4 4" to S, "M8 17l1.8-1.2" to S)),
        IconEntry("boom_extend", "Boom extend", "Machines", rig("boom_extend", "M3 18 12 12.5M3 15l8.5-5.2M12 12.5l-.5-2.7" to S, "M11.8 11.2 19 7" to S, "M15.6 5.2 19 7l-1.2 3.4" to S)),
        IconEntry("outriggers", "Outriggers", "Machines", rig("outriggers", "M7 7.5h10v5H7z" to S, "M7 12.5 3.5 17.5M17 12.5l3.5 5" to S, "M2 18h3M19 18h3" to S, "M9.5 7.5V5h5v2.5" to S)),
        IconEntry("beacon", "Beacon", "Machines", rig("beacon", "M8 17v-4.5a4 4 0 0 1 8 0V17" to S, "M6 17h12v3H6z" to S, "M12 2.5v2M5 5.5l1.4 1.4M19 5.5l-1.4 1.4M2.5 12h2M19.5 12h2" to S)),
        IconEntry("work_light", "Work light", "Machines", rig("work_light", "M4.5 6h7a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1h-7a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1z" to S, "M8 15v4M5 19.5h6" to S, "M15.5 7l5-2M15.5 10.5h6M15.5 14l5 2" to S)),
        IconEntry("plough", "Plough", "Machines", rig("plough", "M4 5.5h3c.8 5 4 9.5 9.5 11.5V19.5H4z" to S, "M17.5 7.5h.01M20.5 10.5h.01M17 11.5h.01M21 14.5h.01" to S)),
        IconEntry("spreader", "Salt spreader", "Machines", rig("spreader", "M5 4h14l-4.5 7h-5z" to S, "M12 11v3M8.5 14h7" to S, "M5.5 18h.01M8.5 20.5h.01M12 18.5h.01M15.5 20.5h.01M18.5 18h.01" to S)),
        IconEntry("tow", "Tow / wheel lift", "Machines", rig("tow", "M2.5 19.5h9M4.5 19.5V9l10.5-5" to S, "M15 4v6" to S, "M15 10v2a2 2 0 1 1-2 2" to S, "M6.5 19.5V15h3" to S)),
        IconEntry("direction", "Direction F/N/R", "Machines", rig("direction", "M3 17V7h4M3 12h3" to S, "M9.5 17V7l5 10V7" to S, "M17 17V7h2.2a2.5 2.5 0 0 1 0 5H17M19.2 12l2.3 5" to S)),
        // Ported from SimDeck's icon set (tools/icons.py in the 1.0 SimDeck repo)
        IconEntry("parking_lights", "Parking lights", "Lights", rigW("parking_lights", P("M10 7 C6.5 7 6.5 17 10 17 Z", S, 1.8f), P("M14 7 C17.5 7 17.5 17 14 17 Z", S, 1.8f), P("M5 8 H2.5 M5 12 H2.5 M5 16 H2.5 M19 8 H21.5 M19 12 H21.5 M19 16 H21.5", S, 1.6f))),
        IconEntry("fog_rear", "Rear fog", "Lights", rigW("fog_rear", P("M10 5 C4.5 5 2.5 8.5 2.5 12 C2.5 15.5 4.5 19 10 19 Z", S, 2f), P("M13 7.5 H21 M13 12 H21 M13 16.5 H21", S, 1.8f), P("M16.5 3.5 C14.5 6 18.5 8.5 16.5 11 C14.5 13.5 18.5 16 16.5 18.5 C15.8 19.4 16.2 20.2 16.5 20.5", S, 1.6f))),
        IconEntry("siren", "Siren", "Lights", rigW("siren", P("M3.5 9 H7.5 L14 5 V19 L7.5 15 H3.5 Z", S, 2f), P("M17.5 8 L21 6 M17.5 12 H21.5 M17.5 16 L21 18", S, 1.8f))),
        IconEntry("underglow", "Underglow", "Lights", rigW("underglow", P("M2.5 13 L4.5 9.5 L9 7.5 H15 L18.5 10 L21.5 11 V14 H2.5 Z", S, 1.8f), P("M4.5 17.5 H19.5 M7 21 H17", S, 1.6f))),
        IconEntry("brake_warn", "Brake warning", "Driving", rigW("brake_warn", P("M5.7,12 a6.3,6.3 0 1,0 12.6,0 a6.3,6.3 0 1,0 -12.6,0 Z", S, 2f), P("M12 8.3 V12.8 M12 15.2 V15.6", S, 2.2f), P("M4.6 6.5 A8.7 8.7 0 0 0 4.6 17.5 M19.4 6.5 A8.7 8.7 0 0 1 19.4 17.5", S, 2f))),
        IconEntry("battery", "Battery", "Driving", rigW("battery", P("M3,7 h18 v12 h-18 Z", S, 2f), P("M6.5 7 V4.8 H9 V7 M15 7 V4.8 H17.5 V7", S, 2f), P("M6 13 H10 M14 13 H18 M16 11 V15", S, 1.8f))),
        IconEntry("temp", "Temp", "Driving", rigW("temp", P("M10 14 V5 A2 2 0 0 1 14 5 V14 A4 4 0 1 1 10 14 Z", S, 1.8f), P("M10,17 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0 Z", F), P("M14 7.5 H17 M14 10.5 H16", S, 1.5f), P("M3 21.5 C5 20 7 23 9 21.5 C11 20 13 23 15 21.5 C17 20 19 23 21 21.5", S, 1.4f))),
        IconEntry("check_engine", "Check engine", "Driving", rigW("check_engine", P("M5 9 H8 V7 H14 L16 9 H18 V11 H20 V9.5 H21.5 V16.5 H20 V15 H18 V17.5 H9.5 L7.5 15.5 H5 Z M11 7 V5 M9 5 H13 M2.5 10.5 V14.5 M2.5 12.5 H5", S, 1.7f), P("M11.6,9.5 h1.8 v3.8 h-1.8 Z", F), P("M11.6,14.2 h1.8 v1.8 h-1.8 Z", F))),
        IconEntry("throttle", "Throttle", "Driving", rigW("throttle", P("M9 3 H15 L16.5 21 H7.5 Z", S, 2f), P("M9.5 7 H14.5 M9.7 11 H14.3 M9.9 15 H14.1", S, 1.4f))),
        IconEntry("brake", "Brake", "Driving", rigW("brake", P("M4 7.5 H20 V17.5 H4 Z", S, 2f), P("M6.5 10.5 H17.5 M6.5 14.5 H17.5", S, 1.4f), P("M12 17.5 V21", S, 2f))),
        IconEntry("clutch", "Clutch", "Driving", rigW("clutch", P("M5.5 6.5 H18.5 V16.5 H5.5 Z", S, 2f), P("M9 9 V14 M12 9 V14 M15 9 V14", S, 1.4f), P("M12 16.5 V21", S, 2f))),
        IconEntry("seat", "Seat", "Driving", rigW("seat", P("M7.5 3.5 H10.5 L11.8 13 H16.5 A2.2 2.2 0 0 1 18.7 15.2 V17 H7.8 Z", S, 1.8f), P("M8.5 21 H18 M13 17 V21", S, 1.8f))),
        IconEntry("door", "Door", "Driving", rigW("door", P("M5 4 H18.5 V21 H5 V11 Z", S, 2f), P("M7.5 6.5 H16 V11 H7.5 Z", S, 1.6f), P("M13.5 14 H16", S, 2f))),
        IconEntry("mirror", "Mirror", "Driving", rigW("mirror", P("M3 7.5 C3 6 4 5.5 5.5 5.5 H18.5 C20 5.5 21 6 21 7.5 V13 C21 14.5 20 15 18.5 15 H5.5 C4 15 3 14.5 3 13 Z", S, 2f), P("M12 15 V19.5 M9 19.5 H15", S, 2f))),
        IconEntry("vehicle", "Vehicle", "Driving", rigW("vehicle", P("M4 11 L6.5 5.5 H17.5 L20 11 V17.5 H4 Z", S, 2f), P("M4 11 H20", S, 2f), P("M5.8 17.5 V20.5 M18.2 17.5 V20.5", S, 2.6f), P("M6.3,14.2 a1.3,1.3 0 1,0 2.6,0 a1.3,1.3 0 1,0 -2.6,0 Z", F), P("M15.099999999999998,14.2 a1.3,1.3 0 1,0 2.6,0 a1.3,1.3 0 1,0 -2.6,0 Z", F))),
        IconEntry("tire_pressure", "Tyre pressure", "Driving", rigW("tire_pressure", P("M5.5 7 C4 11.5 5 16.5 7.3 19 H16.7 C19 16.5 20 11.5 18.5 7", S, 2f), P("M5.5 7 L6.8 4.8 M18.5 7 L17.2 4.8 M4.5 21.5 H19.5", S, 1.7f), P("M12 8.5 V13.6 M12 16 V16.4", S, 2.2f))),
        IconEntry("hill_assist", "Hill assist", "Driving", rigW("hill_assist", P("M2.5 19.5 L21.5 8 V19.5 Z", S, 1.8f), P("M8 12.5 L10.5 8.5 H15 L16.3 10.2", S, 1.6f), P("M8.2,13.2 a1.3,1.3 0 1,0 2.6,0 a1.3,1.3 0 1,0 -2.6,0 Z", F), P("M13.7,10 a1.3,1.3 0 1,0 2.6,0 a1.3,1.3 0 1,0 -2.6,0 Z", F))),
        IconEntry("gear_n", "Neutral", "Drivetrain", rigW("gear_n", P("M7 18.5 V5.5 L17 18.5 V5.5", S, 2.4f))),
        IconEntry("gear_r", "Reverse", "Drivetrain", rigW("gear_r", P("M7.5 18.5 V5.5 H12.5 A3.7 3.7 0 0 1 12.5 12.9 H7.5 M11.8 12.9 L17 18.5", S, 2.4f))),
        IconEntry("gear_auto", "Auto gearbox", "Drivetrain", rigW("gear_auto", P("M6 18.5 L12 5.5 L18 18.5 M8.3 13.8 H15.7", S, 2.4f))),
        IconEntry("2wd", "2WD", "Drivetrain", rigW("2wd", P("M12 6 V18 M7.5 6 H16.5 M7.5 18 H16.5", S, 1.8f), P("M4.6,3.6 h2.4 v4.8 h-2.4 Z", S, 1.3f), P("M17,3.6 h2.4 v4.8 h-2.4 Z", S, 1.3f), P("M4,15 h3.5 v6 h-3.5 Z", F), P("M16.5,15 h3.5 v6 h-3.5 Z", F))),
        IconEntry("diff_mode", "Diff mode", "Drivetrain", rigW("diff_mode", P("M2,6 h3.2 v12 h-3.2 Z", F), P("M18.8,6 h3.2 v12 h-3.2 Z", F), P("M5.2 12 H8.6 M15.4 12 H18.8", S, 2f), P("M8.6,12 a3.4,3.4 0 1,0 6.8,0 a3.4,3.4 0 1,0 -6.8,0 Z", S, 2f), P("M10.7,12 a1.3,1.3 0 1,0 2.6,0 a1.3,1.3 0 1,0 -2.6,0 Z", F))),
        IconEntry("high_range", "High range", "Drivetrain", rigW("high_range", P("M10 5.5 L5 14.5 H12 M10.3 9.5 V18.5", S, 2.2f), P("M14.8 5.5 V18.5 M20 5.5 V18.5 M14.8 12 H20", S, 2.2f))),
        IconEntry("line_lock", "Line lock", "Drivetrain", rigW("line_lock", P("M3.0,11 a6.5,6.5 0 1,0 13.0,0 a6.5,6.5 0 1,0 -13.0,0 Z", S, 2f), P("M7.5,11 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0 Z", S, 2f), P("M14.5,15 h7 v6 h-7 Z", F), P("M16 15 V13.3 A2 2 0 0 1 20 13.3 V15", S, 1.6f))),
        IconEntry("transbrake", "Transbrake", "Drivetrain", rigW("transbrake", P("M4 5 V15 M9 5 V15 M14 5 V9 M4 10 H14", S, 1.8f), P("M14.5,15 h7 v6 h-7 Z", F), P("M16 15 V13.3 A2 2 0 0 1 20 13.3 V15", S, 1.6f))),
        IconEntry("nitrous", "Nitrous", "Drivetrain", rigW("nitrous", P("M9 8.5 H15 V19.8 A1.7 1.7 0 0 1 13.3 21.5 H10.7 A1.7 1.7 0 0 1 9 19.8 Z", S, 2f), P("M10.6 8.5 V5.5 H13.4 V8.5 M12 5.5 V3 M9.8 3 H14.2", S, 1.6f), P("M9,12.5 h6 v3 h-6 Z", F))),
        IconEntry("speed_limiter", "Speed limiter", "Drivetrain", rigW("speed_limiter", P("M3,12 a9,9 0 1,0 18,0 a9,9 0 1,0 -18,0 Z", S, 2.4f), P("M8.5 15.5 V8.5 M12 8.5 V15.5 M15.5 8.5 V15.5", S, 1.6f))),
        IconEntry("retarder", "Retarder", "Drivetrain", rigW("retarder", P("M5.7,12 a6.3,6.3 0 1,0 12.6,0 a6.3,6.3 0 1,0 -12.6,0 Z", S, 2f), P("M10 15.6 V8.4 H12.8 A2 2 0 0 1 12.8 12.4 H10 M12.4 12.4 L14.6 15.6", S, 1.7f), P("M4.6 6.5 A8.7 8.7 0 0 0 4.6 17.5 M19.4 6.5 A8.7 8.7 0 0 1 19.4 17.5", S, 2f))),
        IconEntry("engine_brake", "Engine brake", "Drivetrain", rigW("engine_brake", P("M4.5 6 H9.5 M4.5 6 V18 H9.5 M4.5 12 H8.5", S, 2f), P("M13 6 V18 H16.3 A3 3 0 0 0 16.3 12 H13 M13 12 H16 A3 3 0 0 0 16 6 H13", S, 2f))),
        IconEntry("coupler", "Coupler", "Machines", rigW("coupler", P("M3 17 H12.5 V13.4", S, 2.2f), P("M9.3,9.4 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z", S, 2f), P("M9 21 H16", S, 2f))),
        IconEntry("lift_axle", "Lift axle", "Machines", rigW("lift_axle", P("M4.5,17.5 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0 Z", S, 2f), P("M13.5,17.5 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0 Z", S, 2f), P("M16.5 11 V3 M13.3 6.2 L16.5 3 L19.7 6.2", S, 2f))),
        IconEntry("tailgate", "Tailgate", "Machines", rigW("tailgate", P("M2.5 15 H16 V8.5 H7.5 L5.5 11.5 H2.5 Z", S, 1.8f), P("M16 15 L21.5 17.5", S, 2.2f), P("M4.2,17.3 a1.8,1.8 0 1,0 3.6,0 a1.8,1.8 0 1,0 -3.6,0 Z", S, 1.6f), P("M10.7,17.3 a1.8,1.8 0 1,0 3.6,0 a1.8,1.8 0 1,0 -3.6,0 Z", S, 1.6f))),
        IconEntry("ramp", "Ramp", "Machines", rigW("ramp", P("M2.5 19.5 L21.5 9 V19.5 Z", S, 2f), P("M6 9 L11 6.5 M8.4 5.2 L11 6.5 L9.7 9", S, 1.8f))),
        IconEntry("crane", "Crane", "Machines", rigW("crane", P("M3.5 21 H10.5 M7 21 V4.5 L20 8.5 M7 4.5 L13 10.5 M20 8.5 V12.5", S, 1.8f), P("M20 12.5 A1.8 1.8 0 1 1 18.2 14.3", S, 1.6f))),
        IconEntry("winch_release", "Winch release", "Machines", rigW("winch_release", P("M2.5,7.5 h8 v9 h-8 Z", S, 2f), P("M4.5 7.5 V16.5 M6.5 7.5 V16.5 M8.5 7.5 V16.5", S, 1.2f), P("M12.5 12 H21 M17.5 8.5 L21 12 L17.5 15.5", S, 2f))),
        IconEntry("grapple", "Grapple", "Machines", rigW("grapple", P("M12 3 V8", S, 2f), P("M12 8 C6 8 5 14 7.5 19 M12 8 C18 8 19 14 16.5 19", S, 2f), P("M10.4,8 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0 Z", F))),
        IconEntry("implement_up", "Implement up", "Machines", rigW("implement_up", P("M4 20 H20", S, 2.4f), P("M12 16 V4 M7.5 8.5 L12 4 L16.5 8.5", S, 2.2f))),
        IconEntry("implement_down", "Implement down", "Machines", rigW("implement_down", P("M4 20 H20", S, 2.4f), P("M12 4 V16 M7.5 11.5 L12 16 L16.5 11.5", S, 2.2f))),
        IconEntry("pto", "PTO / add-on", "Machines", rigW("pto", P("M8,12 a4,4 0 1,0 8,0 a4,4 0 1,0 -8,0 Z", S, 2f), P("M12 3 V6 M12 18 V21 M3 12 H6 M18 12 H21 M5.6 5.6 L7.8 7.8 M16.2 16.2 L18.4 18.4 M5.6 18.4 L7.8 16.2 M16.2 7.8 L18.4 5.6", S, 2f), P("M10.6,12 a1.4,1.4 0 1,0 2.8,0 a1.4,1.4 0 1,0 -2.8,0 Z", F))),
        IconEntry("camera_free", "Free camera", "Camera & tools", rigW("camera_free", P("M12 3 V21 M3 12 H21", S, 1.8f), P("M9.5 5.5 L12 3 L14.5 5.5 M9.5 18.5 L12 21 L14.5 18.5 M5.5 9.5 L3 12 L5.5 14.5 M18.5 9.5 L21 12 L18.5 14.5", S, 1.8f))),
        IconEntry("camera_next", "Next camera", "Camera & tools", rigW("camera_next", P("M2.5 7 H12.5 V17 H2.5 Z", S, 1.8f), P("M12.5 10.2 L17 8 V16 L12.5 13.8 Z", S, 1.8f), P("M19.5 9 L22 12 L19.5 15", S, 1.8f))),
        IconEntry("center_view", "Centre view", "Camera & tools", rigW("center_view", P("M8.8,12 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z", S, 2f), P("M3 8 V3 H8 M16 3 H21 V8 M21 16 V21 H16 M8 21 H3 V16", S, 2f))),
        IconEntry("minimap", "Minimap", "Camera & tools", rigW("minimap", P("M3,12 a9,9 0 1,0 18,0 a9,9 0 1,0 -18,0 Z", S, 2f), P("M12 6 L15.5 16 L12 13.8 L8.5 16 Z", F))),
        IconEntry("radial_menu", "Radial menu", "Camera & tools", rigW("radial_menu", P("M3,12 a9,9 0 1,0 18,0 a9,9 0 1,0 -18,0 Z", S, 2f), P("M9,12 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0 Z", S, 2f), P("M9.9 9.9 L5.6 5.6 M14.1 9.9 L18.4 5.6 M9.9 14.1 L5.6 18.4 M14.1 14.1 L18.4 18.4", S, 1.6f))),
        IconEntry("hide_ui", "Hide UI", "Camera & tools", rigW("hide_ui", P("M2 12 C5 6.5 19 6.5 22 12 C19 17.5 5 17.5 2 12 Z", S, 1.8f), P("M9,12 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0 Z", S, 1.8f), P("M3.5 3.5 L20.5 20.5", S, 2.2f))),
        IconEntry("photo_mode", "Photo mode", "Camera & tools", rigW("photo_mode", P("M3 8 V3 H8 M16 3 H21 V8 M21 16 V21 H16 M8 21 H3 V16", S, 2f), P("M7 16.5 L10.5 11.5 L13 14.5 L14.8 12.5 L17.5 16.5 Z", S, 1.6f), P("M14.0,8 a1.5,1.5 0 1,0 3.0,0 a1.5,1.5 0 1,0 -3.0,0 Z", F))),
        IconEntry("home_save", "Save spot", "Game", rigW("home_save", P("M3.5 11 L12 3.8 L20.5 11 M5.5 9.5 V20.5 H18.5 V9.5", S, 2f), P("M12 10.5 V17 M8.8 13.8 H15.2", S, 2f))),
        IconEntry("home_load", "Load spot", "Game", rigW("home_load", P("M3.5 11 L12 3.8 L20.5 11 M5.5 9.5 V20.5 H18.5 V9.5", S, 2f), P("M12 9.5 V17 M9 14 L12 17 L15 14", S, 1.8f))),
        IconEntry("faster", "Faster", "Game", rigW("faster", P("M3 6 L11.5 12 L3 18 Z M12.5 6 L21 12 L12.5 18 Z", F))),
        IconEntry("slower", "Slower", "Game", rigW("slower", P("M21 6 L12.5 12 L21 18 Z M11.5 6 L3 12 L11.5 18 Z", F))),
        IconEntry("traffic", "Traffic", "Game", rigW("traffic", P("M8 2.5 H16 V21.5 H8 Z", S, 1.8f), P("M10.1,7 a1.9,1.9 0 1,0 3.8,0 a1.9,1.9 0 1,0 -3.8,0 Z", F), P("M10.1,12 a1.9,1.9 0 1,0 3.8,0 a1.9,1.9 0 1,0 -3.8,0 Z", F), P("M10.1,17 a1.9,1.9 0 1,0 3.8,0 a1.9,1.9 0 1,0 -3.8,0 Z", F))),
        IconEntry("ai_drive", "AI drive", "Game", rigW("ai_drive", P("M5.5 8.5 H18.5 V19 H5.5 Z", S, 2f), P("M12 8.5 V5", S, 2f), P("M10.6,4 a1.4,1.4 0 1,0 2.8,0 a1.4,1.4 0 1,0 -2.8,0 Z", F), P("M7.800000000000001,12.8 a1.5,1.5 0 1,0 3.0,0 a1.5,1.5 0 1,0 -3.0,0 Z", F), P("M13.2,12.8 a1.5,1.5 0 1,0 3.0,0 a1.5,1.5 0 1,0 -3.0,0 Z", F), P("M9.5 16 H14.5", S, 1.6f), P("M3 12 V15.5 M21 12 V15.5", S, 1.8f))),
        IconEntry("time_of_day", "Time of day", "Game", rigW("time_of_day", P("M8,12 a4,4 0 1,0 8,0 a4,4 0 1,0 -8,0 Z", S, 2f), P("M12 2.5 V5 M12 19 V21.5 M2.5 12 H5 M19 12 H21.5 M5.3 5.3 L7.1 7.1 M16.9 16.9 L18.7 18.7 M5.3 18.7 L7.1 16.9 M16.9 7.1 L18.7 5.3", S, 1.8f))),
        IconEntry("walk", "Walk", "Game", rigW("walk", P("M11.4,4 a2.1,2.1 0 1,0 4.2,0 a2.1,2.1 0 1,0 -4.2,0 Z", F), P("M12.4 7.5 L10.4 13.3 L13.4 16.3 L12.4 21 M10.4 13.3 L7.6 21 M11.8 9.2 L15.2 11.8 L17.4 10.6 M11.8 9.2 L8.4 11.4 L6.8 13.6", S, 2f))),
        IconEntry("explode", "Explode", "Game", rigW("explode", P("M12 2 L13.8 8 L19.5 5 L16 10.5 L22 12 L16 13.8 L19 19.5 L13.6 16.2 L12 22 L10.4 16.2 L5 19.5 L8 13.8 L2 12 L8 10.5 L4.5 5 L10.2 8 Z", F))),
        IconEntry("fire", "Fire", "Game", rigW("fire", P("M12 21.5 C7.5 21.5 5 18.5 5 15 C5 10.5 9 8.5 9 4 C12 5.5 13.5 8 13.3 10.8 C14.6 9.8 15.3 8.4 15.3 7 C17.8 9 19 12 19 15 C19 18.5 16.5 21.5 12 21.5 Z", S, 1.8f))),
        IconEntry("extinguish", "Extinguish", "Game", rigW("extinguish", P("M8.5 9 H15.5 V21 H8.5 Z", S, 2f), P("M10.5 9 V6 H13.5 V9 M13.5 7 H17.5 L20 9.5", S, 1.8f), P("M11 3 H14", S, 2f))),
        IconEntry("lua", "Script", "Game", rigW("lua", P("M8.5 4 C6 4 6 6 6 8 C6 10 5.5 11.5 3.5 12 C5.5 12.5 6 14 6 16 C6 18 6 20 8.5 20", S, 2f), P("M15.5 4 C18 4 18 6 18 8 C18 10 18.5 11.5 20.5 12 C18.5 12.5 18 14 18 16 C18 18 18 20 15.5 20", S, 2f))),
        IconEntry("page_next", "Next page", "Game", rigW("page_next", P("M3,4 h13 v16 h-13 Z", S, 1.8f), P("M18 8 L22 12 L18 16", S, 2.2f))),
        IconEntry("page_prev", "Previous page", "Game", rigW("page_prev", P("M8,4 h13 v16 h-13 Z", S, 1.8f), P("M6 8 L2 12 L6 16", S, 2.2f))),
        IconEntry("landing_gear", "Landing gear", "Aviation", rigW("landing_gear", P("M12 2.5 V12.5 M7.5 5.5 H16.5", S, 2f), P("M7.8,17 a4.2,4.2 0 1,0 8.4,0 a4.2,4.2 0 1,0 -8.4,0 Z", S, 2f), P("M10.6,17 a1.4,1.4 0 1,0 2.8,0 a1.4,1.4 0 1,0 -2.8,0 Z", F))),
        IconEntry("flaps", "Flaps", "Aviation", rigW("flaps", P("M2.5 10 C6 7.5 12 7.5 15 9.5 L2.5 11.5 Z", S, 1.6f), P("M15.5 11 L21 17 L19.5 18 L14 12.5 Z", S, 1.6f))),
        IconEntry("autopilot", "Autopilot", "Aviation", rigW("autopilot", P("M3.5 18.5 L8 5.5 L12.5 18.5 M5.2 13.8 H10.8", S, 2f), P("M15 18.5 V5.5 H18 A3.3 3.3 0 0 1 18 12.1 H15", S, 2f))),
        IconEntry("beacon_air", "Nav / strobe lights", "Aviation", rigW("beacon_air", P("M8.5,12 a3.5,3.5 0 1,0 7.0,0 a3.5,3.5 0 1,0 -7.0,0 Z", F), P("M12 2.5 V6 M12 18 V21.5 M2.5 12 H6 M18 12 H21.5", S, 2f), P("M5.5,12 a6.5,6.5 0 1,0 13.0,0 a6.5,6.5 0 1,0 -13.0,0 Z", S, 1.4f))),
        IconEntry("plane", "Plane", "Aviation", rigW("plane", P("M21 15.5 V13.5 L13.5 9 V4 A1.5 1.5 0 0 0 10.5 4 V9 L3 13.5 V15.5 L10.5 13.2 V18.5 L8.5 20 V21.5 L12 20.5 L15.5 21.5 V20 L13.5 18.5 V13.2 Z", F))),
        // General
        IconEntry("gamepad", "Gamepad", "General", Icons.Rounded.SportsEsports),
        IconEntry("keyboard", "Keyboard", "General", Icons.Rounded.Keyboard),
        IconEntry("power", "Power", "General", Icons.Rounded.PowerSettingsNew),
        IconEntry("bolt", "Boost", "General", Icons.Rounded.Bolt),
        IconEntry("flag", "Flag", "General", Icons.Rounded.Flag),
        IconEntry("speed", "Speed", "General", Icons.Rounded.Speed),
        IconEntry("timer", "Timer", "General", Icons.Rounded.Timer),
        IconEntry("place", "Waypoint", "General", Icons.Rounded.Place),
        IconEntry("navigation", "Navigation", "General", Icons.Rounded.Navigation),
        IconEntry("chat", "Chat", "General", Icons.Rounded.Chat),
        IconEntry("mic", "Mic", "General", Icons.Rounded.Mic),
        IconEntry("mic_off", "Mic off", "General", Icons.Rounded.MicOff),
        IconEntry("volume_up", "Volume up", "General", Icons.Rounded.VolumeUp),
        IconEntry("volume_down", "Volume down", "General", Icons.Rounded.VolumeDown),
        IconEntry("music", "Music", "General", Icons.Rounded.MusicNote),
        IconEntry("screenshot", "Screenshot", "General", Icons.Rounded.Screenshot),
        IconEntry("menu", "Menu", "General", Icons.Rounded.Menu),
        IconEntry("settings", "Settings", "General", Icons.Rounded.Settings),
        IconEntry("home", "Home", "General", Icons.Rounded.Home),
        IconEntry("star", "Star", "General", Icons.Rounded.Star),
        IconEntry("build", "Repair", "General", Icons.Rounded.Build),
        IconEntry("play", "Play", "General", Icons.Rounded.PlayArrow),
        IconEntry("stop", "Stop", "General", Icons.Rounded.Stop),
        IconEntry("fast_forward", "Fast forward", "General", Icons.Rounded.FastForward),
        IconEntry("skip_next", "Next", "General", Icons.Rounded.SkipNext),
        IconEntry("skip_previous", "Previous", "General", Icons.Rounded.SkipPrevious),
        IconEntry("zoom_in", "Zoom in", "General", Icons.Rounded.ZoomIn),
        IconEntry("zoom_out", "Zoom out", "General", Icons.Rounded.ZoomOut),
        IconEntry("visibility", "View", "General", Icons.Rounded.Visibility),
        IconEntry("lightbulb", "Light", "General", Icons.Rounded.Lightbulb),
        IconEntry("inventory", "Inventory", "General", Icons.Rounded.Inventory2),
        IconEntry("target", "Target", "General", Icons.Rounded.GpsFixed),
        IconEntry("shield", "Shield", "General", Icons.Rounded.Shield),
        IconEntry("radio", "Radio", "General", Icons.Rounded.Radio),
        IconEntry("arrow_up", "Up", "General", Icons.Rounded.KeyboardArrowUp),
        IconEntry("arrow_down", "Down", "General", Icons.Rounded.KeyboardArrowDown),
        IconEntry("arrow_left", "Left", "General", Icons.Rounded.KeyboardArrowLeft),
        IconEntry("arrow_right", "Right", "General", Icons.Rounded.KeyboardArrowRight),
        IconEntry("plus", "Plus", "General", Icons.Rounded.Add),
        IconEntry("minus", "Minus", "General", Icons.Rounded.Remove),
    )

    private val byId: Map<String, IconEntry> = entries.associateBy { it.id }

    operator fun get(id: String?): ImageVector? = id?.let { byId[it]?.vector }

    fun entry(id: String?): IconEntry? = id?.let { byId[it] }

    fun inCategory(category: String): List<IconEntry> = entries.filter { it.category == category }

    fun search(query: String): List<IconEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return entries
        return entries.filter { q in it.name.lowercase() || q in it.id || q in it.category.lowercase() }
    }
}
