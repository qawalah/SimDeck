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
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simdeck.model.Control
import kotlin.math.min

/**
 * Themed three-position spring-centred switches: an automotive rocker, a bat toggle, a car window switch. The
 * held half shows by the switch's position and by its arrow lighting; [Control.ends] label the two directions.
 */
fun DrawScope.drawRockerSkin(
    skin: String, t: SimTheme, c: Control, upOn: Boolean, downOn: Boolean, tall: Boolean,
    tm: TextMeasurer, icon: VectorPainter?, readout: String?,
) {
    val lamp = t.lamp(c.accent)
    val ls = t.legendStyle((min(size.width, size.height) / density / 8.5f).coerceIn(8f, 12f).sp)
    val lowLabel = c.ends.getOrNull(0)?.takeIf { it.isNotBlank() }
    val highLabel = c.ends.getOrNull(1)?.takeIf { it.isNotBlank() }
    // Label: across the top when tall, else on the top edge too (wide rockers read left / right).
    val labelH = if (size.height > 64.dp.toPx() && c.label.isNotBlank())
        drawLegend(tm, (readout ?: c.label).uppercase(), ls, if (upOn || downOn) t.legend else t.legendMuted, size.width / 2, 1.dp.toPx(), size.width - 4.dp.toPx()) + 2.dp.toPx() else 0f
    val area = Rect(0f, labelH, size.width, size.height)
    val tilt = when { upOn -> 1f; downOn -> -1f; else -> 0f }
    when (skin) {
        "toggle-3" -> {
            val cc = area.center
            val nutR = min(area.width, area.height) * 0.14f
            drawCircle(Color(0xFF3B3E42), nutR * 1.45f, cc)
            drawPath(polygon(cc, nutR * 1.25f, 6, 0f), Brush.linearGradient(listOf(Color(0xFFD7DADD), Color(0xFF6E7378)), cc - Offset(nutR, nutR), cc + Offset(nutR, nutR)))
            val len = (if (tall) area.height else area.width) / 2 * 0.78f
            val angle = tilt * 38f
            rotate(if (tall) angle * -1f + 0f else 90f + angle, cc) {
                val tip = cc + Offset(0f, -len * (if (tilt == 0f) 0.55f else 1f))
                val w0 = nutR * 0.55f; val w1 = nutR * 0.85f
                val bat = Path().apply { moveTo(cc.x - w0 / 2, cc.y); lineTo(cc.x + w0 / 2, cc.y); lineTo(tip.x + w1 / 2, tip.y); lineTo(tip.x - w1 / 2, tip.y); close() }
                drawPath(bat, Brush.horizontalGradient(listOf(Color(0xFF8A8F95), Color(0xFFEDEFF1), Color(0xFF7C8187)), cc.x - w1 / 2, cc.x + w1 / 2))
                drawOval(Color(0xFFDADDE0), tip - Offset(w1 / 2, w1 * 0.3f), Size(w1, w1 * 0.6f))
            }
            endLabels(t, tm, ls, area, tall, lowLabel, highLabel, upOn, downOn, lamp)
        }
        "window-switch" -> {
            val w = if (tall) min(area.width * 0.6f, area.height * 0.55f) else area.width * 0.7f
            val h = if (tall) area.height * 0.86f else min(area.height * 0.7f, area.width * 0.4f)
            val body = Rect(area.center.x - w / 2, area.center.y - h / 2, area.center.x + w / 2, area.center.y + h / 2)
            drawRoundRect(Color.Black.copy(alpha = 0.6f), body.topLeft + Offset(0f, 3.dp.toPx()), body.size, CornerRadius(w * 0.3f))
            // Shaped paddle: pull lip on the "up" end, a dip in the middle.
            drawRoundRect(Brush.verticalGradient(listOf(t.capHigh, t.cap), body.top, body.bottom), body.topLeft, body.size, CornerRadius(w * 0.3f))
            val lip = if (tall) Rect(body.left, body.top, body.right, body.top + body.height * 0.22f) else Rect(body.right - body.width * 0.22f, body.top, body.right, body.bottom)
            drawRoundRect(if (upOn) lamp.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f), lip.topLeft, lip.size, CornerRadius(w * 0.3f))
            drawRoundRect(t.capEdge.copy(alpha = 0.5f), body.topLeft, body.size, CornerRadius(w * 0.3f), style = Stroke(1.dp.toPx()))
            arrows(t, area, body, tall, upOn, downOn, lamp)
            endLabels(t, tm, ls, area, tall, lowLabel, highLabel, upOn, downOn, lamp)
        }
        else -> { // rocker-3: automotive rocker, the held half pressed in
            val w = if (tall) min(area.width * 0.62f, area.height * 0.52f) else area.width * 0.78f
            val h = if (tall) area.height * 0.9f else min(area.height * 0.78f, area.width * 0.45f)
            val body = Rect(area.center.x - w / 2, area.center.y - h / 2, area.center.x + w / 2, area.center.y + h / 2)
            val rr = CornerRadius(min(w, h) * 0.16f)
            drawRoundRect(Color.Black, body.topLeft - Offset(3.dp.toPx(), 3.dp.toPx()), Size(body.width + 6.dp.toPx(), body.height + 6.dp.toPx()), rr)
            val (a, b) = if (tall) Rect(body.left, body.top, body.right, body.center.y) to Rect(body.left, body.center.y, body.right, body.bottom)
            else Rect(body.center.x, body.top, body.right, body.bottom) to Rect(body.left, body.top, body.center.x, body.bottom)
            fun half(r: Rect, held: Boolean) = drawRoundRect(
                if (tall) Brush.verticalGradient(listOf(if (held) t.capPressed else t.capHigh, if (held) t.cap else t.cap), r.top, r.bottom)
                else Brush.horizontalGradient(listOf(if (held) t.capPressed else t.capHigh, t.cap), r.left, r.right),
                r.topLeft, r.size, rr,
            )
            half(a, upOn); half(b, downOn)
            drawRoundRect(t.capEdge, body.topLeft, body.size, rr, style = Stroke(1.dp.toPx()))
            if (tall) drawLine(Color.Black.copy(alpha = 0.7f), Offset(body.left, body.center.y), Offset(body.right, body.center.y), 1.5.dp.toPx())
            else drawLine(Color.Black.copy(alpha = 0.7f), Offset(body.center.x, body.top), Offset(body.center.x, body.bottom), 1.5.dp.toPx())
            arrows(t, area, body, tall, upOn, downOn, lamp)
            if (icon != null && min(body.width, body.height) > 46.dp.toPx()) drawIcon(icon, body.center, min(body.width, body.height) * 0.24f, t.legendMuted)
            endLabels(t, tm, ls, area, tall, lowLabel, highLabel, upOn, downOn, lamp)
        }
    }
}

