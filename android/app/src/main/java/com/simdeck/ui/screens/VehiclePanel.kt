package com.simdeck.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simdeck.model.IndicatorState
import com.simdeck.model.Control
import com.simdeck.model.ControlType
import com.simdeck.model.VehicleFeature
import com.simdeck.model.VehicleFeaturePages
import com.simdeck.model.VehicleFeatures
import com.simdeck.ui.components.ControlView
import com.simdeck.ui.components.raceFieldColors
import com.simdeck.ui.theme.CapShape
import com.simdeck.ui.theme.Race
import com.simdeck.ui.theme.RaceType

private const val FAVOURITES = "Favourites"
private const val ALL = "All"

/**
 * The BeamNG Vehicle page (SimDeck 1.5): every function of the vehicle you drive, as compact buttons in groups (Doors &
 * body, Lights & sound, Machinery…), searchable, with favourites pinned first. Each button says what pressing it does.
 */
@Composable
fun VehiclePanel(
    vehicle: VehicleFeatures,
    controls: List<Control>,
    favourites: Set<String>,
    onFavourite: (String) -> Unit,
    values: Map<String, Float>,
    indicators: Map<String, IndicatorState>,
    unavailable: Set<String>,
    onControlValue: (String, Float) -> Unit,
    onRotaryStep: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    val byId = remember(vehicle) { vehicle.list.associateBy { it.id } }
    data class Item(val control: Control, val feature: VehicleFeature, val group: String, val favourite: Boolean)
    val items = remember(controls, byId, favourites, vehicle.vehicle) {
        controls.mapNotNull { c ->
            val id = c.id.removePrefix(VehicleFeaturePages.ID_PREFIX)
            val f = byId[id] ?: VehicleFeature(id, c.label)
            Item(c, f, VehicleFeaturePages.groupFor(f), VehicleFeaturePages.favouriteKey(vehicle.vehicle, id) in favourites)
        }
    }
    val favCount = items.count { it.favourite }
    var group by rememberSaveable(vehicle.vehicle) { mutableStateOf(if (favCount > 0) FAVOURITES else ALL) }
    var query by rememberSaveable(vehicle.vehicle) { mutableStateOf("") }
    val groups = VehicleFeaturePages.GROUPS.filter { g -> items.any { it.group == g } }
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val shown = items
        .filter { words.isNotEmpty() || group == ALL || (group == FAVOURITES && it.favourite) || it.group == group }
        .filter { i -> val hay = "${i.feature.title} ${i.feature.id} ${i.group} ${VehicleFeaturePages.kindLabel(i.feature)}".lowercase(); words.all { it in hay } }
        // Favourites first, then the vehicle's own order.
        .sortedByDescending { it.favourite }

    Row(modifier.fillMaxSize()) {
        // Groups, like a menu: favourites, everything, then each group with its count.
        LazyColumn(Modifier.width(184.dp).fillMaxHeight()) {
            item {
                Column(Modifier.padding(start = 4.dp, bottom = 6.dp)) {
                    Text("VEHICLE", style = RaceType.capLabel, color = Race.InkMuted)
                    Text(vehicle.vehicle.ifBlank { "No vehicle" }, style = RaceType.title, color = Race.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item { GroupTab("★  $FAVOURITES", favCount, group == FAVOURITES && words.isEmpty()) { group = FAVOURITES; query = "" } }
            item { GroupTab(ALL, items.size, group == ALL && words.isEmpty()) { group = ALL; query = "" } }
            items(groups) { g -> GroupTab(g, items.count { it.group == g }, group == g && words.isEmpty()) { group = g; query = "" } }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            OutlinedTextField(
                query, { query = it }, singleLine = true, placeholder = { Text("Search this vehicle: door, bed, crane, light…") },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Race.InkMuted) },
                trailingIcon = if (query.isNotEmpty()) { { IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search", tint = Race.InkMuted) } } } else null,
                colors = raceFieldColors(), modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).padding(bottom = 6.dp),
            )
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            words.isNotEmpty() -> "Nothing on this vehicle matches “$query”."
                            group == FAVOURITES -> "No favourites yet. Tap the star on any function to pin it here, first in the list."
                            else -> "Nothing in this group."
                        },
                        style = MaterialTheme.typography.bodyLarge, color = Race.InkMuted, modifier = Modifier.widthIn(max = 460.dp),
                    )
                }
                return@Column
            }
            LazyVerticalGrid(GridCells.Adaptive(132.dp), Modifier.fillMaxSize()) {
                items(shown, key = { it.control.id }, span = { GridItemSpan(if (it.control.type == ControlType.SLIDER) minOf(2, maxLineSpan) else 1) }) { i ->
                    FunctionTile(
                        i.control, i.feature, i.favourite,
                        onFavourite = { onFavourite(i.feature.id) },
                        value = values[i.control.id] ?: 0f, indicator = indicators[i.control.id], enabled = i.control.id !in unavailable,
                        onValue = { onControlValue(i.control.id, it) }, onStep = { onRotaryStep(i.control.id, it) }, interactive = interactive,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupTab(name: String, count: Int, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(CapShape(cut = 8.dp, radius = 4.dp))
            .background(if (on) Race.Cap else Race.Panel)
            .then(if (on) Modifier.border(1.dp, Race.Electric, CapShape(cut = 8.dp, radius = 4.dp)) else Modifier)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { stateDescription = if (on) "Selected" else ""; contentDescription = "$name, $count" }
            .heightIn(min = 46.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, color = if (on) Race.Ink else Race.InkMuted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$count", style = RaceType.unit, color = Race.InkFaint)
    }
}

/** One function: the button (or lever) itself, what it does in a few words, and a star to pin it. */
@Composable
private fun FunctionTile(
    control: Control, feature: VehicleFeature, favourite: Boolean, onFavourite: () -> Unit,
    value: Float, indicator: IndicatorState?, enabled: Boolean, onValue: (Float) -> Unit, onStep: (Int) -> Unit, interactive: Boolean,
) {
    // One plain button style for every function, whatever the theme draws elsewhere, so the list reads evenly.
    val shown = if (control.type == ControlType.MOMENTARY) control.copy(skin = "cap") else control
    Column(Modifier.padding(4.dp)) {
        ControlView(shown, value, indicator, enabled, onValue, onStep, Modifier.fillMaxWidth().height(74.dp), interactive = interactive)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                VehicleFeaturePages.kindLabel(feature), style = MaterialTheme.typography.bodySmall, color = Race.InkFaint,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            IconButton(
                onClick = onFavourite,
                modifier = Modifier.size(36.dp)
                    .semantics { contentDescription = if (favourite) "Unpin ${feature.title}" else "Pin ${feature.title} to favourites" },
            ) {
                Icon(if (favourite) Icons.Rounded.Star else Icons.Rounded.StarBorder, null,
                    tint = if (favourite) Race.Amber else Race.InkFaint, modifier = Modifier.size(20.dp))
            }
        }
    }
}
