package com.simdeck.model

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * One tuning setting of the BeamNG vehicle being driven (jbeam "variables", what the game's Tuning menu shows), SimDeck 1.4.
 * [value] is in the game's own range [min]..[max]; the screen shows it in display units like the game does: mapped onto
 * [minDis]..[maxDis] when those are set, as -100 % to 100 % when the [unit] is empty, else as is.
 */
data class TuningVar(
    val name: String,
    val title: String,
    val category: String,
    val subCategory: String? = null,
    val description: String? = null,
    val unit: String = "",
    val min: Double,
    val max: Double,
    val minDis: Double? = null,
    val maxDis: Double? = null,
    val stepDis: Double? = null,
    val default: Double? = null,
    val value: Double,
) {
    private val span get() = max - min
    private val mapped get() = minDis != null && maxDis != null && span != 0.0
    private val percent get() = !mapped && unit.isBlank() && span != 0.0

    val displayMin: Double get() = when { mapped -> minDis!!; percent -> -100.0; else -> min }
    val displayMax: Double get() = when { mapped -> maxDis!!; percent -> 100.0; else -> max }
    val displayUnit: String get() = if (unit.isBlank()) "%" else unit

    fun toDisplay(v: Double): Double = when {
        mapped -> minDis!! + (v - min) / span * (maxDis!! - minDis!!)
        percent -> -100.0 + (v - min) / span * 200.0
        else -> v
    }

    fun fromDisplay(d: Double): Double = when {
        mapped -> min + (d - minDis!!) / (maxDis!! - minDis!!) * span
        percent -> min + (d + 100.0) / 200.0 * span
        else -> d
    }

    /** Step of the slider and the - / + buttons, in display units. */
    val displayStep: Double get() = when {
        percent -> 1.0
        stepDis != null && stepDis > 0 -> stepDis
        else -> niceStep(abs(displayMax - displayMin) / 100.0)
    }

    /** Clamps [v] (game range) and snaps it to the display step. */
    fun snap(v: Double): Double {
        val lo = minOf(min, max); val hi = maxOf(min, max)
        val d = toDisplay(v.coerceIn(lo, hi))
        val step = displayStep
        val snapped = displayMin + ((d - displayMin) / step).roundToLong() * step
        return fromDisplay(snapped).coerceIn(lo, hi)
    }

    val decimals: Int get() = decimalsFor(displayStep)

    /** [v] (game range) as the screen shows it, e.g. "32.5 psi". */
    fun format(v: Double): String {
        val d = toDisplay(v)
        val s = if (decimals == 0) d.roundToLong().toString() else String.format(java.util.Locale.ROOT, "%.${decimals}f", d)
        return "$s $displayUnit".trim()
    }

    companion object {
        fun niceStep(raw: Double): Double {
            if (raw <= 0 || raw.isNaN()) return 1.0
            val p = 10.0.pow(floor(log10(raw)))
            val m = raw / p
            return p * when { m < 1.5 -> 1.0; m < 3.5 -> 2.0; m < 7.5 -> 5.0; else -> 10.0 }
        }

        fun decimalsFor(step: Double): Int {
            if (step >= 1) return 0
            var d = 0; var s = step
            while (d < 4 && abs(s - s.roundToLong()) > 1e-9) { s *= 10; d++ }
            return d
        }
    }
}

/** The tuning page's state: the vehicle's settings, edits not applied yet, and the last apply result. */
data class TuningState(
    val vehicle: String = "",
    val vars: List<TuningVar> = emptyList(),
    /** Edited values (game range) by variable name, not applied yet. */
    val pending: Map<String, Double> = emptyMap(),
    val applying: Boolean = false,
    val message: String? = null,
    val ok: Boolean = true,
    /** Apply a change by itself shortly after the last edit. */
    val autoApply: Boolean = false,
) {
    fun valueOf(v: TuningVar): Double = pending[v.name] ?: v.value
    val dirty: Boolean get() = pending.any { (k, nv) -> vars.firstOrNull { it.name == k }?.let { abs(it.value - nv) > 1e-12 } ?: false }

    /** Settings grouped as the game's menu groups them: category, then sub-category. */
    fun grouped(query: String = ""): List<Pair<String, List<TuningVar>>> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val list = if (words.isEmpty()) vars else vars.filter { v ->
            val hay = "${v.title} ${v.category} ${v.subCategory.orEmpty()} ${v.name} ${v.description.orEmpty()}".lowercase()
            words.all { it in hay }
        }
        return list.groupBy { it.category }.toList()
    }
}
