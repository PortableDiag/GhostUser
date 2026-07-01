package com.ghostuser.app

import android.app.Application
import com.ghostuser.app.data.MacroRepository

class GhostUserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MacroRepository.init(this)
    }
}
