package com.simdeck.ui.sim

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simdeck.model.Control
import com.simdeck.model.FeedbackSource
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private fun spanDeg(n: Int) = minOf(135f, 32f * (n - 1).coerceAtLeast(1))

/** Angle (degrees, 0 = up, clockwise) of position [i] of [n] on a rotary selector. */
private fun posAngle(i: Int, n: Int): Float {
    if (n <= 1) return 0f
    val span = spanDeg(n)
    return -span + 2 * span * i / (n - 1)
}

/**
 * A rotary selector: a bar-pointer knob with its positions engraved around it (magneto, light switch, wiper speed).
 * Touch or drag around the knob to pick the nearest position; tap the knob to step to the next one.
 */
@Composable
fun RotarySelector(control: Control, index: Int, pending: Boolean, enabled: Boolean, active: Boolean, onPick: (Int) -> Unit, modifier: Modifier) {
    val t = LocalSimTheme.current
    val tm = rememberTextMeasurer()
    val n = control.positions.size.coerceAtLeast(1)
    val pick by rememberUpdatedState(onPick)
    val curIndex by rememberUpdatedState(index)
    val shownAngle by animateFloatAsState(posAngle(index, n), label = "pointer")
    val blink = rememberBlink(pending)
    Canvas(
        modifier
            .alpha(if (enabled) 1f else 0.42f)
            .semantics {
                role = Role.DropdownList
                contentDescription = control.label
                stateDescription = if (!enabled) "Not available on this vehicle" else control.positions.getOrNull(index)?.label ?: ""
                onClick(label = "Next position") { pick((curIndex + 1) % n); true }
            }
            .then(if (!active) Modifier else Modifier.pointerInput(n) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val c0 = Offset(size.width / 2f, size.height * 0.56f)
                    val r = min(size.width, size.height) * 0.3f
                    fun nearest(p: Offset): Int {
                        val a = Math.toDegrees(atan2((p.x - c0.x).toDouble(), -(p.y - c0.y).toDouble())).toFloat()
                        return (0 until n).minByOrNull { kotlin.math.abs(posAngle(it, n) - a) } ?: 0
                    }
                    var moved = false
                    val onKnob = hypot(down.position.x - c0.x, down.position.y - c0.y) < r * 0.75f
                    while (true) {
                        val e = awaitPointerEvent()
                        val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        if (hypot(ch.position.x - down.position.x, ch.position.y - down.position.y) > 6.dp.toPx()) moved = true
                        if (moved) { val i = nearest(ch.position); if (i != curIndex) pick(i) }
                        ch.consume()
                    }
                    if (!moved) pick(if (onKnob) (curIndex + 1) % n else nearest(down.position))
                }
            }),
    ) {
        val labelH = if (size.height > 80.dp.toPx()) drawLegend(tm, control.label.uppercase(), t.legendStyle(11.sp), t.legendMuted, size.width / 2, 1.dp.toPx(), size.width) else 0f
        val c = Offset(size.width / 2f, labelH + (size.height - labelH) * 0.56f)
        val r = min(size.width, size.height - labelH) * 0.28f
        // Position marks and engraved names around the knob.
        val ls = t.legendStyle((r / density / 2.6f).coerceIn(8f, 12f).sp)
        for (i in 0 until n) {
            val a = Math.toRadians(posAngle(i, n).toDouble())
            val dir = Offset(sin(a).toFloat(), -cos(a).toFloat())
            val sel = i == index
            drawLine(if (sel) t.lamp(control.accent) else t.legendMuted, c + dir * (r * 1.12f), c + dir * (r * 1.32f), 2.dp.toPx(), StrokeCap.Round)
            val p = c + dir * (r * 1.62f)
            val col = if (sel) t.lamp(control.accent).copy(alpha = if (pending) 0.4f + 0.6f * blink else 1f) else t.legend
            drawLegend(tm, control.positions[i].label.uppercase(), ls, col, p.x, p.y - ls.fontSize.toPx() * 0.62f, r * 1.4f)
        }
        // Knob: skirt, body, bar pointer.
        drawCircle(Color.Black.copy(alpha = 0.55f), r * 1.02f, c + Offset(0f, 4.dp.toPx()))
        drawCircle(Brush.sweepGradient(listOf(t.capHigh, t.cap, t.capHigh, t.cap, t.capHigh), c), r, c)
        drawCircle(t.capEdge, r, c, style = Stroke(1.dp.toPx()))
        rotate(shownAngle, c) {
            val bw = r * 0.42f
            drawRoundRect(Brush.horizontalGradient(listOf(t.capHigh, Color(0xFF5A5E64), t.capHigh), c.x - bw / 2, c.x + bw / 2),
                Offset(c.x - bw / 2, c.y - r * 1.05f), Size(bw, r * 2.1f), CornerRadius(bw / 2))
            drawLine(Color.White, Offset(c.x, c.y - r * 0.95f), Offset(c.x, c.y - r * 0.45f), 3.dp.toPx(), StrokeCap.Round)
        }
    }
}