private fun DrawScope.arrows(t: SimTheme, area: Rect, body: Rect, tall: Boolean, upOn: Boolean, downOn: Boolean, lamp: Color) {
    val s = min(body.width, body.height) * (if (tall) 0.16f else 0.22f)
    val w = 2.5.dp.toPx()
    if (tall) {
        drawPath(chevron(Offset(body.center.x, body.top + body.height * 0.2f), s, down = false), if (upOn) lamp else t.legend, style = Stroke(w, cap = StrokeCap.Round))
        drawPath(chevron(Offset(body.center.x, body.bottom - body.height * 0.2f), s, down = true), if (downOn) lamp else t.legend, style = Stroke(w, cap = StrokeCap.Round))
    } else {
        val r = Offset(body.right - body.width * 0.18f, body.center.y); val l = Offset(body.left + body.width * 0.18f, body.center.y)
        drawPath(Path().apply { moveTo(r.x - s * 0.5f, r.y - s); lineTo(r.x + s * 0.5f, r.y); lineTo(r.x - s * 0.5f, r.y + s) }, if (upOn) lamp else t.legend, style = Stroke(w, cap = StrokeCap.Round))
        drawPath(Path().apply { moveTo(l.x + s * 0.5f, l.y - s); lineTo(l.x - s * 0.5f, l.y); lineTo(l.x + s * 0.5f, l.y + s) }, if (downOn) lamp else t.legend, style = Stroke(w, cap = StrokeCap.Round))
    }
}

private fun DrawScope.endLabels(
    t: SimTheme, tm: TextMeasurer, ls: androidx.compose.ui.text.TextStyle, area: Rect, tall: Boolean,
    low: String?, high: String?, upOn: Boolean, downOn: Boolean, lamp: Color,
) {
    if (low == null && high == null) return
    if (tall) {
        // Beside the switch, right column.
        val x = area.right - area.width * 0.14f
        high?.let { drawLegend(tm, it, ls, if (upOn) lamp else t.legendMuted, x, area.top + 2.dp.toPx(), area.width * 0.26f, 2) }
        low?.let { drawLegend(tm, it, ls, if (downOn) lamp else t.legendMuted, x, area.bottom - ls.fontSize.toPx() * 2.4f, area.width * 0.26f, 2) }
    } else {
        high?.let { drawLegend(tm, it, ls, if (upOn) lamp else t.legendMuted, area.right - area.width * 0.12f, area.bottom - ls.fontSize.toPx() * 1.4f, area.width * 0.24f) }
        low?.let { drawLegend(tm, it, ls, if (downOn) lamp else t.legendMuted, area.left + area.width * 0.12f, area.bottom - ls.fontSize.toPx() * 1.4f, area.width * 0.24f) }
    }
}
