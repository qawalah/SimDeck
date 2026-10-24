package com.simdeck.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simdeck.model.TuningState
import com.simdeck.model.TuningVar
import com.simdeck.ui.components.SlantButton
import com.simdeck.ui.components.SlantStyle
import com.simdeck.ui.components.raceFieldColors
import com.simdeck.ui.theme.CapShape
import com.simdeck.ui.theme.Race
import com.simdeck.ui.theme.RaceType
import kotlin.math.abs

/** What the tuning page can ask of the app. */
class TuningActions(
    val onSet: (String, Double) -> Unit = { _, _ -> },
    val onDefault: (String) -> Unit = {},
    val onAllDefaults: () -> Unit = {},
    val onRevert: () -> Unit = {},
    val onApply: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onAutoApply: (Boolean) -> Unit = {},
)

/**
 * The BeamNG vehicle's tuning settings on a play page (SimDeck 1.4), laid out like the game's Tuning menu: categories on
 * the left, each setting with its slider, - / + steps, value and unit. Changes collect until Apply (or apply by themselves
 * with Auto apply), because the game reloads the vehicle to apply them.
 */
@Composable
fun TuningPanel(state: TuningState, connected: Boolean, actions: TuningActions, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<TuningVar?>(null) }
    val all = state.grouped(query)
    val categories = state.grouped().map { it.first }
    val shown = all.filter { category == null || query.isNotBlank() || it.first == category }

    Column(modifier.fillMaxSize()) {
        // Header: vehicle, search, auto apply, actions.
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.widthIn(max = 200.dp)) {
                Text("TUNING", style = RaceType.capLabel, color = Race.InkMuted)
                Text(state.vehicle.ifBlank { "No vehicle" }, style = RaceType.title, color = Race.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                query, { query = it }, singleLine = true, placeholder = { Text("Search settings") },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Race.InkMuted) },
                trailingIcon = if (query.isNotEmpty()) { { IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search", tint = Race.InkMuted) } } } else null,
                colors = raceFieldColors(), modifier = Modifier.weight(1f).widthIn(max = 320.dp),
            )
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { contentDescription = "Apply changes by themselves" }) {
                Text("AUTO", style = RaceType.unit, color = Race.InkMuted)
                Spacer(Modifier.width(4.dp))
                Switch(state.autoApply, actions.onAutoApply, colors = SwitchDefaults.colors(checkedTrackColor = Race.Electric, checkedThumbColor = Race.Ink))
            }
            IconButton(onClick = actions.onRefresh, enabled = connected) { Icon(Icons.Rounded.Refresh, "Load the settings from the game again", tint = Race.InkMuted) }
            HeaderText("Defaults", state.vars.isNotEmpty(), actions.onAllDefaults)
            HeaderText("Revert", state.pending.isNotEmpty(), actions.onRevert)
            SlantButton(
                if (state.pending.isEmpty()) "Apply" else "Apply ${state.pending.size}",
                actions.onApply, style = SlantStyle.PRIMARY, enabled = state.pending.isNotEmpty() && !state.applying && connected,
            )
        }
        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = if (state.ok) Race.InkMuted else Race.Amber, modifier = Modifier.padding(vertical = 2.dp))
        }
        if (!connected || state.vars.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 520.dp)) {
                    Text(
                        when {
                            !connected -> "Connect to the PC with BeamNG and the SimDeck mod running to tune the car you drive."
                            state.vehicle.isBlank() -> "Get in a vehicle in BeamNG: its tuning settings show up here."
                            else -> "This vehicle has no tuning settings."
                        },
                        style = MaterialTheme.typography.bodyLarge, color = Race.InkMuted, textAlign = TextAlign.Center,
                    )
                    if (connected) SlantButton("Load again", actions.onRefresh, icon = Icons.Rounded.Refresh, style = SlantStyle.QUIET)
                }
            }
            return@Column
        }
        Row(Modifier.fillMaxSize().padding(top = 4.dp)) {
            // Categories, like the game's menu.
            LazyColumn(Modifier.width(176.dp).fillMaxHeight()) {
                item { CategoryTab("All", state.vars.size, state.pending.size, category == null) { category = null } }
                items(categories) { c ->
                    val inCat = state.vars.filter { it.category == c }
                    CategoryTab(c, inCat.size, inCat.count { it.name in state.pending }, category == c) { category = c }
                }
            }
            Spacer(Modifier.width(10.dp))
            LazyColumn(Modifier.weight(1f).fillMaxHeight()) {
                if (shown.isEmpty()) item { Text("No setting matches “$query”.", color = Race.InkMuted, modifier = Modifier.padding(16.dp)) }
                for ((cat, vars) in shown) {
                    item(key = "c:$cat") { Text(cat.uppercase(), style = RaceType.capLabel, color = Race.InkMuted, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) }
                    var lastSub: String? = null
                    for (v in vars) {
                        if (v.subCategory != null && v.subCategory != lastSub) {
                            val sub: String = v.subCategory ?: ""
                            item(key = "s:$cat:$sub") { Text(sub, style = RaceType.unit, color = Race.InkFaint, modifier = Modifier.padding(top = 6.dp, start = 2.dp)) }
                        }
                        lastSub = v.subCategory
                        item(key = "v:${v.name}") { TuningRow(v, state.valueOf(v), v.name in state.pending, actions, onEdit = { editing = v }) }
                    }
                }
            }
        }
    }
    editing?.let { v -> ExactValueDialog(v, state.valueOf(v), { actions.onSet(v.name, it); editing = null }, { editing = null }) }
}

