package com.simdeck.ui.sim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simdeck.model.Accent
import com.simdeck.model.Control
import com.simdeck.model.FeedbackSource
import com.simdeck.model.IndicatorState
import com.simdeck.ui.icons.IconLibrary
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// ---- scale helpers ---------------------------------------------------------------------------------

/** A gauge's scale: the control's own min / max, else a sensible range for well-known keys. */
fun gaugeRange(c: Control): Pair<Double, Double> {
    val lo = c.min ?: 0.0
    val hi = c.max ?: when (c.feedbackKey) {
        "rpm" -> 8000.0
        "speed" -> when { c.scale > 3.0 -> 240.0; c.scale > 2.0 -> 160.0; else -> 70.0 }
        "fuel" -> if (c.scale >= 50) 100.0 else 1.0
        "cruise.speed" -> if (c.scale > 3.0) 200.0 else 60.0
        else -> 100.0
    }
    return lo to if (hi > lo) hi else lo + 1.0
}

/** A round step for [span] split into about [parts] parts: 1, 2 or 5 times a power of ten. */
fun niceStep(span: Double, parts: Int): Double {
    val raw = span / parts
    val mag = 10.0.pow(floor(log10(raw)))
    val f = raw / mag
    return mag * when { f < 1.5 -> 1.0; f < 3.5 -> 2.0; f < 7.5 -> 5.0; else -> 10.0 }
}

fun bandColor(t: SimTheme, name: String): Color = when (name.lowercase()) {
    "red" -> t.lamp(Accent.RED)
    "amber", "yellow" -> t.lamp(Accent.AMBER)
    "cyan" -> t.lamp(Accent.CYAN)
    "blue" -> t.lamp(Accent.BLUE)
    "white" -> Color(0xFFF2F2F2)
    else -> t.lamp(Accent.GREEN)
}

private fun fmt(v: Double): String = if (kotlin.math.abs(v) >= 1000 && v % 1000 == 0.0) "${(v / 1000).toInt()}k" else if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(java.util.Locale.ROOT, v)

// ---- seven segments ---------------------------------------------------------------------------------

private val SEG: Map<Char, Int> = mapOf(
    // bits: a b c d e f g
    '0' to 0b1111110, '1' to 0b0110000, '2' to 0b1101101, '3' to 0b1111001, '4' to 0b0110011, '5' to 0b1011011,
    '6' to 0b1011111, '7' to 0b1110000, '8' to 0b1111111, '9' to 0b1111011, '-' to 0b0000001, ' ' to 0,
    'A' to 0b1110111, 'B' to 0b0011111, 'C' to 0b1001110, 'D' to 0b0111101, 'E' to 0b1001111, 'F' to 0b1000111,
    'H' to 0b0110111, 'L' to 0b0001110, 'N' to 0b0010101, 'O' to 0b0011101, 'P' to 0b1100111, 'R' to 0b0000101,
    'S' to 0b1011011, 'T' to 0b0001111, 'U' to 0b0111110, 'Y' to 0b0111011, 'J' to 0b0111000, 'M' to 0b1110110,
)

/** Draws [text] as a seven-segment display in [r], right-aligned over [cells] digit cells, unlit segments faint. */
fun DrawScope.drawSegments(text: String, r: Rect, cells: Int, on: Color, off: Color, slant: Float = 0.08f) {
    val chars = ArrayList<Pair<Char, Boolean>>() // char, decimal point after it
    for (ch in text.uppercase()) {
        if (ch == '.' || ch == ',') { if (chars.isNotEmpty()) chars[chars.lastIndex] = chars.last().first to true; continue }
        chars += ch to false
    }
    val n = cells.coerceAtLeast(chars.size).coerceAtLeast(1)
    val cw = r.width / n
    val ch = r.height
    val w = min(cw * 0.78f, ch * 0.52f)
    val th = w * 0.17f
    val start = n - chars.size
    for (i in 0 until n) {
        val cell = Rect(r.left + i * cw + (cw - w) / 2, r.top, r.left + i * cw + (cw + w) / 2, r.bottom)
        val item = chars.getOrNull(i - start)
        val bits = item?.let { SEG[it.first] } ?: 0
        drawDigit(cell, th, bits, on, off, slant)
        val dpOn = item?.second == true
        drawCircle(if (dpOn) on else off, th * 0.6f, Offset(cell.right + (cw - w) / 4, cell.bottom - th * 0.6f))
    }
}

