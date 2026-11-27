package com.hypercat.ai.rear

import android.system.Os
import android.util.Base64
import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import org.json.JSONArray
import org.json.JSONObject

/** Narrow Shizuku bridge for verified Xiaomi rear-screen MAML carriers. */
@Keep
class RearShellUserService : IRearShellService.Stub() {
    override fun exec(command: String): String {
        val displayId = Regex("""am start --display (\\d+) -n com\\.hypercat\\.ai/\\.rear\\.RearPetActivity""").matchEntire(command)
        val allowed = command == "dumpsys display" || displayId != null ||
            command == "am broadcast -a com.hypercat.ai.rear.ACTION_HIDE_PROJECTION -p com.hypercat.ai"
        if (!allowed) throw SecurityException("Unsupported rear projection command")
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
        val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()
        val exitCode = process.waitFor()
        return "$exitCode\\n$stdout\\n$stderr"
    }

    @Synchronized override fun scanCarriers(): String = try {
        val cards = JSONArray()
        var rootsFound = 0
        carrierRoots().forEach { (kind, root) ->
            if (root.isDirectory) {
                rootsFound++
                val candidates = if (kind == "wallpaper") findRearFiles(root, 0) else {
                    root.listFiles()?.asSequence()?.filter { it.isDirectory && it.name.endsWith("_extracted") }
                        ?.mapNotNull { dir ->
                            val f = File(dir, "rearscreen")
                            if (f.isFile && isMamlPackage(f)) f else null
                        }?.toList().orEmpty()
                }
                candidates.distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
                    .sortedBy { it.relativeTo(root).path }
                    .forEach { file ->
                        val relative = file.relativeTo(root).path
                        val id = if (kind == "wallpaper") "w-" + Base64.encodeToString(relative.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                                  else "a-" + file.parentFile.name.removeSuffix("_extracted")
                        if (CARD_ID.matches(id)) {
                            val label = if (kind == "wallpaper") file.parentFile.name else file.parentFile.name.removeSuffix("_extracted")
                            cards.put(JSONObject().put("cardId", id).put("name", label).put("source", kind)
                                .put("path", relative).put("root", if (kind == "wallpaper") ".ai_wallpaper/maml" else ".ai_app"))
                        }
                    }
            }
        }
        JSONObject().put("success", true).put("code", if (cards.length() == 0) "CARRIER_NOT_READY" else "OK")
            .put("message", if (cards.length() == 0) {
                if (rootsFound == 0) "未找到小米主题管理器资源目录。请确认系统已生成 AI 背屏资源，并已启动 Shizuku。"
                else "已检查 .ai_wallpaper/maml/ 和 .ai_app/，但未发现包含有效 manifest.xml 的 rearscreen MAML 包。"
            } else "扫描完成，共发现 ${cards.length()} 个可替换背屏资源")
            .put("cards", cards).toString()
    } catch (error: Exception) { result(false, "SCAN_FAILED", error.message ?: "扫描失败") }

    @Synchronized override fun deployCarrier(cardId: String, packagePath: String, backupPath: String): String = try {
        requireValidCardId(cardId)
        val target = resolveCarrier(cardId)
        require(target.isFile && isMamlPackage(target)) { "目标背屏资源不符合 MAML 包格式，未进行修改。" }
        val source = checkedAppFile(packagePath, "rear-deploy")
        require(source.isFile && isMamlPackage(source, requireWeb = true)) { "内置萌宠卡片包校验失败。" }
        val backup = checkedAppFile(backupPath, "rear-backups")
        require(backup.name == "$cardId-original.zip") { "备份文件名无效。" }
        if (!backup.exists()) copyAtomically(target, backup)
        else require(isMamlPackage(backup)) { "已有备份无法校验；为避免覆盖，已停止部署。" }
        copyAtomically(source, target)
        result(true, "DEPLOYED", "已替换所选背屏资源并完成原始备份。请在系统背屏卡片管理中重新应用对应卡片。")
    } catch (error: Exception) { result(false, "DEPLOY_FAILED", error.message ?: "部署失败") }

    @Synchronized override fun restoreCarrier(cardId: String, backupPath: String): String = try {
        requireValidCardId(cardId)
        val target = resolveCarrier(cardId)
        val backup = checkedAppFile(backupPath, "rear-backups")
        require(backup.name == "$cardId-original.zip" && backup.isFile && isMamlPackage(backup)) { "找不到该背屏对应的有效原始备份。" }
        copyAtomically(backup, target)
        result(true, "RESTORED", "所选背屏的原始资源已恢复。请在系统背屏卡片管理中重新应用。")
    } catch (error: Exception) { result(false, "RESTORE_FAILED", error.message ?: "恢复失败") }

    override fun destroy() { System.exit(0) }

    private fun carrierRoots(): List<Pair<String, File>> = listOf(
        "wallpaper" to File("/storage/emulated/0/Android/data/com.android.thememanager/files/MIUI/.ai_wallpaper/maml"),
        "wallpaper" to File("/sdcard/Android/data/com.android.thememanager/files/MIUI/.ai_wallpaper/maml"),
        "wallpaper" to File("/mnt/user/0/primary/Android/data/com.android.thememanager/files/MIUI/.ai_wallpaper/maml"),
        "app" to File("/storage/emulated/0/Android/data/com.android.thememanager/files/MIUI/.ai_app"),
        "app" to File("/sdcard/Android/data/com.android.thememanager/files/MIUI/.ai_app"),
        "app" to File("/mnt/user/0/primary/Android/data/com.android.thememanager/files/MIUI/.ai_app")
    ).distinctBy { it.second.absolutePath }

    /** Search only rearscreen carrier files; never replace an entire wallpaper directory. */
    private fun findRearFiles(dir: File, depth: Int): List<File> {
        if (depth > 6 || !dir.isDirectory) return emptyList()
        val out = mutableListOf<File>()
        dir.listFiles()?.sortedBy { it.name }?.forEach { child ->
            if (child.isFile && child.name.equals("rearscreen", ignoreCase = true) && isMamlPackage(child)) out += child
            else if (child.isDirectory && !child.name.startsWith(".")) out += findRearFiles(child, depth + 1)
        }
        return out
    }

    private fun resolveCarrier(cardId: String): File {
        if (cardId.startsWith("w-")) {
            val encoded = cardId.removePrefix("w-")
            val relative = String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
            require(relative.isNotBlank() && !relative.startsWith("/") && relative.split('/').none { it == ".." || it == "." }) { "背屏资源路径无效。" }
            val root = carrierRoots().firstOrNull { it.first == "wallpaper" && it.second.isDirectory }?.second?.canonicalFile
                ?: error("小米 AI 背屏资源目录 .ai_wallpaper/maml 不存在。")
            val target = File(root, relative).canonicalFile
            require(target.path.startsWith(root.path + File.separator) && target.name.equals("rearscreen", true)) { "背屏资源路径越界或不是 rearscreen 文件。" }
            return target
        }
        if (cardId.startsWith("a-")) {
            val id = cardId.removePrefix("a-")
            require(CARD_ID.matches(id)) { "无效卡片 ID。" }
            val root = carrierRoots().firstOrNull { it.first == "app" && it.second.isDirectory }?.second?.canonicalFile
                ?: error("旧版 .ai_app 卡片目录不存在。")
            val dir = File(root, "${id}_extracted").canonicalFile
            require(dir.parentFile == root && dir.isDirectory) { "目标卡片目录不存在或路径无效。" }
            return File(dir, "rearscreen").canonicalFile.also { require(it.parentFile == dir) }
        }
        // Backward compatibility with backups created by 0.9.28.
        require(CARD_ID.matches(cardId)) { "无效卡片 ID。" }
        val root = carrierRoots().firstOrNull { it.first == "app" && it.second.isDirectory }?.second?.canonicalFile
            ?: error("旧版 .ai_app 卡片目录不存在。")
        val dir = File(root, "${cardId}_extracted").canonicalFile
        require(dir.parentFile == root && dir.isDirectory) { "目标卡片目录不存在。" }
        return File(dir, "rearscreen").canonicalFile
    }

    private fun requireValidCardId(cardId: String) { require(CARD_ID.matches(cardId) && cardId != "." && cardId != "..") { "无效卡片 ID。" } }

    private fun checkedAppFile(path: String, child: String): File {
        val base = File("/storage/emulated/0/Android/data/com.hypercat.ai/files/$child").canonicalFile
        if (!base.exists()) base.mkdirs()
        val file = File(path).canonicalFile
        require(file.parentFile == base) { "文件路径不在 HyperCat AI 专用目录中。" }
        return file
    }

    private fun isMamlPackage(file: File, requireWeb: Boolean = false): Boolean = try {
        ZipFile(file).use { zip -> zip.getEntry("manifest.xml") != null && (!requireWeb || zip.getEntry("web/index.html") != null) }
    } catch (_: Exception) { false }

    private fun copyAtomically(source: File, destination: File) {
        val parent = destination.parentFile ?: error("目标目录无效。")
        require(parent.isDirectory) { "目标目录不存在。" }
        val temp = File(parent, ".hypercat-${destination.name}.tmp")
        try {
            FileInputStream(source).use { input -> FileOutputStream(temp).use { output -> input.copyTo(output); output.fd.sync() } }
            Os.chmod(temp.absolutePath, 420)
            try { Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        } finally { temp.delete() }
    }

    private fun result(success: Boolean, code: String, message: String) =
        JSONObject().put("success", success).put("code", code).put("message", message).toString()

    private companion object { val CARD_ID = Regex("[A-Za-z0-9_.-]{1,180}") }
}
