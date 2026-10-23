package com.simdeck.ui.sim

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simdeck.model.Control
import com.simdeck.model.FeedbackSource
import com.simdeck.ui.icons.IconLibrary
import kotlin.math.min

/** Everything a face needs to draw one moment of a control. */
class FaceState(
    val lit: Boolean,
    val pressed: Boolean,
    val enabled: Boolean,
    val pending: Boolean,
    /** 0..1 while pending, for the blinking lamp. */
    val blink: Float,
    val readout: String?,
)

/** Shared text drawing for faces: fits [text] in [maxW] on up to [lines] lines, centred on [cx] at [top]. */
fun DrawScope.drawLegend(tm: TextMeasurer, text: String, style: TextStyle, color: Color, cx: Float, top: Float, maxW: Float, lines: Int = 1): Float {
    if (text.isBlank() || maxW < 8f) return 0f
    val layout = tm.measure(text, style.copy(color = color, textAlign = TextAlign.Center), overflow = TextOverflow.Ellipsis, maxLines = lines,
        constraints = Constraints(minWidth = maxW.toInt(), maxWidth = maxW.toInt()))
    drawText(layout, topLeft = Offset(cx - maxW / 2, top))
    return layout.size.height.toFloat()
}

fun DrawScope.drawIcon(painter: VectorPainter?, c: Offset, side: Float, tint: Color) {
    painter ?: return
    translate(c.x - side / 2, c.y - side / 2) {
        with(painter) { draw(Size(side, side), colorFilter = ColorFilter.tint(tint)) }
    }
}

/** Text size for a face, following the cell. */
private fun faceText(minSide: Float, density: Float): TextUnit = ((minSide / density) / 7.2f).coerceIn(10f, 15f).sp

/**
 * A themed button, toggle or key face (docs/THEMES.md, skin catalogue). Input is handled by the caller; this only
 * draws. [skin] is one of the momentary / latching / radio / page skins except the house "cap".
 */
@Composable
fun SkinFace(skin: String, control: Control, s: FaceState, modifier: Modifier, label: String = control.label, icon: String? = control.icon) {
    val t = LocalSimTheme.current
    val tm = rememberTextMeasurer()
    val painter = IconLibrary[icon]?.let { rememberVectorPainter(it) }
    val travel by animateFloatAsState(if (s.pressed) t.pressScale else 1f, label = "travel")
    Canvas(
        modifier
            .alpha(if (s.enabled) 1f else 0.42f)
            .graphicsLayer { scaleX = travel; scaleY = travel },
    ) {
        val lamp = t.lamp(control.accent)
        // Pending: the lamp blinks between half and full, so "sent, waiting for the game" reads even in a still frame.
        val lampLevel = if (s.pending) 0.45f + 0.55f * s.blink else 1f
        val ctx = FaceCtx(t, tm, painter, lamp, lampLevel, s, control, label, density)
        when (skin) {
            "lit-push" -> ctx.litPush(this, split = false)
            "lit-push-split" -> ctx.litPush(this, split = true)
            "lit-row" -> ctx.litPush(this, split = false)
            "ring-push" -> ctx.ringPush(this)
            "mushroom" -> ctx.mushroom(this)
            "rubber-key", "annunciator-row" -> ctx.rubberKey(this)
            "rocker-mom", "rocker-lamp" -> ctx.rocker(this, lamps = skin == "rocker-lamp")
            "glass-tile", "glass-toggle" -> ctx.glass(this, bar = skin == "glass-toggle" || s.lit)
            "toggle-bat" -> ctx.toggleBat(this)
            "push-pull" -> ctx.pushPull(this)
            "key" -> ctx.key(this)
            "gear-lever" -> ctx.gearLever(this)
            "page-tab" -> ctx.pageTab(this)
            else -> ctx.rubberKey(this)
        }
        if (!s.enabled) {
            drawRoundRect(t.legendMuted, Offset(1f, 1f), Size(size.width - 2f, size.height - 2f), CornerRadius(t.corner.toPx()),
                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))))
        }
    }
}

