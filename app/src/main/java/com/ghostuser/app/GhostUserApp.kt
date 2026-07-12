package com.ghostuser.app

import android.app.Application
import com.ghostuser.app.data.MacroRepository
import com.ghostuser.app.service.OverlayPrefs

class GhostUserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MacroRepository.init(this)
        // Must run before the accessibility service connects — it reads this to
        // decide whether the floating panel should come back.
        OverlayPrefs.init(this)
    }
}
