package com.simdeck.ui.sim

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simdeck.model.Control
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Skins that turn by dragging up and down rather than around. */
fun isLinearRotary(skin: String) = skin == "trim-wheel" || skin == "thumb-roller"

/**
 * Themed rotaries. [angle] is the knob's visual angle (15 degrees per step), [steps] the net steps since shown,
 * [readout] the game's value when the control has one.
 */
fun DrawScope.drawRotarySkin(
    skin: String, t: SimTheme, c: Control, angle: Float, steps: Int, turning: Boolean, enabled: Boolean,
    readout: String?, tm: TextMeasurer, icon: VectorPainter?,
) {
    val accent = t.lamp(c.accent)
    val cc = Offset(size.width / 2, size.height / 2)
    val r = min(size.width, size.height) / 2
    when (skin) {
        "trim-wheel", "thumb-roller" -> {
            // A wheel seen edge-on: ribs scroll with the turn, a pointer shows the travel.
            val roller = skin == "thumb-roller"
            val w = if (roller) min(size.width * 0.34f, r * 0.8f) else min(size.width * 0.42f, r * 1.0f)
            val h = size.height * (if (roller) 0.66f else 0.86f)
            val body = Rect(cc.x - w / 2, cc.y - h / 2, cc.x + w / 2, cc.y + h / 2)
            drawRoundRect(Color.Black, body.topLeft - Offset(4.dp.toPx(), 4.dp.toPx()), Size(body.width + 8.dp.toPx(), body.height + 8.dp.toPx()), CornerRadius(w * 0.3f))
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF151515), if (roller) Color(0xFF3B3F45) else Color(0xFF4A4A4A), Color(0xFF151515)), body.left, body.right),
                body.topLeft, body.size, CornerRadius(w * 0.3f))
            val pitch = 9.dp.toPx()
            val offset = ((angle / 15f) * pitch) % pitch
            clipRect(body.left, body.top, body.right, body.bottom) {
                var y = body.top - pitch + offset
                while (y < body.bottom + pitch) {
                    // Ribs bunch up towards the ends, as on a cylinder.
                    val rel = ((y - cc.y) / (h / 2)).coerceIn(-1f, 1f)
                    val alpha = 0.55f * (1f - rel * rel)
                    drawLine(Color.Black.copy(alpha = alpha), Offset(body.left + 3.dp.toPx(), y), Offset(body.right - 3.dp.toPx(), y), 2.dp.toPx())
                    drawLine(Color.White.copy(alpha = alpha * 0.35f), Offset(body.left + 3.dp.toPx(), y + 2.dp.toPx()), Offset(body.right - 3.dp.toPx(), y + 2.dp.toPx()), 1.dp.toPx())
                    y += pitch
                }
            }
            drawRoundRect(if (turning) accent else t.capEdge, body.topLeft, body.size, CornerRadius(w * 0.3f), style = Stroke(1.5.dp.toPx()))
            // Up / down marks beside the wheel.
            val ls = t.legendStyle(10.sp)
            val nose = c.ends.getOrNull(1)?.ifBlank { null } ?: if (roller) "+" else "NOSE UP"
            val tail = c.ends.getOrNull(0)?.ifBlank { null } ?: if (roller) "−" else "NOSE DN"
            val side = (size.width - body.right) / 2 + body.right
            if (size.width - body.right > 30.dp.toPx()) {
                drawLegend(tm, nose, ls, t.legendMuted, side, body.top, size.width - body.right - 4.dp.toPx(), 2)
                drawLegend(tm, tail, ls, t.legendMuted, side, body.bottom - ls.fontSize.toPx() * 2.4f, size.width - body.right - 4.dp.toPx(), 2)
            }
            readout?.let { drawLegend(tm, it, t.readoutStyle(14.sp), accent, body.left / 2, cc.y - 9.dp.toPx(), body.left - 4.dp.toPx()) }
        }
        "concentric" -> {
            // Value window above, then an outer ring and an inner knob (flight-deck FCU style).
            val winH = min(size.height * 0.26f, r * 0.55f)
            val win = Rect(cc.x - r * 0.9f, 2.dp.toPx(), cc.x + r * 0.9f, 2.dp.toPx() + winH)
            drawRoundRect(Color(0xFF050505), win.topLeft, win.size, CornerRadius(2.dp.toPx()))
            val txt = readout ?: (if (steps == 0) "---" else steps.toString())
            drawSegments(txt, win.inset(win.height * 0.18f), maxOf(txt.length, 3), t.segment, t.segment.copy(alpha = 0.07f))
            val kc = Offset(cc.x, (win.bottom + size.height) / 2)
            val kr = min(r * 0.86f, (size.height - win.bottom) / 2 * 0.9f)
            drawCircle(Color.Black.copy(alpha = 0.55f), kr, kc + Offset(0f, 4.dp.toPx()))
            drawCircle(Brush.sweepGradient(listOf(Color(0xFF3A3D41), Color(0xFF1A1C1E), Color(0xFF3A3D41), Color(0xFF1A1C1E), Color(0xFF3A3D41)), kc), kr, kc)
            for (i in 0 until 30) {
                val a = Math.toRadians(i * 12.0 + angle * 0.5)
                val d = Offset(cos(a).toFloat(), sin(a).toFloat())
                drawLine(Color.Black.copy(alpha = 0.5f), kc + d * (kr * 0.8f), kc + d * kr, 1.5.dp.toPx())
            }
            val ir = kr * 0.58f
            drawCircle(Brush.radialGradient(listOf(Color(0xFF4A4E53), Color(0xFF1C1E20)), kc - Offset(ir * 0.3f, ir * 0.4f), ir * 1.6f), ir, kc)
            drawCircle(if (turning) accent else Color.Black, ir, kc, style = Stroke(1.5.dp.toPx()))
            rotate(angle, kc) { drawLine(Color.White, Offset(kc.x, kc.y - ir * 0.85f), Offset(kc.x, kc.y - ir * 0.45f), 2.5.dp.toPx(), StrokeCap.Round) }
        }
        "multi-pos" -> {
            // A numbered motorsport setup rotary: 12 stops, the current number in the window under the knob.
            val pos = Math.floorMod(5 + steps, 12) + 1
            val kr = r * 0.62f
            val ns = t.readoutStyle((r / density / 4.6f).coerceIn(8f, 13f).sp)
            for (i in 1..12) {
                val a = Math.toRadians(-150.0 + (i - 1) * (300.0 / 11))
                val d = Offset(sin(a).toFloat(), -cos(a).toFloat())
                val sel = i == pos && readout == null
                val p = cc + d * (r * 0.86f)
                drawLegend(tm, i.toString(), ns, if (sel) accent else t.legendMuted, p.x, p.y - ns.fontSize.toPx() * 0.62f, r * 0.4f)
            }
            drawCircle(Color.Black.copy(alpha = 0.55f), kr, cc + Offset(0f, 4.dp.toPx()))
            drawCircle(Brush.sweepGradient(listOf(Color(0xFF2E3034), Color(0xFF0E0F10), Color(0xFF2E3034), Color(0xFF0E0F10), Color(0xFF2E3034)), cc), kr, cc)
            val shownPos = readout?.toIntOrNull()?.coerceIn(1, 12) ?: pos
            rotate((-150f + (shownPos - 1) * (300f / 11)), cc) {
                drawLine(accent, Offset(cc.x, cc.y - kr * 0.95f), Offset(cc.x, cc.y - kr * 0.4f), 3.dp.toPx(), StrokeCap.Round)
            }
            drawCircle(t.decor ?: accent, kr * 0.95f, cc, style = Stroke(2.dp.toPx()))
            drawLegend(tm, readout ?: pos.toString(), t.readoutStyle((kr / density / 1.8f).coerceIn(12f, 26f).sp), Color.White, cc.x, cc.y - kr * 0.28f, kr * 1.4f)
        }
        "chrome-dial" -> {
            val kr = r * 0.72f
            drawCircle(Color.Black.copy(alpha = 0.6f), kr * 1.08f, cc + Offset(0f, 4.dp.toPx()))
            drawCircle(Brush.sweepGradient(listOf(Color(0xFFDDE0E3), Color(0xFF7D8186), Color(0xFFF2F3F4), Color(0xFF7D8186), Color(0xFFDDE0E3)), cc), kr * 1.08f, cc)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF2A2C30), Color(0xFF111214)), cc - Offset(kr * 0.3f, kr * 0.4f), kr * 1.6f), kr * 0.94f, cc)
            // Light ring and an indicator dot that turns with the dial.
            drawCircle(if (turning) accent else accent.copy(alpha = 0.35f), kr * 1.14f, cc, style = Stroke(2.dp.toPx()))
            val a = Math.toRadians(angle.toDouble() - 90)
            drawCircle(accent, 3.dp.toPx(), cc + Offset((cos(a) * kr * 0.75f).toFloat(), (sin(a) * kr * 0.75f).toFloat()))
            if (readout != null) drawLegend(tm, readout, t.readoutStyle(16.sp), t.legend, cc.x, cc.y - 10.dp.toPx(), kr * 1.4f)
            else drawIcon(icon, cc, kr * 0.7f, t.legend)
        }
        else -> { // themed encoder
            val kr = r * 0.78f
            for (i in 0 until 24) {
                val a = Math.toRadians(i * 15.0 - 90)
                val major = i % 6 == 0
                val d = Offset(cos(a).toFloat(), sin(a).toFloat())
                drawLine(t.legendMuted.copy(alpha = if (major) 1f else 0.5f), cc + d * (r * (if (major) 0.84f else 0.88f)), cc + d * (r * 0.97f), (if (major) 2f else 1f).dp.toPx())
            }
            drawCircle(Color.Black.copy(alpha = 0.55f), kr, cc + Offset(0f, 4.dp.toPx()))
            drawCircle(Brush.radialGradient(listOf(t.capHigh, t.cap), cc - Offset(kr * 0.3f, kr * 0.4f), kr * 1.6f), kr, cc)
            for (i in 0 until 36) {
                val a = Math.toRadians(i * 10.0 + angle)
                val d = Offset(cos(a).toFloat(), sin(a).toFloat())
                drawLine(Color.Black.copy(alpha = 0.3f), cc + d * (kr * 0.86f), cc + d * kr, 1.dp.toPx())
            }
            drawCircle(if (turning) accent else t.capEdge, kr, cc, style = Stroke(1.5.dp.toPx()))
            rotate(angle, cc) { drawLine(if (enabled) accent else t.legendMuted, Offset(cc.x, cc.y - kr * 0.85f), Offset(cc.x, cc.y - kr * 0.35f), 4.dp.toPx(), StrokeCap.Round) }
            if (readout != null) drawLegend(tm, readout, t.readoutStyle(16.sp), t.legend, cc.x, cc.y + kr * 0.1f, kr * 1.4f)
        }
    }
}

