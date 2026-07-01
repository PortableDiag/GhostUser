package com.ghostuser.app.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * A single long-lived `su` process. Keeping one shell open avoids paying the
 * ~50-150ms cost of spawning a new `su` per command, which matters a lot when a
 * macro is firing dozens of `input` calls per second.
 */
class RootShell {

    private var process: Process? = null
    private var stdin: DataOutputStream? = null

    @Synchronized
    private fun ensureStarted(): Boolean {
        if (process != null) return true
        return try {
            val p = Runtime.getRuntime().exec("su")
            process = p
            stdin = DataOutputStream(p.outputStream)
            true
        } catch (t: Throwable) {
            process = null
            stdin = null
            false
        }
    }

    /** Runs a command, not waiting for output. Fire-and-forget (fast path). */
    @Synchronized
    fun write(command: String): Boolean {
        if (!ensureStarted()) return false
        return try {
            stdin?.apply {
                writeBytes(command + "\n")
                flush()
            }
            true
        } catch (t: Throwable) {
            // The shell died (root revoked, OOM-killed). Reset so the next call
            // tries to respawn it.
            close()
            false
        }
    }

    @Synchronized
    fun close() {
        try { stdin?.writeBytes("exit\n"); stdin?.flush() } catch (_: Throwable) {}
        try { stdin?.close() } catch (_: Throwable) {}
        try { process?.destroy() } catch (_: Throwable) {}
        stdin = null
        process = null
    }

    companion object {
        /**
         * One-shot check for root availability. Spawns a throwaway `su -c id`
         * and looks for uid=0 in the output. Must not run on the main thread.
         */
        suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                val out = BufferedReader(InputStreamReader(p.inputStream)).readText()
                p.waitFor()
                out.contains("uid=0")
            } catch (t: Throwable) {
                false
            }
        }
    }
}
