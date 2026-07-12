package com.ghostuser.app.service

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the user wants the floating control panel on screen.
 *
 * Enabling the accessibility service grants the *capability* to draw the panel;
 * it is not a request to show it. The system binds the service on boot, after an
 * app update, and whenever it restarts our process — so anything shown from
 * [GhostAccessibilityService.onServiceConnected] appears without the user having
 * opened the app or asked for it. This flag is the user's actual answer, and it
 * survives all of the above.
 *
 * SharedPreferences rather than the DataStore in SettingsStore because the
 * service reads it synchronously while connecting; an async read would let the
 * panel flash on screen before the stored "hidden" value arrived.
 */
object OverlayPrefs {

    private const val FILE = "ghost_overlay"
    private const val KEY_VISIBLE = "panel_visible"

    private var prefs: SharedPreferences? = null

    private val _visible = MutableStateFlow(false)

    /** True when the user has asked for the panel and not dismissed it. */
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        _visible.value = p.getBoolean(KEY_VISIBLE, false)
    }

    fun setVisible(value: Boolean) {
        _visible.value = value
        prefs?.edit()?.putBoolean(KEY_VISIBLE, value)?.apply()
    }
}
