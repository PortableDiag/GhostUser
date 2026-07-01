package com.ghostuser.app.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

/** Helpers for reasoning about / navigating to the accessibility service. */
object ServiceUtils {

    /**
     * Whether GhostUser's accessibility service is enabled in system settings.
     * This is the ground truth the UI should trust — the in-process
     * [GhostAccessibilityService.connected] flag can lag right after enabling.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, GhostAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
