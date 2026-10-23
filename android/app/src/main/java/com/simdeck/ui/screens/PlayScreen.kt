package com.simdeck.ui.screens

import com.simdeck.model.Joystick
import com.simdeck.model.ControlType
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ChevronLeft
import com.simdeck.ui.components.rememberPressHaptic
import androidx.compose.foundation.layout.heightIn
import com.simdeck.model.HidStatus
import com.simdeck.model.ConnectionStatus
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simdeck.model.Control
import com.simdeck.model.PanelGroup
import com.simdeck.model.Themes
import com.simdeck.ui.sim.Lighting
import com.simdeck.ui.sim.LocalSimTheme
import com.simdeck.ui.sim.drawPanelGroup
import com.simdeck.ui.sim.simEnvironment
import com.simdeck.ui.sim.simTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.rememberTextMeasurer
import com.simdeck.model.GameId
import com.simdeck.model.Page
import com.simdeck.model.PlayState
import com.simdeck.ui.components.BluetoothLinkBadge
import com.simdeck.ui.components.ControlView
import com.simdeck.ui.components.GameReadouts
import com.simdeck.ui.components.PcLinkBadge
import com.simdeck.ui.components.SlantButton
import com.simdeck.ui.components.SlantStyle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.simdeck.ui.components.SlantTabs
import com.simdeck.ui.theme.Race
import com.simdeck.ui.theme.OnAccent
import com.simdeck.ui.theme.RaceType
import com.simdeck.ui.theme.SlantShape

/** Gap between grid cells on the play surface and in the editor. */
val GridGap = 10.dp

/** Pixel geometry of a page's grid inside a given box. */
data class GridMetrics(val cellW: Dp, val cellH: Dp, val gap: Dp) {
    fun x(col: Int) = (cellW + gap) * col
    fun y(row: Int) = (cellH + gap) * row
    fun w(span: Int) = cellW * span + gap * (span - 1)
    fun h(span: Int) = cellH * span + gap * (span - 1)
}

fun gridMetrics(page: Page, width: Dp, height: Dp, gap: Dp = GridGap) = GridMetrics(
    cellW = (width - gap * (page.columns - 1)) / page.columns,
    cellH = (height - gap * (page.rows - 1)) / page.rows,
    gap = gap,
)

/**
 * The control surface. Edge-to-edge landscape: a slim HUD strip on top and the page's grid
 * filling everything below, so every control is as large as the layout allows.
 */
@Composable
fun PlayScreen(
    state: PlayState,
    onControlValue: (controlId: String, value: Float) -> Unit,
    onRotaryStep: (controlId: String, delta: Int) -> Unit,
    onPageChange: (Int) -> Unit,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    imperialUnits: Boolean = false,
    onOpenConnection: (() -> Unit)? = null,
    onPageControl: (controlId: String) -> Unit = {},
    onDismissWarnings: () -> Unit = {},
    lighting: Lighting = Lighting.AUTO,
    onLighting: (Lighting) -> Unit = {},
) {
    val pages = state.profile.pages
    val pageIndex = state.pageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val page = pages.getOrNull(pageIndex)
    // The equipment theme of this page (docs/THEMES.md), lit for day or night.
    val themeId = Themes.resolve(state.profile, page, state.vehicle.category)
    val night = when (lighting) {
        Lighting.NIGHT -> true
        Lighting.DAY -> false
        Lighting.AUTO -> state.game["lights.low"] == true || state.game["lights.high"] == true
    }
    val sim = remember(themeId, night) { simTheme(themeId, night) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Race.Ground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        PlayHud(state, pageIndex, imperialUnits, onPageChange, onEdit, onBack, onOpenConnection,
            lighting = if (themeId == Themes.RACE) null else lighting, onLighting = onLighting)
        if (state.warnings.isNotEmpty()) WarningStrip(state.warnings, onDismissWarnings)
        if (state.bindHelper) BindHelperStrip()
        CompositionLocalProvider(LocalSimTheme provides sim) {
        Box(Modifier.weight(1f).fillMaxWidth().then(if (sim.isHouse) Modifier else Modifier.simEnvironment(sim)).padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 4.dp)) {
            if (page != null && page.auto != null && page.controls.isEmpty()) {
                AutoPageEmpty(state, onEdit)
            } else if (page == null || page.controls.isEmpty()) {
                EmptyPage(onEdit)
            } else {
                ControlGrid(page, panels = page.panels) { control, mod ->
                    // A joystick is two axes past the UI ("<id>.x", "<id>.y"); a rocker two buttons ("<id>.up", "<id>.down").
                    val (firstId, secondId) = Joystick.ids(control)
                    ControlView(
                        control = control,
                        value = state.values[firstId] ?: 0f,
                        indicator = state.indicators[control.id],
                        enabled = control.id !in state.unavailable,
                        onValue = { onControlValue(firstId, it) },
                        onStep = { onRotaryStep(control.id, it) },
                        modifier = mod,
                        valueY = secondId?.let { state.values[it] } ?: 0f,
                        onValueY = { v -> secondId?.let { onControlValue(it, v) } },
                        onTap = { onPageControl(control.id) },
                    )
                }
            }
        }
        }
    }
}

