package com.ghostuser.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.isSystemInDarkTheme
import com.ghostuser.app.data.AppSettings
import com.ghostuser.app.data.SettingsStore
import com.ghostuser.app.data.ThemeMode
import com.ghostuser.app.engine.EngineProvider
import com.ghostuser.app.ui.screens.HomeScreen
import com.ghostuser.app.ui.screens.MacroEditorScreen
import com.ghostuser.app.ui.screens.SettingsScreen
import com.ghostuser.app.ui.theme.GhostUserTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GhostApp() }
    }
}

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val EDITOR = "editor"
    fun editor(macroId: String) = "$EDITOR/$macroId"
    const val NEW = "new"
}

@Composable
private fun GhostApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsStore = remember { SettingsStore(context.applicationContext) }
    val settings by settingsStore.settings.collectAsStateWithLifecycle(AppSettings())

    // Mirror the engine preference where the overlay can read it synchronously.
    LaunchedEffect(settings.engineMode) { EngineProvider.mode = settings.engineMode }

    val darkTheme = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    GhostUserTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val nav = rememberNavController()
            NavHost(navController = nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onEditMacro = { id -> nav.navigate(Routes.editor(id)) },
                        onNewMacro = { nav.navigate(Routes.editor(Routes.NEW)) },
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        settingsStore = settingsStore,
                        settings = settings,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("${Routes.EDITOR}/{macroId}") { entry ->
                    val macroId = entry.arguments?.getString("macroId") ?: Routes.NEW
                    MacroEditorScreen(
                        macroId = macroId,
                        defaultIntervalMs = settings.defaultIntervalMs,
                        onDone = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}
