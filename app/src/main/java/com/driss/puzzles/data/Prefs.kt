package com.driss.puzzles.data

import android.content.Context
import androidx.core.content.edit
import com.driss.puzzles.engine.PuzzleGrid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    /** How strongly the picture shows under the pieces, 0..1. */
    var ghostAlpha: Float
        get() = prefs.getFloat("ghost_alpha", 0.3f)
        set(value) = prefs.edit { putFloat("ghost_alpha", value) }

    var previewOn: Boolean
        get() = prefs.getBoolean("preview_on", false)
        set(value) = prefs.edit { putBoolean("preview_on", value) }

    private val _kidsLock = MutableStateFlow(prefs.getBoolean("kids_lock", false))
    /** When on, the home screen only shows the Kids puzzles until a grown-up unlocks it. */
    val kidsLock: StateFlow<Boolean> = _kidsLock

    fun setKidsLock(on: Boolean) {
        prefs.edit { putBoolean("kids_lock", on) }
        _kidsLock.value = on
    }

    private val _stars = MutableStateFlow(readStars())
    /** Stars earned per Kids puzzle: one for every time it was finished. */
    val stars: StateFlow<Map<String, Int>> = _stars

    /** Awards a star for finishing [imageId]. Returns the new total. */
    fun addStar(imageId: String): Int {
        val count = (_stars.value[imageId] ?: 0) + 1
        prefs.edit { putInt(STAR_PREFIX + imageId, count) }
        _stars.value = _stars.value + (imageId to count)
        return _stars.value.values.sum()
    }

    private fun readStars(): Map<String, Int> =
        prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(STAR_PREFIX) && value is Int) key.removePrefix(STAR_PREFIX) to value else null
        }.toMap()

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

    private companion object {
        const val STAR_PREFIX = "stars_"
    }
}
