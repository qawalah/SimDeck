package com.simdeck.ui.sim

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simdeck.model.Control
import kotlin.math.min

/** Geometry of a slider's travel: [f] is 0..1 along it, from the rest end. */
class Travel(val vertical: Boolean, val thumb: Float, val w: Float, val h: Float) {
    val len = (if (vertical) h else w) - thumb
    val cross = if (vertical) w else h
    /** Centre point of the travel at [f]. */
    fun at(f: Float, crossPos: Float = cross / 2): Offset {
        val p = thumb / 2 + f.coerceIn(0f, 1f) * len
        return if (vertical) Offset(crossPos, h - p) else Offset(p, crossPos)
    }
}

/** The knob shape of a light-aircraft quadrant lever, from the control's label and icon (shape coding). */
enum class KnobKind { THROTTLE, PROP, MIXTURE, FLAPS, GEAR }

fun knobKind(c: Control): KnobKind {
    val n = (c.label + " " + (c.icon ?: "")).lowercase()
    return when {
        "flap" in n -> KnobKind.FLAPS
        "gear" in n -> KnobKind.GEAR
        "mix" in n -> KnobKind.MIXTURE
        "prop" in n || "rpm" in n || "pitch" in n -> KnobKind.PROP
        else -> KnobKind.THROTTLE
    }
}

