package com.ghostuser.app.engine

import com.ghostuser.app.data.EngineMode
import com.ghostuser.app.service.GhostAccessibilityService

/**
 * Resolves an [EngineMode] preference into a concrete, currently-usable
 * [GestureEngine]. Returns null when nothing can run yet (e.g. ACCESSIBILITY was
 * requested but the service isn't enabled), so callers can surface a clear
 * "enable the service first" message instead of silently doing nothing.
 */
object EngineProvider {

    /**
     * Set to true once a root check has succeeded at least once this session.
     * The Settings screen updates it after probing; AUTO mode consults it to
     * decide whether to even attempt the root path.
     */
    @Volatile
    var rootConfirmed: Boolean = false

    /**
     * The user's current engine preference, mirrored here so the overlay (which
     * has no easy DataStore access) can resolve an engine synchronously. The app
     * keeps this updated whenever settings change.
     */
    @Volatile
    var mode: EngineMode = EngineMode.AUTO

    private var cachedRoot: RootGestureEngine? = null

    fun rootEngine(): RootGestureEngine =
        cachedRoot ?: RootGestureEngine(RootShell()).also { cachedRoot = it }

    private fun accessibilityEngine(): AccessibilityGestureEngine? =
        GhostAccessibilityService.instance?.let { AccessibilityGestureEngine(it) }

    fun resolve(mode: EngineMode): GestureEngine? = when (mode) {
        EngineMode.ROOT -> rootEngine().takeIf { it.isAvailable() }
        EngineMode.ACCESSIBILITY -> accessibilityEngine()
        EngineMode.AUTO -> {
            val root = if (rootConfirmed) rootEngine().takeIf { it.isAvailable() } else null
            root ?: accessibilityEngine()
        }
    }
}
