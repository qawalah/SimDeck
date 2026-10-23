package com.simdeck.ui.sim

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

// ---- textures: generated once, tiled over a base colour --------------------------------------------

private val textureCache = HashMap<Finish, ImageBitmap?>()

/** A small tileable overlay (light and dark specks or weave) for [finish]; null for a plain finish. */
fun texture(finish: Finish): ImageBitmap? = textureCache.getOrPut(finish) {
    val n = 96
    val rnd = Random(finish.ordinal * 7919 + 13)
    fun argb(a: Int, v: Int) = (a.coerceIn(0, 255) shl 24) or (v shl 16) or (v shl 8) or v
    val px = IntArray(n * n)
    when (finish) {
        Finish.FLAT, Finish.GLOSS -> return@getOrPut null
        Finish.GRAIN -> for (i in px.indices) { val r = rnd.nextFloat(); px[i] = if (r < 0.5f) argb((r * 28).toInt(), 0) else argb(((r - 0.5f) * 22).toInt(), 255) }
        Finish.POWDER -> for (i in px.indices) { val r = rnd.nextFloat(); px[i] = if (r < 0.5f) argb((r * 40).toInt(), 0) else argb(((r - 0.5f) * 30).toInt(), 255) }
        Finish.PAINT -> for (i in px.indices) { val r = rnd.nextFloat(); px[i] = argb((r * 10).toInt(), if (r < 0.5f) 0 else 255) }
        Finish.BRUSHED -> for (y in 0 until n) {
            val line = rnd.nextFloat()
            for (x in 0 until n) { val r = (line * 0.8f + rnd.nextFloat() * 0.2f); px[y * n + x] = argb((r * 26).toInt(), if (line > 0.5f) 255 else 0) }
        }
        Finish.CARBON -> {
            // 2x2 twill: 12px tows, each with a soft gradient across it.
            val tow = 12
            for (y in 0 until n) for (x in 0 until n) {
                val cx = x / tow; val cy = y / tow
                val along = if ((cx + cy) % 2 == 0) (x % tow) else (y % tow)
                val t = along / (tow - 1f)
                val shade = 1f - kotlin.math.abs(t - 0.5f) * 2f
                val a = (shade * 34).toInt()
                px[y * n + x] = if ((cx + cy / 2) % 2 == 0) argb(a, 255) else argb((a * 0.6f).toInt(), 255)
            }
        }
    }
    Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
}

fun DrawScope.drawFinish(finish: Finish, topLeft: Offset = Offset.Zero, size: Size = this.size, alpha: Float = 1f) {
    val tex = texture(finish) ?: return
    drawRect(ShaderBrush(ImageShader(tex, TileMode.Repeated, TileMode.Repeated)), topLeft, size, alpha = alpha)
}

/** The theme's environment behind the grid: base gradient, finish, and a soft vignette. */
fun Modifier.simEnvironment(t: SimTheme): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(listOf(t.environment, t.environmentLow)))
    drawFinish(t.envFinish, alpha = 0.9f)
    if (t.envFinish == Finish.GLOSS) {
        // Piano black: one long soft reflection.
        drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0f)),
            Offset(0f, 0f), Offset(size.width, size.height)))
    }
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), center, size.maxDimension * 0.75f))
}

// ---- fasteners -------------------------------------------------------------------------------------

fun DrawScope.drawFastener(kind: Fastener, c: Offset, r: Float, tint: Color) {
    if (kind == Fastener.NONE) return
    drawCircle(Color.Black.copy(alpha = 0.45f), r * 1.05f, c + Offset(0f, r * 0.2f))
    drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 1f), tint.copy(alpha = 0.6f)), c - Offset(r * 0.3f, r * 0.3f), r * 1.6f), r, c)
    val ink = Color.Black.copy(alpha = 0.6f)
    val w = r * 0.28f
    when (kind) {
        Fastener.SLOT -> rotate(35f, c) { drawLine(ink, c - Offset(r * 0.7f, 0f), c + Offset(r * 0.7f, 0f), w) }
        Fastener.DZUS -> rotate(-20f, c) { drawLine(ink, c - Offset(r * 0.75f, 0f), c + Offset(r * 0.75f, 0f), w * 1.2f) }
        Fastener.HEX -> drawPath(polygon(c, r * 0.5f, 6, 0f), ink)
        Fastener.TORX -> drawPath(polygon(c, r * 0.5f, 6, 30f, star = true), ink)
        Fastener.NONE -> Unit
    }
}

