package com.driss.puzzles.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.driss.puzzles.appContainer
import com.driss.puzzles.data.PuzzleImage
import com.driss.puzzles.data.SaveSummary
import com.driss.puzzles.engine.PuzzleGrid
import com.driss.puzzles.ui.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(imageId: String, onBack: () -> Unit, onPlay: (id: String, target: Int, resume: Boolean) -> Unit) {
    val container = appContainer()
    val userPhotos by container.repository.userPhotos.collectAsStateWithLifecycle()
    val image = remember(userPhotos) { container.repository.find(imageId) }
    if (image == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val saves by container.saves.summaries.collectAsStateWithLifecycle()
    val options = if (image.isKids) PuzzleGrid.KIDS_OPTIONS else PuzzleGrid.PIECE_OPTIONS
    var target by rememberSaveable {
        mutableIntStateOf((if (image.isKids) container.prefs.lastKidsTarget else container.prefs.lastTarget).takeIf { it in options } ?: options[options.size / 2])
    }
    val play = { resume: Boolean ->
        if (!resume) {
            if (image.isKids) container.prefs.lastKidsTarget = target else container.prefs.lastTarget = target
        }
        onPlay(imageId, target, resume)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(image.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val wide = maxWidth > maxHeight && maxWidth >= 600.dp
            if (wide) {
                Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Preview(image)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        Options(image, target, { target = it }, saves[imageId], play)
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.heightIn(max = 420.dp)) { Preview(image) }
                    Spacer(Modifier.height(20.dp))
                    Column(Modifier.widthIn(max = 560.dp)) {
                        Options(image, target, { target = it }, saves[imageId], play)
                    }
                }
            }
        }
    }
}

@Composable
private fun Preview(image: PuzzleImage) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(image.model).crossfade(true).build(),
        contentDescription = image.title,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .aspectRatio(image.width.toFloat() / image.height)
            .clip(MaterialTheme.shapes.large),
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun Options(
    image: PuzzleImage,
    target: Int,
    onTarget: (Int) -> Unit,
    save: SaveSummary?,
    play: (resume: Boolean) -> Unit,
) {
    val container = appContainer()
    val grid = PuzzleGrid.forTarget(image.width, image.height, target)
    val best = container.prefs.bestTime(image.id, grid.count)

    Text("Pieces", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (if (image.isKids) PuzzleGrid.KIDS_OPTIONS else PuzzleGrid.PIECE_OPTIONS).forEach { option ->
            val count = PuzzleGrid.forTarget(image.width, image.height, option).count
            FilterChip(selected = option == target, onClick = { onTarget(option) }, label = { Text("$count") })
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        if (image.isKids) "Big pieces that snap in easily"
        else "${grid.cols} × ${grid.rows} grid · " + (best?.let { "Best time ${formatDuration(it)}" } ?: "No best time yet"),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))

    if (save != null) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Game in progress", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${save.placed} of ${save.total} pieces placed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { play(true) }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { play(false) }, modifier = Modifier.fillMaxWidth()) {
            Text("New game with ${grid.count} pieces")
        }
    } else {
        Button(onClick = { play(false) }, modifier = Modifier.fillMaxWidth()) { Text("Start · ${grid.count} pieces") }
    }

    image.author?.let {
        Spacer(Modifier.height(24.dp))
        Text(
            "Photo by $it on Unsplash",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
