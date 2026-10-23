package com.simdeck.ui.sim

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.simdeck.R
import com.simdeck.model.Accent
import com.simdeck.model.Themes
import com.simdeck.ui.theme.BarlowCondensed
import com.simdeck.ui.theme.Race

/** Surface finish, drawn as a generated texture (docs/THEMES.md, Material). */
enum class Finish { FLAT, GRAIN, PAINT, BRUSHED, CARBON, POWDER, GLOSS }

/** How a page's panel groups are drawn. */
enum class PanelStyle { OUTLINE, LIGHTPLATE, PLATE, BAY, ROW, GLASS, CARBON }

/** The look of a safety cover. */
enum class GuardStyle { HOUSE, FLIP_RED, FLIP_CLEAR, HAZARD }

/** Haptic weight: light ticks, a firm click, or a heavy thunk. */
enum class Feel { LIGHT, FIRM, HEAVY }

/** Panel lighting chosen by the user: automatic (follows the game's headlights), day or night. */
enum class Lighting { AUTO, DAY, NIGHT }

/** Every token a theme defines (docs/THEMES.md, Anatomy of a theme), already resolved for day or night. */
@Immutable
data class SimTheme(
    val id: String,
    val night: Boolean,
    val environment: Color,
    val environmentLow: Color,
    val envFinish: Finish,
    val panel: Color,
    val panelLine: Color,
    val panelFinish: Finish,
    val panelStyle: PanelStyle,
    val cap: Color,
    val capHigh: Color,
    val capPressed: Color,
    val capEdge: Color,
    val legend: Color,
    val legendMuted: Color,
    val lampOff: Color,
    val segment: Color,
    /** Decorative tone (machine yellow, sand): frames, collars and guards, never a lamp. */
    val decor: Color?,
    val lamps: Map<Accent, Color>,
    val legendFont: FontFamily,
    val legendWeight: FontWeight,
    val titleFont: FontFamily,
    val readoutFont: FontFamily,
    val readoutItalic: Boolean,
    val corner: Dp,
    val pressScale: Float,
    val feel: Feel,
    val guard: GuardStyle,
    val fasteners: Fastener,
) {
    val isHouse: Boolean get() = id == Themes.RACE

    /** The lit colour for a control accent. */
    fun lamp(a: Accent): Color = lamps[a] ?: lamps.getValue(Accent.NEUTRAL)

    fun legendStyle(size: TextUnit = 12.sp, weight: FontWeight = legendWeight) =
        TextStyle(fontFamily = legendFont, fontWeight = weight, fontSize = size, letterSpacing = 0.05.em, lineHeight = size * 1.1f)

    fun titleStyle(size: TextUnit = 10.sp) =
        TextStyle(fontFamily = titleFont, fontWeight = FontWeight.Bold, fontSize = size, letterSpacing = 0.12.em)

    fun readoutStyle(size: TextUnit) = TextStyle(
        fontFamily = readoutFont, fontWeight = FontWeight.Bold, fontSize = size,
        fontStyle = if (readoutItalic) FontStyle.Italic else FontStyle.Normal, fontFeatureSettings = "tnum",
    )
}

enum class Fastener { NONE, DZUS, SLOT, HEX, TORX }

// ---- fonts ---------------------------------------------------------------------------------------

private val B612 = FontFamily(Font(R.font.b612_regular, FontWeight.Normal), Font(R.font.b612_bold, FontWeight.Bold))
private val Titillium = FontFamily(Font(R.font.titillium_semibold, FontWeight.SemiBold), Font(R.font.titillium_bold, FontWeight.Bold))
private val BarlowSemi = FontFamily(
    Font(R.font.barlow_semi_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_semi_condensed_bold, FontWeight.Bold),
)

@OptIn(ExperimentalTextApi::class)
private val Overpass = FontFamily(
    Font(R.font.overpass_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.overpass_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(750))),
)

