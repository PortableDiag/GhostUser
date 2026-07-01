package com.ghostuser.app.model

import kotlinx.serialization.Serializable

/**
 * A single action inside a macro. Rather than a polymorphic sealed hierarchy
 * (which complicates JSON (de)serialization and in-place editing), every step is
 * one flat record whose relevant fields depend on [type].
 *
 * Coordinates are absolute screen pixels captured on the device the macro was
 * authored on. They are resolution/orientation dependent by design — a macro
 * recorded in portrait will not line up in landscape.
 */
@Serializable
data class Step(
    val type: StepType,
    /** Primary point (tap/long-press point, or swipe start). */
    val x: Float = 0f,
    val y: Float = 0f,
    /** Swipe end point (ignored for other types). */
    val x2: Float = 0f,
    val y2: Float = 0f,
    /**
     * For TAP: unused. For LONG_PRESS: hold time. For SWIPE: travel time.
     * For DELAY: how long to wait.
     */
    val durationMs: Long = DEFAULT_TAP_DURATION,
    /** How many times to repeat this single step back-to-back. */
    val repeat: Int = 1,
) {
    companion object {
        const val DEFAULT_TAP_DURATION = 40L
        const val DEFAULT_LONG_PRESS = 600L
        const val DEFAULT_SWIPE = 300L

        fun tap(x: Float, y: Float) = Step(StepType.TAP, x = x, y = y, durationMs = DEFAULT_TAP_DURATION)
        fun longPress(x: Float, y: Float) = Step(StepType.LONG_PRESS, x = x, y = y, durationMs = DEFAULT_LONG_PRESS)
        fun swipe(x: Float, y: Float, x2: Float, y2: Float) =
            Step(StepType.SWIPE, x = x, y = y, x2 = x2, y2 = y2, durationMs = DEFAULT_SWIPE)
        fun delay(ms: Long) = Step(StepType.DELAY, durationMs = ms)
    }
}

enum class StepType { TAP, LONG_PRESS, SWIPE, DELAY }

/**
 * An ordered list of [Step]s. When [loop] is true the whole sequence repeats:
 * [loopCount] times, or forever when it is 0. [loopDelayMs] is the pause
 * inserted between iterations (the core knob for a plain auto-clicker).
 */
@Serializable
data class Macro(
    val id: String,
    val name: String,
    val steps: List<Step> = emptyList(),
    val loop: Boolean = true,
    val loopCount: Int = 0,
    val loopDelayMs: Long = 100,
) {
    val isAutoClicker: Boolean
        get() = steps.isNotEmpty() && steps.all { it.type == StepType.TAP }
}
