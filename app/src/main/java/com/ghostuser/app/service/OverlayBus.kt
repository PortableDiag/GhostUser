package com.ghostuser.app.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** A point picked on the full-screen picker overlay, in absolute screen pixels. */
data class PickedPoint(val x: Float, val y: Float)

/**
 * Thin event bus connecting the in-app macro editor (an Activity) with the
 * point-picker overlay (hosted by the accessibility service). Both live in the
 * same process, so a shared flow is all the coordination we need.
 */
object OverlayBus {

    /** Emits each tap the user makes while the picker overlay is active. */
    private val _pickedPoints = MutableSharedFlow<PickedPoint>(extraBufferCapacity = 32)
    val pickedPoints: SharedFlow<PickedPoint> = _pickedPoints.asSharedFlow()

    /** True while the picker overlay is on screen. */
    private val _picking = MutableStateFlow(false)
    val picking: StateFlow<Boolean> = _picking.asStateFlow()

    /** True while the floating recorder is capturing gestures. */
    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    fun setRecording(active: Boolean) {
        _recording.value = active
    }

    /** The macro the floating panel controls (usually the last one edited). */
    private val _selectedMacroId = MutableStateFlow<String?>(null)
    val selectedMacroId: StateFlow<String?> = _selectedMacroId.asStateFlow()

    fun emitPoint(x: Float, y: Float) {
        _pickedPoints.tryEmit(PickedPoint(x, y))
    }

    fun setPicking(active: Boolean) {
        _picking.value = active
    }

    fun selectMacro(id: String?) {
        _selectedMacroId.value = id
    }
}
