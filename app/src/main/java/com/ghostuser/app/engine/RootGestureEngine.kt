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

    /**
     * Epoch-millis until which this engine reports itself unavailable, set by a
     * failed shell write and cleared by the next successful one.
     *
     * Deliberately a cooldown rather than a permanent flag. [RootShell.write]
     * already self-heals — on a throw it closes the dead shell so the next call
     * respawns `su` — so a single transient hiccup (root prompt timing out, the
     * shell OOM-killed, Magisk re-authorising) must not disable root for the
     * life of the process. It previously did: [EngineProvider.resolve] and
     * [PlaybackController.start] both gate on [isAvailable], so once the flag
     * latched off, [run] was never reached again and nothing could ever clear
     * it. AUTO then silently fell back to accessibility until a force-stop.
     *
     * Re-probing instead would mean spawning `su`, and [isAvailable] is called
     * synchronously from the overlay on the main thread. A clock check is free.
     */
    @Volatile
    private var unavailableUntilMs = 0L

    override val name: String = "Root"

    override fun isAvailable(): Boolean = System.currentTimeMillis() >= unavailableUntilMs

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
        if (shell.write(cmd)) {
            unavailableUntilMs = 0L
        } else {
            // Back off briefly rather than hammering a shell that just died —
            // but let the next attempt after the cooldown respawn it.
            unavailableUntilMs = System.currentTimeMillis() + FAILURE_COOLDOWN_MS
        }
    }

    override fun shutdown() {
        shell.close()
    }

    private val Float.i: Int get() = this.roundToInt()

    private companion object {
        /**
         * How long a failed write keeps the engine marked unavailable. Long
         * enough to stop a dead shell being hammered for the rest of a run,
         * short enough that the user's next playback attempt retries root.
         */
        const val FAILURE_COOLDOWN_MS = 5_000L
    }
}
