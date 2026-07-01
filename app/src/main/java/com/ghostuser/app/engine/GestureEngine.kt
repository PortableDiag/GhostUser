package com.ghostuser.app.engine

/**
 * Abstraction over "how a tap/swipe actually gets injected into the system".
 * Two implementations exist:
 *
 *  - [AccessibilityGestureEngine] dispatches gestures through the
 *    AccessibilityService (no root, works on stock devices).
 *  - [RootGestureEngine] pipes `input` commands to a persistent `su` shell
 *    (rooted devices; can sustain a higher tap rate).
 *
 * All methods suspend until the gesture has been handed off, so the macro
 * player can pace steps deterministically.
 */
interface GestureEngine {

    /** Human-readable name shown in the UI/logs. */
    val name: String

    /** Whether this engine can run right now (service connected / root granted). */
    fun isAvailable(): Boolean

    suspend fun tap(x: Float, y: Float, durationMs: Long)

    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long)

    /** Long-press is just a stationary "swipe" held for [durationMs]. */
    suspend fun longPress(x: Float, y: Float, durationMs: Long) =
        swipe(x, y, x, y, durationMs)

    /** Release any held resources (e.g. the su shell). */
    fun shutdown() {}
}
