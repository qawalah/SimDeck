package com.simdeck.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simdeck.model.Control
import com.simdeck.model.FeedbackSource
import com.simdeck.model.IndicatorState
import com.simdeck.ui.icons.IconLibrary
import com.simdeck.ui.theme.CapShape
import com.simdeck.ui.theme.Race
import com.simdeck.ui.theme.RaceType
import com.simdeck.ui.theme.lit
import kotlinx.coroutines.delay

/** One half of a rocker: a hold-to-act zone that reports 1 on press and 0 on release (or cancel). */
private fun Modifier.holdZone(active: Boolean, onChange: (Boolean) -> Unit): Modifier =
    if (!active) this else pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false).consume()
            onChange(true)
            try { waitForUpOrCancellation() } finally { onChange(false) }
        }
    }

/**
 * A rocker switch: two momentary halves on one cap, up / down when tall, right / left when wide.
 * [onUp] drives [Control.cw], [onDown] drives [Control.ccw]; both send 1 while held and 0 on release.
 */
@Composable
fun RockerSwitch(
    control: Control,
    upValue: Float,
    downValue: Float,
    indicator: IndicatorState?,
    active: Boolean,
    enabled: Boolean,
    onUp: (Float) -> Unit,
    onDown: (Float) -> Unit,
    modifier: Modifier,
) {
    val accent = control.accent.lit()
    val haptic = rememberPressHaptic()
    val up by rememberUpdatedState(onUp)
    val down by rememberUpdatedState(onDown)
    var upHeld by remember { mutableStateOf(false) }
    var downHeld by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { if (upHeld) up(0f); if (downHeld) down(0f) } }
    val upOn = upHeld || upValue >= 0.5f
    val downOn = downHeld || downValue >= 0.5f
    BoxWithConstraints(
        modifier.semantics {
            contentDescription = control.label
            stateDescription = when { !enabled -> "Not available on this vehicle"; upOn -> "Up held"; downOn -> "Down held"; else -> "Centred" }
        },
    ) {
        val tall = maxHeight >= maxWidth
        ControlCap(
            modifier = Modifier.fillMaxSize(),
            lit = upOn || downOn || indicator?.lit == true,
            accent = accent,
            source = indicator?.source ?: FeedbackSource.LOCAL,
            pressed = upOn || downOn,
            enabled = enabled,
            showBand = indicator?.source == FeedbackSource.GAME && indicator.lit,
        ) {
            val half: @Composable (Boolean, Modifier) -> Unit = { isUp, m ->
                val held = if (isUp) upOn else downOn
                val ink by animateColorAsState(if (held && enabled) accent else Race.InkMuted, label = "rocker")
                Box(
                    m
                        .semantics {
                            role = Role.Button
                            contentDescription = control.label + if (isUp) (if (tall) " up" else " right") else (if (tall) " down" else " left")
                            onClick { (if (isUp) up else down)(1f); (if (isUp) up else down)(0f); true }
                        }
                        .holdZone(active) { pressed ->
                            if (isUp) upHeld = pressed else downHeld = pressed
                            if (pressed) haptic()
                            (if (isUp) up else down)(if (pressed) 1f else 0f)
                        }
                        .then(if (held) Modifier.background(accent.copy(alpha = 0.16f)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    val arrow = when {
                        tall && isUp -> Icons.Rounded.KeyboardArrowUp
                        tall -> Icons.Rounded.KeyboardArrowDown
                        isUp -> Icons.Rounded.KeyboardArrowRight
                        else -> Icons.Rounded.KeyboardArrowLeft
                    }
                    Icon(arrow, null, tint = ink, modifier = Modifier.size(30.dp))
                }
            }
            val label: @Composable (Modifier) -> Unit = { m ->
                Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    IconLibrary[control.icon]?.let { Icon(it, null, tint = if (upOn || downOn) accent else Race.InkMuted, modifier = Modifier.size(20.dp)) }
                    Text(
                        indicator?.text ?: control.label.uppercase(),
                        style = if (indicator?.text != null) RaceType.readout else RaceType.unit,
                        color = Race.Ink, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (tall) Column(Modifier.fillMaxSize()) {
                half(true, Modifier.weight(1f).fillMaxWidth())
                label(Modifier.fillMaxWidth().padding(horizontal = 4.dp))
                half(false, Modifier.weight(1f).fillMaxWidth())
            } else Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                half(false, Modifier.weight(1f).fillMaxSize())
                label(Modifier.weight(1.2f).padding(vertical = 4.dp))
                half(true, Modifier.weight(1f).fillMaxSize())
            }
        }
    }
}

/** A number from the game, large, with its unit: speed, rpm, gear, fuel, a hydraulic position. */
@Composable
fun GaugeReadout(control: Control, indicator: IndicatorState?, modifier: Modifier) {
    val live = indicator?.source == FeedbackSource.GAME
    val accent = control.accent.lit()
    val text = indicator?.text ?: "--"
    BoxWithConstraints(
        modifier
            .clip(CapShape())
            .background(Race.Panel)
            .border(1.dp, Race.Line, CapShape())
            .semantics { contentDescription = "${control.label}: $text ${control.unit ?: ""}".trim() }
            .padding(6.dp),
    ) {
        val big = maxHeight >= 110.dp && maxWidth >= 140.dp
        val medium = maxHeight >= 70.dp
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(control.label.uppercase(), style = RaceType.unit, color = Race.InkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text,
                style = if (big) RaceType.readoutHuge else if (medium) RaceType.readoutLarge else RaceType.readout,
                color = if (live) accent else Race.InkFaint,
                maxLines = 1,
            )
            control.unit?.let { Text(it.uppercase(), style = RaceType.unit, color = Race.InkMuted, maxLines = 1) }
        }
    }
}

/** A tell-tale lamp lit by the game: no input, just state (ABS active, park brake, high beam...). */
@Composable
fun TellTale(control: Control, indicator: IndicatorState?, modifier: Modifier) {
    val lit = indicator?.lit == true
    val accent = control.accent.lit()
    val ink by animateColorAsState(if (lit) accent else Race.Line, label = "lamp")
    Column(
        modifier
            .clip(CapShape())
            .background(if (lit) accent.copy(alpha = 0.12f) else Race.Panel)
            .border(1.dp, if (lit) accent.copy(alpha = 0.6f) else Race.Line, CapShape())
            .semantics { contentDescription = control.label; stateDescription = if (lit) "On" else "Off" }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val v = IconLibrary[control.icon]
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val d = (minOf(maxWidth, maxHeight) * 0.7f).coerceAtMost(56.dp)
            if (v != null) Icon(v, null, tint = ink, modifier = Modifier.size(d))
            else Canvas(Modifier.size(d)) { drawCircle(ink) }
        }
        Text(control.label.uppercase(), style = RaceType.unit, color = if (lit) Race.Ink else Race.InkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Goes to another page; acts on release like a key. */
@Composable
fun PageKey(control: Control, active: Boolean, enabled: Boolean, onTap: () -> Unit, modifier: Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val tap by rememberUpdatedState(onTap)
    val haptic = rememberPressHaptic()
    val accent = control.accent.lit()
    ControlCap(
        modifier = modifier
            .semantics { role = Role.Button; contentDescription = control.label; onClick { tap(); true } }
            .then(
                if (!active) Modifier else Modifier.pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false).consume()
                        pressed = true
                        val up = waitForUpOrCancellation()
                        pressed = false
                        if (up != null) { haptic(); tap() }
                    }
                },
            ),
        lit = false,
        accent = accent,
        pressed = pressed,
        enabled = enabled,
        showBand = false,
    ) {
        CapFace(control.label, control.icon ?: "page_next", false, accent)
    }
}

