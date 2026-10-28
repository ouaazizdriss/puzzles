package com.driss.puzzles.data

import android.content.Context
import androidx.core.content.edit
import com.driss.puzzles.engine.PuzzleGrid

/** Small settings and best times. */
class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("puzzles", Context.MODE_PRIVATE)

    var tableColor: Int
        get() = prefs.getInt("table_color", 0)
        set(value) = prefs.edit { putInt("table_color", value) }

    var lastTarget: Int
        get() = prefs.getInt("last_target", PuzzleGrid.DEFAULT_TARGET)
        set(value) = prefs.edit { putInt("last_target", value) }

    var lastKidsTarget: Int
        get() = prefs.getInt("last_kids_target", PuzzleGrid.DEFAULT_KIDS_TARGET)
        set(value) = prefs.edit { putInt("last_kids_target", value) }

    var soundOn: Boolean
        get() = prefs.getBoolean("sound_on", true)
        set(value) = prefs.edit { putBoolean("sound_on", value) }

    fun bestTime(imageId: String, pieces: Int): Long? =
        prefs.getLong(bestKey(imageId, pieces), -1L).takeIf { it >= 0 }

    /** Stores [ms] if it beats the current best. Returns true for a new record. */
    fun recordTime(imageId: String, pieces: Int, ms: Long): Boolean {
        val best = bestTime(imageId, pieces)
        if (best != null && best <= ms) return false
        prefs.edit { putLong(bestKey(imageId, pieces), ms) }
        return true
    }

    private fun bestKey(imageId: String, pieces: Int) = "best_${imageId}_$pieces"
}
