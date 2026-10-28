package com.driss.puzzles.ui.crop

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.driss.puzzles.AppContainer
import com.driss.puzzles.appContainer
import com.driss.puzzles.data.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class CropAspect(val label: String, val ratio: Float?) {
    Original("Original", null),
    Square("1:1", 1f),
    Landscape("4:3", 4f / 3f),
    Portrait("3:4", 3f / 4f),
    Wide("16:9", 16f / 9f),
}

class CropViewModel(private val container: AppContainer, uri: Uri) : ViewModel() {
    var image by mutableStateOf<ImageBitmap?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    private var source: Bitmap? = null

    init {
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { ImageIO.decodeUri(container.context, uri, ImageIO.CROP_MAX_SIDE) }
                source = bitmap
                image = bitmap.asImageBitmap()
            } catch (e: Exception) {
                failed = true
            }
        }
    }

    /** Crops [rect] (source pixels) and stores it as a new user puzzle. */
    fun save(rect: android.graphics.Rect, title: String, onSaved: (String) -> Unit) {
        val src = source ?: return
        saving = true
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                val cropped = Bitmap.createBitmap(src, rect.left, rect.top, rect.width(), rect.height())
                ImageIO.scaleDown(cropped, ImageIO.PUZZLE_MAX_SIDE)
            }
            val saved = container.repository.addUserPhoto(bitmap, title.ifBlank { "My photo" })
            onSaved(saved.id)
        }
    }
}

/** Pan/zoom of an image under a fixed crop frame. The image always covers the frame. */
private class CropState(val imageW: Float, val imageH: Float, val frame: Rect) {
    private val minScale = max(frame.width / imageW, frame.height / imageH)
    var scale by mutableFloatStateOf(minScale)
    var offset by mutableStateOf(Offset(frame.center.x - imageW * minScale / 2, frame.center.y - imageH * minScale / 2))

    fun transform(centroid: Offset, pan: Offset, zoom: Float) {
        val newScale = (scale * zoom).coerceIn(minScale, minScale * 6f)
        val z = newScale / scale
        val o = (offset - centroid) * z + centroid + pan
        scale = newScale
        offset = Offset(
            o.x.coerceIn(frame.right - imageW * newScale, frame.left),
            o.y.coerceIn(frame.bottom - imageH * newScale, frame.top),
        )
    }

    fun sourceRect(): android.graphics.Rect {
        val left = ((frame.left - offset.x) / scale).roundToInt().coerceIn(0, imageW.toInt() - 1)
        val top = ((frame.top - offset.y) / scale).roundToInt().coerceIn(0, imageH.toInt() - 1)
        val right = ((frame.right - offset.x) / scale).roundToInt().coerceIn(left + 1, imageW.toInt())
        val bottom = ((frame.bottom - offset.y) / scale).roundToInt().coerceIn(top + 1, imageH.toInt())
        return android.graphics.Rect(left, top, right, bottom)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(uri: Uri, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val container = appContainer()
    val vm: CropViewModel = viewModel { CropViewModel(container, uri) }
    var aspect by rememberSaveable { mutableStateOf(CropAspect.Original) }
    var title by rememberSaveable { mutableStateOf("") }
    var cropState by remember { mutableStateOf<CropState?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New puzzle") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    TextButton(
                        enabled = cropState != null && !vm.saving,
                        onClick = { cropState?.let { vm.save(it.sourceRect(), title, onSaved) } },
                    ) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                val image = vm.image
                when {
                    vm.failed -> Text("This image could not be opened.")
                    image == null || vm.saving -> CircularProgressIndicator()
                    else -> CropArea(image, aspect) { cropState = it }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CropAspect.entries.forEach {
                    FilterChip(selected = aspect == it, onClick = { aspect = it }, label = { Text(it.label) })
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(40) },
                label = { Text("Name") },
                placeholder = { Text("My photo") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

@Composable
private fun CropArea(image: ImageBitmap, aspect: CropAspect, onState: (CropState) -> Unit) {
    val margin = with(LocalDensity.current) { 16.dp.toPx() }
    val stroke = with(LocalDensity.current) { 2.dp.toPx() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val state = remember(image, aspect, boxW, boxH) {
            val ratio = aspect.ratio ?: (image.width.toFloat() / image.height)
            val availW = boxW - 2 * margin
            val availH = boxH - 2 * margin
            val fw = min(availW, availH * ratio)
            val fh = fw / ratio
            val frame = Rect(Offset((boxW - fw) / 2, (boxH - fh) / 2), Size(fw, fh))
            CropState(image.width.toFloat(), image.height.toFloat(), frame)
        }
        SideEffect { onState(state) }
        Canvas(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(state) {
                    detectTransformGestures { centroid, pan, zoom, _ -> state.transform(centroid, pan, zoom) }
                },
        ) {
            drawImage(
                image,
                dstOffset = IntOffset(state.offset.x.roundToInt(), state.offset.y.roundToInt()),
                dstSize = IntSize(
                    (image.width * state.scale).roundToInt(),
                    (image.height * state.scale).roundToInt(),
                ),
            )
            val f = state.frame
            val dim = Color.Black.copy(alpha = 0.6f)
            drawRect(dim, Offset.Zero, Size(size.width, f.top))
            drawRect(dim, Offset(0f, f.bottom), Size(size.width, size.height - f.bottom))
            drawRect(dim, Offset(0f, f.top), Size(f.left, f.height))
            drawRect(dim, Offset(f.right, f.top), Size(size.width - f.right, f.height))
            val guide = Color.White.copy(alpha = 0.35f)
            for (i in 1..2) {
                val x = f.left + f.width * i / 3
                val y = f.top + f.height * i / 3
                drawLine(guide, Offset(x, f.top), Offset(x, f.bottom))
                drawLine(guide, Offset(f.left, y), Offset(f.right, y))
            }
            drawRect(Color.White, f.topLeft, f.size, style = Stroke(stroke))
        }
    }
}
