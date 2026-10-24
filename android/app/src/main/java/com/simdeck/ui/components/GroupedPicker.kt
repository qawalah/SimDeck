package com.simdeck.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simdeck.ui.icons.IconLibrary
import com.simdeck.ui.theme.CapShape
import com.simdeck.ui.theme.Race
import com.simdeck.ui.theme.RaceType

/** One row of a [GroupedPickerSheet]: a command or a reading. */
data class PickerItem(
    val id: String,
    val title: String,
    val group: String,
    val detail: String = "",
    /** Extra words the search also matches. */
    val keywords: String = "",
    /** The live value from the game, for readings. */
    val live: String? = null,
    val icon: String? = null,
)

/** Rows matching every word of [query] in title, id, group, detail or keywords. */
fun filterPicker(items: List<PickerItem>, query: String): List<PickerItem> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return items
    return items.filter { i -> val hay = "${i.title} ${i.id} ${i.group} ${i.detail} ${i.keywords}".lowercase(); words.all { it in hay } }
}

/**
 * A grouped, searchable list to pick a BeamNG command or a game reading from (SimDeck 1.4). Group chips narrow the list to
 * one group; headers fold big groups away ("All signals"); search looks in every group at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupedPickerSheet(
    title: String,
    items: List<PickerItem>,
    selected: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    searchHint: String = "Search",
    foldedGroups: Set<String> = emptySet(),
    emptyHint: String = "Nothing matches. Try another word.",
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Race.PanelRaised) {
        GroupedPickerBody(title, items, selected, { onPick(it); onDismiss() }, searchHint, foldedGroups, emptyHint, onClose = onDismiss)
    }
}

/** The sheet's content (separate so screenshots can render it without a modal window). */
@Composable
fun GroupedPickerBody(
    title: String,
    items: List<PickerItem>,
    selected: String?,
    onPick: (String) -> Unit,
    searchHint: String = "Search",
    foldedGroups: Set<String> = emptySet(),
    emptyHint: String = "Nothing matches. Try another word.",
    initialQuery: String = "",
    onClose: (() -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var unfolded by remember { mutableStateOf(setOf<String>()) }
    var folded by remember { mutableStateOf(setOf<String>()) }
    val groups = remember(items) { items.map { it.group }.distinct() }
    val searching = query.isNotBlank()
    val matches = filterPicker(items, query)
    // While searching, the chips count matches, so an empty group is easy to skip.
    val counts = matches.groupingBy { it.group }.eachCount()
    val shown = matches.filter { group == null || it.group == group }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = RaceType.title, color = Race.Ink, modifier = Modifier.weight(1f))
            Text("${items.size} in ${groups.size} groups", style = RaceType.unit, color = Race.InkFaint)
            if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Race.InkMuted) }
        }
        OutlinedTextField(
            query, { query = it }, singleLine = true, placeholder = { Text(searchHint) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Race.InkMuted) },
            trailingIcon = if (query.isNotEmpty()) { { IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = Race.InkMuted) } } } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = raceFieldColors(), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        // Group chips: all, or one group.
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GroupChip("All", matches.size, group == null) { group = null }
            groups.filter { !searching || (counts[it] ?: 0) > 0 || it == group }.forEach { g -> GroupChip(g, counts[g] ?: 0, group == g) { group = if (group == g) null else g } }
        }
        if (shown.isEmpty()) {
            Hint(emptyHint, Modifier.padding(vertical = 24.dp))
            return@Column
        }
        val byGroup = shown.groupBy { it.group }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            for ((g, rows) in byGroup) {
                val isFolded = !searching && group == null &&
                    ((g in foldedGroups || rows.size > 40) && g !in unfolded || g in folded)
                item(key = "h:$g") {
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (isFolded) "Show $g" else "Hide $g") {
                            if (isFolded) { unfolded = unfolded + g; folded = folded - g } else { folded = folded + g; unfolded = unfolded - g }
                        }.padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(g.uppercase(), style = RaceType.capLabel, color = Race.InkMuted, modifier = Modifier.weight(1f))
                        Text("${rows.size}", style = RaceType.unit, color = Race.InkFaint)
                        Icon(if (isFolded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess, null, tint = Race.InkFaint, modifier = Modifier.padding(start = 4.dp).size(20.dp))
                    }
                }
                if (!isFolded) items(rows, key = { "i:${it.group}:${it.id}" }) { it -> PickerRow(it, it.id == selected) { onPick(it.id) } }
            }
        }
        Spacer(Modifier.size(12.dp))
    }
}

@Composable
private fun GroupChip(text: String, count: Int, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(CapShape(cut = 6.dp, radius = 3.dp))
            .background(if (on) Race.Electric else Race.Cap)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { stateDescription = if (on) "Selected" else "" }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$text  $count", style = RaceType.capLabel, color = if (on) com.simdeck.ui.theme.OnAccent else Race.Ink, maxLines = 1)
    }
}

@Composable
private fun PickerRow(item: PickerItem, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(CapShape(cut = 8.dp, radius = 4.dp))
            .background(if (selected) Race.Cap else Race.Panel)
            .then(if (selected) Modifier.border(1.dp, Race.Electric, CapShape(cut = 8.dp, radius = 4.dp)) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = item.title + (item.live?.let { ", now $it" } ?: "") }
            .heightIn(min = 52.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val vector = IconLibrary[item.icon]
        if (vector != null) {
            Icon(vector, null, tint = if (selected) Race.Electric else Race.InkMuted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, color = Race.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(item.id, item.detail).filter { it.isNotBlank() }.distinct().joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = Race.InkFaint,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.live != null) {
            Spacer(Modifier.width(10.dp))
            Text(item.live, style = RaceType.readout, color = Race.Cyan, maxLines = 1)
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Check, contentDescription = "Current", tint = Race.Electric)
        }
    }
}
