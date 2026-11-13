package com.buildai.app.data.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.DataInputStream

data class InstalledModel(val fileName: String, val name: String, val source: String, val sourceUrl: String?, val catalogId: String?, val bytes: Long)
data class DiscoveredModel(val uri: Uri, val name: String, val bytes: Long)

/** Models are retained independently. A failed import never replaces or deletes a working model. */
class LocalModelStore(private val context: Context) {
    private val directory get() = File(context.filesDir, "models").apply { mkdirs() }
    private val preferences = context.getSharedPreferences("local_model", Context.MODE_PRIVATE)
    private fun file(name: String): File? = name.takeIf { !it.contains('/') && !it.contains('\\') && it.endsWith(".gguf", true) }
        ?.let { File(directory, it) }?.takeIf { it.canonicalFile.parentFile == directory.canonicalFile && it.isFile }
    fun selectedFile(): File? = preferences.getString("selected", null)?.let(::file)
    fun displayName(): String? = selectedFile()?.let { selected -> metadata(selected).name }
    fun selectedName(): String? = selectedFile()?.name
    private fun metadata(model: File): InstalledModel {
        val saved = preferences.getString("metadata:${model.name}", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        val legacy = if (preferences.getString("selected", null) == model.name) preferences.getString("display_name", null) else null
        return InstalledModel(model.name, saved?.optString("name") ?: legacy ?: model.name, saved?.optString("source") ?: "User",
            saved?.optString("sourceUrl")?.takeIf(String::isNotBlank), saved?.optString("catalogId")?.takeIf(String::isNotBlank), model.length())
    }
    fun installed(): List<InstalledModel> = directory.listFiles().orEmpty().filter { file(it.name) != null && validHeader(it) }.map(::metadata).sortedBy { it.name.lowercase() }
    private fun validHeader(model: File): Boolean = runCatching { model.length() > 1024 && model.inputStream().use { val bytes = ByteArray(4); it.read(bytes) == 4 && bytes.contentEquals("GGUF".toByteArray()) } }.getOrDefault(false)
    fun select(name: String) { val selected = checkNotNull(file(name)); require(validHeader(selected)) { "This model is incomplete" }; val meta = metadata(selected); preferences.edit().putString("selected", selected.name).putString("display_name", meta.name).apply() }
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }?.take(100) ?: "Local model"
        val target = File(directory, "model-${java.util.UUID.randomUUID()}.gguf"); val temp = File(directory, "${target.name}.partial")
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val input = DataInputStream(stream); val magic = ByteArray(8); input.readFully(magic)
                require(magic.copyOfRange(0, 4).contentEquals("GGUF".toByteArray())) { "Choose a GGUF model file." }
                val version = (magic[4].toInt() and 255) or ((magic[5].toInt() and 255) shl 8) or ((magic[6].toInt() and 255) shl 16) or ((magic[7].toInt() and 255) shl 24)
                require(version in 2..3) { "This GGUF version is not supported." }
                FileOutputStream(temp).use { output ->
                    output.write(magic); val buffer = ByteArray(64 * 1024); var copied = 8L
                    while (true) { val count = input.read(buffer); if (count < 0) break
                        require(directory.usableSpace > count + 64L * 1024 * 1024) { "Not enough free storage for this model." }
                        output.write(buffer, 0, count); copied += count
                    }
                    output.fd.sync(); require(copied > 1024) { "The GGUF file is incomplete." }
                }
            } ?: error("Could not open the selected file.")
            check(temp.renameTo(target)) { "Could not save the local model." }
            val meta = JSONObject().put("name", name).put("source", "User").put("importUri", uri.toString())
            preferences.edit().putString("metadata:${target.name}", meta.toString()).putString("selected", target.name).putString("display_name", name).apply()
            name
        } catch (error: Exception) { temp.delete(); throw error }
    }
    fun installVerifiedDownload(temp: File, displayName: String, catalogId: String? = null) {
        val target = File(directory, "model-${java.util.UUID.randomUUID()}.gguf")
        check(temp.renameTo(target)) { "Could not save the downloaded model." }
        val catalog = catalogId?.let(ModelCatalog::find)
        val meta = JSONObject().put("name", displayName).put("source", "Downloaded").put("sourceUrl", catalog?.sourceUrl ?: "").put("catalogId", catalogId ?: "")
        val saved = preferences.edit().putString("metadata:${target.name}", meta.toString()).putString("selected", target.name).putString("display_name", displayName).commit()
        if (!saved) { target.delete(); error("Could not select the downloaded model.") }
    }
    fun remove(name: String? = selectedName()) {
        val target = name?.let(::file) ?: return
        check(target.delete()) { "Could not remove this model" }
        val editor = preferences.edit().remove("metadata:$name")
        if (selectedName() == null || preferences.getString("selected", null) == name) editor.remove("selected").remove("display_name")
        editor.apply()
    }
    /** Android only permits scanning folders the user has granted. Never assume access to all Downloads. */
    fun discovered(): List<DiscoveredModel> {
        val found = mutableListOf<DiscoveredModel>(); val imported = preferences.all.filterKeys { it.startsWith("metadata:") }.values.mapNotNull { value -> runCatching { JSONObject(value.toString()).optString("importUri") }.getOrNull() }.toSet()
        context.contentResolver.persistedUriPermissions.filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }.forEach { permission ->
            val queue = java.util.ArrayDeque<Pair<String, Int>>(); queue.add(DocumentsContract.getTreeDocumentId(permission.uri) to 0); var visited = 0
            while (queue.isNotEmpty() && visited++ < 2000) {
                val (id, depth) = queue.removeFirst()
                runCatching { context.contentResolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(permission.uri, id), arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { cursor ->
                    while (cursor.moveToNext() && found.size < 100) {
                        val childId = cursor.getString(0); val name = cursor.getString(1).orEmpty(); val mime = cursor.getString(2)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR && depth < 5) queue.add(childId to depth + 1)
                        else if (name.endsWith(".gguf", true) && !name.contains("mmproj", true)) {
                            val uri = DocumentsContract.buildDocumentUriUsingTree(permission.uri, childId)
                            if (uri.toString() !in imported) found += DiscoveredModel(uri, name.take(100), cursor.getLong(3))
                        }
                    }
                } }
            }
        }
        context.getExternalFilesDir(null)?.listFiles()?.filter { it.isFile && it.extension.equals("gguf", true) }?.forEach { found += DiscoveredModel(Uri.fromFile(it), it.name, it.length()) }
        return found.distinctBy { it.uri }.sortedBy { it.name.lowercase() }
    }
}
