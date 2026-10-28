package com.driss.puzzles.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class Recorder : PathSink {
    val points = mutableListOf<Pair<Float, Float>>()
    override fun moveTo(x: Float, y: Float) { points += x to y }
    override fun lineTo(x: Float, y: Float) { points += x to y }
    override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        points += x1 to y1; points += x2 to y2; points += x3 to y3
    }
    override fun close() {}
}

private fun PuzzleCut.points(id: Int) = Recorder().also { outline(id, it) }.points

class PuzzleCutTest {
    private val cut = PuzzleCut(1600f, 1000f, 5, 8, seed = 42)

    @Test
    fun sameSeedGivesSameShapes() {
        val other = PuzzleCut(1600f, 1000f, 5, 8, seed = 42)
        for (id in 0 until cut.count) assertEquals(cut.points(id), other.points(id))
    }

    @Test
    fun differentSeedsGiveDifferentShapes() {
        val other = PuzzleCut(1600f, 1000f, 5, 8, seed = 7)
        assertTrue((0 until cut.count).any { cut.points(it) != other.points(it) })
    }

    @Test
    fun neighboursShareTheirEdge() {
        for (id in 0 until cut.count) {
            val r = cut.row(id)
            val c = cut.col(id)
            if (c < cut.cols - 1) {
                // Right edge of id (points 2..10 after the top edge) is the reversed left edge of id + 1.
                val mine = cut.edgePoints(id, side = 1)
                val theirs = cut.edgePoints(id + 1, side = 3)
                assertEquals(mine, theirs.reversed())
            }
            if (r < cut.rows - 1) {
                val mine = cut.edgePoints(id, side = 2)
                val theirs = cut.edgePoints(id + cut.cols, side = 0)
                assertEquals(mine, theirs.reversed())
            }
        }
    }

    @Test
    fun outlineStaysInsidePaddedCell() {
        for (id in 0 until cut.count) {
            for ((x, y) in cut.points(id)) {
                assertTrue(x >= cut.homeX(id) - cut.pad && x <= cut.homeX(id) + cut.cellW + cut.pad)
                assertTrue(y >= cut.homeY(id) - cut.pad && y <= cut.homeY(id) + cut.cellH + cut.pad)
            }
        }
    }

    @Test
    fun snapToleranceIsRespected() {
        val id = 9
        assertTrue(cut.isNearHome(id, cut.homeX(id) + 10f, cut.homeY(id) - 10f, 12f))
        assertFalse(cut.isNearHome(id, cut.homeX(id) + 13f, cut.homeY(id), 12f))
    }

    @Test
    fun borderPieces() {
        assertTrue(cut.isBorder(0))
        assertTrue(cut.isBorder(cut.count - 1))
        assertFalse(cut.isBorder(cut.cols + 1))
    }
}

/**
 * Points of one side of a piece, including both corners. Sides: 0 top, 1 right, 2 bottom, 3 left.
 * Flat sides are 2 points, knob sides 10.
 */
private fun PuzzleCut.edgePoints(id: Int, side: Int): List<Pair<Float, Float>> {
    val pts = points(id)
    val lengths = listOf(
        if (row(id) == 0) 1 else 9,
        if (col(id) == cols - 1) 1 else 9,
        if (row(id) == rows - 1) 1 else 9,
        if (col(id) == 0) 1 else 9,
    )
    val start = lengths.take(side).sum()
    return pts.subList(start, start + lengths[side] + 1).map { (x, y) -> round(x) to round(y) }
}

private fun round(v: Float) = Math.round(v * 100f) / 100f
