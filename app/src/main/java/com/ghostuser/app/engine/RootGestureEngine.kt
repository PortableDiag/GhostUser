package com.ghostuser.app.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Injects gestures by piping `input` commands into a persistent root shell.
 *
 * Note the trade-off vs [AccessibilityGestureEngine]: `input tap` spawns a
 * short-lived command process inside the shell, so it is not free — but it works
 * even over the top of apps that block synthetic accessibility gestures, and on
 * most devices sustains a higher effective tap rate.
 */
class RootGestureEngine(private val shell: RootShell) : GestureEngine {

    @Volatile
    private var available = true

    override val name: String = "Root"

    override fun isAvailable(): Boolean = available

    override suspend fun tap(x: Float, y: Float, durationMs: Long) = withContext(Dispatchers.IO) {
        // `input tap` has no duration; emulate a held tap with a zero-length
        // swipe when the caller asked for a meaningful hold.
        if (durationMs > 80) {
            run("input swipe ${x.i} ${y.i} ${x.i} ${y.i} ${durationMs}")
        } else {
            run("input tap ${x.i} ${y.i}")
        }
    }

    override suspend fun swipe(
        x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long,
    ) = withContext(Dispatchers.IO) {
        run("input swipe ${x1.i} ${y1.i} ${x2.i} ${y2.i} ${durationMs}")
    }

    private fun run(cmd: String) {
        if (!shell.write(cmd)) available = false
    }

    override fun shutdown() {
        shell.close()
    }

    private val Float.i: Int get() = this.roundToInt()
}
