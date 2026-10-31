package com.simdeck.ui

import android.content.pm.ActivityInfo

/** How the whole app is turned (1.6). The play bar's rotate button steps through these in order. */
enum class Orientation(val activityValue: Int, val label: String) {
    LANDSCAPE(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, "landscape"),
    PORTRAIT(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, "portrait"),
    /** Turns with the phone, and follows the phone's own auto-rotate switch. */
    AUTO(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, "follows the phone");

    val next: Orientation get() = entries[(ordinal + 1) % entries.size]
}