/**
 * Lays out a page's controls on its grid; [content] draws one control into the given modifier. [panels] are drawn
 * behind the controls in the current theme's style, outset into the grid gaps.
 */
@Composable
fun ControlGrid(page: Page, modifier: Modifier = Modifier, panels: List<PanelGroup> = emptyList(), content: @Composable (Control, Modifier) -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val m = gridMetrics(page, maxWidth, maxHeight)
        if (panels.isNotEmpty()) {
            val sim = LocalSimTheme.current
            val tm = rememberTextMeasurer()
            Canvas(Modifier.fillMaxSize()) {
                val out = m.gap.toPx() * 0.45f
                for (g in panels) {
                    val r = Rect(m.x(g.x).toPx() - out, m.y(g.y).toPx() - out, (m.x(g.x) + m.w(g.w)).toPx() + out, (m.y(g.y) + m.h(g.h)).toPx() + out)
                    drawPanelGroup(sim, r, g.title, tm)
                }
            }
        }
        for (c in page.controls) {
            content(
                c,
                Modifier
                    .offset(m.x(c.x), m.y(c.y))
                    .size(m.w(c.w.coerceAtLeast(1)), m.h(c.h.coerceAtLeast(1))),
            )
        }
    }
}

@Composable
private fun PlayHud(
    state: PlayState,
    pageIndex: Int,
    imperial: Boolean,
    onPageChange: (Int) -> Unit,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    onOpenConnection: (() -> Unit)?,
    lighting: Lighting? = null,
    onLighting: (Lighting) -> Unit = {},
) {
    // While anything is linked the player is probably mid-game: Edit then needs a hold, and a
    // stray tap only says so, instead of dropping them into the editor.
    val live = state.game.isNotEmpty() || state.connection.status == ConnectionStatus.CONNECTED ||
        state.hid.status == HidStatus.CONNECTED
    var holdHint by remember { mutableStateOf(false) }
    // A dropped press (no link could carry it) flashes both badges red for 600ms with a double tick.
    var noLink by remember { mutableStateOf(false) }
    var seenDrops by remember { mutableStateOf(state.linkDrops) }
    val haptic = rememberPressHaptic()
    LaunchedEffect(state.linkDrops) {
        if (state.linkDrops != seenDrops) {
            seenDrops = state.linkDrops
            noLink = true
            haptic(); delay(90); haptic()
            delay(510)
            noLink = false
        }
    }
    LaunchedEffect(holdHint) { if (holdHint) { delay(1600); holdHint = false } }
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        // Link badges drop their "BT"/"PC" names only when the strip would otherwise overflow;
        // their status words, lamps, Edit and Back always stay.
        val pages = state.profile.pages.size
        val needed = 48.dp + 200.dp + (if (pages > 1) 112.dp * pages else 0.dp) +
            (if (state.game.isNotEmpty()) 280.dp else 0.dp) + 300.dp + 130.dp
        val compact = maxWidth < needed
        // Narrowest phones (640dp class): lamp-only badges, no rpm, shorter name, so Edit stays on screen.
        val tight = maxWidth < 720.dp
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to profiles", tint = Race.InkMuted)
            }
            Column(Modifier.widthIn(min = if (tight) 72.dp else 96.dp, max = if (tight) 100.dp else if (compact) 180.dp else 240.dp).padding(end = if (tight) 8.dp else 14.dp)) {
                // Tight: the condensed cap style on up to two lines, so names read instead of "BeamN...".
                Text(
                    if (tight) state.profile.name.uppercase() else state.profile.name,
                    style = if (tight) RaceType.capLabel else RaceType.title,
                    color = Race.Ink,
                    maxLines = if (tight) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The game tag only adds information when the name doesn't already say it.
                val tag = state.profile.game.tag()
                if (!tight && !state.profile.name.contains(tag, ignoreCase = true) && !tag.contains(state.profile.name, ignoreCase = true)) {
                    Text(tag.uppercase(), style = RaceType.unit, color = Race.InkFaint, maxLines = 1)
                }
            }
            Box(Modifier.weight(1f)) {
                if (state.profile.pages.size > 1) {
                    if (tight) {
                        // Smallest phones: a pager can't clip the way a scrolled tab row can.
                        PagePager(state.profile.pages.map { it.name }, pageIndex, onPageChange)
                    } else {
                        Box(Modifier.horizontalScroll(rememberScrollState())) {
                            PageTabs(state.profile.pages.map { it.name }, pageIndex, onPageChange)
                        }
                    }
                }
            }
            GameReadouts(state.game, Modifier.padding(start = if (tight) 4.dp else 12.dp, end = if (tight) 8.dp else 14.dp), imperial = imperial, showRpm = !tight)
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .semantics { liveRegion = LiveRegionMode.Assertive }
                    .then(if (onOpenConnection != null) Modifier.clip(SlantShape()).clickable(onClickLabel = "Open connections", onClick = onOpenConnection) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BluetoothLinkBadge(state.hid, compact = compact, lampOnly = tight, alert = noLink)
                PcLinkBadge(state.connection, compact = compact, lampOnly = tight, alert = noLink)
            }
            if (lighting != null) {
                // Panel lighting for themed pages: automatic (follows the headlights), day, night.
                IconButton(onClick = { onLighting(Lighting.entries[(lighting.ordinal + 1) % Lighting.entries.size]) }) {
                    Icon(
                        when (lighting) {
                            Lighting.AUTO -> Icons.Rounded.BrightnessAuto
                            Lighting.DAY -> Icons.Rounded.LightMode
                            Lighting.NIGHT -> Icons.Rounded.DarkMode
                        },
                        contentDescription = "Panel lighting: " + lighting.name.lowercase(),
                        tint = Race.InkMuted,
                    )
                }
            } else Spacer(Modifier.width(10.dp))
            // Tight: Edit goes icon-only so the page pager keeps room for the page name.
            SlantButton(
                text = if (holdHint) "Hold" else if (tight) "" else "Edit",
                icon = Icons.Rounded.Tune,
                onClick = if (live) ({ holdHint = true }) else onEdit,
                onLongClick = if (live) onEdit else null,
                longClickLabel = "Edit layout",
                modifier = Modifier.widthIn(min = if (tight) 64.dp else 104.dp).semantics { contentDescription = "Edit layout" },
            )
        }
    }
}

