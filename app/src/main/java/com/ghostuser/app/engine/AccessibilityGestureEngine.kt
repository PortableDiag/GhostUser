package com.ghostuser.app.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/**
 * Injects gestures via [AccessibilityService.dispatchGesture]. Holds only a
 * [WeakReference] to the service so a torn-down service can be garbage
 * collected; [isAvailable] reflects whether a live service is still attached.
 */
class AccessibilityGestureEngine(service: AccessibilityService) : GestureEngine {

    private val serviceRef = WeakReference(service)

    override val name: String = "Accessibility"

    override fun isAvailable(): Boolean = serviceRef.get() != null

    override suspend fun tap(x: Float, y: Float, durationMs: Long) {
        val path = Path().apply { moveTo(x, y) }
        dispatch(path, 0, durationMs.coerceAtLeast(1))
    }

    override suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        dispatch(path, 0, durationMs.coerceAtLeast(1))
    }

    private suspend fun dispatch(path: Path, startTime: Long, duration: Long) {
        val service = serviceRef.get() ?: return
        val stroke = GestureDescription.StrokeDescription(path, startTime, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        suspendCancellableCoroutine { cont ->
            val ok = service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        // Treat a cancelled gesture as "done" so playback keeps
                        // its cadence instead of hanging forever.
                        if (cont.isActive) cont.resume(Unit)
                    }
                },
                null,
            )
            if (!ok && cont.isActive) cont.resume(Unit)
        }
    }
}
