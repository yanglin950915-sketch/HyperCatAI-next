package com.hypercat.ai.rear

import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader

/** Executes only the display inspection and app projection commands as Shizuku shell. */
@Keep
class RearShellUserService : IRearShellService.Stub() {
    override fun exec(command: String): String {
        val displayId = Regex("""am start --display (\d+) -n com\.hypercat\.ai/\.rear\.RearPetActivity""")
            .matchEntire(command)
        val allowed = command == "dumpsys display" ||
            displayId != null ||
            command == "am broadcast -a com.hypercat.ai.rear.ACTION_HIDE_PROJECTION -p com.hypercat.ai"
        if (!allowed) throw SecurityException("Unsupported rear projection command")
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
        val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()
        val exitCode = process.waitFor()
        return "$exitCode\n$stdout\n$stderr"
    }

    override fun destroy() {
        System.exit(0)
    }
}