private fun DrawScope.drawDigit(r: Rect, th: Float, bits: Int, on: Color, off: Color, slant: Float) {
    val l = r.left; val rt = r.right; val t = r.top + th * 0.5f; val b = r.bottom - th * 0.5f; val m = (t + b) / 2
    fun sk(x: Float, y: Float) = Offset(x + (m - y) * slant, y)
    fun seg(x1: Float, y1: Float, x2: Float, y2: Float, bit: Int) {
        val lit = (bits shr (6 - bit)) and 1 == 1
        drawLine(if (lit) on else off, sk(x1, y1), sk(x2, y2), th, StrokeCap.Round)
    }
    val g = th * 0.75f
    seg(l + g, t, rt - g, t, 0)          // a
    seg(rt, t + g, rt, m - g, 1)         // b
    seg(rt, m + g, rt, b - g, 2)         // c
    seg(l + g, b, rt - g, b, 3)          // d
    seg(l, m + g, l, b - g, 4)           // e
    seg(l, t + g, l, m - g, 5)           // f
    seg(l + g, m, rt - g, m, 6)          // g
}

// ---- gauges -----------------------------------------------------------------------------------------

/** Themed gauge faces: dial (steam), segment window, bar graph, tape, shift lights. */
@Composable
fun SkinGauge(skin: String, control: Control, indicator: IndicatorState?, modifier: Modifier) {
    val t = LocalSimTheme.current
    val tm = rememberTextMeasurer()
    val live = indicator?.source == FeedbackSource.GAME
    val text = indicator?.text ?: "--"
    val value = indicator?.text?.toDoubleOrNull()
    val (lo, hi) = gaugeRange(control)
    val blink = rememberBlink(skin == "shift-lights" && value != null && (value - lo) / (hi - lo) >= 0.96)
    Canvas(modifier.semantics { contentDescription = "${control.label}: $text ${control.unit ?: ""}".trim() }) {
        val accent = t.lamp(control.accent)
        val dens = density
        when (skin) {
            "steam" -> {
                val r = min(size.width, size.height) / 2 * 0.96f
                val c = Offset(size.width / 2, size.height / 2)
                // Bezel and face.
                drawCircle(Color.Black.copy(alpha = 0.55f), r, c + Offset(0f, 3.dp.toPx()))
                drawCircle(Brush.sweepGradient(listOf(Color(0xFF6B6F75), Color(0xFF2B2E32), Color(0xFF9A9EA4), Color(0xFF2B2E32), Color(0xFF6B6F75)), c), r, c)
                val face = r * 0.9f
                drawCircle(Color(0xFF0B0C0D), face, c)
                val start = 135f; val sweep = 270f
                fun ang(v: Double) = Math.toRadians(start + sweep * ((v - lo) / (hi - lo)).coerceIn(0.0, 1.0))
                fun pt(v: Double, rr: Float) = ang(v).let { c + Offset((cos(it) * rr).toFloat(), (sin(it) * rr).toFloat()) }
                // Range bands.
                for (bd in control.bands) {
                    val a0 = (start + sweep * ((bd.from - lo) / (hi - lo)).coerceIn(0.0, 1.0)).toFloat()
                    val a1 = (start + sweep * ((bd.to - lo) / (hi - lo)).coerceIn(0.0, 1.0)).toFloat()
                    val br = face * 0.86f; val bw = face * 0.07f
                    drawArc(bandColor(t, bd.color), a0, a1 - a0, false, c - Offset(br, br), Size(br * 2, br * 2), style = Stroke(bw))
                }
                // Ticks and numerals.
                val step = niceStep(hi - lo, 6)
                val minor = step / 5
                var v = lo
                val numStyle = t.readoutStyle((face / dens / 6.5f).coerceIn(7f, 13f).sp)
                while (v <= hi + 1e-9) {
                    val major = kotlin.math.abs(((v - lo) / step) - Math.round((v - lo) / step)) < 1e-6
                    drawLine(Color(0xFFEDEDED).copy(alpha = if (major) 1f else 0.6f), pt(v, face * (if (major) 0.74f else 0.8f)), pt(v, face * 0.9f), (if (major) 2f else 1f).dp.toPx())
                    if (major && face > 34.dp.toPx()) {
                        val p = pt(v, face * 0.58f)
                        drawLegend(tm, fmt(v), numStyle, Color(0xFFEDEDED), p.x, p.y - numStyle.fontSize.toPx() * 0.62f, face * 0.5f)
                    }
                    v += minor
                }
                // Label and value.
                val ls = t.legendStyle((face / dens / 7.5f).coerceIn(7f, 11f).sp)
                drawLegend(tm, control.label.uppercase(), ls, Color(0xFFBFC3C8), c.x, c.y - face * 0.42f, face * 1.1f)
                val vs = t.readoutStyle((face / dens / 4.6f).coerceIn(9f, 20f).sp)
                drawLegend(tm, text + (control.unit?.let { " $it" } ?: ""), vs, if (live) accent else Color(0xFF8D9196), c.x, c.y + face * 0.4f, face * 1.3f)
                // Needle.
                if (value != null) {
                    val tip = pt(value, face * 0.82f); val tail = pt(value, -face * 0.12f)
                    drawLine(Color.Black.copy(alpha = 0.5f), tail + Offset(0f, 2.dp.toPx()), tip + Offset(0f, 2.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
                    drawLine(if (t.id == com.simdeck.model.Themes.ROADCAR) t.lamp(Accent.RED) else Color(0xFFFF7A1A), tail, tip, 2.6.dp.toPx(), StrokeCap.Round)
                }
                drawCircle(Brush.radialGradient(listOf(Color(0xFF8B8F95), Color(0xFF1B1C1E)), c, face * 0.1f), face * 0.08f, c)
            }
            "segment" -> {
                val pad = 4.dp.toPx()
                val ls = t.legendStyle((min(size.width, size.height) / dens / 7f).coerceIn(9f, 12f).sp)
                val lh = drawLegend(tm, control.label.uppercase(), ls, t.legendMuted, size.width / 2, pad, size.width - pad * 2)
                val unitH = if (control.unit != null) ls.fontSize.toPx() * 1.3f else 0f
                val win = Rect(pad, pad + lh + 3.dp.toPx(), size.width - pad, size.height - pad - unitH)
                drawRoundRect(Color(0xFF050505), win.topLeft, win.size, CornerRadius(3.dp.toPx()))
                drawRoundRect(t.capEdge, win.topLeft, win.size, CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx()))
                val inner = Rect(win.left + win.width * 0.08f, win.top + win.height * 0.16f, win.right - win.width * 0.08f, win.bottom - win.height * 0.16f)
                val cells = maxOf(text.count { it != '.' && it != ',' }, 3)
                val on = if (live) t.segment else t.segment.copy(alpha = 0.45f)
                drawSegments(if (indicator?.text == null) "---" else text, inner, cells, on, t.segment.copy(alpha = 0.07f))
                control.unit?.let { drawLegend(tm, it.uppercase(), ls, t.legendMuted, size.width / 2, size.height - pad - unitH + 2f, size.width - pad * 2) }
            }
            "bar" -> {
                val pad = 4.dp.toPx()
                val ls = t.legendStyle((min(size.width, size.height) / dens / 7f).coerceIn(9f, 12f).sp)
                drawRoundRect(t.panel, Offset.Zero, size, CornerRadius(t.corner.toPx()))
                drawRoundRect(t.panelLine, Offset.Zero, size, CornerRadius(t.corner.toPx()), style = Stroke(1.dp.toPx()))
                val lh = drawLegend(tm, control.label.uppercase() + "  " + text + (control.unit?.let { " $it" } ?: ""), ls, t.legend, size.width / 2, pad, size.width - pad * 2)
                val area = Rect(pad * 2, pad + lh + 4.dp.toPx(), size.width - pad * 2, size.height - pad * 2)
                val n = 14
                val gap = 2.dp.toPx()
                val frac = value?.let { ((it - lo) / (hi - lo)).coerceIn(0.0, 1.0) } ?: 0.0
                val horizontal = area.width >= area.height
                for (i in 0 until n) {
                    val f0 = i / n.toFloat()
                    val at = lo + (hi - lo) * (f0 + 0.5 / n)
                    val band = control.bands.firstOrNull { at >= it.from && at <= it.to }
                    val col = band?.let { bandColor(t, it.color) } ?: accent
                    val lit = value != null && f0 < frac
                    val seg = if (horizontal) Rect(area.left + area.width * f0, area.top, area.left + area.width * (i + 1) / n - gap, area.bottom)
                    else Rect(area.left, area.bottom - area.height * (i + 1) / n + gap, area.right, area.bottom - area.height * f0)
                    drawRoundRect(if (lit) col else col.copy(alpha = 0.12f), seg.topLeft, seg.size, CornerRadius(1.5.dp.toPx()))
                }
            }
            "tape" -> {
                val pad = 3.dp.toPx()
                val win = Rect(size.width * 0.18f, pad, size.width * 0.82f, size.height - pad)
                drawRect(Color(0xFF3B4A5C), win.topLeft, win.size)
                val step = niceStep(hi - lo, 4)
                val px = win.height / 4f / step.toFloat()
                val v = value ?: lo
                val ns = t.readoutStyle((win.width / dens / 4.5f).coerceIn(8f, 13f).sp)
                var k = floor((v - 3 * step) / step) * step
                while (k <= v + 3 * step) {
                    val y = (win.center.y - (k - v) * px).toFloat()
                    if (y > win.top && y < win.bottom) {
                        drawLine(Color.White, Offset(win.right - 8.dp.toPx(), y), Offset(win.right, y), 1.5.dp.toPx())
                        drawLegend(tm, fmt(k), ns, Color.White, win.center.x - 4.dp.toPx(), y - ns.fontSize.toPx() * 0.62f, win.width * 0.8f)
                    }
                    k += step
                }
                val box = Rect(win.left - 4.dp.toPx(), win.center.y - ns.fontSize.toPx() * 0.95f, win.right + 4.dp.toPx(), win.center.y + ns.fontSize.toPx() * 0.95f)
                drawRect(Color.Black, box.topLeft, box.size)
                drawRect(Color(0xFFF2F2F2), box.topLeft, box.size, style = Stroke(1.5.dp.toPx()))
                drawLegend(tm, if (value == null) "---" else text, ns.copy(fontSize = ns.fontSize * 1.15f), if (live) t.lamp(Accent.GREEN) else Color(0xFF8D9196), box.center.x, box.top + 1.dp.toPx(), box.width)
                val ls = t.legendStyle(9.sp)
                drawLegend(tm, control.label.uppercase(), ls, t.legendMuted, size.width / 2, size.height - 13.dp.toPx(), size.width)
            }
            "inclinometer" -> {
                // A vehicle seen from behind (roll) or from the side (pitch), tilted by the game's angle on a marked arc.
                val pitch = (control.feedbackKey ?: "").contains("pitch")
                val r = min(size.width, size.height) / 2 * 0.94f
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(Color.Black.copy(alpha = 0.55f), r, c + Offset(0f, 3.dp.toPx()))
                drawCircle(Brush.sweepGradient(listOf(Color(0xFF6B6F75), Color(0xFF2B2E32), Color(0xFF9A9EA4), Color(0xFF2B2E32), Color(0xFF6B6F75)), c), r, c)
                val face = r * 0.9f
                // Sky over ground, the horizon level.
                drawCircle(Color(0xFF0C1218), face, c)
                drawArc(Color(0xFF2A2116), 0f, 180f, true, c - Offset(face, face), Size(face * 2, face * 2))
                val limit = (control.max ?: 45.0).coerceAtLeast(10.0)
                for (deg in listOf(-30, -20, -10, 0, 10, 20, 30)) {
                    if (kotlin.math.abs(deg) > limit) continue
                    val a = Math.toRadians(-90.0 + deg)
                    val d = Offset(cos(a).toFloat(), sin(a).toFloat())
                    val col = when { kotlin.math.abs(deg) >= 30 -> t.lamp(Accent.RED); kotlin.math.abs(deg) >= 20 -> t.lamp(Accent.AMBER); else -> Color(0xFFEDEDED) }
                    drawLine(col, c + d * (face * 0.8f), c + d * (face * 0.96f), 2.dp.toPx())
                }
                val angle = (value ?: 0.0).coerceIn(-limit, limit).toFloat() * (if (pitch) -1f else 1f)
                val w = face * 1.1f; val h = face * 0.42f
                rotate(angle, c) {
                    val body = Path().apply {
                        if (pitch) {
                            // Side view, nose to the right.
                            moveTo(c.x - w / 2, c.y + h * 0.25f); lineTo(c.x - w / 2, c.y - h * 0.2f); lineTo(c.x - w * 0.15f, c.y - h * 0.2f)
                            lineTo(c.x - w * 0.05f, c.y - h * 0.65f); lineTo(c.x + w * 0.22f, c.y - h * 0.65f); lineTo(c.x + w * 0.32f, c.y - h * 0.2f)
                            lineTo(c.x + w / 2, c.y - h * 0.1f); lineTo(c.x + w / 2, c.y + h * 0.25f); close()
                        } else {
                            // Rear view: body, cab, two wheels.
                            moveTo(c.x - w / 2, c.y + h * 0.2f); lineTo(c.x - w / 2, c.y - h * 0.2f); lineTo(c.x - w * 0.3f, c.y - h * 0.25f)
                            lineTo(c.x - w * 0.22f, c.y - h * 0.7f); lineTo(c.x + w * 0.22f, c.y - h * 0.7f); lineTo(c.x + w * 0.3f, c.y - h * 0.25f)
                            lineTo(c.x + w / 2, c.y - h * 0.2f); lineTo(c.x + w / 2, c.y + h * 0.2f); close()
                        }
                    }
                    val col = if (live) t.lamp(control.accent) else Color(0xFF8D9196)
                    drawPath(body, col)
                    val wr = h * 0.28f
                    drawCircle(Color(0xFF111111), wr, Offset(c.x - w * 0.3f, c.y + h * 0.3f))
                    drawCircle(Color(0xFF111111), wr, Offset(c.x + w * 0.3f, c.y + h * 0.3f))
                }
                drawLine(Color(0xFFEDEDED).copy(alpha = 0.5f), Offset(c.x - face, c.y), Offset(c.x + face, c.y), 1.dp.toPx())
                val vs = t.readoutStyle((face / density / 4.8f).coerceIn(9f, 18f).sp)
                drawLegend(tm, (if (value == null) "--" else text) + "°", vs, if (live) Color.White else Color(0xFF8D9196), c.x, c.y + face * 0.45f, face)
                val ls = t.legendStyle((face / density / 7.5f).coerceIn(7f, 11f).sp)
                drawLegend(tm, control.label.uppercase(), ls, Color(0xFFBFC3C8), c.x, c.y - face * 0.85f + 2.dp.toPx(), face)
            }
            "shift-lights" -> {
                val n = 10
                val frac = value?.let { ((it - lo) / (hi - lo)).coerceIn(0.0, 1.0) } ?: 0.0
                val shift = frac >= 0.96
                val dia = min(size.width / (n + 1), size.height * 0.6f)
                val y = size.height / 2
                val green = t.lamp(Accent.GREEN); val amber = t.lamp(Accent.AMBER); val red = t.lamp(Accent.RED); val blue = t.lamp(Accent.BLUE)
                drawRoundRect(Color(0xFF050505), Offset(0f, y - dia * 0.9f), Size(size.width, dia * 1.8f), CornerRadius(dia * 0.9f))
                for (i in 0 until n) {
                    val x = size.width / (n + 1) * (i + 1)
                    val col = if (shift) blue.copy(alpha = 0.35f + 0.65f * blink) else when { i < 4 -> green; i < 7 -> amber; else -> red }
                    val lit = shift || (value != null && i < frac * n * 1.04)
                    drawCircle(if (lit) col else col.copy(alpha = 0.1f), dia * 0.38f, Offset(x, y))
                    if (lit) drawCircle(col.copy(alpha = 0.2f), dia * 0.55f, Offset(x, y))
                }
            }
        }
    }
}

// ---- lamps ------------------------------------------------------------------------------------------

/** Themed lamps: annunciator lens, master caution (tap to acknowledge, on the phone only), LED, tell-tale. */
@Composable
fun SkinLamp(skin: String, control: Control, indicator: IndicatorState?, modifier: Modifier) {
    val t = LocalSimTheme.current
    val tm = rememberTextMeasurer()
    val lit = indicator?.lit == true
    var acked by remember { mutableStateOf(false) }
    LaunchedEffect(lit) { if (!lit) acked = false }
    val isMaster = skin == "master" || skin == "master-warning"
    val flashing = isMaster && lit && !acked
    val blink = rememberBlink(flashing)
    val painter = IconLibrary[control.icon]?.let { rememberVectorPainter(it) }
    Canvas(
        modifier
            .semantics { contentDescription = control.label; stateDescription = if (lit) "On" else "Off" }
            .then(if (isMaster) Modifier.pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); acked = true } } else Modifier),
    ) {
        // Master caution is always amber and master warning always red, whatever accent the control has.
        val lamp = when (skin) { "master" -> t.lamp(Accent.AMBER); "master-warning" -> t.lamp(Accent.RED); else -> t.lamp(control.accent) }
        val level = if (flashing) 0.35f + 0.65f * blink else 1f
        when (skin) {
            "annunciator", "master", "master-warning" -> {
                val pad = 3.dp.toPx()
                val r = Rect(pad, pad, size.width - pad, size.height - pad)
                drawRoundRect(Color.Black.copy(alpha = 0.5f), r.topLeft + Offset(0f, 2.dp.toPx()), r.size, CornerRadius(3.dp.toPx()))
                drawRoundRect(t.cap, r.topLeft, r.size, CornerRadius(3.dp.toPx()))
                val lens = Rect(r.left + 4.dp.toPx(), r.top + 4.dp.toPx(), r.right - 4.dp.toPx(), r.bottom - 4.dp.toPx())
                drawRoundRect(Color(0xFF070707), lens.topLeft, lens.size, CornerRadius(2.dp.toPx()))
                if (lit) drawRoundRect(lamp.copy(alpha = 0.22f * level), lens.topLeft, lens.size, CornerRadius(2.dp.toPx()))
                // Dark cockpit at night: an unlit legend all but disappears.
                val ink = if (lit) lamp.copy(alpha = level) else Color.White.copy(alpha = if (t.night) 0.07f else 0.2f)
                val text = when (skin) {
                    "master" -> "MASTER\n" + control.label.uppercase().ifBlank { "CAUTION" }
                    "master-warning" -> "MASTER\n" + control.label.uppercase().ifBlank { "WARNING" }
                    else -> control.label.uppercase()
                }
                val st = t.legendStyle((lens.height / density / (if (isMaster) 4.2f else 3.2f)).coerceIn(8f, 15f).sp)
                val lines = text.count { it == '\n' } + 1
                drawLegend(tm, text, st, ink, lens.center.x, lens.center.y - st.fontSize.toPx() * 0.62f * lines, lens.width * 0.94f, lines + 1)
            }
            "led-dot" -> {
                val c = Offset(size.width / 2, size.height * 0.4f)
                val r = min(size.width, size.height) * 0.14f
                drawCircle(Color(0xFF0A0A0A), r * 1.5f, c)
                drawCircle(Brush.sweepGradient(listOf(Color(0xFF8E9399), Color(0xFF3A3D41), Color(0xFF8E9399)), c), r * 1.5f, c, style = Stroke(2.dp.toPx()))
                if (lit) drawCircle(lamp.copy(alpha = 0.25f), r * 2.3f, c)
                drawCircle(if (lit) lamp else lamp.copy(alpha = 0.12f), r, c)
                drawCircle(Color.White.copy(alpha = if (lit) 0.5f else 0.1f), r * 0.35f, c - Offset(r * 0.3f, r * 0.3f))
                val st = t.legendStyle((min(size.width, size.height) / density / 8f).coerceIn(8f, 12f).sp)
                drawLegend(tm, control.label.uppercase(), st, if (lit) t.legend else t.legendMuted, size.width / 2, c.y + r * 2.2f, size.width - 6.dp.toPx(), 2)
            }
            else -> { // telltale, themed
                val pad = 3.dp.toPx()
                val r = Rect(pad, pad, size.width - pad, size.height - pad)
                drawRoundRect(if (lit) lamp.copy(alpha = 0.10f) else t.panel, r.topLeft, r.size, CornerRadius(t.corner.toPx()))
                drawRoundRect(if (lit) lamp.copy(alpha = 0.6f) else t.panelLine, r.topLeft, r.size, CornerRadius(t.corner.toPx()), style = Stroke(1.dp.toPx()))
                val side = min(r.width, r.height) * 0.5f
                drawIcon(painter, Offset(r.center.x, r.top + r.height * 0.42f), side, if (lit) lamp else t.lampOff.copy(alpha = 1f).let { Color(0xFF3A3D41) })
                val st = t.legendStyle((min(size.width, size.height) / density / 8f).coerceIn(8f, 12f).sp)
                drawLegend(tm, control.label.uppercase(), st, if (lit) t.legend else t.legendMuted, r.center.x, r.bottom - st.fontSize.toPx() * 1.5f, r.width - 6.dp.toPx())
            }
        }
    }
}

/** Arrow path pointing along +x from [from] to [to]. */
fun arrowHead(tip: Offset, dir: Offset, size: Float): Path = Path().apply {
    val n = Offset(-dir.y, dir.x)
    moveTo(tip.x, tip.y)
    lineTo(tip.x - dir.x * size + n.x * size * 0.6f, tip.y - dir.y * size + n.y * size * 0.6f)
    lineTo(tip.x - dir.x * size - n.x * size * 0.6f, tip.y - dir.y * size - n.y * size * 0.6f)
    close()
}
