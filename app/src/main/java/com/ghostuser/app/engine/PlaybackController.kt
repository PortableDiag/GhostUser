package com.ghostuser.app.engine

import com.ghostuser.app.model.Macro
import com.ghostuser.app.model.Step
import com.ghostuser.app.model.StepType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Global playback engine. A process-wide singleton so the floating overlay, the
 * app UI, and the accessibility service all observe and control the same run.
 * Only one macro plays at a time — starting a new one cancels the previous.
 */
object PlaybackController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _activeMacroId = MutableStateFlow<String?>(null)
    val activeMacroId: StateFlow<String?> = _activeMacroId.asStateFlow()

    /** Non-null while playback is in a failed state (e.g. engine went away). */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun toggle(macro: Macro, engine: GestureEngine?) {
        if (_isPlaying.value && _activeMacroId.value == macro.id) stop() else start(macro, engine)
    }

    fun start(macro: Macro, engine: GestureEngine?) {
        stop()
        if (engine == null || !engine.isAvailable()) {
            _lastError.value = "No gesture engine available. Enable the accessibility service or grant root."
            return
        }
        if (macro.steps.isEmpty()) {
            _lastError.value = "This macro has no steps."
            return
        }
        _lastError.value = null
        job = scope.launch {
            _isPlaying.value = true
            _activeMacroId.value = macro.id
            try {
                var iteration = 0
                while (isActive) {
                    for (step in macro.steps) {
                        if (!isActive) break
                        runStep(step, engine)
                    }
                    iteration++
                    if (!macro.loop) break
                    if (macro.loopCount in 1..iteration) break
                    if (macro.loopDelayMs > 0) delay(macro.loopDelayMs)
                }
            } finally {
                _isPlaying.value = false
                _activeMacroId.value = null
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _isPlaying.value = false
        _activeMacroId.value = null
    }

    private suspend fun runStep(step: Step, engine: GestureEngine) {
        repeat(step.repeat.coerceAtLeast(1)) {
            when (step.type) {
                StepType.TAP -> engine.tap(step.x, step.y, step.durationMs)
                StepType.LONG_PRESS -> engine.longPress(step.x, step.y, step.durationMs)
                StepType.SWIPE -> engine.swipe(step.x, step.y, step.x2, step.y2, step.durationMs)
                StepType.DELAY -> delay(step.durationMs)
            }
        }
    }
}
