package com.hypercat.ai.rear

import android.content.Context
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
                addAsset(context, zip, "rearscreen/manifest.xml", "manifest.xml")
                addTree(context, zip, "rearscreen/web", "web")
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

    private fun addAsset(context: Context, zip: ZipOutputStream, assetPath: String, entryPath: String) {
        require(!entryPath.startsWith("/") && ".." !in entryPath.split("/")) { "非法资源路径" }
        zip.putNextEntry(ZipEntry(entryPath))
        context.assets.open(assetPath).use { it.copyTo(zip) }
        zip.closeEntry()
    }
}
