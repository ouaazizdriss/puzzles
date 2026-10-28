package com.driss.puzzles.ui.game

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
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
import kotlin.random.Random

/** Position of a piece's cell origin in world units (pixels of the source bitmap). */
data class PieceState(val x: Float, val y: Float, val inTray: Boolean, val placed: Boolean)

class GameViewModel(
    private val container: AppContainer,
    val imageId: String,
    private val target: Int,
    resume: Boolean,
) : ViewModel() {
    val image = container.repository.find(imageId)

    var loading by mutableStateOf(true)
        private set
    var failed by mutableStateOf(false)
        private set
    /** Changes when a new set of pieces is cut (load or restart). */
    var cut by mutableStateOf<PuzzleCut?>(null)
        private set
    var sprites: List<PieceSprite> = emptyList()
        private set
    var ghost: ImageBitmap? = null
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

    var showGhost by mutableStateOf(false)
    var edgesOnly by mutableStateOf(false)
    var tableColor by mutableIntStateOf(container.prefs.tableColor)
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
        sprites = withContext(Dispatchers.Default) { PieceRenderer.render(bitmap, newCut) }
        return newCut
    }

    private suspend fun newGame(bitmap: Bitmap, rows: Int, cols: Int) {
        val newCut = cutPieces(bitmap, rows, cols, Random.nextLong())
        pieces.clear()
        pieces.addAll(List(newCut.count) { PieceState(0f, 0f, inTray = true, placed = false) })
        tray.clear()
        tray.addAll((0 until newCut.count).shuffled())
        loose.clear()
        placedCount = 0
        moves = 0
        elapsedMs = 0
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
            pieces.add(
                if (p.placed) PieceState(newCut.homeX(id), newCut.homeY(id), inTray = false, placed = true)
                else PieceState(p.x * sx, p.y * sy, p.inTray, placed = false),
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

    fun bringToFront(id: Int) {
        if (loose.lastOrNull() == id) return
        loose.remove(id)
        loose.add(id)
    }

    fun takeFromTray(id: Int, x: Float, y: Float) {
        tray.remove(id)
        pieces[id] = pieces[id].copy(x = x, y = y, inTray = false)
        loose.remove(id)
        loose.add(id)
    }

    /** Drops a loose piece at ([x], [y]). Returns true if it snapped into place. */
    fun drop(id: Int, x: Float, y: Float, tolerance: Float): Boolean {
        val c = cut ?: return false
        moves++
        if (c.isNearHome(id, x, y, tolerance)) {
            pieces[id] = PieceState(c.homeX(id), c.homeY(id), inTray = false, placed = true)
            loose.remove(id)
            placedCount++
            if (placedCount == c.count) complete() else scheduleSave()
            return true
        }
        pieces[id] = pieces[id].copy(x = x, y = y)
        bringToFront(id)
        scheduleSave()
        return false
    }

    /** Puts a piece back in the tray at [visibleIndex] of the currently shown tray items. */
    fun returnToTray(id: Int, visibleIndex: Int) {
        loose.remove(id)
        pieces[id] = pieces[id].copy(inTray = true)
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

    /** Moves every loose piece back into the tray. */
    fun collectLoose() {
        val ids = loose.toList()
        ids.forEach { pieces[it] = pieces[it].copy(inTray = true) }
        loose.clear()
        tray.addAll(ids)
        scheduleSave()
    }

    fun selectTableColor(index: Int) {
        tableColor = index
        container.prefs.tableColor = index
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
        saveJob?.cancel()
        newBest = container.prefs.recordTime(imageId, c.count, elapsedMs)
        bestMs = container.prefs.bestTime(imageId, c.count)
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
        if (loading || completed || moves == 0) return
        val bitmap = source ?: return
        val snapshot = SavedGame(
            imageId = imageId,
            rows = c.rows,
            cols = c.cols,
            seed = c.seed,
            width = bitmap.width,
            height = bitmap.height,
            pieces = pieces.map { SavedPiece(it.x, it.y, it.inTray, it.placed) },
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