/**
 * A flip-up safety cover over a control, like the guard on a real ignition or gear switch: the first tap lifts it
 * for a few seconds, then the control underneath works normally. It closes again on its own.
 */
@Composable
fun GuardCover(label: String, active: Boolean, modifier: Modifier, content: @Composable (Modifier) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var touches by remember { mutableStateOf(0) }
    LaunchedEffect(open, touches) { if (open) { delay(GUARD_OPEN_MS); open = false } }
    val haptic = rememberPressHaptic()
    Box(modifier) {
        content(Modifier.fillMaxSize().pointerInput(open) {
            // Any touch on the uncovered control keeps the cover up a little longer.
            awaitEachGesture { awaitFirstDown(requireUnconsumed = false); touches++ }
        })
        if (!open) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CapShape())
                    .background(Race.Ground.copy(alpha = 0.72f))
                    .border(2.dp, Race.Red.copy(alpha = 0.8f), CapShape())
                    .semantics { role = Role.Button; contentDescription = "$label: lift the cover"; onClick { open = true; true } }
                    .then(
                        if (!active) Modifier else Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false).consume()
                                if (waitForUpOrCancellation() != null) { haptic(); open = true }
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f }) {
                    // Hazard stripes across the cover.
                    val step = 18.dp.toPx()
                    var x = -size.height
                    while (x < size.width) {
                        drawLine(Race.Red, Offset(x, size.height), Offset(x + size.height, 0f), 5.dp.toPx())
                        x += step
                    }
                }
                Text("COVER\n${label.uppercase()}", style = RaceType.unit, color = Race.Ink, textAlign = TextAlign.Center, maxLines = 3)
            }
        }
    }
}

const val GUARD_OPEN_MS = 4_000L

/** The colour for a source-less display, kept here so previews and the editor agree. */
val DisplayInk: Color get() = Race.InkMuted
