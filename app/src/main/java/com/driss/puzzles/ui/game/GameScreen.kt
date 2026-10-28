package com.driss.puzzles.ui.game

import android.view.SoundEffectConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.driss.puzzles.appContainer
import com.driss.puzzles.ui.formatDuration
import com.driss.puzzles.ui.theme.TableColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class Mode { None, DragPiece, Pan, Transform, TrayScroll }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(imageId: String, target: Int, resume: Boolean, onExit: () -> Unit, onBack: () -> Unit) {
    val container = appContainer()
    val vm: GameViewModel = viewModel { GameViewModel(container, imageId, target, resume) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.onPause() }

    var menu by remember { mutableStateOf(false) }
    var completionDismissed by remember(vm.cut) { mutableStateOf(false) }
    val density = LocalDensity.current.density
    val board = remember(vm) { BoardState(vm, density) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(vm.image?.title.orEmpty(), maxLines = 1, style = MaterialTheme.typography.titleMedium)
                        vm.cut?.let {
                            Text(
                                "${vm.placedCount}/${it.count} · ${formatDuration(vm.elapsedMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconToggleButton(checked = vm.showGhost, onCheckedChange = { vm.showGhost = it }) {
                        Icon(
                            if (vm.showGhost) Icons.Filled.Image else Icons.Outlined.Image,
                            contentDescription = "Show picture hint",
                        )
                    }
                    IconToggleButton(checked = vm.edgesOnly, onCheckedChange = {
                        vm.edgesOnly = it
                        board.clampTray()
                    }) {
                        Icon(Icons.Filled.BorderOuter, contentDescription = "Edge pieces only")
                    }
                    IconButton(onClick = { board.fit() }) {
                        Icon(Icons.Filled.CenterFocusStrong, contentDescription = "Fit puzzle to screen")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Shuffle tray") }, onClick = { menu = false; vm.shuffleTray() })
                            DropdownMenuItem(
                                text = { Text("Put loose pieces back in tray") },
                                onClick = { menu = false; vm.collectLoose(); board.clampTray() },
                            )
                            DropdownMenuItem(text = { Text("Restart") }, onClick = { menu = false; vm.restart() })
                            HorizontalDivider()
                            Text(
                                "Table color",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TableColors.forEachIndexed { i, color ->
                                    Box(
                                        Modifier
                                            .size(32.dp)
                                            .background(color, CircleShape)
                                            .border(
                                                if (i == vm.tableColor) 3.dp else 1.dp,
                                                if (i == vm.tableColor) MaterialTheme.colorScheme.primary else Color.Gray,
                                                CircleShape,
                                            )
                                            .clickable { vm.selectTableColor(i) },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                vm.failed -> Text("This puzzle could not be loaded.")
                vm.loading || vm.cut == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Cutting pieces…", modifier = Modifier.padding(top = 12.dp))
                }
                else -> Board(vm, board)
            }
        }
    }

    if (vm.completed && !completionDismissed) {
        AlertDialog(
            onDismissRequest = { completionDismissed = true },
            title = { Text("Puzzle complete!") },
            text = {
                Column {
                    Text("${vm.cut?.count} pieces in ${formatDuration(vm.elapsedMs)} · ${vm.moves} moves")
                    Text(
                        if (vm.newBest) "New best time!" else "Best time: ${vm.bestMs?.let(::formatDuration) ?: "—"}",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = { Button(onClick = onExit) { Text("More puzzles") } },
            dismissButton = { TextButton(onClick = { vm.restart() }) { Text("Play again") } },
        )
    }
}

@Composable
private fun Board(vm: GameViewModel, board: BoardState) {
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val onSnap by rememberUpdatedState {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        view.playSoundEffect(SoundEffectConstants.CLICK)
    }
    val table = TableColors[vm.tableColor.coerceIn(TableColors.indices)]
    val trayColor = if (table.luminance() > 0.5f) Color.Black.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.30f)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = maxWidth > maxHeight
        val thickness = with(LocalDensity.current) { (if (min(maxWidth.value, maxHeight.value) >= 600f) 120.dp else 96.dp).toPx() }
        val size = IntSize(constraints.maxWidth, constraints.maxHeight)
        LaunchedEffect(size, side, thickness, vm.cut) { board.layout(size, side, thickness) }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(board) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        val inTray = board.trayRect.contains(start)
                        val trayPiece = if (inTray) board.trayItemAt(start) else null
                        var mode = Mode.None
                        if (!inTray) {
                            board.hitLoose(start)?.let {
                                board.beginDrag(it, start)
                                mode = Mode.DragPiece
                            }
                        }
                        var pointerId = down.id
                        var last = start
                        var total = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2 && mode != Mode.DragPiece) mode = Mode.Transform
                            val primary = pressed.firstOrNull { it.id == pointerId } ?: pressed.first().also { pointerId = it.id }
                            when (mode) {
                                Mode.Transform -> board.transform(
                                    event.calculateCentroid(useCurrent = true),
                                    event.calculatePan(),
                                    event.calculateZoom(),
                                )
                                Mode.DragPiece -> board.dragTo(primary.position)
                                Mode.Pan -> board.pan(primary.positionChange())
                                Mode.TrayScroll -> {
                                    val d = primary.positionChange()
                                    board.scrollTray(-(if (board.trayAtSide) d.y else d.x))
                                }
                                Mode.None -> {
                                    total += primary.positionChange()
                                    if (total.getDistance() > slop) {
                                        mode = if (inTray) {
                                            val alongTray = if (board.trayAtSide) abs(total.y) > abs(total.x) else abs(total.x) > abs(total.y)
                                            if (trayPiece != null && !alongTray) {
                                                board.beginDragFromTray(trayPiece, primary.position)
                                                Mode.DragPiece
                                            } else {
                                                Mode.TrayScroll
                                            }
                                        } else {
                                            Mode.Pan
                                        }
                                    }
                                }
                            }
                            last = primary.position
                            event.changes.forEach { it.consume() }
                        }
                        if (mode == Mode.DragPiece && board.endDrag(last)) onSnap()
                    }
                },
        ) {
            drawRect(table)
            drawPuzzle(vm, board)
            drawTray(vm, board, if (board.overTray) trayColor.copy(alpha = trayColor.alpha + 0.15f) else trayColor)
            board.dragId.takeIf { it >= 0 }?.let { drawDragged(vm, board, it) }
        }
    }
}

