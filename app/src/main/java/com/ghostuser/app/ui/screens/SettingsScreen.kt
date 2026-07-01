package com.ghostuser.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ghostuser.app.data.AppSettings
import com.ghostuser.app.data.EngineMode
import com.ghostuser.app.data.MacroRepository
import com.ghostuser.app.data.MacroTransfer
import com.ghostuser.app.data.SettingsStore
import com.ghostuser.app.data.ThemeMode
import com.ghostuser.app.engine.EngineProvider
import com.ghostuser.app.engine.RootShell
import com.ghostuser.app.service.ServiceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    settings: AppSettings,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var interval by remember(settings.defaultIntervalMs) {
        mutableStateOf(settings.defaultIntervalMs.toString())
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val macros = MacroRepository.macros.value
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(MacroTransfer.export(macros).toByteArray())
                    }
                }.isSuccess
            }
            Toast.makeText(
                context,
                if (ok) "Exported ${macros.size} macro(s)" else "Export failed",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            val macros = text?.let { MacroTransfer.parse(it) } ?: emptyList()
            if (macros.isEmpty()) {
                Toast.makeText(context, "No macros found in that file", Toast.LENGTH_SHORT).show()
            } else {
                val n = MacroRepository.importMacros(macros)
                Toast.makeText(context, "Imported $n macro(s)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Engine mode
            SettingSection("Injection engine") {
                EngineMode.entries.forEach { mode ->
                    RadioRow(
                        selected = settings.engineMode == mode,
                        title = mode.title(),
                        subtitle = mode.subtitle(),
                        onSelect = {
                            scope.launch { settingsStore.setEngineMode(mode) }
                            EngineProvider.mode = mode
                        },
                    )
                }
                Spacer(Modifier.size(6.dp))
                OutlinedButton(onClick = {
                    scope.launch {
                        val ok = RootShell.isRootAvailable()
                        EngineProvider.rootConfirmed = ok
                        Toast.makeText(
                            context,
                            if (ok) "Root access granted ✓" else "Root not available",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }) { Text("Test root access") }
            }

            // Theme
            SettingSection("Theme") {
                ThemeMode.entries.forEach { mode ->
                    RadioRow(
                        selected = settings.themeMode == mode,
                        title = mode.title(),
                        subtitle = null,
                        onSelect = { scope.launch { settingsStore.setThemeMode(mode) } },
                    )
                }
            }

            // Accessibility service
            SettingSection("Accessibility service") {
                Text(
                    "Required for the no-root engine and the floating panel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = { ServiceUtils.openAccessibilitySettings(context) }) {
                    Text("Open accessibility settings")
                }
            }

            // Default interval
            SettingSection("Defaults") {
                OutlinedTextField(
                    value = interval,
                    onValueChange = { new ->
                        val digits = new.filter(Char::isDigit)
                        interval = digits
                        digits.toIntOrNull()?.let { ms ->
                            scope.launch { settingsStore.setDefaultInterval(ms) }
                        }
                    },
                    label = { Text("Default auto-clicker interval (ms)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Backup & sharing
            SettingSection("Backup & sharing") {
                Text(
                    "Export all macros to a JSON file, or import macros from one. " +
                        "Imports are added alongside your existing macros.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (MacroRepository.macros.value.isEmpty()) {
                                Toast.makeText(context, "No macros to export", Toast.LENGTH_SHORT).show()
                            } else {
                                exportLauncher.launch("ghostuser-macros.json")
                            }
                        },
                    ) { Text("Export all") }
                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(
                                arrayOf("application/json", "text/plain", "application/octet-stream")
                            )
                        },
                    ) { Text("Import") }
                }
            }
        }
    }
}

@Composable
private fun RadioRow(
    selected: Boolean,
    title: String,
    subtitle: String?,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.padding(start = 4.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(8.dp))
            content()
        }
    }
}

private fun EngineMode.title(): String = when (this) {
    EngineMode.AUTO -> "Auto (root, else accessibility)"
    EngineMode.ACCESSIBILITY -> "Accessibility (no root)"
    EngineMode.ROOT -> "Root"
}

private fun EngineMode.subtitle(): String = when (this) {
    EngineMode.AUTO -> "Use root when granted, otherwise dispatch gestures."
    EngineMode.ACCESSIBILITY -> "Works on stock devices. Some apps block synthetic gestures."
    EngineMode.ROOT -> "Fastest and most compatible. Requires a rooted device."
}

private fun ThemeMode.title(): String = when (this) {
    ThemeMode.SYSTEM -> "System default"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}
