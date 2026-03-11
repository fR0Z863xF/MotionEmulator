package com.zhufucdev.motion_emulator.ui

import android.location.Location
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhufucdev.me.stub.AgentState
import com.zhufucdev.me.stub.BLOCK_REF
import com.zhufucdev.me.stub.Data
import com.zhufucdev.me.stub.EMPTY_REF
import com.zhufucdev.me.stub.EmulationInfo
import com.zhufucdev.me.stub.Intermediate
import com.zhufucdev.me.stub.Point
import com.zhufucdev.me.stub.Trace
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.AppMeta
import com.zhufucdev.motion_emulator.data.DataLoader
import com.zhufucdev.motion_emulator.data.WorkingData
import com.zhufucdev.motion_emulator.extension.sharedPreferences
import com.zhufucdev.motion_emulator.extension.toFixed
import com.zhufucdev.motion_emulator.ui.composition.LocalSnackbarProvider
import com.zhufucdev.motion_emulator.ui.composition.ScaffoldElements
import com.zhufucdev.motion_emulator.provider.Scheduler
import com.zhufucdev.motion_emulator.ui.map.MapController
import com.zhufucdev.motion_emulator.ui.map.MapDisplayType
import com.zhufucdev.motion_emulator.ui.map.MapTraceCallback
import com.zhufucdev.motion_emulator.ui.map.MapStyle
import com.zhufucdev.motion_emulator.ui.map.TraceBounds
import com.zhufucdev.motion_emulator.ui.map.UnifiedMap
import com.zhufucdev.motion_emulator.ui.map.UnifiedMapProvider
import com.zhufucdev.motion_emulator.ui.model.EmulationRef
import com.zhufucdev.motion_emulator.ui.model.EmulationsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class AgentStatusSnapshot(
    val id: String,
    val state: AgentState,
    val info: EmulationInfo?,
    val intermediate: Intermediate?,
    val app: AppMeta?,
)