/**
 * Themed joysticks: a machine pilot grip in a square gate, an airliner sidestick, a yoke, or the themed round gate.
 * [x] and [y] are -1..1 (forward +1). [Control.ends] label the four directions: left, right, back, forward.
 */
fun DrawScope.drawStickSkin(skin: String, t: SimTheme, c: Control, x: Float, y: Float, held: Boolean, enabled: Boolean, tm: TextMeasurer) {
    val accent = t.lamp(c.accent)
    val cc = Offset(size.width / 2, size.height / 2)
    val r = min(size.width, size.height) / 2
    val gate = r * 0.94f
    val throwPx = r * 0.68f
    val knob = cc + Offset(x, -y) * throwPx
    when (skin) {
        "yoke" -> {
            // Column travel (forward-back) as a vertical bar, roll as the yoke's rotation.
            drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(cc.x - 5.dp.toPx(), cc.y - gate * 0.8f), Size(10.dp.toPx(), gate * 1.6f), CornerRadius(5.dp.toPx()))
            val yc = Offset(cc.x, cc.y - y * gate * 0.45f)
            rotate(x * 35f, yc) {
                val w = gate * 1.5f; val h = gate * 0.52f
                val p = Path().apply {
                    moveTo(yc.x - w / 2, yc.y - h / 2)
                    lineTo(yc.x - w / 2 + h * 0.4f, yc.y - h / 2)
                    lineTo(yc.x - w * 0.18f, yc.y + h * 0.15f)
                    lineTo(yc.x + w * 0.18f, yc.y + h * 0.15f)
                    lineTo(yc.x + w / 2 - h * 0.4f, yc.y - h / 2)
                    lineTo(yc.x + w / 2, yc.y - h / 2)
                    lineTo(yc.x + w / 2, yc.y + h / 2)
                    lineTo(yc.x - w / 2, yc.y + h / 2)
                    close()
                }
                drawPath(p, Color.Black.copy(alpha = 0.5f))
                drawPath(p, Brush.verticalGradient(listOf(Color(0xFF3B3E42), Color(0xFF151618)), yc.y - h / 2, yc.y + h / 2))
                drawPath(p, if (held) accent else Color(0xFF5A5E64), style = Stroke(1.5.dp.toPx()))
                drawCircle(t.decor ?: Color(0xFF8E9399), h * 0.18f, yc + Offset(0f, h * 0.15f))
            }
        }
        "pilot-grip", "sidestick" -> {
            val square = skin == "pilot-grip"
            if (square) {
                val g = Rect(cc.x - gate, cc.y - gate, cc.x + gate, cc.y + gate)
                drawRoundRect(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.6f), t.panel), cc, gate), g.topLeft, g.size, CornerRadius(gate * 0.22f))
                drawRoundRect(t.panelLine, g.topLeft, g.size, CornerRadius(gate * 0.22f), style = Stroke(1.5.dp.toPx()))
                // Rubber boot pleats around the shaft.
                for (k in 1..3) drawCircle(Color.Black.copy(alpha = 0.35f), throwPx * 0.25f * k, cc, style = Stroke(2.dp.toPx()))
            } else {
                drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.6f), t.panel), cc, gate), gate, cc)
                drawCircle(t.panelLine, gate, cc, style = Stroke(1.5.dp.toPx()))
            }
            drawLine(t.panelLine, Offset(cc.x - throwPx, cc.y), Offset(cc.x + throwPx, cc.y), 1.dp.toPx())
            drawLine(t.panelLine, Offset(cc.x, cc.y - throwPx), Offset(cc.x, cc.y + throwPx), 1.dp.toPx())
            if (enabled && (kotlin.math.abs(x) > 0.08f || kotlin.math.abs(y) > 0.08f)) drawLine(accent, cc, knob, 4.dp.toPx(), StrokeCap.Round)
            // Grip seen from above.
            val gw = r * 0.34f; val gl = r * 0.46f
            drawOval(Color.Black.copy(alpha = 0.5f), knob - Offset(gw / 2, gl / 2) + Offset(0f, 5.dp.toPx()), Size(gw, gl))
            drawOval(Brush.verticalGradient(listOf(Color(0xFF3E4247), Color(0xFF16181A)), knob.y - gl / 2, knob.y + gl / 2), knob - Offset(gw / 2, gl / 2), Size(gw, gl))
            drawOval(if (held) accent else Color.Black, knob - Offset(gw / 2, gl / 2), Size(gw, gl), style = Stroke(2.dp.toPx()))
            if (square) {
                // Thumb rollers and buttons on the grip top.
                drawRoundRect(Color(0xFF6B7076), knob - Offset(gw * 0.32f, gl * 0.38f), Size(gw * 0.18f, gl * 0.3f), CornerRadius(3.dp.toPx()))
                drawRoundRect(Color(0xFF6B7076), knob + Offset(gw * 0.14f, -gl * 0.38f), Size(gw * 0.18f, gl * 0.3f), CornerRadius(3.dp.toPx()))
                drawCircle(t.decor ?: accent, gw * 0.09f, knob + Offset(0f, gl * 0.12f))
                drawCircle(Color(0xFF2C2F33), gw * 0.07f, knob + Offset(-gw * 0.2f, gl * 0.26f))
                drawCircle(Color(0xFF2C2F33), gw * 0.07f, knob + Offset(gw * 0.2f, gl * 0.26f))
            } else {
                drawCircle(Color(0xFFC0392B), gw * 0.12f, knob - Offset(gw * 0.12f, gl * 0.28f)) // takeover button
                drawRoundRect(Color(0xFF55595E), knob + Offset(-gw * 0.05f, -gl * 0.44f), Size(gw * 0.3f, gl * 0.1f), CornerRadius(2.dp.toPx()))
            }
        }
        else -> { // themed round gate
            drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), t.panel), cc, gate), gate, cc)
            drawCircle(t.panelLine, gate, cc, style = Stroke(1.5.dp.toPx()))
            drawCircle(t.panelLine, throwPx * 0.1f + r * 0.1f, cc, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
            if (enabled && (kotlin.math.abs(x) > 0.08f || kotlin.math.abs(y) > 0.08f)) drawLine(accent, cc, knob, 4.dp.toPx(), StrokeCap.Round)
            val kr = r * 0.26f
            drawCircle(Color.Black.copy(alpha = 0.5f), kr, knob + Offset(0f, 4.dp.toPx()))
            drawCircle(Brush.verticalGradient(listOf(t.capHigh, t.cap), knob.y - kr, knob.y + kr), kr, knob)
            drawCircle(if (held) accent else t.capEdge, kr, knob, style = Stroke(2.dp.toPx()))
            drawCircle(if (enabled) accent else t.legendMuted, kr * 0.28f, knob)
        }
    }
    drawStickEnds(t, c, x, y, tm)
}

