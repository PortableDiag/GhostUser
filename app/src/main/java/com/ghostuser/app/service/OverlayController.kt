package com.ghostuser.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.ghostuser.app.MainActivity
import com.ghostuser.app.data.MacroRepository
import com.ghostuser.app.engine.EngineProvider
import com.ghostuser.app.engine.PlaybackController
import com.ghostuser.app.model.Macro
import com.ghostuser.app.model.Step
import com.ghostuser.app.ui.theme.OVERLAY_ACCENT_HEX
import com.ghostuser.app.ui.theme.OVERLAY_DANGER_HEX
import com.ghostuser.app.ui.theme.OVERLAY_SURFACE_HEX
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Owns every window this app draws over other apps. Runs inside the accessibility
 * service, so it uses TYPE_ACCESSIBILITY_OVERLAY windows — no draw-over-apps
 * permission required.
 *
 * The control surface has two forms sharing one movable window:
 *  - a small draggable **bubble** (tap to expand, drag to move)
 *  - an expanded **control row**: Record · Start · Stop · Open app · Collapse
 *
 * Collapsing returns to the bubble; the window is never fully removed by the
 * user, so the controls can't get "lost". A separate full-screen layer is used
 * for the editor's point picker and for gesture recording.
 */
class OverlayController(private val service: AccessibilityService) {

    private val wm = service.getSystemService(WindowManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop

    private val accent = Color.parseColor(OVERLAY_ACCENT_HEX)
    private val danger = Color.parseColor(OVERLAY_DANGER_HEX)
    private val surface = Color.parseColor(OVERLAY_SURFACE_HEX)
    private val onDark = Color.WHITE

    // Shared movable control window.
    private var panel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var expanded = false
    private var recButton: TextView? = null
    private var startButton: TextView? = null
    private var loopButton: TextView? = null

    /**
     * When true, Start plays the selected macro on an infinite loop (until Stop),
     * regardless of the macro's saved loop setting. Defaults to on — the common
     * "record a gesture and repeat it" workflow. Toggled by the ⟳ panel button.
     */
    private var loopEnabled = true

    // Full-screen capture layer (point picker OR recorder).
    private var capture: View? = null
    private var pickerCount = 0

    // Recording state.
    private val recorded = mutableListOf<Step>()
    private var recLastEnd = 0L
    private var recDownX = 0f
    private var recDownY = 0f
    private var recDownT = 0L
    private var recLastX = 0f
    private var recLastY = 0f

    private val overlayType: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

    private fun dp(v: Int): Int = (v * service.resources.displayMetrics.density).roundToInt()

    // ---- lifecycle -------------------------------------------------------

    fun start() {
        ensureShown()
        scope.launch {
            combine(PlaybackController.isPlaying, OverlayBus.recording) { playing, recording ->
                playing to recording
            }.collect { (playing, recording) ->
                startButton?.text = if (playing) "■" else "▶"
                startButton?.setTextColor(if (playing) danger else accent)
                recButton?.setTextColor(if (recording) danger else onDark)
                recButton?.text = if (recording) "■" else "●"
            }
        }
    }

    fun destroy() {
        removeCapture()
        removePanel()
        (scope.coroutineContext[Job])?.cancel()
    }

    // ---- movable control window -----------------------------------------

    /** Public entry: make sure the control window exists (as a bubble). */
    fun ensureShown() {
        if (panel == null) {
            expanded = false
            addPanelWindow()
        }
        renderPanel()
    }

    fun showPanel() = ensureShown()

    private fun addPanelWindow() {
        val container = FrameLayout(service)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(140)
        }
        panel = container
        panelParams = params
        runCatching { wm.addView(container, params) }
    }

    /** Rebuild the window content for the current expanded/collapsed state. */
    private fun renderPanel() {
        val container = panel as? FrameLayout ?: return
        container.removeAllViews()
        recButton = null
        startButton = null
        loopButton = null
        if (expanded) container.addView(buildExpandedRow()) else container.addView(buildBubble())
    }