@Composable
fun EmulateHome(paddingValues: PaddingValues) {
    val model = viewModel<EmulationsViewModel>()
    val coroutine = rememberCoroutineScope()
    val context = LocalContext.current
    val packageManager = context.packageManager
    val preferences = remember { context.sharedPreferences() }
    val mapProvider = remember(preferences) {
        when (preferences.getString("map_provider", "gcp_maps")) {
            "amap" -> UnifiedMapProvider.AMAP
            else -> UnifiedMapProvider.GCP_MAPS
        }
    }
    var editingId by remember { mutableStateOf<String?>(null) }
    var draftId by remember { mutableStateOf<String?>(null) }

    ScaffoldElements {
        floatingActionButton {
            ExtendedFloatingActionButton(
                text = { Text(text = stringResource(id = R.string.action_add)) },
                icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
                onClick = {
                    coroutine.launch {
                        val created = model.createDefault()
                        editingId = created.id
                        draftId = created.id
                    }
                },
            )
        }
    }

    val agentStates = remember { mutableStateListOf<AgentStatusSnapshot>() }
    var controllerState by remember { mutableStateOf(Scheduler.controllerState) }

    LaunchedEffect(Unit) {
        while (true) {
            controllerState = Scheduler.controllerState
            agentStates.clear()
            val ids = (Scheduler.instance.keys + Scheduler.intermediate.keys).distinct().sorted()
            agentStates.addAll(
                ids.map { id ->
                    val info = Scheduler.instance[id]
                    AgentStatusSnapshot(
                        id = id,
                        state = Scheduler.currentEmulationState(id),
                        info = info,
                        intermediate = Scheduler.intermediate[id],
                        app = info?.owner?.let { owner ->
                            runCatching {
                                AppMeta.of(
                                    packageManager.getApplicationInfo(owner, PackageManager.GET_META_DATA),
                                    packageManager,
                                )
                            }.getOrNull()
                        }
                    )
                }
            )
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()
    ) {
        if (editingId != null) {
            val target = model.configs.firstOrNull { it.id == editingId }
            if (target != null) {
                EmulationEditor(
                    target = target,
                    onBack = {
                        if (draftId == target.id) {
                            model.configs.removeAll { it.id == target.id }
                            draftId = null
                        }
                        editingId = null
                    },
                    onSave = {
                        coroutine.launch {
                            model.save(it)
                            draftId = null
                            editingId = null
                        }
                    },
                    onRun = {
                        coroutine.launch {
                            model.save(it)
                            model.start(it.value)
                            draftId = null
                            editingId = null
                        }
                    },
                    onDelete = {
                        coroutine.launch {
                            model.remove(it)
                            if (draftId == it.id) {
                                draftId = null
                            }
                            editingId = null
                        }
                    }
                )
            }
        } else if (model.configs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(
                    modifier = Modifier.size(180.dp),
                    painter = painterResource(R.drawable.ic_thinking_face_72),
                    contentDescription = "empty",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    EmulationPreviewCard(
                        provider = mapProvider,
                        agentStates = agentStates,
                    )
                }
                item {
                    EmulationStatusCard(
                        controllerState = controllerState,
                        agentStates = agentStates,
                        onDetermine = { Scheduler.cancelAll() },
                        onRestart = { Scheduler.startAll() }
                    )
                }
                items(model.configs) {
                    EmulationItem(
                        emulation = it.value,
                        onClick = { editingId = it.id },
                        onRun = { model.start(it.value) },
                        onStop = { model.stop() }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmulationPreviewCard(
    provider: UnifiedMapProvider,
    agentStates: List<AgentStatusSnapshot>,
) {
    val trace = Scheduler.emulation?.trace?.value
    var controller by remember { mutableStateOf<MapController?>(null) }

    if (trace == null) return

    DisposableEffect(controller, trace) {
        val callback = controller?.drawTrace(trace)
        onDispose {
            callback?.remove()
        }
    }

    LaunchedEffect(controller, trace) {
        controller?.boundCamera(TraceBounds(trace), animate = false)
    }

    LaunchedEffect(controller, agentStates) {
        val latest = agentStates.firstOrNull { it.intermediate != null }?.intermediate ?: return@LaunchedEffect
        controller?.moveCamera(latest.location, focus = true, animate = false)
        controller?.updateLocationIndicator(latest.location.toLocation())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.title_trace),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${trace.id} · ${trace.points.size}",
                style = MaterialTheme.typography.bodySmall
            )
            UnifiedMap(
                provider = provider,
                modifier = Modifier
                    .fillMaxWidth()
                    .size(320.dp, 200.dp),
                displayStyle = MapStyle.NORMAL,
                displayType = MapDisplayType.STILL,
                onReady = { controller = it }
            )
        }
    }
}

@Composable
private fun EmulationStatusCard(
    controllerState: AgentState,
    agentStates: List<AgentStatusSnapshot>,
    onDetermine: () -> Unit,
    onRestart: () -> Unit,
) {
    val averageProgress = agentStates.mapNotNull { it.intermediate?.progress }.average().takeIf { !it.isNaN() }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = when (controllerState) {
                    AgentState.NOT_JOINED -> stringResource(R.string.title_controller_offline)
                    AgentState.PENDING -> stringResource(R.string.title_emulation_pending)
                    AgentState.RUNNING -> stringResource(R.string.title_emulation_ongoing)
                    AgentState.PAUSED -> stringResource(R.string.title_emulation_stopped)
                    AgentState.COMPLETED -> stringResource(R.string.title_emulation_completed)
                    AgentState.FAILURE -> stringResource(R.string.title_emulation_failure)
                    AgentState.CANCELED -> stringResource(R.string.title_emulation_canceled)
                },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = when (controllerState) {
                    AgentState.NOT_JOINED -> stringResource(R.string.text_controller_offline)
                    AgentState.PENDING -> stringResource(R.string.text_emulation_pending)
                    AgentState.RUNNING -> stringResource(R.string.text_swipe_to_see_more)
                    AgentState.PAUSED -> stringResource(R.string.title_emulation_stopped)
                    AgentState.COMPLETED -> stringResource(R.string.title_emulation_completed)
                    AgentState.FAILURE -> stringResource(R.string.title_emulation_failure)
                    AgentState.CANCELED -> stringResource(R.string.title_emulation_canceled)
                },
                style = MaterialTheme.typography.bodyLarge
            )
            if (controllerState == AgentState.RUNNING || controllerState == AgentState.PENDING) {
                if (averageProgress != null) {
                    LinearProgressIndicator(progress = { averageProgress.toFloat() }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            if (controllerState != AgentState.NOT_JOINED && Scheduler.emulation != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        if (controllerState == AgentState.RUNNING || controllerState == AgentState.PENDING) {
                            onDetermine()
                        } else {
                            onRestart()
                        }
                    }) {
                        Text(
                            if (controllerState == AgentState.RUNNING || controllerState == AgentState.PENDING) {
                                stringResource(R.string.action_determine)
                            } else {
                                stringResource(R.string.action_restart)
                            }
                        )
                    }
                }
            }
            if (agentStates.isEmpty()) {
                Text(
                    text = stringResource(R.string.text_emulation_pending),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                agentStates.forEach { snapshot ->
                    AgentStatusCard(
                        snapshot = snapshot,
                        onRestart = { Scheduler.startAgent(snapshot.id) },
                        onDetermine = {
                            if (snapshot.state == AgentState.RUNNING) {
                                Scheduler.cancelAgent(snapshot.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentStatusCard(
    snapshot: AgentStatusSnapshot,
    onRestart: () -> Unit,
    onDetermine: () -> Unit,
) {
    val velocity = Scheduler.emulation?.velocity?.toFloat()?.toFixed(2)
    val remaining = snapshot.info?.duration?.let { duration ->
        val elapsed = snapshot.intermediate?.elapsed ?: 0.0
        (duration - elapsed).coerceAtLeast(0.0)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = when (snapshot.state) {
                AgentState.RUNNING -> stringResource(
                    R.string.title_named_emulation_ongoing,
                    snapshot.id.take(5)
                )

                AgentState.PENDING -> stringResource(R.string.title_emulation_pending)
                AgentState.NOT_JOINED -> stringResource(R.string.title_agent_offline)
                AgentState.CANCELED -> stringResource(R.string.title_emulation_canceled)
                AgentState.PAUSED -> stringResource(R.string.title_emulation_stopped)
                AgentState.COMPLETED -> stringResource(R.string.title_emulation_completed)
                AgentState.FAILURE -> stringResource(R.string.title_emulation_failure)
            },
            style = MaterialTheme.typography.titleSmall
        )

        snapshot.app?.let { app ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "●",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(20.dp)
                )
                Text(
                    text = stringResource(
                        R.string.text_app_received,
                        app.name ?: app.packageName
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        when (snapshot.state) {
            AgentState.NOT_JOINED -> Text(
                text = stringResource(R.string.text_emulation_pending),
                style = MaterialTheme.typography.bodySmall
            )

            AgentState.PENDING -> Text(
                text = stringResource(R.string.text_emulation_app_pending),
                style = MaterialTheme.typography.bodySmall
            )

            else -> Unit
        }

        when (snapshot.state) {
            AgentState.RUNNING -> {
                FilledTonalButton(onClick = onDetermine) {
                    Text(stringResource(R.string.action_determine))
                }
            }

            AgentState.CANCELED,
            AgentState.COMPLETED,
            AgentState.FAILURE,
            AgentState.PAUSED -> {
                FilledTonalButton(onClick = onRestart) {
                    Text(stringResource(R.string.action_restart))
                }
            }

            AgentState.NOT_JOINED,
            AgentState.PENDING -> Unit
        }

        if (snapshot.state == AgentState.RUNNING) {
            snapshot.intermediate?.progress?.let {
                LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
            }
        }

        velocity?.let {
            Text(
                text = stringResource(R.string.status_velocity, "$it ${stringResource(R.string.suffix_velocity)}"),
                style = MaterialTheme.typography.bodySmall
            )
        }
        snapshot.info?.let {
            Text(
                text = stringResource(
                    R.string.status_total,
                    "${it.length.toFloat().toFixed(2)}${stringResource(R.string.suffix_meter)}"
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
        remaining?.let {
            Text(
                text = stringResource(
                    R.string.status_remaining,
                    "${it.toFloat().toFixed(2)} ${stringResource(R.string.suffix_second)}"
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(text = snapshot.id, style = MaterialTheme.typography.labelSmall)
    }
}

private fun Point.toLocation(): Location =
    Location("motion_emulator").apply {
        latitude = this@toLocation.latitude
        longitude = this@toLocation.longitude
        accuracy = 5f
    }

@Composable
private fun EmulationItem(
    emulation: EmulationRef,
    onClick: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(text = emulation.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(
                    id = R.string.suffix_velocity,
                    emulation.velocity.toFloat().toFixed(2)
                )
            )
            Text(text = stringResource(R.string.title_repeat) + ": ${emulation.repeat}")
            Text(text = stringResource(R.string.title_satellite_count) + ": ${emulation.satelliteCount}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRun) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(stringResource(R.string.action_start_emulation))
                }
                Button(onClick = onStop) {
                    Text(stringResource(R.string.action_determine))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmulationEditor(
    target: DataLoader<EmulationRef>,
    onBack: () -> Unit,
    onSave: (DataLoader<EmulationRef>) -> Unit,
    onRun: (DataLoader<EmulationRef>) -> Unit,
    onDelete: (DataLoader<EmulationRef>) -> Unit,
    model: EmulationsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val preferences = remember { context.sharedPreferences() }
    val mapProvider = remember(preferences) {
        when (preferences.getString("map_provider", "gcp_maps")) {
            "amap" -> UnifiedMapProvider.AMAP
            else -> UnifiedMapProvider.GCP_MAPS
        }
    }
    val snackbars = LocalSnackbarProvider.current
    val coroutine = rememberCoroutineScope()
    val savedAsDefaultMessage = stringResource(R.string.text_saved_as_default)
    val emptyFieldMessage = stringResource(R.string.text_field_must_not_empty)
    val nonPositiveFieldMessage = stringResource(R.string.text_field_must_not_neg_or_zero)
    var name by remember { mutableStateOf(target.value.name) }
    var trace by remember { mutableStateOf(target.value.trace) }
    var motion by remember { mutableStateOf(target.value.motion) }
    var cells by remember { mutableStateOf(target.value.cells) }
    var velocity by remember { mutableStateOf(target.value.velocity.toString()) }
    var repeat by remember { mutableStateOf(target.value.repeat.toString()) }
    var satelliteCount by remember { mutableStateOf(target.value.satelliteCount.toString()) }

    val traceOptions = model.availableTraces().map { it.id to (it.metadata.name ?: it.id) }
    val motionOptions = listOf(EMPTY_REF to stringResource(R.string.name_none), BLOCK_REF to stringResource(R.string.name_block)) +
            model.availableMotions().map { it.id to (it.metadata.name ?: it.id) }
    val cellOptions = listOf(EMPTY_REF to stringResource(R.string.name_none), BLOCK_REF to stringResource(R.string.name_block)) +
            model.availableCells().map { it.id to (it.metadata.name ?: it.id) }
    val selectedTrace = remember(trace, model.configs.size) {
        model.availableTraces().firstOrNull { it.id == trace }?.value
    }

    val parsedVelocity = velocity.toDoubleOrNull()
    val parsedRepeat = repeat.toIntOrNull()
    val parsedSatelliteCount = satelliteCount.toIntOrNull()
    val traceError = when {
        traceOptions.none { it.first == trace } -> emptyFieldMessage
        else -> null
    }
    val velocityError = when {
        velocity.isBlank() || parsedVelocity == null -> emptyFieldMessage
        parsedVelocity <= 0 -> nonPositiveFieldMessage
        else -> null
    }
    val repeatError = when {
        repeat.isBlank() || parsedRepeat == null -> emptyFieldMessage
        parsedRepeat <= 0 -> nonPositiveFieldMessage
        else -> null
    }
    val satelliteCountError = when {
        satelliteCount.isBlank() || parsedSatelliteCount == null -> emptyFieldMessage
        parsedSatelliteCount < 0 -> nonPositiveFieldMessage
        else -> null
    }
    val canSubmit = traceError == null && velocityError == null && repeatError == null && satelliteCountError == null

    fun build(): DataLoader<EmulationRef> = WorkingData(
        target.value.copy(
            name = name,
            trace = trace,
            motion = motion,
            cells = cells,
            velocity = parsedVelocity ?: target.value.velocity,
            repeat = parsedRepeat ?: target.value.repeat,
            satelliteCount = parsedSatelliteCount ?: target.value.satelliteCount,
        ),
        target.metadata.copy(name = name)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = stringResource(R.string.title_emulate), style = MaterialTheme.typography.headlineSmall)
                Row {
                    IconButton(onClick = { onDelete(target) }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                    IconButton(onClick = onBack) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        }
        item {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.title_name)) }, modifier = Modifier.fillMaxWidth())
        }
        item {
            SelectionField(label = stringResource(R.string.title_trace), value = trace, options = traceOptions, onValueChange = { trace = it })
        }
        if (traceError != null) {
            item {
                Text(text = traceError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        } else if (selectedTrace != null) {
            item {
                EditorTracePreviewCard(
                    provider = mapProvider,
                    trace = selectedTrace,
                )
            }
        }
        item {
            SelectionField(label = stringResource(R.string.title_motion), value = motion, options = motionOptions, onValueChange = { motion = it })
        }
        item {
            SelectionField(label = stringResource(R.string.title_cells), value = cells, options = cellOptions, onValueChange = { cells = it })
        }
        item {
            OutlinedTextField(
                value = velocity,
                onValueChange = { velocity = it },
                label = { Text(stringResource(R.string.title_velocity)) },
                modifier = Modifier.fillMaxWidth(),
                isError = velocityError != null,
                supportingText = velocityError?.let { { Text(it) } }
            )
        }
        item {
            OutlinedTextField(
                value = repeat,
                onValueChange = { repeat = it },
                label = { Text(stringResource(R.string.title_repeat)) },
                modifier = Modifier.fillMaxWidth(),
                isError = repeatError != null,
                supportingText = repeatError?.let { { Text(it) } }
            )
        }
        item {
            OutlinedTextField(
                value = satelliteCount,
                onValueChange = { satelliteCount = it },
                label = { Text(stringResource(R.string.title_satellite_count)) },
                modifier = Modifier.fillMaxWidth(),
                isError = satelliteCountError != null,
                supportingText = satelliteCountError?.let { { Text(it) } }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        val config = build()
                        model.saveDefaultConfig(config)
                        coroutine.launch {
                            snackbars?.showSnackbar(savedAsDefaultMessage)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = canSubmit,
                ) {
                    Text(stringResource(R.string.action_set_as_default))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSave(build()) }, modifier = Modifier.weight(1f), enabled = canSubmit) {
                    Text(stringResource(R.string.action_save))
                }
                Button(onClick = { onRun(build()) }, modifier = Modifier.weight(1f), enabled = canSubmit) {
                    Text(stringResource(R.string.action_start_emulation))
                }
            }
        }
    }
}

@Composable
private fun EditorTracePreviewCard(
    provider: UnifiedMapProvider,
    trace: Trace,
) {
    var controller by remember { mutableStateOf<MapController?>(null) }

    DisposableEffect(controller, trace) {
        val callback = controller?.drawTrace(trace)
        onDispose {
            callback?.remove()
        }
    }

    LaunchedEffect(controller, trace) {
        controller?.boundCamera(TraceBounds(trace), animate = false)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.title_trace),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = trace.points.size.toString(),
                style = MaterialTheme.typography.bodySmall
            )
            UnifiedMap(
                provider = provider,
                modifier = Modifier
                    .fillMaxWidth()
                    .size(320.dp, 200.dp),
                displayStyle = MapStyle.NORMAL,
                displayType = MapDisplayType.STILL,
                onReady = { controller = it }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val display = options.firstOrNull { it.first == value }?.second ?: value
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        onValueChange(id)
                        expanded = false
                    }
                )
            }
        }
    }
}