fun polygon(c: Offset, r: Float, sides: Int, rotationDeg: Float, star: Boolean = false): Path = Path().apply {
    val pts = if (star) sides * 2 else sides
    for (i in 0 until pts) {
        val a = Math.toRadians(rotationDeg + i * 360.0 / pts)
        val rr = if (star && i % 2 == 1) r * 0.55f else r
        val p = c + Offset((kotlin.math.cos(a) * rr).toFloat(), (kotlin.math.sin(a) * rr).toFloat())
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

// ---- panel groups ----------------------------------------------------------------------------------

/**
 * One titled panel group behind its controls, in the theme's style: a lightplate with a white boundary line (airliner),
 * a bolted plate (light aircraft, overland), a recessed console bay (machines), a switch row (trucks), glass with a chrome
 * hairline (road car), carbon (motorsport), or a plain outline (house). [r] already includes the outset into the gaps.
 */
fun DrawScope.drawPanelGroup(t: SimTheme, r: Rect, title: String, measurer: TextMeasurer) {
    val corner = when (t.panelStyle) { PanelStyle.BAY -> 18.dp.toPx(); PanelStyle.GLASS -> 14.dp.toPx(); else -> 4.dp.toPx() }
    val cr = CornerRadius(corner)
    val titleLayout = if (title.isBlank()) null else measurer.measure(
        title.uppercase(), t.titleStyle(if (t.panelStyle == PanelStyle.PLATE && t.titleFont != t.legendFont) 12.sp else 9.sp).copy(color = t.panelLine),
        overflow = TextOverflow.Ellipsis, maxLines = 1,
        constraints = Constraints(maxWidth = (r.width - 24.dp.toPx()).toInt().coerceAtLeast(1)),
    )
    when (t.panelStyle) {
        PanelStyle.OUTLINE -> {
            drawRoundRect(t.panelLine, r.topLeft, r.size, cr, style = Stroke(1.dp.toPx()))
        }
        PanelStyle.LIGHTPLATE -> {
            drawRoundRect(Color.Black.copy(alpha = 0.4f), r.topLeft + Offset(0f, 2.dp.toPx()), r.size, cr)
            drawRoundRect(t.panel, r.topLeft, r.size, cr)
            drawFinish(t.panelFinish, r.topLeft, r.size, alpha = 0.6f)
            // White boundary line inset from the plate edge, the cockpit convention for a functional group.
            val inset = 3.dp.toPx()
            drawRoundRect(t.panelLine.copy(alpha = 0.85f), r.topLeft + Offset(inset, inset), Size(r.width - inset * 2, r.height - inset * 2),
                CornerRadius(2.dp.toPx()), style = Stroke(1.2.dp.toPx()))
            val sr = 2.6.dp.toPx()
            for (p in corners(r, 6.dp.toPx())) drawFastener(Fastener.DZUS, p, sr, Color(0xFF9EA3A7))
        }
        PanelStyle.PLATE -> {
            drawRoundRect(Color.Black.copy(alpha = 0.5f), r.topLeft + Offset(0f, 3.dp.toPx()), r.size, cr)
            drawRoundRect(t.panel, r.topLeft, r.size, cr)
            drawFinish(t.panelFinish, r.topLeft, r.size)
            drawRoundRect(t.panelLine, r.topLeft, r.size, cr, style = Stroke(1.dp.toPx()))
            for (p in corners(r, 7.dp.toPx())) drawFastener(t.fasteners, p, 3.4.dp.toPx(), Color(0xFF7D807A))
        }
        PanelStyle.BAY -> {
            drawRoundRect(t.panel, r.topLeft, r.size, cr)
            drawFinish(t.panelFinish, r.topLeft, r.size)
            // Recess: dark at the top edge, light lip at the bottom.
            drawRoundRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), r.top, r.top + 18.dp.toPx()), r.topLeft, r.size, cr)
            drawRoundRect(t.panelLine, r.topLeft, r.size, cr, style = Stroke(1.2.dp.toPx()))
            t.decor?.let { d -> drawRoundRect(d, r.topLeft + Offset(corner, -1.dp.toPx()), Size(28.dp.toPx(), 3.dp.toPx()), CornerRadius(1.5.dp.toPx())) }
        }
        PanelStyle.ROW -> {
            drawRoundRect(Color.Black.copy(alpha = 0.35f), r.topLeft, r.size, cr)
            drawFinish(t.panelFinish, r.topLeft, r.size)
            drawRoundRect(t.panelLine, r.topLeft, r.size, cr, style = Stroke(1.dp.toPx()))
        }
        PanelStyle.GLASS -> {
            drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.01f)), r.top, r.bottom), r.topLeft, r.size, cr)
            drawRoundRect(t.panelLine.copy(alpha = 0.55f), r.topLeft, r.size, cr, style = Stroke(1.dp.toPx()))
        }
        PanelStyle.CARBON -> {
            drawRoundRect(t.panel, r.topLeft, r.size, cr)
            drawFinish(Finish.CARBON, r.topLeft, r.size)
            drawRoundRect(t.panelLine, r.topLeft, r.size, cr, style = Stroke(1.dp.toPx()))
        }
    }
    titleLayout ?: return
    val tw = titleLayout.size.width.toFloat(); val th = titleLayout.size.height.toFloat()
    // Titles sit on the top edge, breaking the boundary line, centred on plates and left-aligned in bays and rows.
    val centred = t.panelStyle == PanelStyle.LIGHTPLATE || t.panelStyle == PanelStyle.PLATE
    val x = if (centred) r.center.x - tw / 2 else r.left + (if (t.panelStyle == PanelStyle.BAY) corner + 34.dp.toPx() else 12.dp.toPx())
    val y = r.top - th / 2 + (if (t.panelStyle == PanelStyle.LIGHTPLATE) 3.dp.toPx() else 0f)
    val pad = 4.dp.toPx()
    val behind = when (t.panelStyle) {
        PanelStyle.LIGHTPLATE -> t.panel
        PanelStyle.CARBON -> t.decor ?: t.panel
        else -> t.environment
    }
    drawRoundRect(behind, Offset(x - pad, y), Size(tw + pad * 2, th), CornerRadius(2.dp.toPx()))
    val color = if (t.panelStyle == PanelStyle.CARBON) Color.White else if (t.panelStyle == PanelStyle.LIGHTPLATE) t.legend else t.legendMuted
    drawText(titleLayout, color = color, topLeft = Offset(x, y))
}

private fun corners(r: Rect, inset: Float) = listOf(
    Offset(r.left + inset, r.top + inset), Offset(r.right - inset, r.top + inset),
    Offset(r.left + inset, r.bottom - inset), Offset(r.right - inset, r.bottom - inset),
)

/** Clips [block] to a rounded rect. */
fun DrawScope.clipRound(r: Rect, radius: Float, block: DrawScope.() -> Unit) {
    clipPath(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(r, CornerRadius(radius))) }, block = block)
}
