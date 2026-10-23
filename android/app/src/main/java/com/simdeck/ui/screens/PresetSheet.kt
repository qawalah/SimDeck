package com.simdeck.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simdeck.model.Action
import com.simdeck.model.Control
import com.simdeck.model.ControlType
import com.simdeck.model.GameId
import com.simdeck.model.Page
import com.simdeck.model.Profile
import com.simdeck.profiles.BuiltInProfiles
import com.simdeck.profiles.Preset
import com.simdeck.profiles.Presets
import com.simdeck.ui.components.ChoiceChips
import com.simdeck.ui.components.Hint
import com.simdeck.ui.components.SlantTabs
import com.simdeck.ui.components.raceFieldColors
import com.simdeck.ui.icons.IconLibrary
import com.simdeck.ui.theme.CapShape
import com.simdeck.ui.theme.Race
import com.simdeck.ui.theme.RaceType
import com.simdeck.ui.theme.lit

/** One line on what a control does, for preset tiles. */
fun Control.does(): String = when (type) {
    ControlType.ROCKER, ControlType.ROTARY -> "${cw.short()} / ${ccw.short()}"
    ControlType.SWITCH, ControlType.RADIO -> positions.joinToString(" · ") { it.label }
    ControlType.GAUGE, ControlType.INDICATOR -> feedbackKey?.let { "Shows $it" } ?: "Shows a game value"
    ControlType.PAGE -> "Goes to the ${pageTarget ?: "next"} page"
    ControlType.JOYSTICK -> "${action.short()} / ${actionY.short()}"
    else -> action.short()
}

private fun Action.short(): String = when (this) {
    is Action.VJoyButton -> if (button == Presets.NEXT_BUTTON) "next free gamepad button" else "gamepad button $button"
    is Action.VJoyAxis -> if (axis == Presets.NEXT_AXIS) "next free axis" else "axis $axis"
    is Action.Key -> if (keys.isEmpty()) "a key" else "key " + keys.joinToString("+")
    is Action.BeamNG -> command
    is Action.Macro -> "macro"
    is Action.XInputButton -> "Xbox $button"
    is Action.XInputAxis -> "Xbox $axis"
    Action.None -> "nothing yet"
}