/** Themed slider skins (docs/THEMES.md, Slider). [f] is the shown position 0..1, [restF] the rest point. */
fun DrawScope.drawSliderSkin(
    skin: String, t: SimTheme, c: Control, vertical: Boolean, thumb: Float, f: Float, restF: Float,
    enabled: Boolean, dragging: Boolean, accent: Color, tm: TextMeasurer,
) {
    val tr = Travel(vertical, thumb, size.width, size.height)
    val rr = CornerRadius(t.corner.toPx())
    // Housing.
    drawRoundRect(t.panel, Offset.Zero, size, rr)
    drawFinish(t.panelFinish, alpha = 0.7f)
    drawRoundRect(t.capEdge, Offset.Zero, size, rr, style = Stroke(1.dp.toPx()))
    val stationSide = tr.cross * 0.74f
    val slotCross = when (skin) { "thrust-lever" -> tr.cross * 0.22f; "quadrant-knob", "stalk" -> 6.dp.toPx(); "hydraulic-lever" -> 11.dp.toPx(); else -> 7.dp.toPx() }
    val slotCenter = if (c.stations.isNotEmpty() && tr.cross > 90.dp.toPx() && skin != "stalk") tr.cross * 0.4f else tr.cross / 2
    // Slot.
    val a = tr.at(0f, slotCenter); val b = tr.at(1f, slotCenter)
    val slot = if (vertical) Rect(a.x - slotCross / 2, b.y, a.x + slotCross / 2, a.y) else Rect(a.x, a.y - slotCross / 2, b.x, a.y + slotCross / 2)
    drawRoundRect(Color.Black.copy(alpha = 0.8f), slot.topLeft, slot.size, CornerRadius(slotCross / 2))
    // Scale ticks either side (not for the stalk, which draws numbered stops).
    if (skin != "stalk" && skin != "pedal") {
        val ticks = if (c.detents > 1) c.detents - 1 else 10
        for (i in 0..ticks) {
            val p = tr.at(i / ticks.toFloat(), slotCenter)
            val major = c.detents > 1 || i % 5 == 0
            val tl = (if (major) 9 else 5).dp.toPx()
            val off = slotCross / 2 + 4.dp.toPx()
            val col = t.legendMuted.copy(alpha = if (major) 0.9f else 0.5f)
            if (vertical) {
                drawLine(col, Offset(p.x - off - tl, p.y), Offset(p.x - off, p.y), 1.dp.toPx())
                drawLine(col, Offset(p.x + off, p.y), Offset(p.x + off + tl, p.y), 1.dp.toPx())
            } else {
                drawLine(col, Offset(p.x, p.y - off - tl), Offset(p.x, p.y - off), 1.dp.toPx())
                drawLine(col, Offset(p.x, p.y + off), Offset(p.x, p.y + off + tl), 1.dp.toPx())
            }
        }
    }
    // Fill from rest to the value, in the accent (the second cue next to the handle's position).
    if (enabled && skin != "stalk") {
        val p0 = tr.at(restF, slotCenter); val p1 = tr.at(f, slotCenter)
        val fw = slotCross * 0.5f
        if (vertical) drawRoundRect(accent.copy(alpha = 0.85f), Offset(p0.x - fw / 2, minOf(p0.y, p1.y)), Size(fw, kotlin.math.abs(p1.y - p0.y)), CornerRadius(fw / 2))
        else drawRoundRect(accent.copy(alpha = 0.85f), Offset(minOf(p0.x, p1.x), p0.y - fw / 2), Size(kotlin.math.abs(p1.x - p0.x), fw), CornerRadius(fw / 2))
    }
    drawStations(t, c, tr, slotCenter, slotCross, tm, f)
    val k = tr.at(f, slotCenter)
    when (skin) {
        "thrust-lever" -> {
            // A big airliner thrust-lever grip across the gate, with a ridge and a white index line.
            val gw = tr.cross * 0.62f; val gl = thumb * 1.15f
            val g = if (vertical) Rect(k.x - gw / 2, k.y - gl / 2, k.x + gw / 2, k.y + gl / 2) else Rect(k.x - gl / 2, k.y - gw / 2, k.x + gl / 2, k.y + gw / 2)
            drawRoundRect(Color.Black.copy(alpha = 0.55f), g.topLeft + Offset(0f, 4.dp.toPx()), g.size, CornerRadius(6.dp.toPx()))
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF4A4E53), Color(0xFF26282B)), g.top, g.bottom), g.topLeft, g.size, CornerRadius(6.dp.toPx()))
            drawRoundRect(if (dragging) accent else Color(0xFF0F1012), g.topLeft, g.size, CornerRadius(6.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            if (vertical) drawLine(Color.White, Offset(g.left + 4.dp.toPx(), k.y), Offset(g.right - 4.dp.toPx(), k.y), 2.dp.toPx())
            else drawLine(Color.White, Offset(k.x, g.top + 4.dp.toPx()), Offset(k.x, g.bottom - 4.dp.toPx()), 2.dp.toPx())
        }
        "quadrant-knob" -> drawQuadrantKnob(knobKind(c), k, min(tr.cross * 0.36f, thumb * 0.62f), vertical, dragging, accent)
        "hydraulic-lever" -> {
            val kr = min(thumb / 2, tr.cross / 2 - 4.dp.toPx())
            // Rubber boot at the slot, then the ball knob with a coloured collar.
            drawCircle(Color.Black.copy(alpha = 0.5f), kr, k + Offset(0f, 4.dp.toPx()))
            drawCircle(t.decor ?: accent, kr * 0.62f, k + Offset(0f, 2.dp.toPx()))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF4A4F55), Color(0xFF15171A)), k - Offset(kr * 0.3f, kr * 0.4f), kr * 1.4f), kr * 0.82f, k)
            drawCircle(if (dragging) accent else Color.Black, kr * 0.82f, k, style = Stroke(2.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.18f), kr * 0.22f, k - Offset(kr * 0.25f, kr * 0.3f))
        }
        "hand-throttle" -> {
            val gw = min(tr.cross * 0.6f, 56.dp.toPx()); val gl = thumb * 0.9f
            val g = if (vertical) Rect(k.x - gw / 2, k.y - gl / 2, k.x + gw / 2, k.y + gl / 2) else Rect(k.x - gl / 2, k.y - gw / 2, k.x + gl / 2, k.y + gw / 2)
            drawRoundRect(Color.Black.copy(alpha = 0.5f), g.topLeft + Offset(0f, 3.dp.toPx()), g.size, CornerRadius(8.dp.toPx()))
            drawRoundRect(t.decor ?: Color(0xFF8A8F95), g.topLeft, g.size, CornerRadius(8.dp.toPx()))
            for (i in 1..4) {
                if (vertical) { val y = g.top + g.height * i / 5f; drawLine(Color.Black.copy(alpha = 0.45f), Offset(g.left + 5.dp.toPx(), y), Offset(g.right - 5.dp.toPx(), y), 2.dp.toPx()) }
                else { val x = g.left + g.width * i / 5f; drawLine(Color.Black.copy(alpha = 0.45f), Offset(x, g.top + 5.dp.toPx()), Offset(x, g.bottom - 5.dp.toPx()), 2.dp.toPx()) }
            }
        }
        "pedal" -> {
            // A pedal pad with tread, pivoting at the rest end: it lengthens as it is pressed.
            val pw = tr.cross * 0.66f
            val base = tr.at(0f, slotCenter)
            val pad = if (vertical) Rect(base.x - pw / 2, k.y - thumb * 0.6f, base.x + pw / 2, k.y + thumb * 0.6f) else Rect(k.x - thumb * 0.6f, base.y - pw / 2, k.x + thumb * 0.6f, base.y + pw / 2)
            drawRoundRect(Color.Black.copy(alpha = 0.55f), pad.topLeft + Offset(0f, 4.dp.toPx()), pad.size, CornerRadius(6.dp.toPx()))
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFB9BDC2), Color(0xFF5E6268)), pad.top, pad.bottom), pad.topLeft, pad.size, CornerRadius(6.dp.toPx()))
            val n = 5
            for (i in 1 until n) {
                if (vertical) { val y = pad.top + pad.height * i / n; drawLine(Color.Black.copy(alpha = 0.5f), Offset(pad.left + 4.dp.toPx(), y), Offset(pad.right - 4.dp.toPx(), y), 2.5.dp.toPx(), StrokeCap.Round) }
                else { val x = pad.left + pad.width * i / n; drawLine(Color.Black.copy(alpha = 0.5f), Offset(x, pad.top + 4.dp.toPx()), Offset(x, pad.bottom - 4.dp.toPx()), 2.5.dp.toPx(), StrokeCap.Round) }
            }
            drawRoundRect(if (dragging) accent else Color.Black, pad.topLeft, pad.size, CornerRadius(6.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        }
        "stalk" -> {
            val n = (if (c.detents > 1) c.detents else 6).coerceAtMost(11)
            val ns = t.readoutStyle((tr.cross / density / 5f).coerceIn(9f, 14f).sp)
            for (i in 0 until n) {
                val p = tr.at(i / (n - 1f), slotCenter)
                val near = kotlin.math.abs(f - i / (n - 1f)) < 0.5f / (n - 1)
                val off = if (vertical) Offset(tr.cross * 0.28f, 0f) else Offset(0f, tr.cross * 0.28f)
                drawCircle(if (near) accent else t.lampOff, 3.5.dp.toPx(), p)
                drawLegend(tm, i.toString(), ns, if (near) t.legend else t.legendMuted, (p + off).x, (p + off).y - ns.fontSize.toPx() * 0.62f, 28.dp.toPx())
            }
            // The stalk seen end-on: a round tip with a ring.
            val sr = min(tr.cross * 0.2f, thumb * 0.5f)
            drawCircle(Color.Black.copy(alpha = 0.5f), sr, k + Offset(0f, 3.dp.toPx()))
            drawCircle(Brush.radialGradient(listOf(t.capHigh, t.cap), k - Offset(sr * 0.3f, sr * 0.3f), sr * 1.5f), sr, k)
            drawCircle(if (dragging) accent else t.capEdge, sr, k, style = Stroke(2.dp.toPx()))
        }
        else -> { // themed fader: a T-handle in the theme's cap colours
            val hw = tr.cross - 14.dp.toPx(); val hl = thumb - 6.dp.toPx()
            val g = if (vertical) Rect(k.x - hw / 2, k.y - hl / 2, k.x + hw / 2, k.y + hl / 2) else Rect(k.x - hl / 2, k.y - hw / 2, k.x + hl / 2, k.y + hw / 2)
            drawRoundRect(Color.Black.copy(alpha = 0.5f), g.topLeft + Offset(0f, 3.dp.toPx()), g.size, CornerRadius(t.corner.toPx()))
            drawRoundRect(Brush.verticalGradient(listOf(t.capHigh, t.cap), g.top, g.bottom), g.topLeft, g.size, CornerRadius(t.corner.toPx()))
            drawRoundRect(if (dragging) accent else t.capEdge, g.topLeft, g.size, CornerRadius(t.corner.toPx()), style = Stroke(1.5.dp.toPx()))
            if (vertical) drawLine(if (enabled) accent else t.legendMuted, Offset(g.left + g.width * 0.2f, k.y), Offset(g.right - g.width * 0.2f, k.y), 3.dp.toPx(), StrokeCap.Round)
            else drawLine(if (enabled) accent else t.legendMuted, Offset(k.x, g.top + g.height * 0.2f), Offset(k.x, g.bottom - g.height * 0.2f), 3.dp.toPx(), StrokeCap.Round)
        }
    }
    drawEnds(t, c, tr, slotCenter, tm, f)
}