@Composable
private fun HeaderText(text: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(text.uppercase(), style = RaceType.capLabel, color = if (enabled) Race.Ink else Race.InkFaint)
    }
}

@Composable
private fun CategoryTab(name: String, count: Int, changed: Int, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(CapShape(cut = 8.dp, radius = 4.dp))
            .background(if (on) Race.Cap else Race.Panel)
            .then(if (on) Modifier.border(1.dp, Race.Electric, CapShape(cut = 8.dp, radius = 4.dp)) else Modifier)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { stateDescription = if (on) "Selected" else ""; contentDescription = "$name, $count settings" + if (changed > 0) ", $changed changed" else "" }
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, color = if (on) Race.Ink else Race.InkMuted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (changed > 0) Text("$changed", style = RaceType.unit, color = Race.Electric, modifier = Modifier.padding(end = 6.dp))
        Text("$count", style = RaceType.unit, color = Race.InkFaint)
    }
}

@Composable
private fun TuningRow(v: TuningVar, value: Double, changed: Boolean, actions: TuningActions, onEdit: () -> Unit) {
    val lo = minOf(v.displayMin, v.displayMax)
    val hi = maxOf(v.displayMin, v.displayMax)
    val shown = v.toDisplay(value)
    val isDefault = v.default?.let { abs(it - value) < 1e-9 } ?: true
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(CapShape(cut = 8.dp, radius = 4.dp))
            .background(Race.Panel)
            .then(if (changed) Modifier.border(1.dp, Race.Electric.copy(alpha = 0.7f), CapShape(cut = 8.dp, radius = 4.dp)) else Modifier)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(0.34f)) {
            Text(v.title, style = MaterialTheme.typography.bodyLarge, color = Race.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                v.description ?: "${v.format(v.fromDisplay(lo))} to ${v.format(v.fromDisplay(hi))}" + (v.default?.let { " · default ${v.format(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = Race.InkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { actions.onSet(v.name, v.fromDisplay(shown - v.displayStep)) }) { Icon(Icons.Rounded.Remove, "Less ${v.title}", tint = Race.Ink) }
        Slider(
            value = shown.toFloat().coerceIn(lo.toFloat(), hi.toFloat()),
            onValueChange = { actions.onSet(v.name, v.fromDisplay(it.toDouble())) },
            valueRange = lo.toFloat()..hi.toFloat().let { if (it <= lo.toFloat()) lo.toFloat() + 1f else it },
            colors = SliderDefaults.colors(thumbColor = if (changed) Race.Electric else Race.Ink, activeTrackColor = if (changed) Race.Electric else Race.InkMuted, inactiveTrackColor = Race.Line),
            modifier = Modifier.weight(0.46f).semantics { contentDescription = v.title; stateDescription = v.format(value) },
        )
        IconButton(onClick = { actions.onSet(v.name, v.fromDisplay(shown + v.displayStep)) }) { Icon(Icons.Rounded.Add, "More ${v.title}", tint = Race.Ink) }
        Text(
            v.format(value), style = RaceType.readout, color = if (changed) Race.Electric else Race.Ink, textAlign = TextAlign.End, maxLines = 1,
            modifier = Modifier.width(104.dp).clickable(role = Role.Button, onClickLabel = "Type a value", onClick = onEdit).padding(vertical = 8.dp),
        )
        IconButton(onClick = { actions.onDefault(v.name) }, enabled = !isDefault) {
            Icon(Icons.Rounded.RestartAlt, "Default ${v.title}", tint = if (isDefault) Race.Line else Race.InkMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ExactValueDialog(v: TuningVar, value: Double, onSet: (Double) -> Unit, onDismiss: () -> Unit) {
    val start = v.toDisplay(value).let { if (v.decimals == 0) Math.round(it).toString() else String.format(java.util.Locale.ROOT, "%.${v.decimals}f", it) }
    var text by remember { mutableStateOf(start) }
    val parsed = text.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Race.PanelRaised,
        title = { Text(v.title, color = Race.Ink) },
        text = {
            OutlinedTextField(
                text, { text = it }, singleLine = true, colors = raceFieldColors(), suffix = { Text(v.displayUnit) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = { Text("${v.format(v.fromDisplay(minOf(v.displayMin, v.displayMax)))} to ${v.format(v.fromDisplay(maxOf(v.displayMin, v.displayMax)))}") },
            )
        },
        confirmButton = { TextButton(onClick = { parsed?.let { onSet(v.fromDisplay(it)) } }, enabled = parsed != null) { Text("SET", color = Race.Electric) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = Race.InkMuted) } },
    )
}
