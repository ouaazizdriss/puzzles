package com.driss.puzzles.ui.home

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.driss.puzzles.appContainer
import com.driss.puzzles.data.ImageIO
import com.driss.puzzles.data.PuzzleImage
import com.driss.puzzles.data.SaveSummary
import com.driss.puzzles.ui.categoryLabel
import kotlinx.coroutines.launch

private val TABS = listOf("Puzzles", "Kids", "My photos", "Saved")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (String) -> Unit, onCrop: (Uri) -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userPhotos by container.repository.userPhotos.collectAsStateWithLifecycle()
    val saves by container.saves.summaries.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sourceMenu by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<PuzzleImage?>(null) }
    val locked by container.prefs.kidsLock.collectAsStateWithLifecycle()
    val stars by container.prefs.stars.collectAsStateWithLifecycle()
    var showGate by remember { mutableStateOf(false) }
    var showStickers by remember { mutableStateOf(false) }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onCrop)
    }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(onCrop)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (locked) "Kids puzzles" else "Puzzles") },
                actions = {
                    if (locked) {
                        IconButton(onClick = { showGate = true }) {
                            Icon(Icons.Outlined.Lock, contentDescription = "Unlock (grown-ups)")
                        }
                    } else {
                        IconButton(onClick = {
                            container.prefs.setKidsLock(true)
                            tab = 1
                        }) {
                            Icon(Icons.Outlined.ChildCare, contentDescription = "Lock to Kids puzzles")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!locked) Box {
                ExtendedFloatingActionButton(
                    onClick = { sourceMenu = true },
                    icon = { Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null) },
                    text = { Text("From photo") },
                )
                DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Choose from gallery") },
                        leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                        onClick = {
                            sourceMenu = false
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Take a photo") },
                        leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                        onClick = {
                            sourceMenu = false
                            val uri = ImageIO.newCameraUri(context)
                            cameraUri = uri
                            try {
                                takePhoto.launch(uri)
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, "No camera app found", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!locked) {
                PrimaryTabRow(selectedTabIndex = tab) {
                    TABS.forEachIndexed { i, label ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                    }
                }
            }
            when (if (locked) 1 else tab) {
                0 -> PuzzleGrid {
                    container.repository.builtIn.filterNot { it.isKids }.groupBy { it.category }.forEach { (category, images) ->
                        header(categoryLabel(category))
                        cards(images, saves, onOpen)
                    }
                }
                1 -> PuzzleGrid {
                    item(key = "stars", span = { GridItemSpan(maxLineSpan) }) {
                        StarsCard(stars.values.sum(), onClick = { showStickers = true })
                    }
                    container.repository.builtIn.filter { it.isKids }.groupBy { it.group }.forEach { (group, images) ->
                        header(kidsGroupLabel(group))
                        cards(images, saves, onOpen, stars = stars)
                    }
                }
                2 -> if (userPhotos.isEmpty()) {
                    EmptyState("Turn any photo into a puzzle.\nTap “From photo” to start.")
                } else {
                    PuzzleGrid {
                        cards(userPhotos, saves, onOpen, onLongClick = { toDelete = it })
                    }
                }
                else -> {
                    val all = container.repository.builtIn + userPhotos
                    val inProgress = all.filter { it.id in saves }.sortedByDescending { saves[it.id]?.updatedAt }
                    if (inProgress.isEmpty()) {
                        EmptyState("No puzzles in progress.\nGames are saved automatically when you leave them.")
                    } else {
                        PuzzleGrid { cards(inProgress, saves, onOpen) }
                    }
                }
            }
        }
    }

    if (showGate) {
        GrownUpGate(
            onUnlock = {
                showGate = false
                container.prefs.setKidsLock(false)
            },
            onDismiss = { showGate = false },
        )
    }

    if (showStickers) {
        StickersDialog(
            container.repository.builtIn.filter { (stars[it.id] ?: 0) > 0 },
            stars,
            onDismiss = { showStickers = false },
        )
    }

    toDelete?.let { image ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete “${image.title}”?") },
            text = { Text("The photo puzzle and any game in progress will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    scope.launch {
                        container.saves.delete(image.id)
                        container.repository.deleteUserPhoto(image.id)
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PuzzleGrid(content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
        content = content,
    )
}

private fun LazyGridScope.header(text: String) {
    item(key = "header_$text", span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )
    }
}

private fun LazyGridScope.cards(
    images: List<PuzzleImage>,
    saves: Map<String, SaveSummary>,
    onOpen: (String) -> Unit,
    onLongClick: ((PuzzleImage) -> Unit)? = null,
    stars: Map<String, Int> = emptyMap(),
) {
    items(images, key = { it.id }) { image ->
        PuzzleCard(
            image, saves[image.id], stars[image.id] ?: 0,
            onClick = { onOpen(image.id) }, onLongClick = onLongClick?.let { { it(image) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PuzzleCard(image: PuzzleImage, progress: SaveSummary?, stars: Int, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(context).data(image.model).size(480).crossfade(true).build(),
                contentDescription = image.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
            )
            if (progress != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    ProgressBadge(progress)
                }
            }
            if (stars > 0) {
                Surface(
                    color = Color.White.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = StarColor, modifier = Modifier.size(18.dp))
                        if (stars > 1) Text("$stars", style = MaterialTheme.typography.labelMedium, color = Color.DarkGray)
                    }
                }
            }
        }
        Text(
            image.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ProgressBadge(progress: SaveSummary) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(Icons.Outlined.Extension, contentDescription = null, modifier = Modifier.padding(end = 4.dp).size(16.dp))
        Text("${progress.placed}/${progress.total}", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val StarColor = Color(0xFFFFB300)

private fun kidsGroupLabel(group: String?) = when (group) {
    "pictures" -> "Pictures"
    "animals" -> "Animals"
    "numbers" -> "Count with me"
    "letters" -> "Letters"
    "photos" -> "Photos"
    else -> "More puzzles"
}

@Composable
private fun StarsCard(total: Int, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4D6)),
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(enabled = total > 0, onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = StarColor, modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    if (total == 0) "Collect stars!" else "My stars: $total",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF5D4300),
                )
                Text(
                    if (total == 0) "Finish a puzzle to win a star." else "Tap to see your stickers",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF7A5A00),
                )
            }
        }
    }
}

