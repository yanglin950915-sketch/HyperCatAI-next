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

data class RearCard(val cardId: String, val name: String, val source: String, val path: String, val root: String)

/** Shizuku-backed, user-selected deployment of a bundled MAML pet card. */
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

    suspend fun scan(context: Context): List<RearCard> = withContext(Dispatchers.IO) {
        val service = requireService(context)
        val response = JSONObject(service.scanCarriers())
        if (!response.optBoolean("success")) error(response.optString("message", "扫描失败"))
        val array = response.optJSONArray("cards") ?: org.json.JSONArray()
        val cards = (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { item ->
                RearCard(item.optString("cardId"), item.optString("name"), item.optString("source"),
                    item.optString("path"), item.optString("root"))
            }
        }
        if (cards.isEmpty()) error(response.optString("message", "没有找到可替换的背屏资源"))
        cards
    }

    suspend fun deploy(context: Context, card: RearCard): String = withContext(Dispatchers.IO) {
        val service = requireService(context)
        val packageFile = RearCardPackageBuilder.build(context)
        val backupDir = File(requireNotNull(context.getExternalFilesDir(null)), "rear-backups")
        if (!backupDir.exists() && !backupDir.mkdirs()) error("无法创建原始卡片备份目录")
        val backup = File(backupDir, "${card.cardId}-original.zip")
        val response = JSONObject(service.deployCarrier(card.cardId, packageFile.absolutePath, backup.absolutePath))
        if (!response.optBoolean("success")) error(response.optString("message", "部署失败"))
        "已替换「${card.name}」：${response.optString("message")}"
    }

    fun listBackups(context: Context): List<String> {
        val backupDir = File(requireNotNull(context.getExternalFilesDir(null)), "rear-backups")
        return backupDir.listFiles()?.filter { it.isFile && it.name.endsWith("-original.zip") }
            ?.map { it.name.removeSuffix("-original.zip") }?.sorted().orEmpty()
    }

    suspend fun restore(context: Context, cardId: String): String = withContext(Dispatchers.IO) {
        val service = requireService(context)
        val backup = File(File(requireNotNull(context.getExternalFilesDir(null)), "rear-backups"), "$cardId-original.zip")
        val response = JSONObject(service.restoreCarrier(cardId, backup.absolutePath))
        if (!response.optBoolean("success")) error(response.optString("message", "恢复失败"))
        PetStateStore.setEnabled(context, false)
        response.optString("message")
    }

    suspend fun hide(context: Context): String = runCatching {
        PetStateStore.setEnabled(context, false)
        "已关闭背屏萌宠状态同步；卡片仍保留在系统中，可再次开启或恢复原始卡片"
    }.getOrElse { "关闭背屏萌宠失败：${it.message ?: it.javaClass.simpleName}" }

    private suspend fun requireService(context: Context): IRearShellService {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) error("Shizuku 服务未运行，请先启动 Shizuku")
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) error("尚未获得 Shizuku 授权")
        return bindUserService(context)
    }

    private suspend fun bindUserService(context: Context): IRearShellService = withTimeout(15_000) {
        remoteService?.let { return@withTimeout it }
        suspendCancellableCoroutine { continuation ->
            val args = Shizuku.UserServiceArgs(ComponentName(context, RearShellUserService::class.java))
                .daemon(false).processNameSuffix("hypercat_rear_shell").tag("hypercat-rear-shell").version(3)
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
