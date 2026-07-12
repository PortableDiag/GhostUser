package com.ghostuser.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghostuser.app.data.MacroRepository
import com.ghostuser.app.engine.EngineProvider
import com.ghostuser.app.engine.PlaybackController
import com.ghostuser.app.model.Macro
import com.ghostuser.app.model.StepType
import com.ghostuser.app.service.GhostAccessibilityService
import com.ghostuser.app.service.OverlayBus
import com.ghostuser.app.service.OverlayPrefs
import com.ghostuser.app.service.ServiceUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onEditMacro: (String) -> Unit,
    onNewMacro: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val macros by MacroRepository.macros.collectAsStateWithLifecycle()
    val isPlaying by PlaybackController.isPlaying.collectAsStateWithLifecycle()
    val activeId by PlaybackController.activeMacroId.collectAsStateWithLifecycle()
    val serviceConnected by GhostAccessibilityService.connected.collectAsStateWithLifecycle()
    val panelVisible by OverlayPrefs.visible.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("GhostUser", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        val svc = GhostAccessibilityService.instance
                        when {
                            svc == null -> Toast.makeText(
                                context,
                                "Enable the accessibility service first",
                                Toast.LENGTH_LONG,
                            ).show()

                            panelVisible -> {
                                svc.hideOverlayPanel()
                                Toast.makeText(context, "Floating controls hidden", Toast.LENGTH_SHORT).show()
                            }

                            else -> {
                                svc.showOverlayPanel()
                                Toast.makeText(context, "Floating controls shown", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(
                            Icons.Filled.ControlCamera,
                            contentDescription = if (panelVisible) {
                                "Hide floating controls"
                            } else {
                                "Show floating controls"
                            },
                            tint = if (panelVisible) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewMacro,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New macro") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (!serviceConnected) {
                ServiceWarning(
                    onEnable = { ServiceUtils.openAccessibilitySettings(context) },
                )
                Spacer(Modifier.size(8.dp))
            }

            if (macros.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(macros, key = { it.id }) { macro ->
                        MacroRow(
                            macro = macro,
                            isPlaying = isPlaying && activeId == macro.id,
                            onToggle = {
                                OverlayBus.selectMacro(macro.id)
                                val engine = EngineProvider.resolve(EngineProvider.mode)
                                if (engine == null) {
                                    Toast.makeText(
                                        context,
                                        "Enable the accessibility service or grant root first",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } else {
                                    PlaybackController.toggle(macro, engine)
                                }
                            },
                            onEdit = { onEditMacro(macro.id) },
                            onDelete = {
                                if (activeId == macro.id) PlaybackController.stop()
                                MacroRepository.delete(macro.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceWarning(onEnable: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Gesture service is off",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                "Enable GhostUser in Accessibility settings to play macros without root.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "Open accessibility settings",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickableText(onEnable),
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "No macros yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            "Tap “New macro” to create an auto-clicker or a gesture sequence.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MacroRow(
    macro: Macro,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play",
                    tint = if (isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.size(6.dp))
            Column(Modifier.padding(end = 8.dp).weight(1f)) {
                Text(
                    macro.name.ifBlank { "Untitled" },
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    macro.summary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun Macro.summary(): String {
    val taps = steps.count { it.type == StepType.TAP }
    val swipes = steps.count { it.type == StepType.SWIPE }
    val kind = if (isAutoClicker) "Auto-clicker" else "Macro"
    val loopText = when {
        !loop -> "once"
        loopCount > 0 -> "×$loopCount"
        else -> "loop"
    }
    return buildString {
        append(kind)
        append(" • ${steps.size} steps")
        if (taps > 0) append(" • $taps taps")
        if (swipes > 0) append(" • $swipes swipes")
        append(" • $loopText")
        if (loop && loopDelayMs > 0) append(" • ${loopDelayMs}ms")
    }
}

/** Small helper so tappable text reads clearly at the call site. */
private fun Modifier.clickableText(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable { onClick() })
