package com.ghostuser.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.KeyboardOptions
import com.ghostuser.app.data.MacroRepository
import com.ghostuser.app.data.MacroTransfer
import com.ghostuser.app.model.Macro
import com.ghostuser.app.model.Step
import com.ghostuser.app.model.StepType
import com.ghostuser.app.service.GhostAccessibilityService
import com.ghostuser.app.service.OverlayBus

/** Wraps a Step with a stable key so reorder/delete keeps row state consistent. */
private class StepHolder(val key: Long, initial: Step) {
    var step by mutableStateOf(initial)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroEditorScreen(
    macroId: String,
    defaultIntervalMs: Int,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val existing = remember(macroId) { if (macroId == "new") null else MacroRepository.get(macroId) }

    var name by remember { mutableStateOf(existing?.name ?: "New macro") }
    var loop by remember { mutableStateOf(existing?.loop ?: true) }
    var loopCount by remember { mutableStateOf((existing?.loopCount ?: 0).toString()) }
    var loopDelay by remember {
        mutableStateOf((existing?.loopDelayMs ?: defaultIntervalMs.toLong()).toString())
    }

    val keyGen = remember { java.util.concurrent.atomic.AtomicLong(0) }
    val holders = remember {
        (existing?.steps ?: emptyList())
            .map { StepHolder(keyGen.getAndIncrement(), it) }
            .toMutableStateList()
    }

    val picking by OverlayBus.picking.collectAsStateWithLifecycle()

    // Append a Tap step for every point tapped on the picker overlay. The
    // service re-launches this Activity on "Done", so the collected points are
    // already in the list when the editor returns to the foreground.
    LaunchedEffect(Unit) {
        OverlayBus.pickedPoints.collect { p ->
            holders.add(StepHolder(keyGen.getAndIncrement(), Step.tap(p.x, p.y)))
        }
    }

    fun addStep(step: Step) {
        holders.add(StepHolder(keyGen.getAndIncrement(), step))
    }

    fun buildMacro(): Macro = Macro(
        id = existing?.id ?: MacroRepository.newId(),
        name = name.trim().ifBlank { "Untitled" },
        steps = holders.map { it.step },
        loop = loop,
        loopCount = loopCount.toIntOrNull()?.coerceAtLeast(0) ?: 0,
        loopDelayMs = loopDelay.toLongOrNull()?.coerceAtLeast(0) ?: 0,
    )

    fun save() {
        val macro = buildMacro()
        MacroRepository.upsert(macro)
        OverlayBus.selectMacro(macro.id)
        onDone()
    }

    fun share() {
        val macro = buildMacro()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TEXT, MacroTransfer.export(listOf(macro)))
            putExtra(Intent.EXTRA_SUBJECT, "${macro.name}.json")
        }
        context.startActivity(Intent.createChooser(send, "Share macro"))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New macro" else "Edit macro") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { share() }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share macro")
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                LoopControls(
                    loop = loop,
                    onLoopChange = { loop = it },
                    loopCount = loopCount,
                    onLoopCountChange = { loopCount = it.filter(Char::isDigit) },
                    loopDelay = loopDelay,
                    onLoopDelayChange = { loopDelay = it.filter(Char::isDigit) },
                )
            }

            item {
                AddStepBar(
                    picking = picking,
                    onPick = {
                        val svc = GhostAccessibilityService.instance
                        if (svc == null) {
                            Toast.makeText(
                                context,
                                "Enable the accessibility service first (Settings ▸ Accessibility)",
                                Toast.LENGTH_LONG,
                            ).show()
                        } else {
                            svc.startPointPicker()
                            (context as? Activity)?.moveTaskToBack(true)
                        }
                    },
                    onAddTap = { addStep(Step.tap(500f, 500f)) },
                    onAddSwipe = { addStep(Step.swipe(300f, 800f, 700f, 800f)) },
                    onAddLongPress = { addStep(Step.longPress(500f, 500f)) },
                    onAddDelay = { addStep(Step.delay(500L)) },
                )
            }

            item {
                Text(
                    "Steps (${holders.size})",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            items(holders, key = { it.key }) { holder ->
                val index = holders.indexOf(holder)
                StepCard(
                    number = index + 1,
                    holder = holder,
                    canUp = index > 0,
                    canDown = index < holders.size - 1,
                    onUp = { if (index > 0) holders.swap(index, index - 1) },
                    onDown = { if (index < holders.size - 1) holders.swap(index, index + 1) },
                    onDelete = { holders.remove(holder) },
                )
            }

            item {
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = { save() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save macro") }
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

private fun <T> SnapshotStateList<T>.swap(a: Int, b: Int) {
    val tmp = this[a]; this[a] = this[b]; this[b] = tmp
}

@Composable
private fun LoopControls(
    loop: Boolean,
    onLoopChange: (Boolean) -> Unit,
    loopCount: String,
    onLoopCountChange: (String) -> Unit,
    loopDelay: String,
    onLoopDelayChange: (String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Loop", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                Switch(checked = loop, onCheckedChange = onLoopChange)
            }
            if (loop) {
                Spacer(Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(
                        label = "Repeats (0 = ∞)",
                        value = loopCount,
                        onValueChange = onLoopCountChange,
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        label = "Interval (ms)",
                        value = loopDelay,
                        onValueChange = onLoopDelayChange,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddStepBar(
    picking: Boolean,
    onPick: () -> Unit,
    onAddTap: () -> Unit,
    onAddSwipe: () -> Unit,
    onAddLongPress: () -> Unit,
    onAddDelay: () -> Unit,
) {
    Column {
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
            Text(if (picking) "Picking… tap targets on screen" else "Pick tap points on screen")
        }
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = onAddTap, label = { Text("+ Tap") })
            AssistChip(onClick = onAddSwipe, label = { Text("+ Swipe") })
        }
        Spacer(Modifier.size(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = onAddLongPress, label = { Text("+ Long press") })
            AssistChip(onClick = onAddDelay, label = { Text("+ Delay") })
        }
    }
}

@Composable
private fun StepCard(
    number: Int,
    holder: StepHolder,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
) {
    val step = holder.step
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$number. ${step.type.label()}",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onUp, enabled = canUp) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = onDown, enabled = canDown) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.size(6.dp))
            when (step.type) {
                StepType.DELAY -> {
                    NumberField(
                        label = "Wait (ms)",
                        value = step.durationMs.toString(),
                        onValueChange = { holder.step = step.copy(durationMs = it.toLongOrNull() ?: 0) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                StepType.SWIPE -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField("X1", step.x.fmt(), { holder.step = step.copy(x = it.toFloatOrNull() ?: 0f) }, Modifier.weight(1f))
                        NumberField("Y1", step.y.fmt(), { holder.step = step.copy(y = it.toFloatOrNull() ?: 0f) }, Modifier.weight(1f))
                    }
                    Spacer(Modifier.size(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField("X2", step.x2.fmt(), { holder.step = step.copy(x2 = it.toFloatOrNull() ?: 0f) }, Modifier.weight(1f))
                        NumberField("Y2", step.y2.fmt(), { holder.step = step.copy(y2 = it.toFloatOrNull() ?: 0f) }, Modifier.weight(1f))
                    }
                    Spacer(Modifier.size(6.dp))
                    DurationAndRepeat(step, holder)
                }
                else -> { // TAP, LONG_PRESS
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField("X", step.x.fmt(), { holder.step = step.copy(x = it.toFloatOrNull() ?: 0f) }, Modifier.weight(1f))
                        NumberField("Y", step.y.fmt(), { holder.step = step.copy(y = it.toFloatOrNull() ?: 0f) }, Modifier.weight(1f))
                    }
                    Spacer(Modifier.size(6.dp))
                    DurationAndRepeat(step, holder)
                }
            }
        }
    }
}

@Composable
private fun DurationAndRepeat(step: Step, holder: StepHolder) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(
            label = if (step.type == StepType.LONG_PRESS) "Hold (ms)" else "Duration (ms)",
            value = step.durationMs.toString(),
            onValueChange = { holder.step = holder.step.copy(durationMs = it.toLongOrNull() ?: 0) },
            modifier = Modifier.weight(1f),
        )
        NumberField(
            label = "Repeat",
            value = step.repeat.toString(),
            onValueChange = { holder.step = holder.step.copy(repeat = it.toIntOrNull()?.coerceAtLeast(1) ?: 1) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

private fun StepType.label(): String = when (this) {
    StepType.TAP -> "Tap"
    StepType.LONG_PRESS -> "Long press"
    StepType.SWIPE -> "Swipe"
    StepType.DELAY -> "Delay"
}

private fun Float.fmt(): String = if (this % 1f == 0f) toInt().toString() else toString()
