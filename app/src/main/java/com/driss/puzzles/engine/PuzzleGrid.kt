package com.driss.puzzles.engine

import kotlin.math.abs
import kotlin.math.ln

data class GridSize(val rows: Int, val cols: Int) {
    val count get() = rows * cols
}

object PuzzleGrid {
    val PIECE_OPTIONS = listOf(9, 16, 36, 64, 100, 150)
    const val DEFAULT_TARGET = 36
    /** Big, few pieces for young children. */
    val KIDS_OPTIONS = listOf(4, 6, 9, 12, 16)
    const val DEFAULT_KIDS_TARGET = 6

    /** Picks the grid closest to [target] pieces whose cells stay close to square for this image. */
    fun forTarget(width: Int, height: Int, target: Int): GridSize {
        var best = GridSize(2, 2)
        var bestScore = Float.MAX_VALUE
        for (rows in 2..30) {
            for (cols in 2..30) {
                val cellAspect = (width.toFloat() / cols) / (height.toFloat() / rows)
                val score = 2f * abs(rows * cols - target) / target + abs(ln(cellAspect))
                if (score < bestScore) {
                    bestScore = score
                    best = GridSize(rows, cols)
                }
            }
        }
        return best
    }
}