@OptIn(ExperimentalTextApi::class)
private val Stencil = FontFamily(
    Font(R.font.big_shoulders_stencil_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

// ---- the themes ----------------------------------------------------------------------------------

private fun lamps(neutral: Color, red: Color = Color(0xFFFF3B30), amber: Color = Color(0xFFFFB000), green: Color = Color(0xFF3DDC84),
                  blue: Color = Color(0xFF4C9BFF), cyan: Color = Color(0xFF36C5F0)) =
    mapOf(Accent.NEUTRAL to neutral, Accent.RED to red, Accent.AMBER to amber, Accent.GREEN to green, Accent.BLUE to blue, Accent.CYAN to cyan)

/** [id] with day or night lighting. Unknown ids fall back to the house theme. */
fun simTheme(id: String?, night: Boolean): SimTheme = when (id) {
    Themes.AIRLINER -> SimTheme(
        id = Themes.AIRLINER, night = night,
        environment = if (night) Color(0xFF0B0D0F) else Color(0xFF1E2226), environmentLow = if (night) Color(0xFF060708) else Color(0xFF15181B),
        envFinish = Finish.PAINT,
        panel = if (night) Color(0xFF26292C) else Color(0xFF6F7478), panelLine = if (night) Color(0xFF8F8A7A) else Color(0xFFF2F2F2),
        panelFinish = Finish.PAINT, panelStyle = PanelStyle.LIGHTPLATE,
        cap = Color(0xFF232629), capHigh = Color(0xFF34383C), capPressed = Color(0xFF1A1C1F), capEdge = Color(0xFF0E0F11),
        legend = if (night) Color(0xFFF3EBD3) else Color(0xFFFFFFFF), legendMuted = if (night) Color(0xFFB9B29C) else Color(0xFFE3E5E7),
        lampOff = Color(0xFF15171A), segment = Color(0xFFFFB347), decor = null,
        lamps = lamps(neutral = Color(0xFFF5F5F5)),
        legendFont = B612, legendWeight = FontWeight.Bold, titleFont = B612, readoutFont = B612, readoutItalic = false,
        corner = 2.dp, pressScale = 0.975f, feel = Feel.LIGHT, guard = GuardStyle.FLIP_RED, fasteners = Fastener.DZUS,
    )
    Themes.GA -> SimTheme(
        id = Themes.GA, night = night,
        environment = if (night) Color(0xFF080808) else Color(0xFF141414), environmentLow = Color(0xFF070707), envFinish = Finish.GRAIN,
        panel = if (night) Color(0xFF111213) else Color(0xFF1C1D1F), panelLine = if (night) Color(0xFF6B5A4A) else Color(0xFF3A3B3E),
        panelFinish = Finish.PAINT, panelStyle = PanelStyle.PLATE,
        cap = Color(0xFF2A2B2E), capHigh = Color(0xFF3B3C40), capPressed = Color(0xFF1E1F21), capEdge = Color(0xFF0C0C0D),
        legend = if (night) Color(0xFFFFE2B8) else Color(0xFFF2F2F2), legendMuted = if (night) Color(0xFFBFA585) else Color(0xFFB0B2B5),
        lampOff = Color(0xFF151516), segment = Color(0xFFFFFFFF), decor = null,
        lamps = lamps(neutral = Color(0xFFF7F3E8)),
        legendFont = BarlowSemi, legendWeight = FontWeight.Bold, titleFont = BarlowSemi, readoutFont = B612, readoutItalic = false,
        corner = 4.dp, pressScale = 0.97f, feel = Feel.FIRM, guard = GuardStyle.FLIP_RED, fasteners = Fastener.SLOT,
    )
    Themes.HEAVY, Themes.AGRICULTURE -> {
        val agri = id == Themes.AGRICULTURE
        SimTheme(
            id = id, night = night,
            environment = if (night) Color(0xFF0C0D0E) else Color(0xFF16181A), environmentLow = Color(0xFF0A0B0C), envFinish = Finish.GRAIN,
            panel = if (night) Color(0xFF17191B) else Color(0xFF24272A), panelLine = if (night) Color(0xFF2E3236) else Color(0xFF3A3F44),
            panelFinish = Finish.GRAIN, panelStyle = PanelStyle.BAY,
            cap = Color(0xFF2E3236), capHigh = Color(0xFF3B4046), capPressed = Color(0xFF23262A), capEdge = Color(0xFF111315),
            legend = if (night) Color(0xFFFFB45C) else Color(0xFFE9ECEF), legendMuted = if (night) Color(0xFFB98445) else Color(0xFFA9B0B7),
            lampOff = Color(0xFF1B1D20), segment = Color(0xFFFFB347),
            decor = if (agri) Color(0xFF9AA5B1) else Color(0xFFF2B400),
            lamps = lamps(neutral = if (agri) Color(0xFF3EDC81) else Color(0xFFFFB300), green = Color(0xFF3EDC81), amber = Color(0xFFFFB300), red = Color(0xFFFF453A)),
            legendFont = BarlowSemi, legendWeight = FontWeight.Bold, titleFont = BarlowSemi, readoutFont = BarlowCondensed, readoutItalic = false,
            corner = 12.dp, pressScale = 0.95f, feel = Feel.HEAVY, guard = GuardStyle.HAZARD, fasteners = Fastener.HEX,
        )
    }
    Themes.COMMERCIAL -> SimTheme(
        id = Themes.COMMERCIAL, night = night,
        environment = if (night) Color(0xFF0A0B0C) else Color(0xFF121416), environmentLow = Color(0xFF0B0C0D), envFinish = Finish.GRAIN,
        panel = if (night) Color(0xFF15171A) else Color(0xFF1F2225), panelLine = if (night) Color(0xFF2A2D31) else Color(0xFF34383D),
        panelFinish = Finish.GRAIN, panelStyle = PanelStyle.ROW,
        cap = Color(0xFF2A2D31), capHigh = Color(0xFF363A3F), capPressed = Color(0xFF202326), capEdge = Color(0xFF0E0F11),
        legend = if (night) Color(0xFFFFA04A) else Color(0xFFEDEFF2), legendMuted = if (night) Color(0xFFB0753D) else Color(0xFFA2A8AF),
        lampOff = Color(0xFF17191C), segment = Color(0xFFFFA04A), decor = null,
        lamps = lamps(neutral = Color(0xFFEDEFF2)),
        legendFont = Overpass, legendWeight = FontWeight.Bold, titleFont = Overpass, readoutFont = Overpass, readoutItalic = false,
        corner = 6.dp, pressScale = 0.965f, feel = Feel.FIRM, guard = GuardStyle.FLIP_CLEAR, fasteners = Fastener.NONE,
    )
    Themes.ROADCAR -> SimTheme(
        id = Themes.ROADCAR, night = night,
        environment = if (night) Color(0xFF07080A) else Color(0xFF0E0F11), environmentLow = Color(0xFF050506), envFinish = Finish.GLOSS,
        panel = if (night) Color(0xFF0D0E10) else Color(0xFF141518), panelLine = Color(0xFFA7ABB0),
        panelFinish = Finish.GLOSS, panelStyle = PanelStyle.GLASS,
        cap = Color(0xFF1B1D21), capHigh = Color(0xFF26292E), capPressed = Color(0xFF141518), capEdge = Color(0xFF8E9399),
        legend = Color(0xFFF5F6F7), legendMuted = if (night) Color(0xFF8D9196) else Color(0xFFA6ABB1),
        lampOff = Color(0xFF121316), segment = Color(0xFFFFFFFF), decor = Color(0xFFA7ABB0),
        lamps = lamps(neutral = Color(0xFFFFFFFF), blue = Color(0xFF3B8CFF)),
        legendFont = FontFamily.SansSerif, legendWeight = FontWeight.Medium, titleFont = FontFamily.SansSerif, readoutFont = FontFamily.SansSerif,
        readoutItalic = false, corner = 10.dp, pressScale = 0.98f, feel = Feel.LIGHT, guard = GuardStyle.FLIP_CLEAR, fasteners = Fastener.NONE,
    )
    Themes.OVERLAND -> SimTheme(
        id = Themes.OVERLAND, night = night,
        environment = if (night) Color(0xFF090A08) else Color(0xFF121310), environmentLow = Color(0xFF0A0A09), envFinish = Finish.POWDER,
        panel = if (night) Color(0xFF141512) else Color(0xFF1C1D1A), panelLine = if (night) Color(0xFF3A3B36) else Color(0xFF4A4C46),
        panelFinish = Finish.POWDER, panelStyle = PanelStyle.PLATE,
        cap = Color(0xFF2A2B28), capHigh = Color(0xFF363833), capPressed = Color(0xFF1F201E), capEdge = Color(0xFF0D0D0C),
        legend = if (night) Color(0xFFFFB000) else Color(0xFFECE9DF), legendMuted = if (night) Color(0xFFB07A00) else Color(0xFFA8A496),
        lampOff = Color(0xFF181917), segment = Color(0xFFFFB000), decor = Color(0xFFC8B48A),
        lamps = lamps(neutral = Color(0xFFFFB000)),
        legendFont = BarlowSemi, legendWeight = FontWeight.Bold, titleFont = Stencil, readoutFont = BarlowCondensed, readoutItalic = false,
        corner = 4.dp, pressScale = 0.96f, feel = Feel.FIRM, guard = GuardStyle.HAZARD, fasteners = Fastener.TORX,
    )
    Themes.MOTORSPORT -> SimTheme(
        id = Themes.MOTORSPORT, night = night,
        environment = Color(0xFF0B0B0C), environmentLow = Color(0xFF050505), envFinish = Finish.CARBON,
        panel = if (night) Color(0xFF111214) else Color(0xFF17181A), panelLine = Color(0xFF2C2E31),
        panelFinish = Finish.CARBON, panelStyle = PanelStyle.CARBON,
        cap = Color(0xFF232528), capHigh = Color(0xFF2F3236), capPressed = Color(0xFF18191B), capEdge = Color(0xFF09090A),
        legend = Color(0xFFFFFFFF), legendMuted = Color(0xFFA9ADB3),
        lampOff = Color(0xFF141517), segment = Color(0xFFFF2D2D), decor = Color(0xFFD0312D),
        lamps = lamps(neutral = Color(0xFFFFFFFF), red = Color(0xFFFF2D2D), green = Color(0xFF2EE66B), blue = Color(0xFF2F7BFF)),
        legendFont = Titillium, legendWeight = FontWeight.Bold, titleFont = Titillium, readoutFont = BarlowCondensed, readoutItalic = true,
        corner = 4.dp, pressScale = 0.965f, feel = Feel.FIRM, guard = GuardStyle.FLIP_RED, fasteners = Fastener.HEX,
    )
    else -> SimTheme(
        id = Themes.RACE, night = night,
        environment = Race.Ground, environmentLow = Race.Ground, envFinish = Finish.FLAT,
        panel = Race.Panel, panelLine = Race.Line, panelFinish = Finish.FLAT, panelStyle = PanelStyle.OUTLINE,
        cap = Race.Cap, capHigh = Race.CapHigh, capPressed = Race.CapPressed, capEdge = Race.Line,
        legend = Race.Ink, legendMuted = Race.InkMuted, lampOff = Race.Panel, segment = Race.Cyan, decor = null,
        lamps = mapOf(Accent.NEUTRAL to Race.Electric, Accent.AMBER to Race.Amber, Accent.GREEN to Race.Green, Accent.RED to Race.Red,
            Accent.BLUE to Race.Blue, Accent.CYAN to Race.Cyan),
        legendFont = BarlowCondensed, legendWeight = FontWeight.SemiBold, titleFont = BarlowCondensed, readoutFont = BarlowCondensed,
        readoutItalic = true, corner = 6.dp, pressScale = 0.965f, feel = Feel.FIRM, guard = GuardStyle.HOUSE, fasteners = Fastener.NONE,
    )
}

/** The theme the play surface draws with. The house theme outside a themed surface. */
val LocalSimTheme = staticCompositionLocalOf { simTheme(Themes.RACE, false) }

/** A haptic click in the theme's weight. */
@Composable
fun rememberThemeHaptic(feel: Feel): () -> Unit {
    val view = LocalView.current
    val sound = LocalSimSound.current
    return remember(view, feel, sound) {
        val type = when (feel) {
            Feel.LIGHT -> HapticFeedbackConstants.KEYBOARD_TAP
            Feel.FIRM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
            Feel.HEAVY -> HapticFeedbackConstants.LONG_PRESS
        }
        { view.performHapticFeedback(type); sound?.invoke(feel) }
    }
}