/** Blink phase for pending lamps. */
@Composable
fun rememberBlink(active: Boolean): Float {
    if (!active) return 0f
    val tr = rememberInfiniteTransition(label = "blink")
    return tr.animateFloat(0f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "b").value
}

private class FaceCtx(
    val t: SimTheme,
    val tm: TextMeasurer,
    val icon: VectorPainter?,
    val lamp: Color,
    val level: Float,
    val s: FaceState,
    val c: Control,
    val label: String,
    val density: Float,
) {
    val lit get() = s.lit
    fun lampColor(on: Boolean) = if (on) lamp.copy(alpha = level) else t.lampOff

    /** Engraved label strip at the top; returns its height. */
    fun DrawScope.topLabel(maxW: Float, text: String = label): Float {
        if (text.isBlank() || size.height < 60.dp.toPx()) return 0f
        val st = t.legendStyle(faceText(min(size.width, size.height), density) * 0.85f)
        return drawLegend(tm, text.uppercase(), st, if (lit) t.legend else t.legendMuted, size.width / 2, 2.dp.toPx(), maxW) + 4.dp.toPx()
    }

    fun DrawScope.bottomLabel(top: Float, maxW: Float, text: String = label) {
        if (text.isBlank()) return
        val st = t.legendStyle(faceText(min(size.width, size.height), density) * 0.85f)
        drawLegend(tm, text.uppercase(), st, if (lit) t.legend else t.legendMuted, size.width / 2, top, maxW, 2)
    }

    // ---- lit pushbutton (flight deck) ----
    fun litPush(d: DrawScope, split: Boolean) = with(d) {
        val labelH = topLabel(size.width - 4.dp.toPx())
        val avail = Rect(0f, labelH, size.width, size.height)
        val side = min(avail.width * 0.92f, avail.height * 0.96f)
        val bw = min(avail.width * 0.92f, side * 1.25f)
        val body = Rect(avail.center.x - bw / 2, avail.center.y - side / 2, avail.center.x + bw / 2, avail.center.y + side / 2)
        val r = CornerRadius(t.corner.toPx() + 2.dp.toPx())
        drawRoundRect(Color.Black.copy(alpha = 0.55f), body.topLeft + Offset(0f, 2.5.dp.toPx()), body.size, r)
        drawRoundRect(Brush.verticalGradient(listOf(t.capHigh, t.cap), body.top, body.bottom), body.topLeft, body.size, r)
        drawRoundRect(t.capEdge, body.topLeft, body.size, r, style = Stroke(1.dp.toPx()))
        val inset = side * 0.11f
        val lens = Rect(body.left + inset, body.top + inset, body.right - inset, body.bottom - inset)
        drawRoundRect(if (s.pressed) Color.Black else t.lampOff, lens.topLeft, lens.size, CornerRadius(2.dp.toPx()))
        val ts = t.legendStyle((lens.height / density / (if (split) 4.2f else 3.4f)).coerceIn(9f, 16f).sp)
        if (split) {
            val mid = lens.center.y
            drawLine(Color.Black, Offset(lens.left, mid), Offset(lens.right, mid), 1.5.dp.toPx())
            val upper = c.legends.getOrNull(0).orEmpty()
            val lower = c.legends.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "ON"
            // Upper half: the fault or status word, engraved (no fault data reaches the phone).
            drawLegend(tm, upper, ts, Color(0xFFFFB000).copy(alpha = 0.22f), lens.center.x, lens.top + lens.height * 0.08f, lens.width * 0.9f)
            if (lit) drawRect(lamp.copy(alpha = 0.10f * level), Offset(lens.left, mid), Size(lens.width, lens.height / 2))
            drawLegend(tm, lower, ts, if (lit) lamp.copy(alpha = level) else t.legendMuted.copy(alpha = 0.28f), lens.center.x, mid + lens.height * 0.08f, lens.width * 0.9f)
            if (lit) drawLine(lamp.copy(alpha = level), Offset(lens.left + lens.width * 0.2f, lens.bottom - 4.dp.toPx()), Offset(lens.right - lens.width * 0.2f, lens.bottom - 4.dp.toPx()), 2.dp.toPx())
        } else {
            if (lit) drawRoundRect(lamp.copy(alpha = 0.16f * level), lens.topLeft, lens.size, CornerRadius(2.dp.toPx()))
            val legend = c.legends.lastOrNull()?.takeIf { it.isNotBlank() } ?: s.readout
            val ink = if (lit) lamp.copy(alpha = level) else t.legendMuted.copy(alpha = if (t.night) 0.25f else 0.45f)
            if (legend != null) drawLegend(tm, legend, ts, ink, lens.center.x, lens.center.y - ts.fontSize.toPx() * 0.6f, lens.width * 0.92f)
            else if (icon != null) drawIcon(icon, lens.center, min(lens.width, lens.height) * 0.55f, ink)
            else drawLegend(tm, label, ts, ink, lens.center.x, lens.center.y - ts.fontSize.toPx() * 0.6f, lens.width * 0.92f, 2)
        }
        // Glass highlight.
        drawRect(Color.White.copy(alpha = 0.05f), lens.topLeft, Size(lens.width, lens.height * 0.35f))
    }

    // ---- round pushbutton with a lit ring ----
    fun ringPush(d: DrawScope) = with(d) {
        val labelRoom = if (size.height > 70.dp.toPx() && label.isNotBlank()) 22.dp.toPx() else 0f
        val dia = min(size.width, size.height - labelRoom) * 0.86f
        val cc = Offset(size.width / 2, (size.height - labelRoom) / 2)
        val r = dia / 2
        drawCircle(Color.Black.copy(alpha = 0.5f), r, cc + Offset(0f, 3.dp.toPx()))
        // Bezel: brushed metal ring.
        drawCircle(Brush.sweepGradient(listOf(Color(0xFF9DA1A6), Color(0xFF4B4F55), Color(0xFFC9CDD2), Color(0xFF5A5E64), Color(0xFF9DA1A6)), cc), r, cc)
        // Light ring.
        val ringR = r * 0.80f
        drawCircle(Color.Black, ringR, cc)
        drawCircle(lampColor(lit), ringR - r * 0.05f, cc, style = Stroke(r * 0.09f))
        if (lit) drawCircle(lamp.copy(alpha = 0.18f * level), ringR + r * 0.06f, cc, style = Stroke(r * 0.12f))
        // Cap.
        val capR = ringR - r * 0.16f
        drawCircle(Brush.radialGradient(listOf(t.capHigh, if (s.pressed) t.capPressed else t.cap), cc - Offset(capR * 0.3f, capR * 0.4f), capR * 1.6f), capR, cc)
        drawCircle(Color.White.copy(alpha = 0.06f), capR, cc, style = Stroke(1.dp.toPx()))
        val legend = c.legends.lastOrNull()?.takeIf { it.isNotBlank() } ?: s.readout
        val ink = if (lit) lamp.copy(alpha = level) else t.legend
        if (legend != null) {
            val ts = t.legendStyle((capR / density / 2.4f).coerceIn(9f, 16f).sp)
            drawLegend(tm, legend, ts, ink, cc.x, cc.y - ts.fontSize.toPx() * 0.6f, capR * 1.7f)
        } else drawIcon(icon, cc, capR * 1.05f, ink)
        if (labelRoom > 0f) bottomLabel(size.height - labelRoom + 2.dp.toPx(), size.width - 4.dp.toPx())
    }

    // ---- emergency mushroom ----
    fun mushroom(d: DrawScope) = with(d) {
        val labelRoom = if (size.height > 70.dp.toPx()) 22.dp.toPx() else 0f
        val r = min(size.width, size.height - labelRoom) * 0.44f
        val cc = Offset(size.width / 2, (size.height - labelRoom) / 2)
        drawCircle(t.decor ?: Color(0xFFF2B400), r, cc)
        drawCircle(Color.Black.copy(alpha = 0.6f), r * 0.78f, cc + Offset(0f, 3.dp.toPx()))
        val dome = if (s.pressed) r * 0.7f else r * 0.74f
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF6A5E), Color(0xFFC21D12), Color(0xFF7A0E08)), cc - Offset(dome * 0.35f, dome * 0.45f), dome * 1.7f), dome, cc)
        if (lit) drawCircle(Color.White.copy(alpha = 0.25f * level), dome, cc, style = Stroke(3.dp.toPx()))
        if (labelRoom > 0f) bottomLabel(size.height - labelRoom + 2.dp.toPx(), size.width - 4.dp.toPx())
    }

    // ---- rubber key with a lamp window (machines) ----
    fun rubberKey(d: DrawScope) = with(d) {
        val pad = 3.dp.toPx()
        val body = Rect(pad, pad, size.width - pad, size.height - pad)
        val rr = CornerRadius(min(t.corner.toPx(), body.minDimension / 3))
        drawRoundRect(Color.Black.copy(alpha = 0.6f), body.topLeft + Offset(0f, if (s.pressed) 1.dp.toPx() else 4.dp.toPx()), body.size, rr)
        drawRoundRect(Brush.verticalGradient(listOf(if (s.pressed) t.cap else t.capHigh, if (s.pressed) t.capPressed else t.cap), body.top, body.bottom),
            body.topLeft, body.size, rr)
        drawFinish(Finish.GRAIN, body.topLeft, body.size, alpha = 0.5f)
        drawRoundRect(t.capEdge, body.topLeft, body.size, rr, style = Stroke(1.dp.toPx()))
        // Lamp window, top right.
        val lw = body.width * 0.26f; val lh = (body.height * 0.09f).coerceAtLeast(5.dp.toPx())
        val lr = Rect(body.right - lw - body.width * 0.1f, body.top + body.height * 0.1f, body.right - body.width * 0.1f, body.top + body.height * 0.1f + lh)
        drawRoundRect(Color.Black, lr.topLeft - Offset(1.dp.toPx(), 1.dp.toPx()), Size(lr.width + 2.dp.toPx(), lr.height + 2.dp.toPx()), CornerRadius(lh / 2))
        drawRoundRect(lampColor(lit), lr.topLeft, lr.size, CornerRadius(lh / 2))
        if (lit) drawRoundRect(lamp.copy(alpha = 0.25f * level), lr.topLeft - Offset(3.dp.toPx(), 3.dp.toPx()), Size(lr.width + 6.dp.toPx(), lr.height + 6.dp.toPx()), CornerRadius(lh))
        val showLabel = body.height > 64.dp.toPx() && label.isNotBlank()
        val iconSide = min(body.width, body.height) * (if (showLabel) 0.36f else 0.5f)
        val iconC = Offset(body.center.x, body.top + body.height * (if (showLabel) 0.45f else 0.55f))
        val legend = s.readout
        if (legend != null) {
            val ts = t.readoutStyle((iconSide / density / 1.4f).coerceIn(12f, 28f).sp)
            drawLegend(tm, legend, ts, if (lit) lamp else t.legend, iconC.x, iconC.y - ts.fontSize.toPx() * 0.65f, body.width * 0.9f)
        } else drawIcon(icon, iconC, iconSide, t.legend)
        if (showLabel) bottomLabel(body.bottom - body.height * 0.3f, body.width - 8.dp.toPx())
    }

    // ---- automotive rocker (with or without lamp windows) ----
    fun rocker(d: DrawScope, lamps: Boolean) = with(d) {
        val labelRoom = if (size.height > 72.dp.toPx() && label.isNotBlank()) 20.dp.toPx() else 0f
        val h = (size.height - labelRoom) * 0.94f
        val w = min(size.width * 0.66f, h * 0.56f)
        val body = Rect(size.width / 2 - w / 2, (size.height - labelRoom - h) / 2, size.width / 2 + w / 2, (size.height - labelRoom + h) / 2)
        val rr = CornerRadius(w * 0.16f)
        // Bezel.
        drawRoundRect(Color.Black, body.topLeft - Offset(3.dp.toPx(), 3.dp.toPx()), Size(body.width + 6.dp.toPx(), body.height + 6.dp.toPx()), CornerRadius(w * 0.2f))
        val on = lit || s.pressed
        // The pressed half sits lower: darker gradient on that half.
        val top = Rect(body.left, body.top, body.right, body.center.y)
        val bottom = Rect(body.left, body.center.y, body.right, body.bottom)
        drawRoundRect(Brush.verticalGradient(listOf(if (on) t.capPressed else t.capHigh, if (on) t.cap else t.cap), top.top, top.bottom), top.topLeft, Size(top.width, top.height + rr.y), rr)
        drawRoundRect(Brush.verticalGradient(listOf(if (on) t.capHigh else t.cap, if (on) t.cap else t.capPressed), bottom.top, bottom.bottom), Offset(bottom.left, bottom.top - rr.y), Size(bottom.width, bottom.height + rr.y), rr)
        drawLine(Color.Black.copy(alpha = 0.7f), Offset(body.left, body.center.y), Offset(body.right, body.center.y), 1.5.dp.toPx())
        drawRoundRect(t.capEdge, body.topLeft, body.size, rr, style = Stroke(1.dp.toPx()))
        // Icon in the upper half, laser-etched and backlit.
        val etched = if (t.night) t.legend else t.legend.copy(alpha = 0.92f)
        drawIcon(icon, Offset(top.center.x, top.center.y + top.height * 0.05f), min(top.width, top.height) * 0.62f, etched)
        if (lamps) {
            // Lower lamp window lights when the function is on.
            val lw = body.width * 0.5f; val lh = (body.height * 0.07f).coerceAtLeast(5.dp.toPx())
            val lr = Rect(bottom.center.x - lw / 2, bottom.center.y - lh / 2, bottom.center.x + lw / 2, bottom.center.y + lh / 2)
            drawRoundRect(lampColor(lit), lr.topLeft, lr.size, CornerRadius(lh / 2))
            if (lit) drawRoundRect(lamp.copy(alpha = 0.25f * level), lr.topLeft - Offset(3.dp.toPx(), 3.dp.toPx()), Size(lr.width + 6.dp.toPx(), lr.height + 6.dp.toPx()), CornerRadius(lh))
        } else {
            drawPath(chevron(Offset(bottom.center.x, bottom.center.y), bottom.width * 0.16f, down = true), t.legendMuted, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
        }
        if (labelRoom > 0f) bottomLabel(size.height - labelRoom + 2.dp.toPx(), size.width - 4.dp.toPx())
    }

    // ---- glass tile (road car) ----
    fun glass(d: DrawScope, bar: Boolean) = with(d) {
        val pad = 3.dp.toPx()
        val body = Rect(pad, pad, size.width - pad, size.height - pad)
        val rr = CornerRadius(t.corner.toPx())
        drawRoundRect(Brush.verticalGradient(listOf(t.capHigh, t.cap), body.top, body.bottom), body.topLeft, body.size, rr)
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), body.top, body.top + body.height * 0.5f), body.topLeft, body.size, rr)
        drawRoundRect(t.capEdge.copy(alpha = 0.35f), body.topLeft, body.size, rr, style = Stroke(1.dp.toPx()))
        if (s.pressed) drawRoundRect(Color.White.copy(alpha = 0.05f), body.topLeft, body.size, rr)
        val showLabel = body.height > 60.dp.toPx()
        val ink = if (lit) lamp.copy(alpha = level) else t.legend
        drawIcon(icon, Offset(body.center.x, body.top + body.height * (if (showLabel) 0.38f else 0.5f)), min(body.width, body.height) * 0.34f, ink)
        if (showLabel) {
            val st = t.legendStyle(faceText(min(size.width, size.height), density) * 0.82f)
            drawLegend(tm, label, st, t.legendMuted, body.center.x, body.top + body.height * 0.62f, body.width - 10.dp.toPx(), 2)
        }
        if (bar) {
            val bw = body.width * 0.36f
            drawRoundRect(lampColor(lit), Offset(body.center.x - bw / 2, body.bottom - 7.dp.toPx()), Size(bw, 3.dp.toPx()), CornerRadius(1.5.dp.toPx()))
        }
    }

    // ---- bat toggle switch ----
    fun toggleBat(d: DrawScope) = with(d) {
        val labelH = topLabel(size.width - 4.dp.toPx())
        val area = Rect(0f, labelH, size.width, size.height)
        val cc = area.center
        val nutR = min(area.width, area.height) * 0.16f
        val on = lit
        val ts = t.legendStyle((min(area.width, area.height) / density / 8f).coerceIn(8f, 12f).sp)
        val up = c.legends.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "ON"
        val down = c.legends.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "OFF"
        drawLegend(tm, up, ts, if (on) t.legend else t.legendMuted, cc.x + area.width * 0.28f, area.top + 2.dp.toPx(), area.width * 0.4f)
        drawLegend(tm, down, ts, if (!on) t.legend else t.legendMuted, cc.x + area.width * 0.28f, area.bottom - ts.fontSize.toPx() * 1.4f, area.width * 0.4f)
        // Hex nut and washer.
        drawCircle(Color(0xFF3B3E42), nutR * 1.45f, cc)
        drawPath(polygon(cc, nutR * 1.25f, 6, 0f), Brush.linearGradient(listOf(Color(0xFFD7DADD), Color(0xFF6E7378)), cc - Offset(nutR, nutR), cc + Offset(nutR, nutR)))
        drawCircle(Color(0xFF2A2C2F), nutR * 0.7f, cc)
        // Bat: tapered, pointing up when on.
        val len = (area.height / 2 - nutR * 0.2f) * 0.86f
        val dir = if (on) -1f else 1f
        val tip = cc + Offset(0f, dir * len)
        val baseW = nutR * 0.55f; val tipW = nutR * 0.9f
        val bat = Path().apply {
            moveTo(cc.x - baseW / 2, cc.y); lineTo(cc.x + baseW / 2, cc.y)
            lineTo(tip.x + tipW / 2, tip.y); lineTo(tip.x - tipW / 2, tip.y); close()
        }
        drawPath(bat, Color.Black.copy(alpha = 0.4f), alpha = 1f)
        translate(0f, 2.dp.toPx()) { drawPath(bat, Color.Black.copy(alpha = 0.35f)) }
        drawPath(bat, Brush.horizontalGradient(listOf(Color(0xFF8A8F95), Color(0xFFEDEFF1), Color(0xFF7C8187)), cc.x - tipW / 2, cc.x + tipW / 2))
        drawOval(Brush.radialGradient(listOf(Color.White, Color(0xFF9DA2A8)), tip - Offset(tipW * 0.2f, tipW * 0.2f), tipW), tip - Offset(tipW / 2, tipW * 0.3f), Size(tipW, tipW * 0.6f))
        // Status lamp beside the switch.
        val lampC = Offset(area.left + area.width * 0.2f, cc.y)
        drawCircle(Color.Black, 5.dp.toPx(), lampC)
        drawCircle(lampColor(on), 3.8.dp.toPx(), lampC)
    }

    // ---- push-pull knob ----
    fun pushPull(d: DrawScope) = with(d) {
        val labelRoom = if (size.height > 70.dp.toPx() && label.isNotBlank()) 22.dp.toPx() else 0f
        val cc = Offset(size.width / 2, (size.height - labelRoom) / 2)
        val r = min(size.width, size.height - labelRoom) * 0.34f
        val out = lit
        if (out) {
            // Pulled: the shaft collar shows in the lamp colour.
            drawCircle(lamp.copy(alpha = level), r * 0.8f, cc + Offset(0f, 4.dp.toPx()))
            drawCircle(Color.Black.copy(alpha = 0.5f), r * 1.02f, cc + Offset(0f, 9.dp.toPx()))
        } else drawCircle(Color.Black.copy(alpha = 0.5f), r * 1.02f, cc + Offset(0f, 2.dp.toPx()))
        val kr = if (out) r * 1.08f else r
        drawCircle(Brush.radialGradient(listOf(t.capHigh, t.cap), cc - Offset(kr * 0.3f, kr * 0.4f), kr * 1.6f), kr, cc)
        for (i in 0 until 24) {
            val a = Math.toRadians(i * 15.0)
            val p = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
            drawLine(Color.Black.copy(alpha = 0.35f), cc + p * (kr * 0.84f), cc + p * kr, 1.dp.toPx())
        }
        drawIcon(icon, cc, kr * 0.9f, t.legend)
        if (labelRoom > 0f) bottomLabel(size.height - labelRoom + 2.dp.toPx(), size.width - 4.dp.toPx())
    }

    // ---- key switch ----
    fun key(d: DrawScope) = with(d) {
        val labelRoom = if (size.height > 70.dp.toPx() && label.isNotBlank()) 22.dp.toPx() else 0f
        val cc = Offset(size.width / 2, (size.height - labelRoom) / 2 + 4.dp.toPx())
        val r = min(size.width, size.height - labelRoom) * 0.32f
        val ts = t.legendStyle((r / density / 3.2f).coerceIn(8f, 11f).sp)
        drawLegend(tm, "OFF", ts, if (!lit) t.legend else t.legendMuted, cc.x - r * 1.05f, cc.y - r * 1.45f, r * 1.4f)
        drawLegend(tm, "ON", ts, if (lit) t.legend else t.legendMuted, cc.x + r * 1.05f, cc.y - r * 1.45f, r * 1.4f)
        drawCircle(Brush.sweepGradient(listOf(Color(0xFFB9BDC2), Color(0xFF5D6166), Color(0xFFDADDE0), Color(0xFF6B6F74), Color(0xFFB9BDC2)), cc), r, cc)
        drawCircle(Color(0xFF26282B), r * 0.72f, cc)
        rotate(if (lit) 40f else -40f, cc) {
            drawRoundRect(Color(0xFF1C1D1F), Offset(cc.x - r * 0.42f, cc.y - r * 1.05f), Size(r * 0.84f, r * 0.9f), CornerRadius(r * 0.3f))
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFE2E4E6), Color(0xFF8F9398))), Offset(cc.x - r * 0.12f, cc.y - r * 0.5f), Size(r * 0.24f, r * 1.0f), CornerRadius(r * 0.08f))
        }
        val lampC = Offset(cc.x + r * 1.15f, cc.y + r * 0.6f)
        drawCircle(Color.Black, 5.dp.toPx(), lampC)
        drawCircle(lampColor(lit), 3.6.dp.toPx(), lampC)
        if (labelRoom > 0f) bottomLabel(size.height - labelRoom + 2.dp.toPx(), size.width - 4.dp.toPx())
    }

    // ---- landing-gear lever with a wheel knob ----
    fun gearLever(d: DrawScope) = with(d) {
        val labelH = topLabel(size.width - 4.dp.toPx())
        val area = Rect(0f, labelH, size.width, size.height)
        val slotX = area.left + area.width * 0.42f
        val top = area.top + area.height * 0.14f
        val bottom = area.bottom - area.height * 0.14f
        val ts = t.legendStyle((area.width / density / 9f).coerceIn(8f, 12f).sp)
        drawLegend(tm, c.legends.getOrNull(0)?.ifBlank { null } ?: "UP", ts, t.legendMuted, slotX, area.top, area.width * 0.6f)
        drawLegend(tm, c.legends.getOrNull(1)?.ifBlank { null } ?: "DN", ts, t.legendMuted, slotX, area.bottom - ts.fontSize.toPx() * 1.3f, area.width * 0.6f)
        drawRoundRect(Color.Black, Offset(slotX - 5.dp.toPx(), top), Size(10.dp.toPx(), bottom - top), CornerRadius(5.dp.toPx()))
        val down = lit
        val knobY = if (down) bottom - 6.dp.toPx() else top + 6.dp.toPx()
        val pivot = Offset(slotX, (top + bottom) / 2)
        drawLine(Brush.horizontalGradient(listOf(Color(0xFF8A8F95), Color(0xFFE6E8EA), Color(0xFF8A8F95)), slotX - 4.dp.toPx(), slotX + 4.dp.toPx()),
            pivot, Offset(slotX, knobY), 7.dp.toPx(), StrokeCap.Round)
        // Wheel knob: tyre and hub, the shape pilots find by touch.
        val wr = min(area.width * 0.2f, area.height * 0.17f)
        val kc = Offset(slotX, knobY)
        drawCircle(Color.Black.copy(alpha = 0.5f), wr, kc + Offset(0f, 3.dp.toPx()))
        drawCircle(Color(0xFF1B1C1E), wr, kc)
        for (i in 0 until 16) {
            val a = Math.toRadians(i * 22.5)
            val p = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
            drawLine(Color(0xFF3A3C40), kc + p * (wr * 0.8f), kc + p * wr, 2.dp.toPx())
        }
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFB8BCC1)), kc - Offset(wr * 0.15f, wr * 0.15f), wr * 0.6f), wr * 0.5f, kc)
        // Three gear lamps: green down and locked, red while in transit (pending), dark when up.
        val lx = area.left + area.width * 0.8f
        val green = t.lamp(com.simdeck.model.Accent.GREEN)
        val red = t.lamp(com.simdeck.model.Accent.RED)
        for (i in 0 until 3) {
            val p = Offset(lx, area.top + area.height * (0.3f + i * 0.2f))
            drawCircle(Color.Black, 6.dp.toPx(), p)
            val col = when { s.pending -> red.copy(alpha = level); down -> green; else -> t.lampOff }
            drawCircle(col, 4.6.dp.toPx(), p)
        }
    }

    // ---- page key as a panel tab ----
    fun pageTab(d: DrawScope) = with(d) {
        val pad = 4.dp.toPx()
        val body = Rect(pad, size.height * 0.2f, size.width - pad, size.height * 0.8f)
        drawRoundRect(if (s.pressed) t.capPressed else t.cap, body.topLeft, body.size, CornerRadius(t.corner.toPx()))
        drawRoundRect(t.panelLine, body.topLeft, body.size, CornerRadius(t.corner.toPx()), style = Stroke(1.dp.toPx()))
        val st = t.titleStyle(faceText(min(size.width, size.height), density))
        val prev = c.pageTarget == "prev"
        drawLegend(tm, (if (prev) "‹ " else "") + label.uppercase() + (if (prev) "" else " ›"), st, t.legend, body.center.x, body.center.y - st.fontSize.toPx() * 0.65f, body.width - 8.dp.toPx())
    }
}

fun chevron(c: Offset, r: Float, down: Boolean): Path = Path().apply {
    val d = if (down) 1f else -1f
    moveTo(c.x - r, c.y - r * 0.5f * d); lineTo(c.x, c.y + r * 0.5f * d); lineTo(c.x + r, c.y - r * 0.5f * d)
}

/** Face state from a control's visual inputs, with the blink phase for a pending lamp. */
@Composable
fun faceState(lit: Boolean, pressed: Boolean, enabled: Boolean, source: FeedbackSource?, readout: String?): FaceState {
    val pending = lit && source == FeedbackSource.PENDING
    return FaceState(lit, pressed, enabled, pending, rememberBlink(pending), readout)
}
