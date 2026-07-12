package com.ghostuser.app.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
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
        // Mark connected FIRST so the service counts as up even if the optional
        // overlay setup below fails — the gesture engine works without it, and
        // nothing here should be able to make the system tear the binding down.
        instance = this
        _connected.value = true
        // Connecting is NOT a request for the panel. The system binds us on boot,
        // after an app update, and whenever it restarts our process — showing the
        // panel here put it on screen with the user having done nothing. Restore
        // it only if they left it showing.
        if (OverlayPrefs.visible.value) {
            // Deferred and guarded: on some OEM ROMs adding a window during
            // onServiceConnected can throw, which would crash the service and make
            // the accessibility toggle flip back off.
            Handler(Looper.getMainLooper()).post { ensureOverlay()?.showPanel() }
        }
    }

    /** Show the floating panel and remember that the user wants it. */
    fun showOverlayPanel() {
        OverlayPrefs.setVisible(true)
        ensureOverlay()?.showPanel()
    }

    /** Hide the floating panel and remember that the user dismissed it. */
    fun hideOverlayPanel() {
        OverlayPrefs.setVisible(false)
        overlay?.hidePanel()
    }

    /** Enter the full-screen point-picker used by the macro editor. */
    fun startPointPicker() {
        ensureOverlay()?.startPointPicker()
    }

    /**
     * The overlay host, created on demand. Constructing it draws nothing — windows
     * are added only by [OverlayController.showPanel] / [OverlayController.startPointPicker] —
     * so the picker still works even when the panel is hidden.
     */
    private fun ensureOverlay(): OverlayController? {
        overlay?.let { return it }
        return runCatching { OverlayController(this).also { it.start() } }
            .onSuccess { overlay = it }
            .getOrNull()
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
