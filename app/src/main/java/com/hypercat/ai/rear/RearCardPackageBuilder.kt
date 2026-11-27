package com.hypercat.ai.rear

import android.content.Context
import com.hypercat.ai.avatar.Live2DModelStore
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