/**
 * Add from presets: ready-made functions (by game, or for any game with gamepad numbers filled in), the BeamNG game's
 * own actions, and any control or page of your other profiles. A tap puts it on the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetSheet(
    profile: Profile,
    profiles: List<Profile>,
    catalog: BeamNGCatalog,
    onPick: (Control) -> Unit,
    onPastePage: (Page) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tabs = buildList {
        add("Presets")
        if (profile.game == GameId.BEAMNG || catalog.actions.isNotEmpty()) add("BeamNG actions")
        add("From profiles")
    }
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Race.PanelRaised) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add from presets", style = RaceType.title, color = Race.Ink, modifier = Modifier.weight(1f))
                Box(Modifier.horizontalScroll(rememberScrollState())) { SlantTabs(tabs, tab.coerceIn(0, tabs.lastIndex), { tab = it }, height = 40) }
            }
            OutlinedTextField(
                query, { query = it }, singleLine = true, placeholder = { Text("Search: hazard, diff lock, flaps, tailgate…") },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Race.InkMuted) }, colors = raceFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            when (tabs[tab.coerceIn(0, tabs.lastIndex)]) {
                "Presets" -> PresetsTab(profile, query, onPick)
                "BeamNG actions" -> GameActionsTab(catalog, query, onPick)
                else -> ProfilesTab(profile, profiles, query, onPick, onPastePage)
            }
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun PresetsTab(profile: Profile, query: String, onPick: (Control) -> Unit) {
    val forGame = "For ${if (profile.game == GameId.CUSTOM) "any game" else profile.game.displayName}"
    val sources = listOf(forGame, "Any game") + BuiltInProfiles.all.map { it.name }
    var source by rememberSaveable(profile.game) { mutableStateOf(forGame) }
    Box(Modifier.horizontalScroll(rememberScrollState())) { ChoiceChips(sources, source, { source = it }) }
    val all = Presets.builtIn
    val list = when (source) {
        forGame -> all.filter { it.games.isEmpty() || profile.game in it.games }
        "Any game" -> Presets.generic
        else -> all.filter { it.source == source }
    }
    PresetGrid(Presets.search(list, query), showSource = source == forGame && profile.game != GameId.CUSTOM, onPick = onPick)
}

@Composable
private fun GameActionsTab(catalog: BeamNGCatalog, query: String, onPick: (Control) -> Unit) {
    val list = catalog.vehicle.map { Presets.vehicleFeature(it) } + catalog.actions.map { Presets.gameAction(it) }
    if (list.isEmpty()) {
        Hint("Connect to the PC with BeamNG running the SimDeck mod: every input action of the game and of the vehicle you drive shows up here, ready to add with no binding.")
        Spacer(Modifier.size(24.dp))
        return
    }
    PresetGrid(Presets.search(list, query), showSource = false, onPick = onPick)
}

@Composable
private fun ProfilesTab(profile: Profile, profiles: List<Profile>, query: String, onPick: (Control) -> Unit, onPastePage: (Page) -> Unit) {
    // The profile being edited first (with its unsaved changes), then every other one.
    val all = listOf(profile) + profiles.filter { it.id != profile.id }
    var chosen by rememberSaveable { mutableStateOf(profile.id) }
    Box(Modifier.horizontalScroll(rememberScrollState())) {
        ChoiceChips(all.map { if (it.id == profile.id) "This profile" else it.name }, all.firstOrNull { it.id == chosen }?.let { if (it.id == profile.id) "This profile" else it.name }, { label ->
            chosen = all.first { (if (it.id == profile.id) "This profile" else it.name) == label }.id
        })
    }
    val p = all.firstOrNull { it.id == chosen } ?: profile
    val q = query.trim().lowercase()
    LazyVerticalGrid(GridCells.Adaptive(176.dp), Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
        for (pg in p.pages.filter { it.auto == null }) {
            val controls = pg.controls.filter { q.isEmpty() || q in it.label.lowercase() || q in it.does().lowercase() }
            if (controls.isEmpty()) continue
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(pg.name.uppercase(), style = RaceType.capLabel, color = Race.InkMuted, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onPastePage(pg) }) {
                        Icon(Icons.Rounded.ContentCopy, null, tint = Race.Electric, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("COPY WHOLE PAGE", style = RaceType.unit, color = Race.Electric)
                    }
                }
            }
            items(controls, key = { "${pg.id}/${it.id}" }) { c -> PresetTile(c, c.label, c.does(), null) { onPick(c) } }
        }
    }
}

@Composable
private fun PresetGrid(list: List<Preset>, showSource: Boolean, onPick: (Control) -> Unit) {
    if (list.isEmpty()) {
        Hint("Nothing matches. Try another word, or another source above.")
        Spacer(Modifier.size(24.dp))
        return
    }
    val groups = list.groupBy { if (showSource && it.source != "Any game") "${it.source} · ${it.category}" else it.category }
    LazyVerticalGrid(GridCells.Adaptive(176.dp), Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
        groups.forEach { (cat, items) ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(cat.uppercase(), style = RaceType.capLabel, color = Race.InkMuted, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 4.dp))
            }
            items(items, key = { it.id }) { p -> PresetTile(p.control, p.name, p.description.ifBlank { p.control.does() }, p.control.type) { onPick(p.control) } }
        }
    }
}

@Composable
private fun PresetTile(c: Control, name: String, sub: String, type: ControlType?, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(CapShape(cut = 10.dp, radius = 4.dp))
            .background(Race.Cap)
            .border(1.dp, Race.Line, CapShape(cut = 10.dp, radius = 4.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "Add $name: $sub" }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val v = IconLibrary[c.icon] ?: (c.type.paletteIconPublic())
        Box(Modifier.size(34.dp).clip(CapShape(cut = 6.dp, radius = 3.dp)).background(Race.Panel), contentAlignment = Alignment.Center) {
            if (v != null) Icon(v, null, tint = c.accent.lit(), modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = Race.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                ((type ?: c.type).displayName() + " · " + sub), style = MaterialTheme.typography.bodySmall, color = Race.InkMuted,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
