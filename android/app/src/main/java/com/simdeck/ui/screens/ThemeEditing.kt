package com.simdeck.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simdeck.model.Band
import com.simdeck.model.Control
import com.simdeck.model.ControlType
import com.simdeck.model.GameId
import com.simdeck.model.Page
import com.simdeck.model.PanelGroup
import com.simdeck.model.Profile
import com.simdeck.model.Skins
import com.simdeck.model.Station
import com.simdeck.model.Themes
import com.simdeck.ui.components.ChoiceChips
import com.simdeck.ui.components.Field
import com.simdeck.ui.components.Gap
import com.simdeck.ui.components.Hint
import com.simdeck.ui.components.SectionHeader
import com.simdeck.ui.components.Stepper
import com.simdeck.ui.components.ToggleRow
import com.simdeck.ui.theme.Race
import com.simdeck.ui.theme.RaceType
import java.util.Locale

/** "IDLE 0, CL 62, TOGA 100" (percent of the travel) to stations, and back. */
fun parseStations(text: String): List<Station>? = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { part ->
    val tokens = part.split(Regex("\\s+"))
    val at = tokens.last().removeSuffix("%").toFloatOrNull() ?: return null
    Station((at / 100f).coerceIn(0f, 1f), tokens.dropLast(1).joinToString(" "))
}

fun formatStations(list: List<Station>): String = list.joinToString(", ") { (if (it.label.isBlank()) "" else it.label + " ") + Math.round(it.at * 100) }

/** "0-80 green, 80-100 red" to gauge bands, and back. */
fun parseBands(text: String): List<Band>? = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { part ->
    val m = Regex("^(-?[\\d.]+)\\s*-\\s*(-?[\\d.]+)\\s*([a-zA-Z]*)$").find(part) ?: return null
    Band(m.groupValues[1].toDouble(), m.groupValues[2].toDouble(), m.groupValues[3].ifBlank { "green" }.lowercase(Locale.ROOT))
}

fun formatBands(list: List<Band>): String = list.joinToString(", ") { "${num(it.from)}-${num(it.to)} ${it.color}" }

