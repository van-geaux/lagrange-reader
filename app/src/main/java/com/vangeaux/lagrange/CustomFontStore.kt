package com.vangeaux.lagrange

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val CUSTOM_FONT_DIRECTORY = "custom_fonts"
private const val MAX_CUSTOM_FONT_BYTES = 20L * 1024 * 1024

/** One imported, app-private custom EPUB reader font. */
data class CustomFontRecord(
    val fileName: String,
    val displayName: String,
    val familyName: String
)

internal sealed interface CustomFontImportResult {
    data class Imported(val record: CustomFontRecord) : CustomFontImportResult
    data class Rejected(val reason: String) : CustomFontImportResult
}

internal fun customFontStorageValue(record: CustomFontRecord): String = JSONObject().apply {
    put("fileName", record.fileName)
    put("displayName", record.displayName)
    put("familyName", record.familyName)
}.toString()

internal fun customFontFromStorage(value: String?): CustomFontRecord? {
    if (value.isNullOrBlank()) return null
    return runCatching {
        val root = JSONObject(value)
        val fileName = root.optString("fileName").takeIf { it.isNotBlank() } ?: return null
        val familyName = root.optString("familyName").takeIf { it.isNotBlank() } ?: return null
        CustomFontRecord(
            fileName = fileName,
            displayName = root.optString("displayName").ifBlank { fileName },
            familyName = familyName
        )
    }.getOrNull()
}

internal class CustomFontStore(context: Context) {
    private val appContext = context.applicationContext

    fun fontFile(record: CustomFontRecord): File = File(customFontDirectory(), record.fileName)

    suspend fun import(uri: Uri, displayName: String?): CustomFontImportResult = withContext(Dispatchers.IO) {
        val name = displayName.orEmpty()
        val extension = fontExtension(name) ?: fontExtensionFromUri(uri)
            ?: return@withContext CustomFontImportResult.Rejected("Choose a .ttf or .otf font file.")
        val directory = customFontDirectory().apply { mkdirs() }
        val stagingFile = File(directory, "staging-${UUID.randomUUID()}.tmp")
        try {
            val copiedBytes = appContext.contentResolver.openInputStream(uri)?.use { input ->
                stagingFile.outputStream().use { output -> copyWithLimit(input, output, MAX_CUSTOM_FONT_BYTES) }
            } ?: return@withContext CustomFontImportResult.Rejected("The selected font file could not be opened.")
            if (copiedBytes <= 0L) {
                return@withContext CustomFontImportResult.Rejected("The selected font file is empty.")
            }
            if (copiedBytes > MAX_CUSTOM_FONT_BYTES) {
                return@withContext CustomFontImportResult.Rejected("The selected font file is too large (max 20 MB).")
            }
            if (!isValidFontFile(stagingFile)) {
                return@withContext CustomFontImportResult.Rejected("The selected file is not a valid TrueType or OpenType font.")
            }
            val id = UUID.randomUUID().toString().replace("-", "")
            val fileName = "custom-font-$id.$extension"
            val destination = File(directory, fileName)
            check(stagingFile.renameTo(destination)) { "Could not store the selected font." }
            CustomFontImportResult.Imported(
                CustomFontRecord(
                    fileName = fileName,
                    displayName = name.ifBlank { fileName },
                    familyName = "bookorbit-custom-$id"
                )
            )
        } finally {
            stagingFile.delete()
        }
    }

    fun remove(record: CustomFontRecord?) {
        record?.let(::fontFile)?.delete()
    }

    private fun customFontDirectory(): File = File(appContext.filesDir, CUSTOM_FONT_DIRECTORY)
}

private fun fontExtension(name: String): String? = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
    "ttf" -> "ttf"
    "otf" -> "otf"
    else -> null
}

private fun fontExtensionFromUri(uri: Uri): String? = fontExtension(uri.lastPathSegment.orEmpty())

private fun copyWithLimit(input: java.io.InputStream, output: java.io.OutputStream, limit: Long): Long {
    val buffer = ByteArray(8 * 1024)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) return total
        total += read
        if (total > limit) return total
        output.write(buffer, 0, read)
    }
}

internal fun isValidFontFile(file: File): Boolean {
    if (!file.isFile || file.length() < 12) return false
    val header = ByteArray(4)
    val read = file.inputStream().use { it.read(header) }
    if (read < 4) return false
    val tag = String(header, Charsets.ISO_8859_1)
    return tag == "OTTO" || tag == "true" || tag == "ttcf" ||
        (header[0] == 0.toByte() && header[1] == 1.toByte() &&
            header[2] == 0.toByte() && header[3] == 0.toByte())
}
