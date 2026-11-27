package com.hypercat.ai.pet

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.zip.ZipInputStream

data class Live2DModel(
    val directory: String,
    val entry: String,
    val displayName: String
)

/** Imports a user-owned Cubism model bundle. Model files are never bundled without permission. */
object Live2DModelStore {
    private const val PREFS = "hypercat_live2d"
    private const val KEY_DIRECTORY = "model_directory"
    private const val KEY_ENTRY = "model_entry"
    private val mutableModel = MutableStateFlow<Live2DModel?>(null)
    val state = mutableModel.asStateFlow()

    fun load(context: Context) { mutableModel.value = current(context) }

    fun current(context: Context): Live2DModel? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val directory = prefs.getString(KEY_DIRECTORY, null) ?: return null
        val entry = prefs.getString(KEY_ENTRY, null) ?: return null
        val parent = File(Uri.parse(directory).path ?: return null)
        val modelFile = File(parent, entry)
        if (!parent.isDirectory || !modelFile.isFile) return null
        return Live2DModel(directory, entry, modelFile.name.removeSuffix(".model3.json"))
    }

    suspend fun importZip(context: Context, uri: Uri): Live2DModel = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "live2d-models").apply { mkdirs() }
        val destination = File(root, "model-${System.currentTimeMillis()}").apply { mkdirs() }
        var expanded = 0L
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("无法读取所选模型压缩包")
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val item = zip.nextEntry ?: break
                    val raw = item.name.replace('\\\\', '/')
                    require(!raw.startsWith("/") && !raw.split('/').any { it == ".." }) { "模型压缩包包含不安全路径" }
                    if (raw.isBlank() || item.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    val output = File(destination, raw)
                    require(output.canonicalPath.startsWith(destination.canonicalPath + File.separator)) { "模型文件路径越界" }
                    output.parentFile?.mkdirs()
                    output.outputStream().buffered().use { out ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            expanded += count
                            require(expanded <= 160L * 1024L * 1024L) { "模型解压后超过 160 MB 限制" }
                            out.write(buffer, 0, count)
                        }
                    }
                    zip.closeEntry()
                }
            }
            val settings = destination.walkTopDown().firstOrNull {
                it.isFile && it.name.endsWith(".model3.json", ignoreCase = true)
            } ?: error("压缩包中没有找到 .model3.json 模型入口文件")
            val json = org.json.JSONObject(settings.readText())
            val references = json.optJSONObject("FileReferences") ?: error("模型配置缺少 FileReferences")
            val modelDir = settings.parentFile ?: error("模型目录无效")
            val mocPath = references.optString("Moc")
            require(mocPath.isNotBlank() && !mocPath.split('/').contains("..") && File(modelDir, mocPath).isFile) { "模型 MOC3 文件缺失或路径无效" }
            val textures = references.optJSONArray("Textures") ?: error("模型配置缺少贴图列表")
            require(textures.length() > 0) { "模型没有贴图资源" }
            for (index in 0 until textures.length()) {
                val texture = textures.optString(index)
                require(texture.isNotBlank() && !texture.split('/').contains("..") && File(modelDir, texture).isFile) { "模型贴图文件缺失或路径无效：$texture" }
            }
            val model = Live2DModel(
                directory = Uri.fromFile(modelDir).toString().trimEnd('/') + "/",
                entry = settings.name,
                displayName = settings.name.removeSuffix(".model3.json")
            )
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val previousDirectory = prefs.getString(KEY_DIRECTORY, null)
            check(prefs.edit().putString(KEY_DIRECTORY, model.directory).putString(KEY_ENTRY, model.entry).commit()) { "无法保存 Live2D 模型配置" }
            if (previousDirectory != null) {
                val previous = File(Uri.parse(previousDirectory).path.orEmpty())
                if (previous.canonicalPath.startsWith(root.canonicalPath + File.separator)) previous.deleteRecursively()
            }
            mutableModel.value = model
            model
        } catch (error: Throwable) {
            destination.deleteRecursively()
            throw error
        }
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val directory = prefs.getString(KEY_DIRECTORY, null)
        prefs.edit().remove(KEY_DIRECTORY).remove(KEY_ENTRY).apply()
        if (directory != null) runCatching {
            val root = File(context.filesDir, "live2d-models").canonicalFile
            val modelDirectory = File(Uri.parse(directory).path.orEmpty()).canonicalFile
            if (modelDirectory.path.startsWith(root.path + File.separator)) modelDirectory.deleteRecursively()
        }
        mutableModel.value = null
    }
}