private fun num(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

private val legendSkins = setOf("lit-push", "lit-push-split", "ring-push", "rocker-lamp", "toggle-bat", "gear-lever", "glass-tile", "glass-toggle")

/** Inspector section: the control's skin and the details a skin can show (legends, end labels, stations, gauge scale). */
@Composable
fun ThemeLookSection(c: Control, theme: String, onUpdate: (Control) -> Unit) {
    val skins = Skins.byType[c.type].orEmpty()
    SectionHeader("Style")
    val current = Skins.resolve(c, theme)
    val default = "Theme default (${Skins.displayName(Skins.resolve(c.copy(skin = null), theme))})"
    ChoiceChips(listOf(default) + skins.map { Skins.displayName(it) }, c.skin?.let { Skins.displayName(it) } ?: default, { label ->
        onUpdate(c.copy(skin = skins.firstOrNull { Skins.displayName(it) == label }))
    })
    Hint("How it looks in ${Themes.displayName(theme)}. It sends the same input in every style.")
    if (current in legendSkins) {
        Gap(6)
        Row {
            Field(c.legends.getOrNull(0).orEmpty(), { v -> onUpdate(c.copy(legends = listOf(v, c.legends.getOrNull(1).orEmpty()))) }, "Upper legend",
                hint = if (current == "toggle-bat") "Up position, e.g. ON" else "e.g. FAULT", modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Field(c.legends.getOrNull(1).orEmpty(), { v -> onUpdate(c.copy(legends = listOf(c.legends.getOrNull(0).orEmpty(), v))) }, "Lower legend",
                hint = if (current == "toggle-bat") "Down position, e.g. OFF" else "e.g. ON", modifier = Modifier.weight(1f))
        }
    }
    if (current in setOf("rocker-lamp", "ring-push", "rocker-mom")) {
        Gap(6)
        Text("BACKLIGHT", style = RaceType.unit, color = Race.InkFaint)
        val colours = listOf("Theme", "Amber", "Red", "Green", "Blue", "White")
        ChoiceChips(colours, c.backlight?.replaceFirstChar { it.uppercase() } ?: "Theme", { v ->
            onUpdate(c.copy(backlight = if (v == "Theme") null else v.lowercase()))
        })
    }
    when (c.type) {
        ControlType.SLIDER, ControlType.ROCKER -> {
            Gap(6)
            Row {
                Field(c.ends.getOrNull(0).orEmpty(), { v -> onUpdate(c.copy(ends = listOf(v, c.ends.getOrNull(1).orEmpty()).trimEnds())) },
                    if (c.type == ControlType.ROCKER) "Down label" else "Low end", modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Field(c.ends.getOrNull(1).orEmpty(), { v -> onUpdate(c.copy(ends = listOf(c.ends.getOrNull(0).orEmpty(), v).trimEnds())) },
                    if (c.type == ControlType.ROCKER) "Up label" else "High end", modifier = Modifier.weight(1f))
            }
            if (c.type == ControlType.SLIDER) {
                var text by remember(c.id) { mutableStateOf(formatStations(c.stations)) }
                val parsed = parseStations(text)
                Field(text, { t -> text = t; parseStations(t)?.let { onUpdate(c.copy(stations = it)) } }, "Stations",
                    hint = "Name and percent of travel, e.g. IDLE 0, CL 62, TOGA 100. Felt as detents.",
                    error = if (parsed == null) "Write each as a name then a number, separated by commas" else null)
            }
        }
        ControlType.JOYSTICK -> {
            Gap(6)
            Text("DIRECTION LABELS", style = RaceType.unit, color = Race.InkFaint)
            val names = listOf("Left", "Right", "Back", "Forward")
            for (row in 0..1) Row {
                for (col in 0..1) {
                    val i = row * 2 + col
                    Field(c.ends.getOrNull(i).orEmpty(), { v ->
                        val list = (0..3).map { j -> if (j == i) v else c.ends.getOrNull(j).orEmpty() }
                        onUpdate(c.copy(ends = list.trimEnds()))
                    }, names[i], modifier = Modifier.weight(1f).padding(end = if (col == 0) 8.dp else 0.dp))
                }
            }
            Hint("Say what the machine does, e.g. SWING L, SWING R, STICK IN, STICK OUT.")
        }
        ControlType.GAUGE -> {
            Gap(6)
            Row {
                Field(c.min?.let { num(it) }.orEmpty(), { v -> onUpdate(c.copy(min = v.toDoubleOrNull())) }, "Scale from", number = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Field(c.max?.let { num(it) }.orEmpty(), { v -> onUpdate(c.copy(max = v.toDoubleOrNull())) }, "Scale to", number = true, modifier = Modifier.weight(1f))
            }
            var text by remember(c.id) { mutableStateOf(formatBands(c.bands)) }
            val parsed = parseBands(text)
            Field(text, { t -> text = t; parseBands(t)?.let { onUpdate(c.copy(bands = it)) } }, "Coloured ranges",
                hint = "e.g. 0-90 green, 90-110 amber, 110-130 red (in the shown units)",
                error = if (parsed == null) "Write each as from-to colour, separated by commas" else null)
        }
        else -> Unit
    }
}

private fun List<String>.trimEnds(): List<String> = if (all { it.isBlank() }) emptyList() else this

/** Profile and page settings for themes: the profile's theme, following the vehicle, a page override, panel groups. */
@Composable
fun ThemeSettingsSection(profile: Profile, page: Page, pageIndex: Int, onProfileChange: ((Profile) -> Profile) -> Unit) {
    SectionHeader("Theme")
    val names = Themes.all.map { Themes.displayName(it) }
    ChoiceChips(names, Themes.displayName(profile.theme), { label ->
        val id = Themes.all.first { Themes.displayName(it) == label }
        onProfileChange { it.copy(theme = if (id == Themes.RACE) null else id) }
    })
    Hint("How the play surface looks and feels: levers, switches, gauges and panels after real equipment. Inputs stay the same.")
    if (profile.game == GameId.BEAMNG) {
        ToggleRow("Follow the vehicle", profile.themeFollowsVehicle, { on -> onProfileChange { it.copy(themeFollowsVehicle = on) } },
            hint = "Trucks get the truck theme, machines the heavy-equipment theme, race cars motorsport, and so on.")
    }
    val actions = LocalEditorActions.current
    ToggleRow("Control sounds", actions.soundsOn, actions.onSounds, hint = "A click, clack or thunk with each press on themed pages, at the phone's touch-sound volume. For every profile.")
    Gap(8)
    Text("THIS PAGE", style = RaceType.unit, color = Race.InkFaint)
    Gap(4)
    val same = "Same as profile"
    ChoiceChips(listOf(same) + names, page.theme?.let { Themes.displayName(it) } ?: same, { label ->
        val id = Themes.all.firstOrNull { Themes.displayName(it) == label }
        onProfileChange { p -> p.copy(pages = p.pages.mapIndexed { i, pg -> if (i == pageIndex) pg.copy(theme = id) else pg }) }
    })
    SectionHeader("Panels", trailing = {
        IconButton(onClick = {
            onProfileChange { p -> p.copy(pages = p.pages.mapIndexed { i, pg -> if (i == pageIndex) pg.copy(panels = pg.panels + PanelGroup("PANEL ${pg.panels.size + 1}", 0, 0, minOf(2, pg.columns), minOf(2, pg.rows))) else pg }) }
        }) { Icon(Icons.Rounded.Add, contentDescription = "Add panel", tint = Race.Ink) }
    })
    Hint("Titled groups drawn behind the controls: cockpit lightplates, cab console bays, switch rows.")
    page.panels.forEachIndexed { gi, g ->
        fun upd(n: PanelGroup) = onProfileChange { p ->
            p.copy(pages = p.pages.mapIndexed { i, pg -> if (i == pageIndex) pg.copy(panels = pg.panels.toMutableList().also { it[gi] = n }) else pg })
        }
        Column(Modifier.padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(g.title, { upd(g.copy(title = it.take(24))) }, "Title", modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    onProfileChange { p -> p.copy(pages = p.pages.mapIndexed { i, pg -> if (i == pageIndex) pg.copy(panels = pg.panels.filterIndexed { j, _ -> j != gi }) else pg }) }
                }) { Icon(Icons.Rounded.Close, contentDescription = "Remove panel ${g.title}", tint = Race.InkMuted) }
            }
            Stepper("Column", g.x + 1, 1..page.columns, { upd(g.copy(x = it - 1, w = g.w.coerceAtMost(page.columns - it + 1))) })
            Stepper("Row", g.y + 1, 1..page.rows, { upd(g.copy(y = it - 1, h = g.h.coerceAtMost(page.rows - it + 1))) })
            Stepper("Width", g.w, 1..(page.columns - g.x), { upd(g.copy(w = it)) })
            Stepper("Height", g.h, 1..(page.rows - g.y), { upd(g.copy(h = it)) })
        }
    }
    if (page.panels.isEmpty()) Text("No panels on this page.", style = MaterialTheme.typography.bodySmall, color = Race.InkMuted)
}
