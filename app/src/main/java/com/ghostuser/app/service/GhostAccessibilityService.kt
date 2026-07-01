package com.ghostuser.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one privileged component in the app. It exists to:
 *  1. Provide a live [AccessibilityService] instance so gestures can be
 *     dispatched without root ([com.ghostuser.app.engine.AccessibilityGestureEngine]).
 *  2. Host every over-other-apps window via [OverlayController].
 *
 * It deliberately does NOT read window content — see the config's
 * canRetrieveWindowContent="false". We never inspect what's on screen.
 */
class GhostAccessibilityService : AccessibilityService() {

    private var overlay: OverlayController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _connected.value = true
        overlay = OverlayController(this).also { it.start() }
    }

    /** Re-show the floating panel (e.g. after the user closed it). */
    fun showOverlayPanel() {
        overlay?.showPanel()
    }

    /** Enter the full-screen point-picker used by the macro editor. */
    fun startPointPicker() {
        overlay?.startPointPicker()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Injection-only service: nothing to observe.
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        overlay?.destroy()
        overlay = null
        if (instance === this) instance = null
        _connected.value = false
    }

    companion object {
        @Volatile
        var instance: GhostAccessibilityService? = null
            private set

        private val _connected = MutableStateFlow(false)

        /** Observed by the UI to reflect whether the service is enabled/running. */
        val connected: StateFlow<Boolean> = _connected.asStateFlow()
    }
}