/** Direction labels on a stick's gate: [Control.ends] = left, right, back, forward. The one being pushed lights. */
fun DrawScope.drawStickEnds(t: SimTheme, c: Control, x: Float, y: Float, tm: TextMeasurer) {
    val accent = t.lamp(c.accent)
    val cc = Offset(size.width / 2, size.height / 2)
    val r = min(size.width, size.height) / 2
    val gate = r * 0.94f
    if (c.ends.isNotEmpty()) {
        val ls = t.legendStyle((r / density / 7f).coerceIn(8f, 11f).sp)
        fun lab(i: Int, p: Offset, on: Boolean) = c.ends.getOrNull(i)?.takeIf { it.isNotBlank() }?.let {
            drawLegend(tm, it, ls, if (on) accent else t.legend, p.x, p.y - ls.fontSize.toPx() * 0.62f, r * 0.62f, 2)
        }
        lab(0, Offset(cc.x - gate * 0.66f, cc.y + gate * 0.12f), x < -0.3f)
        lab(1, Offset(cc.x + gate * 0.66f, cc.y + gate * 0.12f), x > 0.3f)
        lab(2, Offset(cc.x, cc.y + gate * 0.78f), y < -0.3f)
        lab(3, Offset(cc.x, cc.y - gate * 0.86f), y > 0.3f)
    }
}