/**
 * A gated lever (P R N D, F N R, 1 2 3): a slot with a notch per position and the names beside it. Touch or drag
 * along the gate to pick the nearest station.
 */
@Composable
fun GatedLever(control: Control, index: Int, pending: Boolean, enabled: Boolean, active: Boolean, onPick: (Int) -> Unit, modifier: Modifier) {
    val t = LocalSimTheme.current
    val tm = rememberTextMeasurer()
    val n = control.positions.size.coerceAtLeast(1)
    val pick by rememberUpdatedState(onPick)
    val curIndex by rememberUpdatedState(index)
    val shown by animateFloatAsState(index.toFloat(), label = "lever")
    val blink = rememberBlink(pending)
    Canvas(
        modifier
            .alpha(if (enabled) 1f else 0.42f)
            .semantics {
                role = Role.DropdownList
                contentDescription = control.label
                stateDescription = if (!enabled) "Not available on this vehicle" else control.positions.getOrNull(index)?.label ?: ""
                onClick(label = "Next position") { pick((curIndex + 1) % n); true }
            }
            .then(if (!active) Modifier else Modifier.pointerInput(n) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val horizontal = size.width >= size.height
                    fun at(p: Offset): Int {
                        val f = if (horizontal) p.x / size.width else p.y / size.height
                        return (f * n).toInt().coerceIn(0, n - 1)
                    }
                    if (at(down.position) != curIndex) pick(at(down.position))
                    while (true) {
                        val e = awaitPointerEvent()
                        val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        val i = at(ch.position)
                        if (i != curIndex) pick(i)
                        ch.consume()
                    }
                }
            }),
    ) {
        val horizontal = size.width >= size.height
        val rr = CornerRadius(t.corner.toPx())
        drawRoundRect(t.panel, Offset.Zero, size, rr)
        drawFinish(t.panelFinish, alpha = 0.7f)
        drawRoundRect(t.capEdge, Offset.Zero, size, rr, style = Stroke(1.dp.toPx()))
        val seg = (if (horizontal) size.width else size.height) / n
        fun center(f: Float) = if (horizontal) Offset(seg * (f + 0.5f), size.height * 0.42f) else Offset(size.width * 0.36f, seg * (f + 0.5f))
        val a = center(0f); val b = center(n - 1f)
        val slotW = 9.dp.toPx()
        drawLine(Color.Black, a, b, slotW, StrokeCap.Round)
        val ls = t.legendStyle(((if (horizontal) size.height else size.width) / density / 5.5f).coerceIn(10f, 16f).sp)
        for (i in 0 until n) {
            val p = center(i.toFloat())
            // Notch, then the position's name.
            val notch = if (horizontal) Offset(0f, slotW) else Offset(slotW, 0f)
            drawLine(Color.Black, p, p + notch, slotW * 0.9f, StrokeCap.Round)
            val sel = i == index
            val col = if (sel) t.lamp(control.accent).copy(alpha = if (pending) 0.4f + 0.6f * blink else 1f) else t.legendMuted
            val lp = if (horizontal) Offset(p.x, size.height * 0.72f) else Offset(size.width * 0.72f, p.y - ls.fontSize.toPx() * 0.62f)
            drawLegend(tm, control.positions[i].label.uppercase(), ls, col, lp.x, lp.y, if (horizontal) seg else size.width * 0.5f)
        }
        // Lever knob: a ball with a collar on machines, a chrome T on cars, a plain knob elsewhere.
        val k = center(shown)
        val kr = min(seg * 0.32f, (if (horizontal) size.height else size.width) * 0.2f)
        drawCircle(Color.Black.copy(alpha = 0.5f), kr, k + Offset(0f, 4.dp.toPx()))
        t.decor?.let { drawCircle(it, kr * 0.75f, k + Offset(0f, 2.dp.toPx())) }
        drawCircle(Brush.radialGradient(listOf(t.capHigh, Color.Black), k - Offset(kr * 0.3f, kr * 0.4f), kr * 1.5f), kr * 0.85f, k)
        drawCircle(t.lamp(control.accent).copy(alpha = 0.9f), kr * 0.85f, k, style = Stroke(2.dp.toPx()))
    }
}

/** True when the control's position comes from the game and was just changed (waiting for the echo). */
fun isPending(source: FeedbackSource?) = source == FeedbackSource.PENDING

/** The rect of a cell inset by [d]. */
fun Rect.inset(d: Float) = Rect(left + d, top + d, right - d, bottom - d)
