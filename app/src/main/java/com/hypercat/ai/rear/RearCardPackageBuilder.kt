package com.hypercat.ai.rear

import android.content.Context
import com.hypercat.ai.avatar.Live2DModelStore
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipFile
import org.json.JSONObject

/** Builds the offline MAML carrier directly from this APK's bundled rear-screen assets. */
object RearCardPackageBuilder {
    fun build(context: Context): File {
        val outputDir = File(requireNotNull(context.getExternalFilesDir(null)), "rear-deploy")
        check(outputDir.isDirectory || outputDir.mkdirs()) { "无法创建背屏部署目录" }
        val output = File(outputDir, "HyperCatAI-rearscreen.zip")
        val partial = File(outputDir, ".HyperCatAI-rearscreen.partial")
        try {
            ZipOutputStream(partial.outputStream().buffered()).use { zip ->
                addAsset(context, zip, "manifest.xml", "manifest.xml")
                addTree(context, zip, "web", "web")
                addImportedLive2DModel(context, zip)
            }
            verifyPackage(partial, context)
            if (output.exists() && !output.delete()) error("无法替换旧的背屏资源包")
            check(partial.renameTo(output)) { "无法生成背屏资源包" }
            return output
        } catch (error: Exception) { partial.delete(); throw error }
    }

    private fun addTree(context: Context, zip: ZipOutputStream, assetPath: String, entryPath: String) {
        context.assets.list(assetPath).orEmpty().sorted().forEach { child ->
            val nextAsset = "$assetPath/$child"
            val nextEntry = "$entryPath/$child"
            if (context.assets.list(nextAsset).orEmpty().isEmpty()) addAsset(context, zip, nextAsset, nextEntry)
            else addTree(context, zip, nextAsset, nextEntry)
        }
    }

    /** Reject incomplete packages before exposing them to the Xiaomi theme manager. */
    private fun verifyPackage(file: File, context: Context) {
        val model = Live2DModelStore.current(context)
        val root = "web/models/active/"
        ZipFile(file).use { archive ->
            val names = archive.entries().asSequence().map { it.name }.toHashSet()
            fun requireFile(path: String) {
                require(path.isNotBlank() && !path.startsWith("/") && ".." !in path.split("/")) { "模型资源路径无效：$path" }
                require(root + path in names) { "背屏资源包缺少模型文件：$path" }
            }
            require("manifest.xml" in names) { "背屏资源包缺少 manifest.xml" }
            require("web/index.html" in names) { "背屏资源包缺少 web/index.html" }
            require("web/live2d/render.html" in names) { "背屏资源包缺少 Live2D 渲染器" }
            require("web/models/active/model-entry.js" in names) { "背屏资源包缺少模型入口脚本" }
            requireFile(model.entry)
            val settings = JSONObject(archive.getInputStream(archive.getEntry(root + model.entry)).bufferedReader().use { it.readText() })
            val refs = settings.optJSONObject("FileReferences") ?: error("模型配置缺少 FileReferences")
            val moc = refs.optString("Moc")
            requireFile(moc)
            val textures = refs.optJSONArray("Textures") ?: error("模型配置缺少纹理列表")
            require(textures.length() > 0) { "模型配置没有纹理资源" }
            for (index in 0 until textures.length()) requireFile(textures.optString(index))

            // A model can have valid MOC/textures but still fail to initialize if an
            // auxiliary file referenced by the settings was omitted from the exported ZIP.
            listOf("Physics", "Pose", "UserData").forEach { key ->
                val path = refs.optString(key)
                if (path.isNotBlank() && path != "null") requireFile(path)
            }
            val expressions = refs.optJSONArray("Expressions")
            if (expressions != null) for (index in 0 until expressions.length()) {
                val path = expressions.optJSONObject(index)?.optString("File").orEmpty()
                if (path.isNotBlank()) requireFile(path)
            }
            val motions = refs.optJSONObject("Motions")
            if (motions != null) {
                val groups = motions.keys()
                while (groups.hasNext()) {
                    val group = motions.getJSONArray(groups.next())
                    for (index in 0 until group.length()) {
                        val path = group.optJSONObject(index)?.optString("File").orEmpty()
                        if (path.isNotBlank()) requireFile(path)
                    }
                }
            }
        }
    }

    private fun addImportedLive2DModel(context: Context, zip: ZipOutputStream) {
        val model = Live2DModelStore.current(context)
        val assetDirectory = model.directory
            .removePrefix("file:///android_asset/")
            .trim('/')
        require(assetDirectory.startsWith("avatar/") && ".." !in assetDirectory.split("/")) {
            "内置 Live2D 模型资源路径无效"
        }
        addAssetTree(context, zip, assetDirectory, "web/models/active")
        zip.putNextEntry(ZipEntry("web/models/active/model-entry.js"))
        val marker = "window.HyperCatLive2DModel=" + org.json.JSONObject().put("entry", model.entry).toString() + ";"
        zip.write(marker.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun addAssetTree(context: Context, zip: ZipOutputStream, assetPath: String, entryRoot: String) {
        context.assets.list(assetPath).orEmpty().sorted().forEach { child ->
            val nextAsset = "$assetPath/$child"
            val nextEntry = "$entryRoot/$child"
            if (context.assets.list(nextAsset).orEmpty().isEmpty()) {
                addAsset(context, zip, nextAsset, nextEntry)
            } else {
                addAssetTree(context, zip, nextAsset, nextEntry)
            }
        }
    }

    private fun addAsset(context: Context, zip: ZipOutputStream, assetPath: String, entryPath: String) {
        require(!entryPath.startsWith("/") && ".." !in entryPath.split("/")) { "非法资源路径" }
        zip.putNextEntry(ZipEntry(entryPath))
        context.assets.open(assetPath).use { it.copyTo(zip) }
        zip.closeEntry()
    }
}