/** Named stations (IDLE, CL, TOGA): a tick across the slot and the name beside it. */
fun DrawScope.drawStations(t: SimTheme, c: Control, tr: Travel, slotCenter: Float, slotCross: Float, tm: TextMeasurer, f: Float) {
    if (c.stations.isEmpty()) return
    val ls = t.legendStyle((tr.cross / density / 7.5f).coerceIn(8f, 11f).sp)
    for (st in c.stations) {
        val p = tr.at(st.at, slotCenter)
        val near = kotlin.math.abs(f - st.at) < 0.04f
        val col = if (near) t.lamp(com.simdeck.model.Accent.NEUTRAL) else t.legend
        if (tr.vertical) {
            drawLine(col, Offset(p.x - slotCross / 2 - 3.dp.toPx(), p.y), Offset(p.x + slotCross / 2 + 3.dp.toPx(), p.y), 2.dp.toPx())
            val room = tr.cross - (p.x + slotCross / 2 + 6.dp.toPx())
            if (st.label.isNotBlank() && room > 18.dp.toPx()) {
                drawLegend(tm, st.label, ls, col, p.x + slotCross / 2 + 6.dp.toPx() + room / 2, p.y - ls.fontSize.toPx() * 0.62f, room)
            }
        } else {
            drawLine(col, Offset(p.x, p.y - slotCross / 2 - 3.dp.toPx()), Offset(p.x, p.y + slotCross / 2 + 3.dp.toPx()), 2.dp.toPx())
            if (st.label.isNotBlank()) drawLegend(tm, st.label, ls, col, p.x, p.y + slotCross / 2 + 5.dp.toPx(), 56.dp.toPx())
        }
    }
}

