package com.driss.puzzles.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PuzzleGridTest {
    @Test
    fun squareImageGetsExactSquareGrids() {
        assertEquals(GridSize(3, 3), PuzzleGrid.forTarget(1000, 1000, 9))
        assertEquals(GridSize(10, 10), PuzzleGrid.forTarget(1000, 1000, 100))
    }

    @Test
    fun landscapeImageHasMoreColumns() {
        val grid = PuzzleGrid.forTarget(1600, 1067, 150)
        assertEquals(GridSize(10, 15), grid)
    }

    @Test
    fun portraitImageHasMoreRows() {
        val grid = PuzzleGrid.forTarget(1200, 1600, 64)
        assertTrue(grid.rows > grid.cols)
    }

    @Test
    fun kidsOptionsGiveTinyGrids() {
        assertEquals(4, PuzzleGrid.forTarget(1600, 1200, 4).count)
        assertEquals(6, PuzzleGrid.forTarget(1600, 1200, 6).count)
        assertEquals(12, PuzzleGrid.forTarget(1600, 1200, 12).count)
    }

    @Test
    fun countsStayCloseToTarget() {
        for (target in PuzzleGrid.PIECE_OPTIONS) {
            for ((w, h) in listOf(1600 to 1067, 1067 to 1600, 1600 to 900, 1000 to 1000)) {
                val count = PuzzleGrid.forTarget(w, h, target).count
                assertTrue("$w x $h, target $target gave $count", abs(count - target) <= target * 0.25)
            }
        }
    }
}
