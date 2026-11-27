package com.hypercat.ai.rear

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Shizuku-backed deployment of the bundled MAML card into a verified local carrier. */
object RearProjectionController {
    private const val PERMISSION_REQUEST_CODE = 24025
    @Volatile private var remoteService: IRearShellService? = null

    fun requestPermission(activity: Activity, callback: (granted: Boolean, message: String) -> Unit) {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            callback(false, "未检测到正在运行的 Shizuku。请先启动 Shizuku，再开启背屏投放。"); return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            callback(true, "Shizuku 授权已就绪"); return
        }
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != PERMISSION_REQUEST_CODE) return
                Shizuku.removeRequestPermissionResultListener(this)
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                callback(granted, if (granted) "Shizuku 授权成功" else "Shizuku 授权未通过，未修改任何卡片")
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }.onFailure {
            Shizuku.removeRequestPermissionResultListener(listener)
            callback(false, "无法请求 Shizuku 授权：${it.message ?: "未知错误"}")
        }
    }

    suspend fun connect(context: Context): String {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return "Shizuku 服务未运行，请先启动 Shizuku"
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return "尚未获得 Shizuku 授权"
        return runCatching {
            val service = bindUserService(context)
            val scan = JSONObject(withContext(Dispatchers.IO) { service.scanCarriers() })
            if (!scan.optBoolean("success")) return scan.optString("message", "扫描卡片失败")
            val cards = scan.optJSONArray("cards") ?: return "未找到可用的背屏卡片载体"
            if (cards.length() == 0) return "没有找到可替换的本地背屏卡片。请先在系统背屏设置中下载或添加一张卡片，再重试。"
            if (cards.length() != 1) {
                val names = (0 until cards.length()).mapNotNull { cards.optJSONObject(it)?.optString("name") }.joinToString("、")
                return "检测到 ${cards.length()} 张兼容卡片（$names），为避免覆盖错误卡片，暂未修改任何文件。请先在系统背屏卡片管理中只保留一张可替换卡片后重试。"
            }
            val cardId = cards.getJSONObject(0).getString("cardId")
            val packageFile = RearCardPackageBuilder.build(context)
            val backupDir = File(requireNotNull(context.getExternalFilesDir(null)), "rear-backups")
            if (!backupDir.exists() && !backupDir.mkdirs()) return "无法创建原始卡片备份目录"
            val backup = File(backupDir, "$cardId-original.zip")
            val response = JSONObject(withContext(Dispatchers.IO) {
                service.deployCarrier(cardId, packageFile.absolutePath, backup.absolutePath)
            })
            if (response.optBoolean("success")) "已部署背屏卡片：${response.optString("message")}（卡片 ID：$cardId）"
            else "背屏卡片部署失败：${response.optString("message")}"
        }.getOrElse { "背屏卡片部署失败：${it.message ?: it.javaClass.simpleName}" }
    }

    suspend fun restore(context: Context): String {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false) ||
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return "需要先启动 Shizuku 并授权，才能恢复原始卡片"
        return runCatching {
            val service = bindUserService(context)
            val backupDir = File(requireNotNull(context.getExternalFilesDir(null)), "rear-backups")
            val backups = backupDir.listFiles()?.filter { it.name.endsWith("-original.zip") }.orEmpty()
            if (backups.size != 1) return "检测到 ${backups.size} 个备份；为避免恢复到错误卡片，请先确保只保留一个待恢复备份"
            val backup = backups.single()
            val cardId = backup.name.removeSuffix("-original.zip")
            val response = JSONObject(withContext(Dispatchers.IO) { service.restoreCarrier(cardId, backup.absolutePath) })
            if (response.optBoolean("success")) {
                PetStateStore.setEnabled(context, false)
                response.optString("message")
            } else "恢复失败：${response.optString("message")}"
        }.getOrElse { "恢复失败：${it.message ?: it.javaClass.simpleName}" }
    }

    suspend fun hide(context: Context): String = runCatching {
        PetStateStore.setEnabled(context, false)
        "已关闭背屏萌宠状态同步；卡片仍保留在系统中，可再次开启或恢复原始卡片"
    }.getOrElse { "关闭背屏萌宠失败：${it.message ?: it.javaClass.simpleName}" }

    private suspend fun bindUserService(context: Context): IRearShellService = withTimeout(15_000) {
        remoteService?.let { return@withTimeout it }
        suspendCancellableCoroutine { continuation ->
            val args = Shizuku.UserServiceArgs(ComponentName(context, RearShellUserService::class.java))
                .daemon(false).processNameSuffix("hypercat_rear_shell").tag("hypercat-rear-shell").version(2)
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val service = binder?.let { IRearShellService.Stub.asInterface(it) }
                    if (service == null) {
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Shizuku UserService 未返回有效 Binder"))
                    } else {
                        remoteService = service
                        if (continuation.isActive) continuation.resume(service)
                    }
                }
                override fun onServiceDisconnected(name: ComponentName?) {
                    remoteService = null
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Shizuku UserService 已断开"))
                }
            }
            continuation.invokeOnCancellation { runCatching { Shizuku.unbindUserService(args, connection, false) } }
            runCatching { Shizuku.bindUserService(args, connection) }.onFailure {
                if (continuation.isActive) continuation.resumeWithException(it)
            }
        }
    }
}