/** Every finished Kids picture as a round sticker, with its stars. */
@Composable
private fun StickersDialog(images: List<PuzzleImage>, stars: Map<String, Int>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("My stickers") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(images, key = { it.id }) { image ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(image.model).size(256).build(),
                                contentDescription = image.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(88.dp).clip(CircleShape),
                            )
                            Surface(
                                color = StarColor,
                                shape = CircleShape,
                                modifier = Modifier.align(Alignment.BottomEnd),
                            ) {
                                Text(
                                    "${stars[image.id] ?: 0}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                )
                            }
                        }
                        Text(image.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private const val HOLD_MS = 3000

/** Asks a grown-up to press and hold for three seconds, which young children rarely manage on purpose. */
@Composable
private fun GrownUpGate(onUnlock: () -> Unit, onDismiss: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
        title = { Text("Grown-ups only") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Press and hold the button for 3 seconds to show all puzzles and photos.", textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()
                                val fill = scope.launch {
                                    progress.animateTo(1f, tween((HOLD_MS * (1f - progress.value)).toInt(), easing = LinearEasing))
                                    onUnlock()
                                }
                                waitForUpOrCancellation()
                                if (progress.value < 1f) {
                                    fill.cancel()
                                    scope.launch { progress.animateTo(0f, tween(250)) }
                                }
                            }
                        },
                ) {
                    CircularProgressIndicator(
                        progress = { progress.value },
                        strokeWidth = 8.dp,
                        modifier = Modifier.size(104.dp),
                    )
                    Text("Hold", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