/** End labels: [Control.ends] low at the rest end, high at the far end. */
fun DrawScope.drawEnds(t: SimTheme, c: Control, tr: Travel, slotCenter: Float, tm: TextMeasurer, f: Float) {
    if (c.ends.isEmpty()) return
    val ls = t.legendStyle((tr.cross / density / 7.5f).coerceIn(8f, 11f).sp)
    val low = c.ends.getOrNull(0).orEmpty(); val high = c.ends.getOrNull(1).orEmpty()
    val lit = t.lamp(c.accent)
    if (tr.vertical) {
        drawLegend(tm, high, ls, if (f > 0.9f) lit else t.legendMuted, tr.cross / 2, 2.dp.toPx(), tr.cross - 4.dp.toPx())
        drawLegend(tm, low, ls, if (f < 0.1f) lit else t.legendMuted, tr.cross / 2, tr.h - ls.fontSize.toPx() * 1.35f, tr.cross - 4.dp.toPx())
    } else {
        drawLegend(tm, low, ls, if (f < 0.1f) lit else t.legendMuted, 30.dp.toPx(), 2.dp.toPx(), 56.dp.toPx())
        drawLegend(tm, high, ls, if (f > 0.9f) lit else t.legendMuted, tr.w - 30.dp.toPx(), 2.dp.toPx(), 56.dp.toPx())
    }
}

