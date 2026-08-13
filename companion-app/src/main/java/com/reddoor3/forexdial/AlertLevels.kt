package com.reddoor3.forexdial

import android.content.SharedPreferences

// Alert thresholds, stored as one comma-separated string.
//
// A list rather than named upper/lower fields even though the UI currently
// offers exactly two (Ambar 2026-08-12: "i might want N levels, but start by
// building two"). Storing a list now means N later is a UI change, not a
// rewrite of storage, payload and crossing detection.
//
// Levels are NOT semantically upper/lower - crossing is detected in both
// directions, so any level alerts whichever way price passes through it.
object AlertLevels {

    // Tolerates blanks and junk. Falls back to the legacy single-value key so
    // an install predating the list keeps its alert instead of silently
    // losing it on upgrade.
    fun read(prefs: SharedPreferences): List<Float> {
        val raw = prefs.getString(Constants.KEY_ALERT_LEVELS, null)
        if (raw != null) {
            return raw.split(",")
                .mapNotNull { it.trim().toFloatOrNull() }
                .filter { it > 0f }
        }
        val legacy = prefs.getFloat(Constants.KEY_ALERT_LEVEL, 0f)
        return if (legacy > 0f) listOf(legacy) else emptyList()
    }

    fun write(prefs: SharedPreferences, levels: List<Float>) {
        prefs.edit()
            .putString(Constants.KEY_ALERT_LEVELS,
                levels.filter { it > 0f }.joinToString(",") { "%.5f".format(it) })
            // Reset the crossing baseline whenever the levels change, so a
            // price already past a newly-armed level isn't counted as a fresh
            // crossing the instant it's saved.
            .putFloat(Constants.KEY_ALERT_LAST_PRICE, 0f)
            .apply()
    }
}