private val ShadowFilter = ColorFilter.tint(Color.Black)

private fun DrawScope.drawPuzzle(vm: GameViewModel, board: BoardState) {
    val cut = vm.cut ?: return
    val pad = cut.pad
    withTransform({
        translate(board.offset.x, board.offset.y)
        scale(board.scale, board.scale, pivot = Offset.Zero)
    }) {
        val frame = Size(cut.width, cut.height)
        drawRect(Color.Black.copy(alpha = 0.18f), size = frame)
        drawRect(Color.White.copy(alpha = 0.35f), size = frame, style = Stroke(width = 1.5f / board.scale))
        vm.ghost?.takeIf { vm.showGhost }?.let {
            drawImage(it, dstSize = IntSize(cut.width.roundToInt(), cut.height.roundToInt()), alpha = 0.3f)
        }
        for (id in vm.pieces.indices) {
            val p = vm.pieces[id]
            if (p.placed) drawImage(vm.sprites[id].image, Offset(p.x - pad, p.y - pad))
        }
        val shadow = cut.unit * 0.025f
        for (id in vm.loose) {
            if (id == board.dragId) continue
            val p = vm.pieces[id]
            val sprite = vm.sprites[id]
            drawImage(sprite.shadow, Offset(p.x - pad + shadow, p.y - pad + shadow), alpha = 0.35f, colorFilter = ShadowFilter)
            drawImage(sprite.image, Offset(p.x - pad, p.y - pad))
        }
    }
}

private fun DrawScope.drawDragged(vm: GameViewModel, board: BoardState, id: Int) {
    val cut = vm.cut ?: return
    val pad = cut.pad
    val sprite = vm.sprites[id]
    val pos = board.dragPos
    withTransform({
        translate(board.offset.x, board.offset.y)
        scale(board.scale, board.scale, pivot = Offset.Zero)
    }) {
        val lift = max(cut.unit * 0.06f, 8f / board.scale)
        drawImage(sprite.shadow, Offset(pos.x - pad + lift, pos.y - pad + lift), alpha = 0.4f, colorFilter = ShadowFilter)
        drawImage(sprite.image, Offset(pos.x - pad, pos.y - pad))
    }
}

private fun DrawScope.drawTray(vm: GameViewModel, board: BoardState, color: Color) {
    val rect = board.trayRect
    drawRect(color, rect.topLeft, rect.size)
    val items = vm.trayItems()
    val slot = board.slot
    val length = if (board.trayAtSide) rect.height else rect.width
    clipRect(rect.left, rect.top, rect.right, rect.bottom) {
        items.forEachIndexed { i, id ->
            val along = i * slot - board.trayScroll
            if (along + slot < 0 || along > length) return@forEachIndexed
            val image = vm.sprites[id].image
            val s = slot * 0.9f / max(image.width, image.height)
            val w = (image.width * s).roundToInt()
            val h = (image.height * s).roundToInt()
            val cx = if (board.trayAtSide) rect.left + slot / 2 else rect.left + along + slot / 2
            val cy = if (board.trayAtSide) rect.top + along + slot / 2 else rect.top + slot / 2
            drawImage(
                image,
                dstOffset = IntOffset((cx - w / 2f).roundToInt(), (cy - h / 2f).roundToInt()),
                dstSize = IntSize(w, h),
            )
        }
    }
}
