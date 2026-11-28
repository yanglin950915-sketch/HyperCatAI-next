package com.hypercat.ai.rear

import android.content.Context
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipFile

/** Builds the offline MAML carrier with the original upstream pet runtime and no external model bundle. */
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
            }
            verifyPackage(partial)
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
    private fun verifyPackage(file: File) {
        ZipFile(file).use { archive ->
            val names = archive.entries().asSequence().map { it.name }.toHashSet()
            require("manifest.xml" in names) { "背屏资源包缺少 manifest.xml" }
            require("web/index.html" in names) { "背屏资源包缺少 web/index.html" }
            val html = archive.getInputStream(archive.getEntry("web/index.html")).bufferedReader().use { it.readText() }
            require("window.__setAgentState" in html && "window.__setAgentJson" in html) { "背屏桌宠缺少 Agent 状态桥接" }
            require("maml.doAction" in html && "Mi Rear AI Pet" in html) { "背屏桌宠资源或触摸桥接不完整" }
        }
    }

    private fun addAsset(context: Context, zip: ZipOutputStream, assetPath: String, entryPath: String) {
        require(!entryPath.startsWith("/") && ".." !in entryPath.split("/")) { "非法资源路径" }
        zip.putNextEntry(ZipEntry(entryPath))
        context.assets.open(assetPath).use { it.copyTo(zip) }
        zip.closeEntry()
    }
}
