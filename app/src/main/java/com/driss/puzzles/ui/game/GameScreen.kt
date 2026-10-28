package com.driss.puzzles.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.driss.puzzles.appContainer
import com.driss.puzzles.ui.formatDuration
import com.driss.puzzles.ui.theme.TableColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private enum class Mode { None, DragPiece, Pan, Transform, TrayScroll, Done }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(imageId: String, target: Int, resume: Boolean, onExit: () -> Unit, onBack: () -> Unit) {
    val container = appContainer()
    val vm: GameViewModel = viewModel { GameViewModel(container, imageId, target, resume) }
    val context = LocalContext.current
    val sounds = remember { Sounds(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { sounds.release() } }
    val haptics = LocalHapticFeedback.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.onPause() }

    var menu by remember { mutableStateOf(false) }
    val density = LocalDensity.current.density
    val board = remember(vm) { BoardState(vm, density) }

    // Completion: the seams fade out, confetti falls, then the dialog appears.
    val celebrate = remember(vm.cut) { Animatable(0f) }
    var showDone by remember(vm.cut) { mutableStateOf(false) }
    LaunchedEffect(vm.completed, vm.cut) {
        if (vm.completed) {
            if (vm.soundOn) sounds.win()
            launch {
                delay(1600)
                showDone = true
            }
            celebrate.animateTo(1f, tween(3200, easing = LinearEasing))
        }
    }
    LaunchedEffect(vm.hintId) {
        if (vm.hintId >= 0) {
            board.scrollTrayTo(vm.hintId)
            delay(3500)
            vm.clearHint()
        }
    }

    val onDrop: (DropResult) -> Unit = { result ->
        when (result) {
            DropResult.Placed -> {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                if (vm.soundOn && !vm.completed) sounds.snap()
            }
            DropResult.Joined -> {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (vm.soundOn) sounds.join()
            }
            DropResult.None -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(vm.image?.title.orEmpty(), maxLines = 1, style = MaterialTheme.typography.titleMedium)
                        vm.cut?.let {
                            Text(
                                if (vm.kids) "${vm.placedCount} / ${it.count}"
                                else "${vm.placedCount}/${it.count} · ${formatDuration(vm.elapsedMs)}",
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
                    IconButton(onClick = { vm.showHint() }, enabled = !vm.completed) {
                        Icon(Icons.Outlined.Lightbulb, contentDescription = "Hint")
                    }
                    IconToggleButton(checked = vm.showGhost, onCheckedChange = { vm.showGhost = it }) {
                        Icon(
                            if (vm.showGhost) Icons.Filled.Image else Icons.Outlined.Image,
                            contentDescription = "Show picture hint",
                        )
                    }
                    if (!vm.kids) {
                        IconToggleButton(checked = vm.edgesOnly, onCheckedChange = {
                            vm.edgesOnly = it
                            board.clampTray()
                        }) {
                            Icon(Icons.Filled.BorderOuter, contentDescription = "Edge pieces only")
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (!vm.kids) {
                                DropdownMenuItem(
                                    text = { Text("Fit puzzle to screen") },
                                    leadingIcon = { Icon(Icons.Filled.CenterFocusStrong, contentDescription = null) },
                                    onClick = { menu = false; board.fit() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort tray: edges first") },
                                    onClick = { menu = false; vm.sortTrayEdgesFirst(); board.clampTray() },
                                )
                            }
                            DropdownMenuItem(text = { Text("Shuffle tray") }, onClick = { menu = false; vm.shuffleTray() })
                            if (!vm.kids) {
                                DropdownMenuItem(
                                    text = { Text("Put loose pieces back in tray") },
                                    onClick = { menu = false; vm.collectLoose(); board.clampTray() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (vm.soundOn) "Sound on" else "Sound off") },
                                leadingIcon = {
                                    Icon(
                                        if (vm.soundOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                        contentDescription = null,
                                    )
                                },
                                onClick = { vm.toggleSound() },
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
                else -> Board(vm, board, celebrate.value, onDrop)
            }
        }
    }

    if (vm.completed && showDone) {
        AlertDialog(
            onDismissRequest = { showDone = false },
            title = {
                Text(
                    if (vm.kids) "Well done!" else "Puzzle complete!",
                    textAlign = TextAlign.Center,
                    fontSize = if (vm.kids) 32.sp else MaterialTheme.typography.headlineSmall.fontSize,
                )
            },
            text = {
                if (vm.kids) {
                    Text("You finished the puzzle!", style = MaterialTheme.typography.titleMedium)
                } else {
                    Column {
                        Text("${vm.cut?.count} pieces in ${formatDuration(vm.elapsedMs)} · ${vm.moves} moves")
                        Text(
                            if (vm.newBest) "New best time!" else "Best time: ${vm.bestMs?.let(::formatDuration) ?: "—"}",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = { Button(onClick = onExit) { Text("More puzzles") } },
            dismissButton = { TextButton(onClick = { vm.restart() }) { Text("Play again") } },
        )
    }
}

private class Confetti(val x: Float, val delay: Float, val speed: Float, val size: Float, val color: Color, val spin: Float, val phase: Float)

private val ConfettiColors = listOf(
    Color(0xFFEF476F), Color(0xFFFFD166), Color(0xFF06D6A0), Color(0xFF118AB2), Color(0xFF8338EC), Color(0xFFFF9F1C),
)

@Composable
private fun Board(vm: GameViewModel, board: BoardState, celebrate: Float, onDrop: (DropResult) -> Unit) {
    val onDropState by rememberUpdatedState(onDrop)
    val table = TableColors[vm.tableColor.coerceIn(TableColors.indices)]
    val trayColor = if (table.luminance() > 0.5f) Color.Black.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.30f)

    val flash = remember { Animatable(0f) }
    LaunchedEffect(board.flashKey) {
        if (board.flashKey > 0) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(450))
        }
    }
    val pulse by rememberInfiniteTransition(label = "hint").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "hintPulse",
    )
    val confetti = remember(vm.cut) {
        val rnd = Random(7)
        List(140) {
            Confetti(
                x = rnd.nextFloat(), delay = rnd.nextFloat() * 0.3f, speed = 0.7f + rnd.nextFloat() * 0.6f,
                size = 6f + rnd.nextFloat() * 8f, color = ConfettiColors[rnd.nextInt(ConfettiColors.size)],
                spin = rnd.nextFloat() * 2f - 1f, phase = rnd.nextFloat() * 6.28f,
            )
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = maxWidth > maxHeight
        val large = min(maxWidth.value, maxHeight.value) >= 600f
        val thickness = with(LocalDensity.current) {
            (if (vm.kids) (if (large) 160.dp else 132.dp) else if (large) 120.dp else 96.dp).toPx()
        }
        val size = IntSize(constraints.maxWidth, constraints.maxHeight)
        val dp = LocalDensity.current.density
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
                        if (!inTray && !vm.completed) {
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
                            if (pressed.size >= 2 && !vm.kids && mode != Mode.DragPiece && mode != Mode.Done) mode = Mode.Transform
                            val primary = pressed.firstOrNull { it.id == pointerId } ?: pressed.first().also { pointerId = it.id }
                            when (mode) {
                                Mode.Transform -> board.transform(
                                    event.calculateCentroid(useCurrent = true),
                                    event.calculatePan(),
                                    event.calculateZoom(),
                                )
                                Mode.DragPiece -> board.dragTo(primary.position)?.let {
                                    onDropState(it)
                                    mode = Mode.Done
                                }
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
                                            if (trayPiece != null && !alongTray && !vm.completed) {
                                                board.beginDragFromTray(trayPiece, primary.position)
                                                Mode.DragPiece
                                            } else {
                                                Mode.TrayScroll
                                            }
                                        } else if (vm.kids) {
                                            Mode.Done
                                        } else {
                                            Mode.Pan
                                        }
                                    }
                                }
                                Mode.Done -> Unit
                            }
                            last = primary.position
                            event.changes.forEach { it.consume() }
                        }
                        if (mode == Mode.DragPiece) onDropState(board.endDrag(last))
                    }
                },
        ) {
            drawRect(table)
            drawPuzzle(vm, board, flash.value, pulse, celebrate, dp)
            drawTray(vm, board, if (board.overTray) trayColor.copy(alpha = trayColor.alpha + 0.15f) else trayColor, pulse)
            if (board.dragId >= 0) drawDragged(vm, board)
            if (celebrate > 0f && celebrate < 1f) drawConfetti(confetti, celebrate, dp)
        }
    }
}

private val ShadowFilter = ColorFilter.tint(Color.Black)
private val FlashFilter = ColorFilter.tint(Color.White)
private val HintColor = Color(0xFFFFD54F)

private fun DrawScope.worldTransform(board: BoardState, block: DrawScope.() -> Unit) = withTransform({
    translate(board.offset.x, board.offset.y)
    scale(board.scale, board.scale, pivot = Offset.Zero)
}, block)

private fun DrawScope.drawPuzzle(vm: GameViewModel, board: BoardState, flash: Float, pulse: Float, celebrate: Float, dp: Float) {
    val cut = vm.cut ?: return
    val pad = cut.pad
    worldTransform(board) {
        val frame = Size(cut.width, cut.height)
        drawRect(Color.Black.copy(alpha = 0.18f), size = frame)
        drawRect(Color.White.copy(alpha = 0.35f), size = frame, style = Stroke(width = 1.5f * dp / board.scale))
        vm.ghost?.takeIf { vm.showGhost }?.let {
            drawImage(it, dstSize = IntSize(cut.width.roundToInt(), cut.height.roundToInt()), alpha = if (vm.kids) 0.35f else 0.3f)
        }
        if (vm.kids) {
            // Silhouettes of the missing pieces, so children can match shapes.
            val outline = Stroke(width = 2.5f * dp / board.scale)
            for (id in vm.pieces.indices) {
                if (!vm.pieces[id].placed) drawPath(vm.outlines[id], Color.White.copy(alpha = 0.55f), style = outline)
            }
        }
        for (id in vm.pieces.indices) {
            val p = vm.pieces[id]
            if (p.placed) drawImage(vm.sprites[id].image, Offset(p.x - pad, p.y - pad))
        }
        if (flash > 0f) {
            for (id in board.flashIds) {
                val p = vm.pieces[id]
                drawImage(vm.sprites[id].shadow, Offset(p.x - pad, p.y - pad), alpha = flash * 0.6f, colorFilter = FlashFilter)
            }
        }
        // Finished: fade the seams away by laying the whole picture over the pieces.
        if (vm.completed) {
            vm.ghost?.let { drawImage(it, dstSize = IntSize(cut.width.roundToInt(), cut.height.roundToInt()), alpha = (celebrate * 2.5f).coerceAtMost(1f)) }
        }
        val shadow = cut.unit * 0.025f
        for (id in vm.loose) {
            if (id in board.dragMembers) continue
            val p = vm.pieces[id]
            val sprite = vm.sprites[id]
            drawImage(sprite.shadow, Offset(p.x - pad + shadow, p.y - pad + shadow), alpha = 0.35f, colorFilter = ShadowFilter)
            drawImage(sprite.image, Offset(p.x - pad, p.y - pad))
        }
        val hint = vm.hintId
        if (hint >= 0 && hint < vm.outlines.size) {
            val stroke = Stroke(width = 4f * dp / board.scale)
            val color = HintColor.copy(alpha = pulse)
            drawPath(vm.outlines[hint], color, style = stroke)
            val p = vm.pieces[hint]
            if (!p.inTray && !p.placed) {
                translate(p.x - cut.homeX(hint), p.y - cut.homeY(hint)) { drawPath(vm.outlines[hint], color, style = stroke) }
            }
        }
    }
}

private fun DrawScope.drawDragged(vm: GameViewModel, board: BoardState) {
    val cut = vm.cut ?: return
    val pad = cut.pad
    val d = board.dragDelta
    worldTransform(board) {
        val lift = max(cut.unit * 0.06f, 8f / board.scale)
        for (id in vm.loose) {
            if (id !in board.dragMembers) continue
            val p = vm.pieces[id]
            drawImage(vm.sprites[id].shadow, Offset(p.x + d.x - pad + lift, p.y + d.y - pad + lift), alpha = 0.4f, colorFilter = ShadowFilter)
        }
        for (id in vm.loose) {
            if (id !in board.dragMembers) continue
            val p = vm.pieces[id]
            drawImage(vm.sprites[id].image, Offset(p.x + d.x - pad, p.y + d.y - pad))
        }
    }
}

private fun DrawScope.drawTray(vm: GameViewModel, board: BoardState, color: Color, pulse: Float) {
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
            if (id == vm.hintId) {
                drawCircle(HintColor.copy(alpha = 0.45f * pulse), radius = slot * 0.48f, center = Offset(cx, cy))
            }
            drawImage(
                image,
                dstOffset = IntOffset((cx - w / 2f).roundToInt(), (cy - h / 2f).roundToInt()),
                dstSize = IntSize(w, h),
            )
        }
    }
}

private fun DrawScope.drawConfetti(pieces: List<Confetti>, progress: Float, dp: Float) {
    for (c in pieces) {
        val t = ((progress - c.delay) / 0.7f).coerceIn(0f, 1f)
        if (t <= 0f) continue
        val x = (c.x + 0.04f * sin(t * 12f + c.phase)) * size.width
        val y = (-0.05f + t * 1.15f * c.speed) * size.height
        val alpha = if (t > 0.85f) (1f - t) / 0.15f else 1f
        val s = c.size * dp
        rotate(c.spin * t * 720f, pivot = Offset(x, y)) {
            drawRect(c.color.copy(alpha = alpha), topLeft = Offset(x - s / 2, y - s / 4), size = Size(s, s / 2))
        }
    }
}
