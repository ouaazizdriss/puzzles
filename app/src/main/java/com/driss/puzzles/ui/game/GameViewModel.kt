package com.driss.puzzles.ui.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driss.puzzles.AppContainer
import com.driss.puzzles.data.SavedGame
import com.driss.puzzles.data.SavedPiece
import com.driss.puzzles.engine.PieceRenderer
import com.driss.puzzles.engine.PieceSprite
import com.driss.puzzles.engine.PuzzleCut
import com.driss.puzzles.engine.PuzzleGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Position of a piece's cell origin in world units (pixels of the source bitmap). Loose pieces that
 * have been joined share a [group] id and keep their correct relative positions.
 */
data class PieceState(val x: Float, val y: Float, val inTray: Boolean, val placed: Boolean, val group: Int)

enum class DropResult { None, Joined, Placed }

class GameViewModel(
    private val container: AppContainer,
    val imageId: String,
    private val target: Int,
    resume: Boolean,
) : ViewModel() {
    val image = container.repository.find(imageId)
    /** Kids mode: big forgiving snaps, outlines on the board, no zoom, no timer. */
    val kids = image?.isKids == true

    var loading by mutableStateOf(true)
        private set
    var failed by mutableStateOf(false)
        private set
    /** Changes when a new set of pieces is cut (load or restart). */
    var cut by mutableStateOf<PuzzleCut?>(null)
        private set
    var sprites: List<PieceSprite> = emptyList()
        private set
    var outlines: List<Path> = emptyList()
        private set
    var ghost: ImageBitmap? = null
        private set
    /** Average color of each piece, for sorting the tray. */
    private var colors = IntArray(0)

    /**
     * Every placed piece painted into one picture, so the board draws a single image instead of one per
     * placed piece. [placedVersion] changes whenever it is repainted.
     */
    private var placedBitmap: Bitmap? = null
    var placedImage: ImageBitmap? = null
        private set
    var placedVersion by mutableIntStateOf(0)
        private set

    /** Indexed by piece id. */
    val pieces = mutableStateListOf<PieceState>()
    /** Piece ids in the tray, in display order. */
    val tray = mutableStateListOf<Int>()
    /** Loose (unplaced, out of tray) piece ids, bottom to top. */
    val loose = mutableStateListOf<Int>()

    var placedCount by mutableIntStateOf(0)
        private set
    var moves by mutableIntStateOf(0)
        private set
    var elapsedMs by mutableLongStateOf(0L)
        private set
    var completed by mutableStateOf(false)
        private set
    var newBest by mutableStateOf(false)
        private set
    var bestMs by mutableStateOf<Long?>(null)
        private set
    /** Piece currently pointed out by the hint button, or -1. */
    var hintId by mutableIntStateOf(-1)
        private set
    var hintsUsed by mutableIntStateOf(0)
        private set

    var showGhost by mutableStateOf(kids)
    var ghostAlpha by mutableFloatStateOf(container.prefs.ghostAlpha)
        private set
    var showPreview by mutableStateOf(container.prefs.previewOn)
        private set
    /** Stars collected in Kids mode, including the one for this puzzle once it is finished. */
    var starsTotal by mutableIntStateOf(0)
        private set
    var edgesOnly by mutableStateOf(false)
    var tableColor by mutableIntStateOf(container.prefs.tableColor)
        private set
    var soundOn by mutableStateOf(container.prefs.soundOn)
        private set

    private var running = false
    private var source: Bitmap? = null
    private var saveJob: Job? = null

    init {
        viewModelScope.launch { load(resume) }
        viewModelScope.launch { tick() }
    }

    private suspend fun load(resume: Boolean) {
        val img = image
        val bitmap = img?.let { runCatching { container.repository.loadBitmap(it) }.getOrNull() }
        if (bitmap == null) {
            failed = true
            loading = false
            return
        }
        source = bitmap
        ghost = bitmap.asImageBitmap()
        val saved = if (resume) container.saves.load(imageId) else null
        if (saved != null && saved.pieces.size == saved.rows * saved.cols) {
            restore(bitmap, saved)
        } else {
            val grid = PuzzleGrid.forTarget(bitmap.width, bitmap.height, target)
            newGame(bitmap, grid.rows, grid.cols)
        }
        loading = false
    }

    private suspend fun cutPieces(bitmap: Bitmap, rows: Int, cols: Int, seed: Long): PuzzleCut {
        val newCut = PuzzleCut(bitmap.width.toFloat(), bitmap.height.toFloat(), rows, cols, seed)
        withContext(Dispatchers.Default) {
            sprites = PieceRenderer.render(bitmap, newCut)
            outlines = PieceRenderer.outlines(newCut)
            colors = PieceRenderer.averageColors(bitmap, newCut)
        }
        val layer = placedBitmap?.takeIf { it.width == bitmap.width && it.height == bitmap.height }
            ?: Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        layer.eraseColor(Color.TRANSPARENT)
        placedBitmap = layer
        placedImage = layer.asImageBitmap()
        placedVersion++
        return newCut
    }

    private suspend fun newGame(bitmap: Bitmap, rows: Int, cols: Int) {
        val newCut = cutPieces(bitmap, rows, cols, Random.nextLong())
        pieces.clear()
        pieces.addAll(List(newCut.count) { PieceState(0f, 0f, inTray = true, placed = false, group = it) })
        tray.clear()
        tray.addAll((0 until newCut.count).shuffled())
        loose.clear()
        placedCount = 0
        moves = 0
        elapsedMs = 0
        hintId = -1
        hintsUsed = 0
        completed = false
        newBest = false
        cut = newCut
    }

    private suspend fun restore(bitmap: Bitmap, saved: SavedGame) {
        val newCut = cutPieces(bitmap, saved.rows, saved.cols, saved.seed)
        // The bitmap may decode at a different size than when saved (e.g. a changed max side).
        val sx = bitmap.width.toFloat() / saved.width
        val sy = bitmap.height.toFloat() / saved.height
        pieces.clear()
        saved.pieces.forEachIndexed { id, p ->
            val group = if (p.group in saved.pieces.indices) p.group else id
            pieces.add(
                if (p.placed) PieceState(newCut.homeX(id), newCut.homeY(id), inTray = false, placed = true, group = id)
                else PieceState(p.x * sx, p.y * sy, p.inTray, placed = false, group = if (p.inTray) id else group),
            )
        }
        tray.clear()
        tray.addAll(saved.tray.filter { it in pieces.indices && pieces[it].inTray })
        loose.clear()
        loose.addAll(saved.loose.filter { it in pieces.indices && !pieces[it].inTray && !pieces[it].placed })
        // Repair anything the lists missed so no piece is ever lost.
        pieces.forEachIndexed { id, p ->
            if (p.inTray && id !in tray) tray.add(id)
            if (!p.inTray && !p.placed && id !in loose) loose.add(id)
        }
        placedCount = pieces.count { it.placed }
        paintPlaced(newCut, pieces.indices.filter { pieces[it].placed })
        moves = saved.moves
        elapsedMs = saved.elapsedMs
        completed = false
        cut = newCut
    }

    private suspend fun tick() {
        var last = SystemClock.elapsedRealtime()
        while (true) {
            delay(250)
            val now = SystemClock.elapsedRealtime()
            if (running && !loading && !completed) elapsedMs += now - last
            last = now
        }
    }

    fun onResume() {
        running = true
    }

    fun onPause() {
        running = false
        persist()
    }

    fun trayItems(): List<Int> =
        if (edgesOnly) cut?.let { c -> tray.filter { c.isBorder(it) } } ?: tray else tray

    /** Loose pieces joined with [id] (including itself), in drawing order. */
    fun groupOf(id: Int): List<Int> {
        val p = pieces[id]
        if (p.inTray || p.placed) return listOf(id)
        return loose.filter { pieces[it].group == p.group }
    }

    fun bringToFront(ids: List<Int>) {
        if (loose.takeLast(ids.size) == ids) return
        loose.removeAll(ids)
        loose.addAll(ids)
    }

    fun takeFromTray(id: Int, x: Float, y: Float) {
        tray.remove(id)
        pieces[id] = pieces[id].copy(x = x, y = y, inTray = false, group = id)
        loose.remove(id)
        loose.add(id)
    }

    /**
     * Moves the group of [grabbed] by ([dx], [dy]). The group locks into the frame if a piece lands near
     * its home, or joins loose neighbours that line up.
     */
    fun dropGroup(grabbed: Int, dx: Float, dy: Float, tolerance: Float): DropResult {
        val c = cut ?: return DropResult.None
        val members = groupOf(grabbed)
        moves++
        if (members.any { c.isNearHome(it, pieces[it].x + dx, pieces[it].y + dy, tolerance) }) {
            members.forEach { pieces[it] = PieceState(c.homeX(it), c.homeY(it), inTray = false, placed = true, group = it) }
            paintPlaced(c, members)
            loose.removeAll(members)
            placedCount += members.size
            if (hintId in members) hintId = -1
            if (placedCount == c.count) complete() else scheduleSave()
            return DropResult.Placed
        }
        members.forEach { pieces[it] = pieces[it].copy(x = pieces[it].x + dx, y = pieces[it].y + dy) }

        var joined = false
        var changed = true
        while (changed) {
            changed = false
            val group = pieces[grabbed].group
            val current = loose.filter { pieces[it].group == group }
            search@ for (m in current) {
                for (n in c.neighbours(m)) {
                    val pn = pieces[n]
                    if (pn.inTray || pn.placed || pn.group == group) continue
                    val pm = pieces[m]
                    val ex = pn.x - pm.x - (c.homeX(n) - c.homeX(m))
                    val ey = pn.y - pm.y - (c.homeY(n) - c.homeY(m))
                    if (abs(ex) <= tolerance && abs(ey) <= tolerance) {
                        current.forEach {
                            pieces[it] = pieces[it].copy(x = pieces[it].x + ex, y = pieces[it].y + ey, group = pn.group)
                        }
                        joined = true
                        changed = true
                        break@search
                    }
                }
            }
        }
        bringToFront(groupOf(grabbed))
        scheduleSave()
        return if (joined) DropResult.Joined else DropResult.None
    }

    private fun paintPlaced(c: PuzzleCut, ids: List<Int>) {
        val layer = placedBitmap ?: return
        if (ids.isEmpty()) return
        val canvas = Canvas(layer)
        ids.forEach { canvas.drawBitmap(sprites[it].bitmap, c.homeX(it) - c.pad, c.homeY(it) - c.pad, null) }
        placedVersion++
    }

    /** Puts a single loose piece back in the tray at [visibleIndex] of the currently shown tray items. */
    fun returnToTray(id: Int, visibleIndex: Int) {
        loose.remove(id)
        pieces[id] = pieces[id].copy(inTray = true, group = id)
        val visible = trayItems()
        val index = if (visibleIndex < visible.size) tray.indexOf(visible[visibleIndex.coerceAtLeast(0)]) else tray.size
        tray.add(index.coerceIn(0, tray.size), id)
        scheduleSave()
    }

    fun shuffleTray() {
        val shuffled = tray.shuffled()
        tray.clear()
        tray.addAll(shuffled)
    }

    /** Corners, then edge pieces, then the middle. */
    fun sortTrayEdgesFirst() {
        val c = cut ?: return
        val sorted = tray.sortedBy { c.edgeRank(it) }
        tray.clear()
        tray.addAll(sorted)
    }

    /** Groups the tray by color: hues around the color wheel, then greys from light to dark. */
    fun sortTrayByColor() {
        if (colors.isEmpty()) return
        val hsv = FloatArray(3)
        val key = { id: Int ->
            Color.colorToHSV(colors[id], hsv)
            if (hsv[1] < 0.18f || hsv[2] < 0.18f) 400f + (1f - hsv[2]) * 100f else hsv[0]
        }
        val sorted = tray.sortedBy(key)
        tray.clear()
        tray.addAll(sorted)
    }

    /** Pieces that "spread" would lay out: the tray plus loose pieces not joined to others. */
    fun spreadable(): List<Int> =
        tray + loose.filter { id -> loose.none { it != id && pieces[it].group == pieces[id].group } }

    /**
     * Lays the tray and the single loose pieces out on the table around the frame, nearest spots first,
     * without covering joined groups. Running it again (e.g. after rotating) re-arranges them. [boardAspect] (width / height of the visible board) shapes the layout so it
     * fits the screen: tall screens get more pieces above and below the frame.
     */
    fun spreadTray(boardAspect: Float) {
        val c = cut ?: return
        val ids = spreadable().shuffled()
        if (ids.isEmpty()) return
        val moving = ids.toSet()
        val gap = c.pad * 0.6f
        val stepX = c.cellW + 2 * c.pad - gap
        val stepY = c.cellH + 2 * c.pad - gap
        val taken = loose.filter { it !in moving }.map { pieces[it].x + c.cellW / 2 to pieces[it].y + c.cellH / 2 }
        val reach = ceil(sqrt(ids.size.toFloat())).toInt() + max(c.rows, c.cols)
        val spots = ArrayList<Triple<Float, Float, Float>>()
        for (i in -reach..reach) for (j in -reach..reach) {
            val x = c.width / 2 + i * stepX - c.cellW / 2
            val y = c.height / 2 + j * stepY - c.cellH / 2
            // Distance from the frame, with knobs counted, so no piece overlaps the frame.
            val dx = max(max(-(x + c.cellW + c.pad), x - c.pad - c.width), 0f)
            val dy = max(max(-(y + c.cellH + c.pad), y - c.pad - c.height), 0f)
            if (dx <= 0f && dy <= 0f) continue
            val cx = x + c.cellW / 2
            val cy = y + c.cellH / 2
            if (taken.any { abs(it.first - cx) < stepX * 0.8f && abs(it.second - cy) < stepY * 0.8f }) continue
            // Rings shaped like the screen around the frame's center, so the pieces fill the view.
            val ox = cx - c.width / 2
            val oy = cy - c.height / 2
            val score = max(abs(ox) / boardAspect.coerceIn(0.3f, 3f), abs(oy)) + hypot(ox, oy) * 0.001f
            spots.add(Triple(x, y, score))
        }
        spots.sortBy { it.third }
        ids.zip(spots).forEach { (id, spot) ->
            pieces[id] = pieces[id].copy(x = spot.first, y = spot.second, inTray = false, group = id)
            tray.remove(id)
            if (id !in loose) loose.add(id)
        }
        scheduleSave()
    }

    /** Moves every loose piece back into the tray. */
    fun collectLoose() {
        val ids = loose.toList()
        ids.forEach { pieces[it] = pieces[it].copy(inTray = true, group = it) }
        loose.clear()
        tray.addAll(ids)
        scheduleSave()
    }

    /** Points out a piece that is not placed yet: a loose one if any, otherwise one from the tray. */
    fun showHint() {
        val candidates = loose.toList().ifEmpty { trayItems() }.ifEmpty { tray.toList() }
        if (candidates.isEmpty()) return
        hintId = candidates.random()
        hintsUsed++
    }

    fun clearHint() {
        hintId = -1
    }

    fun updateGhostAlpha(value: Float) {
        ghostAlpha = value
        showGhost = true
    }

    fun saveGhostAlpha() {
        container.prefs.ghostAlpha = ghostAlpha
    }

    fun togglePreview() {
        showPreview = !showPreview
        container.prefs.previewOn = showPreview
    }

    fun selectTableColor(index: Int) {
        tableColor = index
        container.prefs.tableColor = index
    }

    fun toggleSound() {
        soundOn = !soundOn
        container.prefs.soundOn = soundOn
    }

    fun restart() {
        val bitmap = source ?: return
        val c = cut ?: return
        saveJob?.cancel()
        loading = true
        viewModelScope.launch {
            container.saves.delete(imageId)
            newGame(bitmap, c.rows, c.cols)
            loading = false
        }
    }

    private fun complete() {
        val c = cut ?: return
        completed = true
        hintId = -1
        saveJob?.cancel()
        newBest = container.prefs.recordTime(imageId, c.count, elapsedMs)
        bestMs = container.prefs.bestTime(imageId, c.count)
        if (kids) starsTotal = container.prefs.addStar(imageId)
        container.appScope.launch { container.saves.delete(imageId) }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(800)
            persist()
        }
    }

    /** Writes the current game to disk (on the app scope, so it finishes even if the screen closes). */
    fun persist() {
        val c = cut ?: return
        // Nothing to keep until a piece has left the tray.
        if (loading || completed || (moves == 0 && loose.isEmpty())) return
        val bitmap = source ?: return
        val snapshot = SavedGame(
            imageId = imageId,
            rows = c.rows,
            cols = c.cols,
            seed = c.seed,
            width = bitmap.width,
            height = bitmap.height,
            pieces = pieces.map { SavedPiece(it.x, it.y, it.inTray, it.placed, it.group) },
            tray = tray.toList(),
            loose = loose.toList(),
            elapsedMs = elapsedMs,
            moves = moves,
            updatedAt = System.currentTimeMillis(),
        )
        container.appScope.launch { container.saves.save(snapshot) }
    }

    override fun onCleared() {
        persist()
    }
}