/** Page switcher: slanted plates, the current page filled electric. */
@Composable
fun PageTabs(names: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) =
    SlantTabs(names, selected, onSelect, modifier)

/** Previous / next with the current page name between: always fits, two 48dp targets. */
@Composable
private fun PagePager(names: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onSelect(selected - 1) }, enabled = selected > 0) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous page", tint = if (selected > 0) Race.Ink else Race.InkFaint)
        }
        Box(
            Modifier
                .weight(1f, fill = false)
                .heightIn(min = 36.dp)
                .clip(SlantShape())
                .background(Race.Electric)
                .padding(horizontal = 10.dp)
                .semantics { contentDescription = "Page ${selected + 1} of ${names.size}: ${names[selected]}" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                names[selected].uppercase(),
                style = RaceType.capLabel.copy(fontStyle = FontStyle.Italic),
                color = OnAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { onSelect(selected + 1) }, enabled = selected < names.lastIndex) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next page", tint = if (selected < names.lastIndex) Race.Ink else Race.InkFaint)
        }
    }
}

/** Problems the companion found with this profile (vJoy set up too small...), until dismissed. */
@Composable
private fun WarningStrip(warnings: List<String>, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(SlantShape())
            .background(Race.Amber.copy(alpha = 0.14f))
            .border(1.dp, Race.Amber.copy(alpha = 0.5f), SlantShape())
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            warnings.joinToString("  ·  "),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = Race.Ink,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Dismiss", color = Race.Amber) }
    }
}

@Composable
private fun BindHelperStrip() {
    Text(
        "BIND HELPER ON · sticks move one axis at a time, so the game captures the right one",
        style = RaceType.unit,
        color = Race.Cyan,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
    )
}

/** An auto page with nothing on it yet: say why, and show what the mod reported so problems can be traced. */
@Composable
private fun AutoPageEmpty(state: PlayState, onEdit: () -> Unit) {
    val connected = state.connection.status == ConnectionStatus.CONNECTED
    val beamng = state.connection.capabilities.beamng
    val (title, body) = when {
        !connected -> "Connect to the PC" to "This page fills itself with the vehicle's own functions (tailgate, doors, bed, crane, outriggers...). It needs the PC companion."
        !beamng -> "Waiting for BeamNG" to "Start BeamNG.drive with the SimDeck mod enabled. The companion shows \"BeamNG: connected\" when the mod is talking to it."
        state.vehicle.vehicle.isEmpty() -> "Get in a vehicle" to "Spawn or enter a vehicle. Its functions appear here a second later."
        else -> "${state.vehicle.vehicle}: nothing extra found" to "The mod found no vehicle-specific functions on this vehicle. The details below say where it looked."
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = Race.Ink)
        Spacer(Modifier.height(6.dp))
        Text(body, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = Race.InkMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (state.vehicle.diagnostics.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                state.vehicle.diagnostics.take(8).joinToString("\n"),
                style = RaceType.unit,
                color = Race.InkFaint,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EmptyPage(onEdit: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("This page is empty", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = Race.Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "Add buttons, switches and sliders in the editor.",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = Race.InkMuted,
        )
        Spacer(Modifier.height(20.dp))
        SlantButton("Open the editor", onEdit, icon = Icons.Rounded.Tune, style = SlantStyle.PRIMARY)
    }
}
