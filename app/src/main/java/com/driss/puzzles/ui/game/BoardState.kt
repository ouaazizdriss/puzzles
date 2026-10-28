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
 * Camera, tray scroll and the group being dragged. Screen coordinates are pixels of the board canvas;
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
    /** The piece under the finger, or -1. */
    var dragId by mutableIntStateOf(-1)
        private set
    /** Every piece moving with [dragId] (its joined group). */
    var dragMembers by mutableStateOf<Set<Int>>(emptySet())
        private set
    /** World offset of the dragged group from where the drag started. */
    var dragDelta by mutableStateOf(Offset.Zero)
        private set
    /** Finger is over the tray while dragging a single piece: it will go back into the tray. */
    var overTray by mutableStateOf(false)
        private set
    /** Pieces that just locked into place, for a short flash; [flashKey] changes on every snap. */
    var flashIds by mutableStateOf<List<Int>>(emptyList())
        private set
    var flashKey by mutableIntStateOf(0)
        private set

    var size by mutableStateOf(IntSize.Zero)
        private set
    var trayAtSide by mutableStateOf(false)
        private set
    var trayThickness by mutableFloatStateOf(0f)
        private set

    private var dragStart = Offset.Zero

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
        // Show any pieces already spread on the table too, not just the frame.
        fitContent()
    }

    private fun fitScale(): Float {
        val cut = vm.cut ?: return 1f
        return scaleFor(cut.width, cut.height)
    }

    private fun scaleFor(width: Float, height: Float): Float {
        val b = boardRect
        return min(b.width * 0.92f / width, b.height * 0.9f / height).coerceAtLeast(0.01f)
    }

    /** World rect holding the frame and every loose piece. */
    private fun contentBounds(): Rect {
        val cut = vm.cut ?: return Rect.Zero
        var r = Rect(0f, 0f, cut.width, cut.height)
        for (id in vm.loose) {
            val p = vm.pieces[id]
            r = Rect(
                min(r.left, p.x - cut.pad), min(r.top, p.y - cut.pad),
                max(r.right, p.x + cut.cellW + cut.pad), max(r.bottom, p.y + cut.cellH + cut.pad),
            )
        }
        return r
    }

    /** Width / height of the area the puzzle is shown in. */
    val boardAspect: Float
        get() = boardRect.let { if (it.height > 0f) it.width / it.height else 1f }

    /** Zooms out until the frame and every piece on the table are visible. */
    fun fitContent() {
        if (vm.cut == null || size == IntSize.Zero) return
        val r = contentBounds()
        val s = scaleFor(r.width, r.height)
        val b = boardRect
        scale = s
        offset = Offset(b.left + (b.width - r.width * s) / 2 - r.left * s, b.top + (b.height - r.height * s) / 2 - r.top * s)
        clampTray()
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
        val members = vm.groupOf(id)
        vm.bringToFront(members)
        dragStart = toWorld(p)
        dragDelta = Offset.Zero
        dragMembers = members.toSet()
        dragId = id
    }

    fun beginDragFromTray(id: Int, p: Offset) {
        val cut = vm.cut ?: return
        val w = toWorld(p)
        vm.takeFromTray(id, w.x - cut.cellW / 2, w.y - cut.cellH / 2)
        dragStart = w
        dragDelta = Offset.Zero
        dragMembers = setOf(id)
        dragId = id
        clampTray()
    }

    private fun snapTolerance(): Float {
        val cut = vm.cut ?: return 0f
        return if (vm.kids) max(cut.unit * 0.45f, 32f * density / scale)
        else max(cut.unit * 0.22f, 20f * density / scale)
    }

    /**
     * Follows the finger. In kids mode the piece drops itself as soon as it is close to home, so a
     * child does not have to let go at exactly the right spot; returns [DropResult.Placed] then.
     */
    fun dragTo(p: Offset): DropResult? {
        val cut = vm.cut ?: return null
        dragDelta = toWorld(p) - dragStart
        overTray = trayRect.contains(p) && dragMembers.size == 1
        if (vm.kids && dragId >= 0) {
            val piece = vm.pieces[dragId]
            if (cut.isNearHome(dragId, piece.x + dragDelta.x, piece.y + dragDelta.y, snapTolerance())) {
                return finish(vm.dropGroup(dragId, dragDelta.x, dragDelta.y, snapTolerance()))
            }
        }
        return null
    }

    /** Ends a drag with the finger at [p]. */
    fun endDrag(p: Offset): DropResult {
        val id = dragId
        if (id < 0 || vm.cut == null) return finish(DropResult.None)
        if (trayRect.contains(p) && dragMembers.size == 1) {
            vm.returnToTray(id, trayInsertIndex(p))
            return finish(DropResult.None)
        }
        val result = vm.dropGroup(id, dragDelta.x, dragDelta.y, snapTolerance())
        // Young children lose track of pieces left on the table: anything not placed goes back.
        if (vm.kids && result != DropResult.Placed) vm.returnToTray(id, 0)
        return finish(result)
    }

    private fun finish(result: DropResult): DropResult {
        if (result == DropResult.Placed) {
            flashIds = dragMembers.toList()
            flashKey++
        }
        dragId = -1
        dragMembers = emptySet()
        dragDelta = Offset.Zero
        overTray = false
        clampTray()
        return result
    }

    fun pan(delta: Offset) {
        offset += delta
    }

    fun transform(centroid: Offset, pan: Offset, zoom: Float) {
        val fit = fitScale()
        val bounds = contentBounds()
        val minScale = min(fit * 0.5f, scaleFor(bounds.width, bounds.height) * 0.8f)
        val newScale = (scale * zoom).coerceIn(minScale, fit * 8f)
        val z = newScale / scale
        offset = (offset - centroid) * z + centroid + pan
        scale = newScale
    }

    fun scrollTray(delta: Float) {
        trayScroll += delta
        clampTray()
    }

    /** Scrolls the tray so piece [id] is in the middle, if it is in the tray. */
    fun scrollTrayTo(id: Int) {
        val index = vm.trayItems().indexOf(id)
        if (index < 0) return
        trayScroll = index * slot - trayLength / 2 + slot / 2
        clampTray()
    }

    fun clampTray() {
        val max = max(0f, vm.trayItems().size * slot - trayLength)
        trayScroll = trayScroll.coerceIn(0f, max)
    }
}
