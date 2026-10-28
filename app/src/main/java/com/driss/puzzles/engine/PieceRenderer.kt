package com.driss.puzzles.engine

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.ceil
import kotlin.math.max

/**
 * A pre-rendered piece. [bitmap] covers the cell plus [PuzzleCut.pad] on every side, so it is drawn
 * at (pieceX - pad, pieceY - pad). [shadow] is its alpha mask.
 */
class PieceSprite(val bitmap: Bitmap, val image: ImageBitmap, val shadow: ImageBitmap)

private class AndroidPathSink(val path: Path) : PathSink {
    override fun moveTo(x: Float, y: Float) = path.moveTo(x, y)
    override fun lineTo(x: Float, y: Float) = path.lineTo(x, y)
    override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        path.cubicTo(x1, y1, x2, y2, x3, y3)
    override fun close() = path.close()
}

private class ComposePathSink(val path: androidx.compose.ui.graphics.Path) : PathSink {
    override fun moveTo(x: Float, y: Float) = path.moveTo(x, y)
    override fun lineTo(x: Float, y: Float) = path.lineTo(x, y)
    override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        path.cubicTo(x1, y1, x2, y2, x3, y3)
    override fun close() = path.close()
}

object PieceRenderer {

    /** Outline of every piece at its home position, in world coordinates. */
    fun outlines(cut: PuzzleCut): List<androidx.compose.ui.graphics.Path> =
        List(cut.count) { id -> androidx.compose.ui.graphics.Path().also { cut.outline(id, ComposePathSink(it)) } }

    fun render(source: Bitmap, cut: PuzzleCut): List<PieceSprite> {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val stroke = max(1f, cut.unit * 0.012f)
        val outline = strokePaint(stroke, 0x66000000)
        val highlight = strokePaint(stroke * 2f, 0x50FFFFFF)
        val shade = strokePaint(stroke * 2f, 0x40000000)
        val w = ceil(cut.cellW + 2 * cut.pad).toInt()
        val h = ceil(cut.cellH + 2 * cut.pad).toInt()

        return List(cut.count) { id ->
            val path = Path().also { cut.outline(id, AndroidPathSink(it)) }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.translate(cut.pad - cut.homeX(id), cut.pad - cut.homeY(id))
            canvas.drawPath(path, fill)
            // Light bevel: highlight inside the top-left edges, shade inside the bottom-right ones.
            canvas.save()
            canvas.clipPath(path)
            canvas.save()
            canvas.translate(stroke, stroke)
            canvas.drawPath(path, highlight)
            canvas.restore()
            canvas.translate(-stroke, -stroke)
            canvas.drawPath(path, shade)
            canvas.restore()
            canvas.drawPath(path, outline)
            PieceSprite(bmp, bmp.asImageBitmap(), bmp.extractAlpha().asImageBitmap())
        }
    }

    private fun strokePaint(width: Float, argb: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        color = argb
    }
}
