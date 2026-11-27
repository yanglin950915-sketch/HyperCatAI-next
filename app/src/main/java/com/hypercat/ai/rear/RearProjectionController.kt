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
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Direct rear-display projection through Shizuku UserService.
 * The display selector fails closed unless the framework reports a known rear
 * display name or the 904x572 Xiaomi rear-panel logical size.
 */
object RearProjectionController {
    private const val PERMISSION_REQUEST_CODE = 24024
    private const val HIDE_ACTION = "com.hypercat.ai.rear.ACTION_HIDE_PROJECTION"
    @Volatile private var remoteService: IRearShellService? = null

    fun requestPermission(
        activity: Activity,
        callback: (granted: Boolean, message: String) -> Unit
    ) {
        val available = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!available) {
            callback(false, "未检测到正在运行的 Shizuku。请先安装并启动 Shizuku，再重新开启背屏投放。")
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            callback(true, "Shizuku 授权已就绪")
            return
        }
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != PERMISSION_REQUEST_CODE) return
                Shizuku.removeRequestPermissionResultListener(this)
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                callback(granted, if (granted) "Shizuku 授权成功" else "Shizuku 授权未通过，背屏投放未开启")
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
            .onFailure {
                Shizuku.removeRequestPermissionResultListener(listener)
                callback(false, "无法请求 Shizuku 授权：${it.message ?: "未知错误"}")
            }
    }

    suspend fun connect(context: Context): String {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            return "Shizuku 服务未运行，请先启动 Shizuku"
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return "尚未获得 Shizuku 授权，请重新开启背屏投放"
        }
        return runCatching {
            val dump = runShell(context, "dumpsys display")
            if (dump.exitCode != 0) return "读取系统显示器失败：${dump.output.take(240)}"
            val displayId = findRearDisplayId(dump.output)
                ?: return "没有从系统信息中可靠识别到背屏 display。为避免把萌宠投到外接屏，已停止自动投放。"
            val launch = runShell(
                context,
                "am start --display $displayId -n com.hypercat.ai/.rear.RearPetActivity"
            )
            if (launch.exitCode != 0 || launch.output.contains("Error:", ignoreCase = true)) {
                "背屏 display=$displayId 已识别，但系统拒绝启动萌宠 Activity：${launch.output.take(260)}"
            } else {
                "已请求背屏显示（display=$displayId）。请检查背屏是否出现萌宠；首次设备验证仍需确认 HyperOS 是否允许该 display 启动第三方 Activity。"
            }
        }.getOrElse { "背屏投放失败：${it.message ?: it.javaClass.simpleName}" }
    }

    suspend fun hide(context: Context): String {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false) ||
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
        ) {
            return "背屏萌宠已在 Agent 中关闭；Shizuku 不可用，无法发送远程关闭命令"
        }
        return runCatching {
            val result = runShell(context, "am broadcast -a $HIDE_ACTION -p com.hypercat.ai")
            if (result.exitCode == 0) "已发送背屏关闭指令" else "关闭指令失败：${result.output.take(220)}"
        }.getOrElse { "关闭背屏失败：${it.message ?: it.javaClass.simpleName}" }
    }

    private data class ShellResult(val exitCode: Int, val output: String)

    private suspend fun runShell(context: Context, command: String): ShellResult {
        val service = remoteService ?: bindUserService(context)
        val raw = withContext(Dispatchers.IO) { service.exec(command) }
        val exitCode = raw.substringBefore('\n').toIntOrNull() ?: -1
        return ShellResult(exitCode, raw.substringAfter('\n', "").trim())
    }

    private suspend fun bindUserService(context: Context): IRearShellService = withTimeout(15_000) {
        suspendCancellableCoroutine { continuation ->
            val args = Shizuku.UserServiceArgs(
                ComponentName(context, RearShellUserService::class.java)
            )
                .daemon(false)
                .processNameSuffix("hypercat_rear_shell")
                .tag("hypercat-rear-shell")
                .version(1)
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val service = binder?.let { IRearShellService.Stub.asInterface(it) }
                    if (service == null) {
                        if (continuation.isActive) continuation.resumeWithException(
                            IllegalStateException("Shizuku UserService 未返回有效 Binder")
                        )
                    } else {
                        remoteService = service
                        if (continuation.isActive) continuation.resume(service)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    remoteService = null
                    if (continuation.isActive) continuation.resumeWithException(
                        IllegalStateException("Shizuku UserService 已断开")
                    )
                }
            }
            continuation.invokeOnCancellation {
                runCatching { Shizuku.unbindUserService(args, connection, false) }
            }
            runCatching { Shizuku.bindUserService(args, connection) }
                .onFailure {
                    if (continuation.isActive) continuation.resumeWithException(it)
                }
        }
    }

    private fun findRearDisplayId(dump: String): Int? {
        val candidates = linkedSetOf<Int>()
        val displayInfo = Regex("""DisplayInfo\{([^}]+)}""")
        val idPattern = Regex("""displayId[ =]+(\d+)""")
        val rearName = Regex("""(?i)(rear|back|subscreen|secondary|副屏|背屏)""")
        val dimensions = Regex("""(?:logicalWidth|realWidth|appWidth)[ =]+(\d+).*?(?:logicalHeight|realHeight|appHeight)[ =]+(\d+)""")
        displayInfo.findAll(dump).forEach { match ->
            val block = match.groupValues[1]
            val id = idPattern.find(block)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val size = dimensions.find(block)
            val width = size?.groupValues?.getOrNull(1)?.toIntOrNull()
            val height = size?.groupValues?.getOrNull(2)?.toIntOrNull()
            val rearSize = (width == 904 && height == 572) || (width == 572 && height == 904)
            if (id != null && id != 0 && (rearName.containsMatchIn(block) || rearSize)) candidates += id
        }
        return candidates.singleOrNull()
    }
}
