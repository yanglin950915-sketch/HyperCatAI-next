package com.hypercat.ai.rear

import android.system.Os
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

/** Narrow Shizuku shell bridge for verified Xiaomi ThemeManager rear-card carriers. */
@Keep
class RearShellUserService : IRearShellService.Stub() {
    override fun exec(command: String): String {
        val displayId = Regex("""am start --display (\d+) -n com\.hypercat\.ai/\.rear\.RearPetActivity""").matchEntire(command)
        val allowed = command == "dumpsys display" || displayId != null ||
            command == "am broadcast -a com.hypercat.ai.rear.ACTION_HIDE_PROJECTION -p com.hypercat.ai"
        if (!allowed) throw SecurityException("Unsupported rear projection command")
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
        val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()
        val exitCode = process.waitFor()
        return "$exitCode\n$stdout\n$stderr"
    }

    @Synchronized override fun scanCarriers(): String = try {
        val root = carrierRoot()
        if (!root.isDirectory) {
            val parent = root.parentFile
            val parentState = when {
                parent == null -> "路径无效"
                !parent.exists() -> "主题管理器数据目录不存在或尚未初始化"
                !parent.isDirectory -> "主题管理器数据路径不是目录"
                else -> "主题管理器目录可访问，但 .ai_app 尚未创建"
            }
            JSONObject().put("success", true).put("code", "CARRIER_NOT_READY")
                .put("message", "尚未发现可替换的背屏卡片载体（" + root.path + "）。" + parentState + "。请先在系统背屏/主题管理器中下载并添加一张应用卡，再返回这里重新扫描。")
                .put("cards", JSONArray()).toString()
        } else {
            val cards = JSONArray()
            root.listFiles()?.asSequence()?.filter { it.isDirectory && it.name.endsWith("_extracted") }
                ?.sortedBy { it.name }?.forEach { dir ->
                    val id = dir.name.removeSuffix("_extracted")
                    if (CARD_ID.matches(id)) {
                        val carrier = File(dir, "rearscreen")
                        if (carrier.isFile && isMamlPackage(carrier)) {
                            cards.put(JSONObject().put("cardId", id).put("name", id))
                        }
                    }
                }
            JSONObject().put("success", true).put("code", "OK")
                .put("message", "扫描完成").put("cards", cards).toString()
        }
    } catch (error: Exception) { result(false, "SCAN_FAILED", error.message ?: "扫描失败") }

    @Synchronized override fun deployCarrier(cardId: String, packagePath: String, backupPath: String): String = try {
        requireValidCardId(cardId)
        val root = carrierRoot().canonicalFile
        val dir = File(root, "${cardId}_extracted").canonicalFile
        require(dir.parentFile == root && dir.isDirectory) { "目标卡片目录不存在或路径无效。" }
        val target = File(dir, "rearscreen").canonicalFile
        require(target.parentFile == dir && target.isFile && isMamlPackage(target)) { "目标卡片资源不符合预期，未进行修改。" }
        val source = checkedAppFile(packagePath, "rear-deploy")
        require(source.isFile && isMamlPackage(source, requireWeb = true)) { "内置萌宠卡片包校验失败。" }
        val backup = checkedAppFile(backupPath, "rear-backups")
        require(backup.name == "$cardId-original.zip") { "备份文件名无效。" }
        if (!backup.exists()) copyAtomically(target, backup)
        else require(isMamlPackage(backup)) { "已有备份无法校验；为避免覆盖，已停止部署。" }
        copyAtomically(source, target)
        result(true, "DEPLOYED", "萌宠卡片资源已写入。请进入系统背屏卡片管理，移除旧卡片后重新应用该卡片。")
    } catch (error: Exception) { result(false, "DEPLOY_FAILED", error.message ?: "部署失败") }

    @Synchronized override fun restoreCarrier(cardId: String, backupPath: String): String = try {
        requireValidCardId(cardId)
        val root = carrierRoot().canonicalFile
        val dir = File(root, "${cardId}_extracted").canonicalFile
        require(dir.parentFile == root && dir.isDirectory) { "目标卡片目录不存在。" }
        val target = File(dir, "rearscreen").canonicalFile
        require(target.parentFile == dir) { "目标路径无效。" }
        val backup = checkedAppFile(backupPath, "rear-backups")
        require(backup.name == "$cardId-original.zip" && backup.isFile && isMamlPackage(backup)) { "找不到有效的原始卡片备份。" }
        copyAtomically(backup, target)
        result(true, "RESTORED", "原始卡片资源已恢复。请在系统背屏卡片管理中重新应用。")
    } catch (error: Exception) { result(false, "RESTORE_FAILED", error.message ?: "恢复失败") }

    override fun destroy() { System.exit(0) }

    private fun carrierRoot(): File {
        val candidates = listOf(
            File("/storage/emulated/0/Android/data/com.android.thememanager/files/MIUI/.ai_app"),
            File("/sdcard/Android/data/com.android.thememanager/files/MIUI/.ai_app"),
            File("/mnt/user/0/primary/Android/data/com.android.thememanager/files/MIUI/.ai_app")
        )
        return candidates.firstOrNull { it.isDirectory } ?: candidates.first()
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
        ZipFile(file).use { zip ->
            zip.getEntry("manifest.xml") != null && (!requireWeb || zip.getEntry("web/index.html") != null)
        }
    } catch (_: Exception) { false }

    private fun copyAtomically(source: File, destination: File) {
        val parent = destination.parentFile ?: error("目标目录无效。")
        require(parent.isDirectory) { "目标目录不存在。" }
        val temp = File(parent, ".hypercat-${destination.name}.tmp")
        try {
            FileInputStream(source).use { input -> FileOutputStream(temp).use { output -> input.copyTo(output); output.fd.sync() } }
            Os.chmod(temp.absolutePath, 420)
            try {
                Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temp.delete() }
    }

    private fun result(success: Boolean, code: String, message: String) =
        JSONObject().put("success", success).put("code", code).put("message", message).toString()

    private companion object { val CARD_ID = Regex("[A-Za-z0-9_.-]{1,96}") }
}
