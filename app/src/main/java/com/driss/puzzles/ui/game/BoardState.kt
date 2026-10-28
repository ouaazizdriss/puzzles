package com.driss.puzzles.ui.game

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min

/**
 * Camera, tray scroll and the piece being dragged. Screen coordinates are pixels of the board canvas;
 * world coordinates are pixels of the puzzle image, with the puzzle frame at (0, 0)-(width, height).
 */
@Stable
class BoardState(private val vm: GameViewModel, private val density: Float) {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var trayScroll by mutableFloatStateOf(0f)
        private set
    var dragId by mutableIntStateOf(-1)
        private set
    /** World position of the dragged piece's cell origin. */
    var dragPos by mutableStateOf(Offset.Zero)
        private set
    /** Finger is over the tray while dragging: the piece will go back into it. */
    var overTray by mutableStateOf(false)
        private set

    var size by mutableStateOf(IntSize.Zero)
        private set
    var trayAtSide by mutableStateOf(false)
        private set
    var trayThickness by mutableFloatStateOf(0f)
        private set

    private var grab = Offset.Zero

    val trayRect: Rect
        get() = if (trayAtSide) Rect(size.width - trayThickness, 0f, size.width.toFloat(), size.height.toFloat())
        else Rect(0f, size.height - trayThickness, size.width.toFloat(), size.height.toFloat())

    val boardRect: Rect
        get() = if (trayAtSide) Rect(0f, 0f, size.width - trayThickness, size.height.toFloat())
        else Rect(0f, 0f, size.width.toFloat(), size.height - trayThickness)

    private val trayLength get() = if (trayAtSide) trayRect.height else trayRect.width
    val slot get() = trayThickness

    fun layout(newSize: IntSize, side: Boolean, thickness: Float) {
        size = newSize
        trayAtSide = side
        trayThickness = thickness
        fit()
    }

    private fun fitScale(): Float {
        val cut = vm.cut ?: return 1f
        val b = boardRect
        return min(b.width * 0.92f / cut.width, b.height * 0.9f / cut.height).coerceAtLeast(0.01f)
    }

    /** Centers the puzzle frame in the board area. */
    fun fit() {
        val cut = vm.cut ?: return
        if (size == IntSize.Zero) return
        val s = fitScale()
        val b = boardRect
        scale = s
        offset = Offset(b.left + (b.width - cut.width * s) / 2, b.top + (b.height - cut.height * s) / 2)
        clampTray()
    }

    fun toWorld(p: Offset) = (p - offset) / scale

    private fun along(p: Offset) = if (trayAtSide) p.y - trayRect.top else p.x - trayRect.left

    fun trayItemAt(p: Offset): Int? {
        if (!trayRect.contains(p)) return null
        val index = ((along(p) + trayScroll) / slot).toInt()
        return vm.trayItems().getOrNull(index)
    }

    private fun trayInsertIndex(p: Offset) = ((along(p) + trayScroll) / slot + 0.5f).toInt().coerceAtLeast(0)

    /** Topmost loose piece under [p], tested against the piece's actual shape. */
    fun hitLoose(p: Offset): Int? {
        val cut = vm.cut ?: return null
        val w = toWorld(p)
        for (id in vm.loose.asReversed()) {
            val piece = vm.pieces[id]
            val bmp = vm.sprites[id].bitmap
            val lx = (w.x - (piece.x - cut.pad)).toInt()
            val ly = (w.y - (piece.y - cut.pad)).toInt()
            if (lx in 0 until bmp.width && ly in 0 until bmp.height && (bmp.getPixel(lx, ly) ushr 24) > 40) return id
        }
        return null
    }

    fun beginDrag(id: Int, p: Offset) {
        val piece = vm.pieces[id]
        grab = Offset(piece.x, piece.y) - toWorld(p)
        dragPos = Offset(piece.x, piece.y)
        dragId = id
        vm.bringToFront(id)
    }

    fun beginDragFromTray(id: Int, p: Offset) {
        val cut = vm.cut ?: return
        grab = Offset(-cut.cellW / 2, -cut.cellH / 2)
        dragPos = toWorld(p) + grab
        vm.takeFromTray(id, dragPos.x, dragPos.y)
        dragId = id
        clampTray()
    }

    fun dragTo(p: Offset) {
        dragPos = toWorld(p) + grab
        overTray = trayRect.contains(p)
    }

    /** Ends a drag with the finger at [p]. Returns true if the piece snapped into place. */
    fun endDrag(p: Offset): Boolean {
        val id = dragId
        val cut = vm.cut
        dragId = -1
        overTray = false
        if (id < 0 || cut == null) return false
        if (trayRect.contains(p)) {
            vm.returnToTray(id, trayInsertIndex(p))
            return false
        }
        val tolerance = max(cut.unit * 0.22f, 20f * density / scale)
        return vm.drop(id, dragPos.x, dragPos.y, tolerance)
    }

    fun pan(delta: Offset) {
        offset += delta
    }

    fun transform(centroid: Offset, pan: Offset, zoom: Float) {
        val fit = fitScale()
        val newScale = (scale * zoom).coerceIn(fit * 0.5f, fit * 8f)
        val z = newScale / scale
        offset = (offset - centroid) * z + centroid + pan
        scale = newScale
    }

    fun scrollTray(delta: Float) {
        trayScroll += delta
        clampTray()
    }

    fun clampTray() {
        val max = max(0f, vm.trayItems().size * slot - trayLength)
        trayScroll = trayScroll.coerceIn(0f, max)
    }
}
