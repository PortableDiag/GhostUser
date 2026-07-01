package com.ghostuser.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Which injection path playback should use. */
enum class EngineMode {
    /** Prefer root, fall back to accessibility if root isn't available. */
    AUTO,
    ACCESSIBILITY,
    ROOT;

    companion object {
        fun fromName(name: String?): EngineMode =
            entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/** Follows the system by default; can be forced light or dark. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

data class AppSettings(
    val engineMode: EngineMode = EngineMode.AUTO,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Default interval (ms) pre-filled when creating a new auto-clicker. */
    val defaultIntervalMs: Int = 100,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ghost_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val ENGINE = stringPreferencesKey("engine_mode")
        val THEME = stringPreferencesKey("theme_mode")
        val INTERVAL = intPreferencesKey("default_interval_ms")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            engineMode = EngineMode.fromName(p[Keys.ENGINE]),
            themeMode = ThemeMode.fromName(p[Keys.THEME]),
            defaultIntervalMs = p[Keys.INTERVAL] ?: 100,
        )
    }

    suspend fun setEngineMode(mode: EngineMode) {
        context.dataStore.edit { it[Keys.ENGINE] = mode.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setDefaultInterval(ms: Int) {
        context.dataStore.edit { it[Keys.INTERVAL] = ms }
    }
}