    private fun buildBubble(): View {
        val bubble = TextView(service).apply {
            text = "◉"
            gravity = Gravity.CENTER
            setTextColor(onDark)
            textSize = 22f
            val s = dp(52)
            layoutParams = FrameLayout.LayoutParams(s, s)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(surface)
                setStroke(dp(2), accent)
            }
        }
        makeInteractive(bubble) { expanded = true; renderPanel() }
        return bubble
    }

    private fun buildExpandedRow(): View {
        val grip = glyph("⠿", onDark)
        makeInteractive(grip) { /* tap on grip does nothing; drag moves */ }

        val rec = glyph("●", onDark).also { recButton = it }
        rec.setOnClickListener { onRecordPressed() }

        val start = glyph("▶", accent).also { startButton = it }
        start.setOnClickListener { onStartPressed() }

        val loop = glyph("⟳", if (loopEnabled) accent else onDark).also { loopButton = it }
        loop.setOnClickListener {
            loopEnabled = !loopEnabled
            loopButton?.setTextColor(if (loopEnabled) accent else onDark)
            toast(if (loopEnabled) "Loop on" else "Loop off")
        }

        val stop = glyph("■", onDark)
        stop.setOnClickListener {
            PlaybackController.stop()
            if (OverlayBus.recording.value) stopRecording()
        }

        val app = glyph("☰", onDark)
        app.setOnClickListener {
            service.startActivity(
                Intent(service, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        val collapse = glyph("▽", onDark)
        collapse.setOnClickListener { expanded = false; renderPanel() }

        val close = glyph("✕", danger)
        close.setOnClickListener {
            PlaybackController.stop()
            if (OverlayBus.recording.value) stopRecording()
            removePanel()
            toast("Controls hidden — reopen from the GhostUser app")
        }

        return LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(26).toFloat()
                setColor(surface)
                setStroke(dp(1), accent)
            }
            addView(grip)
            addView(rec)
            addView(start)
            addView(loop)
            addView(stop)
            addView(app)
            addView(collapse)
            addView(close)
        }
    }

    private fun glyph(text: String, tint: Int): TextView = TextView(service).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextColor(tint)
        textSize = 18f
        val s = dp(40)
        layoutParams = LinearLayout.LayoutParams(s, s)
    }

    private fun onStartPressed() {
        val id = OverlayBus.selectedMacroId.value
            ?: MacroRepository.macros.value.firstOrNull()?.id
        val macro = id?.let { MacroRepository.get(it) }
        if (macro == null) {
            toast("No macro yet — record one or create it in the app")
            return
        }
        val engine = EngineProvider.resolve(EngineProvider.mode)
        if (engine == null) {
            toast("No gesture engine available")
            return
        }
        // The ⟳ toggle overrides the macro's saved loop setting for quick plays:
        // on → repeat forever, off → run once.
        val toPlay = if (loopEnabled) {
            macro.copy(loop = true, loopCount = 0)
        } else {
            macro.copy(loop = false)
        }
        PlaybackController.toggle(toPlay, engine)
    }

    private fun onRecordPressed() {
        if (OverlayBus.recording.value) stopRecording() else startRecording()
    }

    private fun removePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
        panelParams = null
        recButton = null
        startButton = null
    }

    // ---- point picker (used by the macro editor) ------------------------

    fun startPointPicker() {
        if (capture != null) return
        pickerCount = 0
        OverlayBus.setPicking(true)

        val root = captureRoot("Tap targets to add points  •  Done when finished")
        addDoneButton(root) { finishPointPicker() }
        root.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) {
                addMarker(root, e.rawX, e.rawY)
                OverlayBus.emitPoint(e.rawX, e.rawY)
            }
            false
        }
        addCaptureWindow(root)
    }

    private fun finishPointPicker() {
        removeCapture()
        OverlayBus.setPicking(false)
        returnToApp()
    }

    // ---- gesture recorder -----------------------------------------------

    fun startRecording() {
        if (capture != null) return
        recorded.clear()
        recLastEnd = 0L
        OverlayBus.setRecording(true)
        // Collapse the panel so it's out of the way while recording.
        expanded = false
        renderPanel()

        val root = captureRoot("● Recording — your taps are captured here, not sent to the app below")
        addDoneButton(root) { stopRecording() }
        root.setOnTouchListener { _, e ->
            handleRecordTouch(e)
            true
        }
        addCaptureWindow(root)
        toast("Recording started")
    }

    private fun handleRecordTouch(e: MotionEvent) {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                recDownX = e.rawX; recDownY = e.rawY
                recLastX = e.rawX; recLastY = e.rawY
                recDownT = SystemClock.uptimeMillis()
            }
            MotionEvent.ACTION_MOVE -> {
                recLastX = e.rawX; recLastY = e.rawY
            }
            MotionEvent.ACTION_UP -> {
                val up = SystemClock.uptimeMillis()
                if (recLastEnd > 0L) {
                    val gap = (recDownT - recLastEnd).coerceIn(0L, 600_000L)
                    if (gap > 0) recorded.add(Step.delay(gap))
                }
                val dist = hypot((recLastX - recDownX).toDouble(), (recLastY - recDownY).toDouble())
                val dur = (up - recDownT).coerceAtLeast(1L)
                if (dist > touchSlop) {
                    recorded.add(Step.swipe(recDownX, recDownY, recLastX, recLastY).copy(durationMs = dur))
                } else if (dur > 400L) {
                    recorded.add(Step.longPress(recDownX, recDownY).copy(durationMs = dur))
                } else {
                    recorded.add(Step.tap(recDownX, recDownY))
                }
                recLastEnd = up
            }
        }
    }

    fun stopRecording() {
        removeCapture()
        OverlayBus.setRecording(false)
        if (recorded.isNotEmpty()) {
            val macro = Macro(
                id = MacroRepository.newId(),
                name = "Recording",
                steps = recorded.toList(),
                // Recordings default to looping — the usual reason to record a
                // gesture is to repeat it. Adjust count/interval in the editor.
                loop = true,
                loopCount = 0,
                loopDelayMs = 0,
            )
            MacroRepository.upsert(macro)
            OverlayBus.selectMacro(macro.id)
            toast("Saved recording (${recorded.size} steps) — press ▶ to loop")
        } else {
            toast("Nothing recorded")
        }
        recorded.clear()
    }

    // ---- shared capture-layer helpers -----------------------------------

    private fun captureRoot(hintText: String): FrameLayout {
        val root = FrameLayout(service).apply { setBackgroundColor(Color.parseColor("#33000000")) }
        val hint = TextView(service).apply {
            text = hintText
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(surface)
            }
        }
        root.addView(hint, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; topMargin = dp(40) })
        return root
    }

    private fun addDoneButton(root: FrameLayout, onDone: () -> Unit) {
        val done = TextView(service).apply {
            text = "Done"
            setTextColor(onDark)
            textSize = 16f
            setPadding(dp(30), dp(12), dp(30), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(accent)
            }
            setOnClickListener { onDone() }
        }
        root.addView(done, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; bottomMargin = dp(48) })
    }

    private fun addCaptureWindow(root: FrameLayout) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        capture = root
        runCatching { wm.addView(root, params) }
    }

    private fun addMarker(root: FrameLayout, rawX: Float, rawY: Float) {
        pickerCount++
        val size = dp(28)
        val marker = TextView(service).apply {
            text = pickerCount.toString()
            gravity = Gravity.CENTER
            setTextColor(onDark)
            textSize = 12f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accent)
                setStroke(dp(2), Color.WHITE)
            }
        }
        root.addView(marker, FrameLayout.LayoutParams(size, size).apply {
            leftMargin = (rawX - size / 2).roundToInt()
            topMargin = (rawY - size / 2).roundToInt()
        })
    }

    private fun removeCapture() {
        capture?.let { runCatching { wm.removeView(it) } }
        capture = null
    }

    private fun returnToApp() {
        service.startActivity(
            Intent(service, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    // ---- drag + tap handling --------------------------------------------

    /**
     * Makes [target] drag the whole panel window, and fire [onTap] on a tap that
     * didn't move. Returns true from ACTION_DOWN so Android keeps delivering the
     * subsequent MOVE/UP events (the bug that previously made the panel un-draggable).
     */
    private fun makeInteractive(target: View, onTap: () -> Unit) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        target.setOnTouchListener { _, e ->
            val params = panelParams ?: return@setOnTouchListener false
            val container = panel ?: return@setOnTouchListener false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).roundToInt()
                    val dy = (e.rawY - downY).roundToInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    runCatching { wm.updateViewLayout(container, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onTap()
                    true
                }
                else -> false
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(service, msg, Toast.LENGTH_SHORT).show()
    }
}
