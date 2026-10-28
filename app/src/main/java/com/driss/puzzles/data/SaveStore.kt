package com.driss.puzzles.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SavedPiece(
    val x: Float,
    val y: Float,
    val inTray: Boolean,
    val placed: Boolean,
    /** Id of the group the piece is joined to; -1 in saves from 1.0. */
    val group: Int = -1,
)

/** A game in progress. Piece positions are in world units of a [width] x [height] source bitmap. */
@Serializable
data class SavedGame(
    val imageId: String,
    val rows: Int,
    val cols: Int,
    val seed: Long,
    val width: Int,
    val height: Int,
    val pieces: List<SavedPiece>,
    val tray: List<Int>,
    val loose: List<Int>,
    val elapsedMs: Long,
    val moves: Int,
    val updatedAt: Long,
)

data class SaveSummary(val imageId: String, val placed: Int, val total: Int, val updatedAt: Long)

private fun SavedGame.summary() = SaveSummary(imageId, pieces.count { it.placed }, pieces.size, updatedAt)

class SaveStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dir = File(context.filesDir, "saves").apply { mkdirs() }

    private val _summaries = MutableStateFlow(
        dir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { readFile(it)?.summary() }
            .associateBy { it.imageId },
    )
    val summaries: StateFlow<Map<String, SaveSummary>> = _summaries

    suspend fun load(imageId: String): SavedGame? = withContext(Dispatchers.IO) { readFile(fileFor(imageId)) }

    suspend fun save(game: SavedGame) = withContext(Dispatchers.IO) {
        writeAtomically(fileFor(game.imageId), json.encodeToString(game))
        _summaries.update { it + (game.imageId to game.summary()) }
    }

    suspend fun delete(imageId: String) = withContext(Dispatchers.IO) {
        fileFor(imageId).delete()
        _summaries.update { it - imageId }
    }

    private fun fileFor(imageId: String) = File(dir, "$imageId.json")

    private fun readFile(file: File): SavedGame? =
        if (!file.exists()) null
        else runCatching { json.decodeFromString<SavedGame>(file.readText()) }.getOrNull()
}
