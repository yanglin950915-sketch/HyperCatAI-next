package com.hypercat.ai.avatar

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
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
    private const val MAX_EXPANDED_BYTES = 160L * 1024L * 1024L
    private const val MAX_ARCHIVE_DEPTH = 3
    private const val MAX_ARCHIVE_ENTRIES = 5000

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
        return Live2DModel(directory, entry, displayNameFor(modelFile.name))
    }

    private data class ExtractionBudget(var bytes: Long = 0L, var entries: Int = 0)

    private fun isModelEntry(name: String): Boolean {
        val normalized = name.lowercase()
        return normalized == "model3.json" || normalized.endsWith(".model3.json")
    }

    private fun displayNameFor(entryName: String): String =
        if (entryName.equals("model3.json", ignoreCase = true)) "Live2D"
        else entryName.removeSuffix(".model3.json")

    private fun File.isZipArchive(): Boolean = runCatching {
        FileInputStream(this).use { input ->
            val signature = ByteArray(4)
            input.read(signature) == 4 &&
                signature[0] == 0x50.toByte() && signature[1] == 0x4b.toByte() &&
                signature[2] == 0x03.toByte() && signature[3] == 0x04.toByte()
        }
    }.getOrDefault(false)

    private fun extractArchive(
        archive: File,
        target: File,
        budget: ExtractionBudget,
        depth: Int
    ) {
        require(depth <= MAX_ARCHIVE_DEPTH) { "模型压缩包嵌套层级超过限制" }
        require(archive.isFile && archive.isZipArchive()) {
            "所选文件不是有效 ZIP 压缩包，或压缩包格式不受支持"
        }
        check(target.mkdirs() || target.isDirectory) { "无法创建模型解压目录" }
        var extractedEntries = 0
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val item = zip.nextEntry ?: break
                extractedEntries++
                budget.entries++
                require(budget.entries <= MAX_ARCHIVE_ENTRIES) { "模型压缩包文件数量过多" }

                val raw = item.name.replace(92.toChar(), '/')
                require(raw.isNotBlank() && !raw.startsWith("/") &&
                    !raw.split('/').any { it == ".." } &&
                    !raw.contains(":")) { "模型压缩包包含不安全路径" }

                val output = File(target, raw)
                require(output.canonicalPath.startsWith(target.canonicalPath + File.separator)) {
                    "模型文件路径越界"
                }
                if (item.isDirectory) {
                    output.mkdirs()
                    zip.closeEntry()
                    continue
                }

                output.parentFile?.mkdirs()
                output.outputStream().buffered().use { out ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        budget.bytes += count
                        require(budget.bytes <= MAX_EXPANDED_BYTES) {
                            "模型解压后超过 160 MB 限制"
                        }
                        out.write(buffer, 0, count)
                    }
                }
                zip.closeEntry()
            }
        }
        require(extractedEntries > 0) { "ZIP 压缩包为空，或无法读取其中的文件" }
    }

    private fun expandNestedArchives(root: File, budget: ExtractionBudget) {
        var depth = 0
        while (depth < MAX_ARCHIVE_DEPTH) {
            val archives = root.walkTopDown().filter { it.isFile && it.isZipArchive() }.toList()
            if (archives.isEmpty()) return

            for (archive in archives) {
                var target = File(archive.parentFile, "${archive.nameWithoutExtension}_contents")
                var suffix = 1
                while (target.exists()) {
                    target = File(archive.parentFile, "${archive.nameWithoutExtension}_contents_${suffix++}")
                }
                extractArchive(archive, target, budget, depth + 1)
                check(archive.delete()) { "无法清理嵌套 ZIP 临时文件" }
            }
            depth++
        }
        val remaining = root.walkTopDown().any { it.isFile && it.isZipArchive() }
        require(!remaining) { "模型压缩包嵌套层级超过限制，请先手动解压内层 ZIP" }
    }

    private fun resolveModelResource(modelDir: File, reference: String): File {
        val normalized = reference.replace(92.toChar(), '/')
        require(normalized.isNotBlank() && !normalized.startsWith("/") &&
            !normalized.contains(":") && !normalized.split('/').any { it == ".." }) {
            "模型资源路径无效：$reference"
        }
        val file = File(modelDir, normalized)
        require(file.canonicalPath.startsWith(modelDir.canonicalPath + File.separator) && file.isFile) {
            "模型资源文件缺失或路径无效：$reference"
        }
        return file
    }

    suspend fun importZip(context: Context, uri: Uri): Live2DModel = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "live2d-models").apply { mkdirs() }
        val destination = File(root, "model-${System.currentTimeMillis()}").apply { mkdirs() }
        val budget = ExtractionBudget()
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("无法读取所选模型压缩包")
            val outerArchive = File(destination, "selected-upload.zip")
            outerArchive.outputStream().buffered().use { output ->
                input.use { it.copyTo(output) }
            }
            require(outerArchive.length() > 0L) { "所选文件为空" }

            val extracted = File(destination, "extracted")
            extractArchive(outerArchive, extracted, budget, 0)
            outerArchive.delete()
            expandNestedArchives(extracted, budget)

            val modelEntries = extracted.walkTopDown().filter {
                it.isFile && isModelEntry(it.name)
            }.toList()
            val settings = when (modelEntries.size) {
                0 -> {
                    val legacyModel = extracted.walkTopDown().any {
                        it.isFile && it.name.endsWith(".model.json", ignoreCase = true)
                    }
                    if (legacyModel) error("检测到 Cubism 2 的 .model.json 模型；当前仅支持 Cubism 3/4 的 .model3.json 模型")
                    error("未找到 Live2D 模型入口（*.model3.json 或 model3.json）。请上传包含模型入口、MOC3 和贴图的完整 ZIP；RAR/7z 暂不支持")
                }
                1 -> modelEntries.first()
                else -> error("压缩包中包含 ${modelEntries.size} 个 Live2D 模型入口，请将每个模型单独打包后再导入")
            }
            val json = org.json.JSONObject(settings.readText(Charsets.UTF_8).removePrefix("\uFEFF"))
            val references = json.optJSONObject("FileReferences")
                ?: error("模型配置缺少 FileReferences")
            val modelDir = settings.parentFile ?: error("模型目录无效")

            val mocPath = references.optString("Moc")
            require(mocPath.isNotBlank()) { "模型配置没有指定 MOC3 文件" }
            resolveModelResource(modelDir, mocPath)

            val textures = references.optJSONArray("Textures")
                ?: error("模型配置缺少贴图列表")
            require(textures.length() > 0) { "模型没有贴图资源" }
            for (index in 0 until textures.length()) {
                resolveModelResource(modelDir, textures.optString(index))
            }

            val model = Live2DModel(
                directory = Uri.fromFile(modelDir).toString().trimEnd('/') + "/",
                entry = settings.name,
                displayName = displayNameFor(settings.name)
            )
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val previousDirectory = prefs.getString(KEY_DIRECTORY, null)
            check(prefs.edit().putString(KEY_DIRECTORY, model.directory)
                .putString(KEY_ENTRY, model.entry).commit()) { "无法保存 Live2D 模型配置" }

            if (previousDirectory != null) {
                runCatching {
                    val previous = File(Uri.parse(previousDirectory).path.orEmpty())
                    if (previous.canonicalPath.startsWith(root.canonicalPath + File.separator)) {
                        previous.deleteRecursively()
                    }
                }
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
