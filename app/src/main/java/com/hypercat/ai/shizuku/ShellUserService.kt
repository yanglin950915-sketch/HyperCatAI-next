package com.hypercat.ai.shizuku

import android.content.Context
import android.os.RemoteException
import android.util.Log
import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Runs inside Shizuku's shell-identity UserService process.
 * Only fixed, app-authored commands are sent by the UI.
 */
class ShellUserService : IRemoteShell.Stub() {
    @Keep
    constructor(context: Context) : this()

    constructor() : super()

    @Throws(RemoteException::class)
    override fun exec(command: String): String {
        // Restrict this bridge to the small set of diagnostics/launch operations used by HyperCat.
        val allowed = command == "am display list" ||
            command == "am start --display 1 -n com.hypercat.ai/.MainActivity"
        if (!allowed) return "拒绝执行未授权命令"
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val exitCode = process.waitFor()
            "exit=$exitCode\n$output"
        } catch (e: Exception) {
            "执行失败：${e.message}"
        }
    }

    override fun destroy() {
        Log.i("HyperCatShell", "Shizuku user service destroyed")
        System.exit(0)
    }
}
