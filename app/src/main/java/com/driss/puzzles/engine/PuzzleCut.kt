package com.driss.puzzles.engine

import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/** Receives the outline of a piece. Implemented over android.graphics.Path by the renderer. */
interface PathSink {
    fun moveTo(x: Float, y: Float)
    fun lineTo(x: Float, y: Float)
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float)
    fun close()
}

/**
 * Cuts a [width] x [height] image into a [rows] x [cols] jigsaw.
 *
 * Every internal edge is generated once (tab direction + jitter from [seed]) and shared by the two
 * pieces on either side, so neighbouring pieces always fit. Coordinates are in image pixels
 * ("world" units). Piece ids are row-major: id = row * cols + col.
 */
class PuzzleCut(
    val width: Float,
    val height: Float,
    val rows: Int,
    val cols: Int,
    val seed: Long,
) {
    val cellW = width / cols
    val cellH = height / rows
    /** Knob size reference: knobs stay round even when cells are not square. */
    val unit = min(cellW, cellH)
    /** Margin around a cell that a piece bitmap needs to contain its tabs. */
    val pad = unit * PAD_RATIO
    val count get() = rows * cols

    // Horizontal edges sit between row r and r + 1 (index r * cols + c).
    // Vertical edges sit between col c and c + 1 (index r * (cols - 1) + c).
    // Each edge is 10 points (p0..p9) flattened to 20 floats, in world coordinates.
    private val hEdges: Array<FloatArray>
    private val vEdges: Array<FloatArray>

    init {
        require(rows >= 1 && cols >= 1) { "Grid must be at least 1x1" }
        val rnd = Random(seed)
        hEdges = Array((rows - 1) * cols) { i ->
            val r = i / cols
            val c = i % cols
            edge(rnd, c * cellW, (r + 1) * cellH, 1f, 0f, cellW)
        }
        vEdges = Array(rows * (cols - 1)) { i ->
            val r = i / (cols - 1)
            val c = i % (cols - 1)
            edge(rnd, (c + 1) * cellW, r * cellH, 0f, 1f, cellH)
        }
    }

    fun row(id: Int) = id / cols
    fun col(id: Int) = id % cols
    fun homeX(id: Int) = col(id) * cellW
    fun homeY(id: Int) = row(id) * cellH
    fun isBorder(id: Int) = row(id) == 0 || col(id) == 0 || row(id) == rows - 1 || col(id) == cols - 1

    /** True when a piece whose cell origin is at ([x], [y]) is close enough to snap home. */
    fun isNearHome(id: Int, x: Float, y: Float, tolerance: Float) =
        abs(x - homeX(id)) <= tolerance && abs(y - homeY(id)) <= tolerance

    /** Emits the closed outline of piece [id], clockwise, in world coordinates. */
    fun outline(id: Int, sink: PathSink) {
        val r = row(id)
        val c = col(id)
        val x0 = c * cellW
        val y0 = r * cellH
        val x1 = (c + 1) * cellW
        val y1 = (r + 1) * cellH
        sink.moveTo(x0, y0)
        if (r == 0) sink.lineTo(x1, y0) else forward(hEdges[(r - 1) * cols + c], sink)
        if (c == cols - 1) sink.lineTo(x1, y1) else forward(vEdges[r * (cols - 1) + c], sink)
        if (r == rows - 1) sink.lineTo(x0, y1) else backward(hEdges[r * cols + c], sink)
        if (c == 0) sink.lineTo(x0, y0) else backward(vEdges[r * (cols - 1) + c - 1], sink)
        sink.close()
    }

    private fun forward(p: FloatArray, sink: PathSink) {
        for (s in 0 until 3) {
            val i = 2 + s * 6
            sink.cubicTo(p[i], p[i + 1], p[i + 2], p[i + 3], p[i + 4], p[i + 5])
        }
    }

    private fun backward(p: FloatArray, sink: PathSink) {
        for (s in 0 until 3) {
            val k = 8 - s * 3
            sink.cubicTo(p[2 * k], p[2 * k + 1], p[2 * k - 2], p[2 * k - 1], p[2 * k - 4], p[2 * k - 3])
        }
    }

    /**
     * Classic knob edge (after Draradech's jigsaw generator). The edge starts at ([sx], [sy]) and runs
     * [length] along ([dx], [dy]); the knob bulges along the normal (dy, dx), flipped at random.
     */
    private fun edge(rnd: Random, sx: Float, sy: Float, dx: Float, dy: Float, length: Float): FloatArray {
        val flip = if (rnd.nextBoolean()) 1f else -1f
        fun jitter() = (rnd.nextFloat() * 2f - 1f) * JITTER
        val a = jitter()
        val b = jitter()
        val c = jitter()
        val d = jitter()
        val e = jitter()
        val t = TAB
        val half = length / 2f
        val u = unit
        // (distance along the edge, offset from the edge) for p0..p9
        val pts = floatArrayOf(
            0f, 0f,
            0.2f * length, a * u,
            half + (b + d) * u, (-t + c) * u,
            half + (-t + b) * u, (t + c) * u,
            half + (-2 * t + b - d) * u, (3 * t + c) * u,
            half + (2 * t + b - d) * u, (3 * t + c) * u,
            half + (t + b) * u, (t + c) * u,
            half + (b + d) * u, (-t + c) * u,
            0.8f * length, e * u,
            length, 0f,
        )
        val out = FloatArray(20)
        for (k in 0 until 10) {
            val along = pts[2 * k]
            val off = pts[2 * k + 1] * flip
            out[2 * k] = sx + dx * along + dy * off
            out[2 * k + 1] = sy + dy * along + dx * off
        }
        return out
    }

    companion object {
        const val TAB = 0.1f
        const val JITTER = 0.035f
        /** Control points reach at most (3 * TAB + JITTER) * unit from the edge. */
        const val PAD_RATIO = 3 * TAB + JITTER + 0.02f
    }
}
