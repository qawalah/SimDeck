package com.simdeck.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.simdeck.model.BeamNGCommands
import com.simdeck.model.Reading
import com.simdeck.model.ReadingInfo
import com.simdeck.model.Readings
import com.simdeck.ui.components.Field
import com.simdeck.ui.components.GroupedPickerSheet
import com.simdeck.ui.components.PickerItem
import com.simdeck.ui.theme.Race
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the reading picker needs: the vehicle's own readings (from the mod), keys seen in game state, the live values,
 * and a callback that tells the PC a picker is open (the mod then sends every reading, so the list shows live values).
 */
data class ReadingSource(
    val info: List<ReadingInfo> = emptyList(),
    val stateKeys: List<String> = emptyList(),
    val game: StateFlow<Map<String, Any>> = MutableStateFlow(emptyMap()),
    val onPicker: (Boolean) -> Unit = {},
)

val LocalReadingSource = compositionLocalOf { ReadingSource() }

/** Readings as picker rows with their live values; [filter] narrows them (lamps only, availability keys...). */
@Composable
fun readingItems(src: ReadingSource, filter: (Reading) -> Boolean = { true }): List<PickerItem> {
    val game by src.game.collectAsState()
    val catalog = remember(src.info, src.stateKeys) { Readings.catalog(src.info, src.stateKeys) }
    return catalog.filter(filter).map { r ->
        PickerItem(r.key, r.title, r.group, detail = if (r.lamp) "on / off" else r.unit, keywords = r.keywords,
            live = game[r.key]?.let { Readings.format(r, it) }, icon = r.icon)
    }
}

/**
 * A game-state key with a grouped, searchable picker beside it (SimDeck 1.4). Typing still works for keys the app doesn't
 * know. [onPick] gets the whole reading, so a gauge can take its unit, scale and range with it.
 */
@Composable
fun ReadingField(
    value: String,
    onType: (String) -> Unit,
    onPick: (Reading) -> Unit,
    label: String,
    example: String,
    filter: (Reading) -> Boolean = { true },
    title: String = "Pick a game reading",
) {
    val src = LocalReadingSource.current
    var open by remember { mutableStateOf(false) }
    val game by src.game.collectAsState()
    val current = value.takeIf { it.isNotBlank() }?.let { Readings.forKey(it, src.info) }
    val hint = when {
        current != null && current.group != "Other" -> current.title + (game[value]?.let { " · now " + Readings.format(current, it) } ?: "")
        value.isBlank() -> "Tap the list to pick, e.g. $example"
        else -> "Not a known reading; it works if the game reports it"
    }
    Field(value, onType, label, hint = hint, mono = true, trailing = {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.Search, contentDescription = "Pick $label from a list", tint = Race.Ink) }
    })
    if (open) {
        DisposableEffect(Unit) {
            src.onPicker(true)
            onDispose { src.onPicker(false) }
        }
        val items = readingItems(src, filter)
        GroupedPickerSheet(
            title = title, items = items, selected = value,
            onPick = { k -> onPick(Readings.forKey(k, src.info)) }, onDismiss = { open = false },
            searchHint = "Search: oil, coolant, boost, brake, tyre…", foldedGroups = setOf("All signals", "Vehicle has", "Other"),
            emptyHint = "Nothing matches. With BeamNG running, every signal the car has is listed under All signals.",
        )
    }
}

/** A gauge set to show [r]: key, unit, scale, decimals, and its dial range when the gauge has none yet. */
fun com.simdeck.model.Control.showing(r: Reading): com.simdeck.model.Control {
    val t = Readings.control(r)
    return copy(
        feedbackKey = r.key, scale = r.scale, decimals = r.decimals, unit = r.unit.ifBlank { null },
        min = t.min, max = t.max, bands = t.bands,
        label = if (label.isBlank() || label == "Gauge" || label == "Lamp") t.label else label,
        icon = icon ?: t.icon,
    )
}

/** The grouped command picker for a BeamNG action field. */
@Composable
fun CommandPickerSheet(
    selected: String,
    beamngCommands: List<String>,
    axisOnly: Boolean,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val catalog = LocalBeamNGCatalog.current
    val items = remember(catalog, beamngCommands, axisOnly) {
        BeamNGCommands.catalog(catalog.vehicle, catalog.actions, beamngCommands, axisOnly).map {
            PickerItem(it.id, it.title, it.group, it.detail, it.keywords, icon = null)
        }
    }
    GroupedPickerSheet(
        title = if (axisOnly) "Pick what the lever drives" else "Pick a BeamNG command",
        items = items, selected = selected, onPick = onPick, onDismiss = onDismiss,
        searchHint = "Search: lights, diff, tailgate, cruise…",
        foldedGroups = items.map { it.group }.filter { it.startsWith("Game actions") }.toSet(),
        emptyHint = if (axisOnly) "Nothing matches. Hydraulics and analog game actions show up here with BeamNG running."
        else "Nothing matches. With BeamNG running, the vehicle's functions and every game action are listed too.",
    )
}
