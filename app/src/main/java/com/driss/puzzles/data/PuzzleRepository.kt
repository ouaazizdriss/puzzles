package com.driss.puzzles.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

data class PuzzleImage(
    val id: String,
    val title: String,
    val category: String?,
    val width: Int,
    val height: Int,
    val assetFile: String? = null,
    val file: File? = null,
    val author: String? = null,
) {
    val isUser get() = file != null
    val isKids get() = category == KIDS_CATEGORY
    /** Something Coil can load. */
    val model: Any get() = file ?: "file:///android_asset/puzzles/$assetFile"
}

const val KIDS_CATEGORY = "kids"

@Serializable
private data class CatalogEntry(
    val id: String,
    val title: String,
    val category: String,
    val file: String,
    val width: Int,
    val height: Int,
    val author: String? = null,
)

@Serializable
private data class UserPhoto(
    val id: String,
    val title: String,
    val width: Int,
    val height: Int,
    val createdAt: Long,
)

class PuzzleRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val userDir = File(context.filesDir, "user_puzzles").apply { mkdirs() }
    private val indexFile = File(userDir, "index.json")

    val builtIn: List<PuzzleImage> = context.assets.open("puzzles/catalog.json").use { stream ->
        json.decodeFromString<List<CatalogEntry>>(stream.readBytes().decodeToString()).map {
            PuzzleImage(it.id, it.title, it.category, it.width, it.height, assetFile = it.file, author = it.author)
        }
    }

    private val _userPhotos = MutableStateFlow(readIndex().map { it.toImage() })
    /** Newest first. */
    val userPhotos: StateFlow<List<PuzzleImage>> = _userPhotos

    fun find(id: String): PuzzleImage? =
        builtIn.firstOrNull { it.id == id } ?: _userPhotos.value.firstOrNull { it.id == id }

    suspend fun loadBitmap(image: PuzzleImage): Bitmap = withContext(Dispatchers.IO) {
        val file = image.file
        if (file != null) {
            ImageIO.decodeStream({ file.inputStream() }, ImageIO.PUZZLE_MAX_SIDE)
        } else {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bitmap = context.assets.open("puzzles/${image.assetFile}").use { BitmapFactory.decodeStream(it, null, opts) }
                ?: error("Could not decode ${image.assetFile}")
            ImageIO.scaleDown(bitmap, ImageIO.PUZZLE_MAX_SIDE)
        }
    }

    suspend fun addUserPhoto(bitmap: Bitmap, title: String): PuzzleImage = withContext(Dispatchers.IO) {
        val id = "user_" + UUID.randomUUID().toString().replace("-", "")
        val format = if (Build.VERSION.SDK_INT >= 30) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        File(userDir, "$id.webp").outputStream().use { bitmap.compress(format, 88, it) }
        val entry = UserPhoto(id, title, bitmap.width, bitmap.height, System.currentTimeMillis())
        writeIndex(listOf(entry) + readIndex())
        val image = entry.toImage()
        _userPhotos.update { listOf(image) + it }
        image
    }

    suspend fun deleteUserPhoto(id: String) = withContext(Dispatchers.IO) {
        File(userDir, "$id.webp").delete()
        writeIndex(readIndex().filterNot { it.id == id })
        _userPhotos.update { list -> list.filterNot { it.id == id } }
    }

    private fun UserPhoto.toImage() =
        PuzzleImage(id, title, null, width, height, file = File(userDir, "$id.webp"))

    private fun readIndex(): List<UserPhoto> =
        if (!indexFile.exists()) emptyList()
        else runCatching { json.decodeFromString<List<UserPhoto>>(indexFile.readText()) }.getOrDefault(emptyList())

    private fun writeIndex(entries: List<UserPhoto>) = writeAtomically(indexFile, json.encodeToString(entries))
}

internal fun writeAtomically(target: File, text: String) {
    val tmp = File(target.parentFile, target.name + ".tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(target)) {
        target.writeText(text)
        tmp.delete()
    }
}