/** Shape-coded quadrant knobs: smooth black throttle, scalloped blue propeller, fluted red mixture, airfoil flaps, wheel gear. */
fun DrawScope.drawQuadrantKnob(kind: KnobKind, k: Offset, r: Float, vertical: Boolean, dragging: Boolean, accent: Color) {
    drawCircle(Color.Black.copy(alpha = 0.5f), r, k + Offset(0f, 4.dp.toPx()))
    val ring = if (dragging) accent else Color.Black
    when (kind) {
        KnobKind.THROTTLE -> {
            val w = r * 2.2f; val h = r * 1.5f
            val rr = if (vertical) Rect(k.x - w / 2, k.y - h / 2, k.x + w / 2, k.y + h / 2) else Rect(k.x - h / 2, k.y - w / 2, k.x + h / 2, k.y + w / 2)
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF3A3B3E), Color(0xFF0E0E0F)), rr.top, rr.bottom), rr.topLeft, rr.size, CornerRadius(h / 2))
            drawRoundRect(ring, rr.topLeft, rr.size, CornerRadius(h / 2), style = Stroke(1.5.dp.toPx()))
        }
        KnobKind.PROP -> {
            drawCircle(Color(0xFF2F6FD6), r, k)
            for (i in 0 until 10) {
                val a = Math.toRadians(i * 36.0)
                drawCircle(Color(0xFF1D4E9E), r * 0.22f, k + Offset((kotlin.math.cos(a) * r).toFloat(), (kotlin.math.sin(a) * r).toFloat()))
            }
            drawCircle(Color.White.copy(alpha = 0.25f), r * 0.35f, k - Offset(r * 0.25f, r * 0.3f))
            drawCircle(ring, r, k, style = Stroke(1.5.dp.toPx()))
        }
        KnobKind.MIXTURE -> {
            drawPath(polygon(k, r * 1.05f, 8, 0f, star = true), Color(0xFFD23A2E))
            drawCircle(Color(0xFFE85A4E), r * 0.6f, k)
            drawCircle(ring, r * 0.6f, k, style = Stroke(1.5.dp.toPx()))
        }
        KnobKind.FLAPS -> {
            // An airfoil section, nose to the left.
            val l = r * 2.6f; val th = r * 0.9f
            rotate(if (vertical) 0f else 90f, k) {
                val p = Path().apply {
                    moveTo(k.x - l / 2, k.y)
                    cubicTo(k.x - l / 2, k.y - th, k.x - l * 0.1f, k.y - th * 0.7f, k.x + l / 2, k.y + th * 0.05f)
                    cubicTo(k.x - l * 0.1f, k.y + th * 0.35f, k.x - l / 2, k.y + th * 0.6f, k.x - l / 2, k.y)
                    close()
                }
                drawPath(p, Brush.verticalGradient(listOf(Color(0xFFF2F2F2), Color(0xFF9EA2A7)), k.y - th, k.y + th))
                drawPath(p, ring, style = Stroke(1.5.dp.toPx()))
            }
        }
        KnobKind.GEAR -> {
            drawCircle(Color(0xFF1B1C1E), r, k)
            for (i in 0 until 14) {
                val a = Math.toRadians(i * (360.0 / 14))
                val d = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
                drawLine(Color(0xFF3A3C40), k + d * (r * 0.78f), k + d * r, 2.dp.toPx())
            }
            drawCircle(Color(0xFFDADDE0), r * 0.48f, k)
            drawCircle(ring, r, k, style = Stroke(1.5.dp.toPx()))
        }
    }
}
