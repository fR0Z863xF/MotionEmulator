package com.zhufucdev.motion_emulator.ui

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.zhufucdev.me.stub.CoordinateSystem
import com.zhufucdev.me.stub.Metadata
import com.zhufucdev.me.stub.Point
import com.zhufucdev.me.stub.Trace
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.Traces
import com.zhufucdev.motion_emulator.data.WorkingData
import com.zhufucdev.motion_emulator.extension.sharedPreferences
import com.zhufucdev.motion_emulator.extension.toFixed
import com.zhufucdev.motion_emulator.extension.toPoint
import com.zhufucdev.motion_emulator.ui.composition.LocalNavControllerProvider
import com.zhufucdev.motion_emulator.ui.composition.LocalSnackbarProvider
import com.zhufucdev.motion_emulator.ui.composition.ScaffoldElements
import com.zhufucdev.motion_emulator.ui.map.GpsSamplingSession
import com.zhufucdev.motion_emulator.ui.map.MapController
import com.zhufucdev.motion_emulator.ui.map.MapDisplayType
import com.zhufucdev.motion_emulator.ui.map.MapScrawl
import com.zhufucdev.motion_emulator.ui.map.MapStyle
import com.zhufucdev.motion_emulator.ui.map.Poi
import com.zhufucdev.motion_emulator.ui.map.PoiSearchEngine
import com.zhufucdev.motion_emulator.ui.map.TraceBounds
import com.zhufucdev.motion_emulator.ui.map.UnifiedMap
import com.zhufucdev.motion_emulator.ui.map.UnifiedMapProvider
import com.zhufucdev.motion_emulator.ui.map.AMapPoiEngine
import com.zhufucdev.motion_emulator.ui.map.GooglePoiEngine
import com.zhufucdev.motion_emulator.ui.model.ManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private enum class TraceTool {
    Move,
    Draw,
    Gps,
}

private enum class PermissionAction {
    Locate,
    StartGps,
}

private data class PendingTrace(
    val record: WorkingData<Trace>,
    val name: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraceApp(
    paddingValues: PaddingValues,
    managerModel: ManagerViewModel,
    returnToDataAfterSave: Boolean = false,
) {
    ScaffoldElements {
        noFloatingButton()
    }

    val context = LocalContext.current
    val prefs = remember { context.sharedPreferences() }
    val coroutine = rememberCoroutineScope()
    val snackbars = LocalSnackbarProvider.current
    val navController = LocalNavControllerProvider.current

    var provider by remember { mutableStateOf(prefs.readMapProvider()) }
    var displayStyle by remember { mutableStateOf(MapStyle.NORMAL) }
    var controller by remember { mutableStateOf<MapController?>(null) }
    var tool by remember { mutableStateOf(TraceTool.Move) }
    var gpsSession by remember { mutableStateOf<GpsSamplingSession.Active?>(null) }
    var drawPen by remember { mutableStateOf<MapScrawl?>(null) }
    val activePoints = remember { mutableStateListOf<Point>() }
    val pendingTraces = remember { mutableStateListOf<PendingTrace>() }
    val searchResults = remember { mutableStateListOf<Poi>() }
    var searchQuery by remember { mutableStateOf("") }
    var searchLoading by remember { mutableStateOf(false) }
    var pendingPermissionAction by remember { mutableStateOf<PermissionAction?>(null) }
    var showProviderChooser by remember { mutableStateOf(!prefs.contains("map_provider")) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var hasLocatedInitially by remember { mutableStateOf(false) }

    val poiEngine = remember(context, provider) {
        when (prefs.readPoiProvider(provider)) {
            UnifiedMapProvider.AMAP -> AMapPoiEngine(context)
            UnifiedMapProvider.GCP_MAPS -> GooglePoiEngine(context)
        }
    }
    val unsaved by remember {
        derivedStateOf {
            pendingTraces.isNotEmpty() || activePoints.isNotEmpty() || tool != TraceTool.Move
        }
    }

    fun syncActive(points: List<Point>) {
        activePoints.clear()
        activePoints.addAll(points)
    }

    suspend fun finalizeActiveTrace() {
        val points = activePoints.toList()
        gpsSession?.stop()
        gpsSession = null
        drawPen = null
        tool = TraceTool.Move
        if (points.isEmpty()) {
            syncActive(emptyList())
            return
        }

        val coord = points.firstOrNull()?.coordinateSystem ?: CoordinateSystem.WGS84
        val name = controller?.buildTraceName(points.firstOrNull()) ?: context.getString(R.string.title_trace)
        val record = WorkingData(
            Trace(
                id = NanoIdUtils.randomNanoId(),
                points = points,
                coordinateSystem = coord,
            ),
            Metadata(0, name = name)
        )
        pendingTraces.add(PendingTrace(record, name))
        syncActive(emptyList())
        snackbars?.showSnackbar(context.getString(R.string.text_trace_name, name))
    }

    fun discardAndBack() {
        gpsSession?.stop()
        gpsSession = null
        navController?.popBackStack()
    }

    suspend fun startGpsSampling() {
        val currentController = controller ?: return
        when (val session = currentController.beginGpsSampling(
            onSample = { point, firstFix, points ->
                if (firstFix) {
                    currentController.moveCamera(point, focus = true, animate = true)
                }
                syncActive(points)
            },
            onLocation = { location -> currentController.updateLocationIndicator(location) }
        )) {
            is GpsSamplingSession.Unsupported -> {
                snackbars?.showSnackbar(context.getString(R.string.text_no_gps_provider))
                tool = TraceTool.Move
            }

            is GpsSamplingSession.Active -> {
                gpsSession?.stop()
                gpsSession = session
                tool = TraceTool.Gps
                syncActive(session.pen.points)
                snackbars?.showSnackbar(context.getString(R.string.text_gps_pending))
            }
        }
    }

    suspend fun locateCurrentPosition() {
        val currentController = controller ?: return
        val location = context.findBestLocation() ?: return
        currentController.updateLocationIndicator(location)
        currentController.moveCamera(location.toPoint(), focus = true, animate = false)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.any { it.value }
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (!granted) return@rememberLauncherForActivityResult
        when (action) {
            PermissionAction.Locate -> coroutine.launch { locateCurrentPosition() }
            PermissionAction.StartGps -> coroutine.launch { startGpsSampling() }
            null -> Unit
        }
    }

    LaunchedEffect(controller, hasLocatedInitially) {
        if (controller == null || hasLocatedInitially) return@LaunchedEffect
        hasLocatedInitially = true
        if (context.hasLocationPermission()) {
            locateCurrentPosition()
        } else {
            pendingPermissionAction = PermissionAction.Locate
            permissionLauncher.launch(locationPermissions)
        }
    }

    LaunchedEffect(tool) {
        if (tool != TraceTool.Gps) {
            gpsSession?.stop()
            gpsSession = null
        }
        if (tool != TraceTool.Draw) {
            drawPen = null
        }
    }

    LaunchedEffect(controller, tool) {
        if (tool == TraceTool.Draw && controller != null && drawPen == null) {
            drawPen = controller?.usePen()
            syncActive(drawPen?.points.orEmpty())
        }
    }

    DisposableEffect(controller, pendingTraces.toList()) {
        val callbacks = controller?.let { ctrl ->
            pendingTraces.map { ctrl.drawTrace(it.record.value) }
        }.orEmpty()
        onDispose {
            callbacks.forEach { it.remove() }
        }
    }

    BackHandler(enabled = unsaved) {
        showDiscardDialog = true
    }

    if (showProviderChooser) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.title_is_gcs_accessible)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProviderOption(
                        title = stringResource(R.string.title_gcs_accessible),
                        description = stringResource(R.string.text_gcs_accessible),
                        selected = provider == UnifiedMapProvider.GCP_MAPS,
                        onClick = { provider = UnifiedMapProvider.GCP_MAPS }
                    )
                    ProviderOption(
                        title = stringResource(R.string.title_gcs_inaccessible),
                        description = stringResource(R.string.text_gcs_inaccessible),
                        selected = provider == UnifiedMapProvider.AMAP,
                        onClick = { provider = UnifiedMapProvider.AMAP }
                    )
                    Text(
                        text = stringResource(R.string.text_pending_map_provider),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.writeMapProviders(provider)
                    showProviderChooser = false
                }) {
                    Text(stringResource(R.string.action_continue))
                }
            }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.title_unsaved_trace)) },
            text = { Text(stringResource(R.string.text_unsaved_trace)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    discardAndBack()
                }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.title_draw_trace)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (unsaved) showDiscardDialog = true else navController?.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (tool != TraceTool.Move) {
                        IconButton(onClick = {
                            when (tool) {
                                TraceTool.Draw -> {
                                    drawPen?.undo()
                                }

                                TraceTool.Gps -> {
                                    gpsSession?.pen?.undo()
                                }

                                TraceTool.Move -> Unit
                            }
                            syncActive(
                                when (tool) {
                                    TraceTool.Gps -> gpsSession?.pen?.points.orEmpty()
                                    TraceTool.Draw -> drawPen?.points.orEmpty()
                                    TraceTool.Move -> emptyList()
                                }
                            )
                        }) {
                            Icon(Icons.Default.Undo, contentDescription = null)
                        }
                        IconButton(onClick = {
                            when (tool) {
                                TraceTool.Draw -> {
                                    drawPen?.clear()
                                    syncActive(drawPen?.points.orEmpty())
                                }

                                TraceTool.Gps -> {
                                    gpsSession?.let {
                                        if (it.isPaused) it.unpause() else it.pause()
                                    }
                                }

                                TraceTool.Move -> Unit
                            }
                        }) {
                            Icon(
                                imageVector = when {
                                    tool == TraceTool.Gps && gpsSession?.isPaused == true -> Icons.Default.PlayArrow
                                    tool == TraceTool.Gps -> Icons.Default.Pause
                                    else -> Icons.Default.ClearAll
                                },
                                contentDescription = null
                            )
                        }
                        IconButton(onClick = { coroutine.launch { finalizeActiveTrace() } }) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }

                    IconButton(onClick = {
                        coroutine.launch(Dispatchers.IO) {
                            if (pendingTraces.isEmpty()) return@launch
                            val count = pendingTraces.size
                            val saving = pendingTraces.toList()
                            val saved = mutableListOf<com.zhufucdev.motion_emulator.data.DataLoader<*>>()
                            Traces.require(context)
                            saving.forEach {
                                Traces.put(it.record, overwrite = true)?.let(saved::add)
                            }
                            launch(Dispatchers.Main) {
                                saved.forEach { record ->
                                    val oldIndex = managerModel.data.indexOfFirst { it.id == record.id }
                                    if (oldIndex < 0) {
                                        managerModel.data.add(record)
                                    } else {
                                        managerModel.data[oldIndex] = record
                                    }
                                }
                                managerModel.data.sortBy { it.id }
                                pendingTraces.removeAll(saving.toSet())
                                snackbars?.showSnackbar(
                                    context.getString(R.string.text_trace_saved, count)
                                )
                                if (returnToDataAfterSave) {
                                    navController?.popBackStack()
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Save, contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(innerPadding)
        ) {
            UnifiedMap(
                provider = provider,
                modifier = Modifier.fillMaxSize(),
                displayStyle = displayStyle,
                displayType = MapDisplayType.INTERACTIVE,
                onReady = { controller = it }
            )

            if (tool == TraceTool.Draw && controller != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.02f))
                        .pointerInput(controller, tool) {
                            detectTapGestures { tap ->
                                val ctrl = controller ?: return@detectTapGestures
                                val pen = drawPen ?: ctrl.usePen().also { drawPen = it }
                                pen.markBegin()
                                pen.addPoint(ctrl.project(tap.x.toInt(), tap.y.toInt()))
                                syncActive(pen.points)
                            }
                        }
                        .pointerInput(controller, tool) {
                            var pen = drawPen
                            detectDragGestures(
                                onDragStart = { start ->
                                    val ctrl = controller ?: return@detectDragGestures
                                    pen = pen ?: ctrl.usePen().also { drawPen = it }
                                    pen?.markBegin()
                                    pen?.addPoint(ctrl.project(start.x.toInt(), start.y.toInt()))
                                    syncActive(pen?.points.orEmpty())
                                },
                                onDrag = { change, _ ->
                                    val ctrl = controller ?: return@detectDragGestures
                                    change.consume()
                                    pen?.addPoint(ctrl.project(change.position.x.toInt(), change.position.y.toInt()))
                                    syncActive(pen?.points.orEmpty())
                                }
                            )
                        }
                )
            }

            TraceOverlayPanel(
                tool = tool,
                displayStyle = displayStyle,
                onStyleChanged = { displayStyle = it },
                activePoints = activePoints,
                gpsPaused = gpsSession?.isPaused == true,
                pendingTraces = pendingTraces,
                searchQuery = searchQuery,
                onQueryChanged = { searchQuery = it },
                searchLoading = searchLoading,
                onSearch = {
                    coroutine.launch {
                        searchLoading = true
                        searchResults.clear()
                        runCatching { poiEngine.search(searchQuery, 20) }
                            .onSuccess { searchResults.addAll(it) }
                        searchLoading = false
                    }
                },
                searchResults = searchResults,
                onSelectSearchResult = { poi ->
                    searchResults.clear()
                    searchQuery = poi.name
                    controller?.moveCamera(poi.location, focus = true, animate = true)
                },
                onMove = { tool = TraceTool.Move },
                onDraw = {
                    tool = TraceTool.Draw
                    syncActive(emptyList())
                },
                onGps = {
                    if (context.hasLocationPermission()) {
                        coroutine.launch { startGpsSampling() }
                    } else {
                        pendingPermissionAction = PermissionAction.StartGps
                        permissionLauncher.launch(locationPermissions)
                    }
                },
                onLocateCurrent = {
                    if (context.hasLocationPermission()) {
                        coroutine.launch { locateCurrentPosition() }
                    } else {
                        pendingPermissionAction = PermissionAction.Locate
                        permissionLauncher.launch(locationPermissions)
                    }
                },
                onLocateTrace = { trace ->
                    controller?.boundCamera(TraceBounds(trace.record.value), animate = true)
                }
            )
        }
    }
}

@Composable
private fun TraceOverlayPanel(
    tool: TraceTool,
    displayStyle: MapStyle,
    onStyleChanged: (MapStyle) -> Unit,
    activePoints: List<Point>,
    gpsPaused: Boolean,
    pendingTraces: List<PendingTrace>,
    searchQuery: String,
    onQueryChanged: (String) -> Unit,
    searchLoading: Boolean,
    onSearch: () -> Unit,
    searchResults: List<Poi>,
    onSelectSearchResult: (Poi) -> Unit,
    onMove: () -> Unit,
    onDraw: () -> Unit,
    onGps: () -> Unit,
    onLocateCurrent: () -> Unit,
    onLocateTrace: (PendingTrace) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        SearchPanel(
            query = searchQuery,
            onQueryChanged = onQueryChanged,
            loading = searchLoading,
            onSearch = onSearch,
            results = searchResults,
            onSelect = onSelectSearchResult,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TraceToolSelector(
                    current = tool,
                    onMove = onMove,
                    onDraw = onDraw,
                    onGps = onGps,
                )

                TraceStyleSelector(
                    current = displayStyle,
                    onChanged = onStyleChanged,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Button(onClick = onLocateCurrent) {
                        Icon(Icons.Default.MyLocation, contentDescription = null)
                        Text(text = stringResource(R.string.action_move))
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActiveTraceSummary(
                            tool = tool,
                            points = activePoints,
                            gpsPaused = gpsPaused,
                        )

                        PendingTraceList(
                            traces = pendingTraces,
                            onLocate = onLocateTrace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPanel(
    query: String,
    onQueryChanged: (String) -> Unit,
    loading: Boolean,
    onSearch: () -> Unit,
    results: List<Poi>,
    onSelect: (Poi) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    label = { Text(stringResource(R.string.action_search)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Button(onClick = onSearch, enabled = query.isNotBlank()) {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            }
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (results.isNotEmpty()) {
                Column {
                    results.take(3).forEachIndexed { index, poi ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(poi) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(text = poi.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = listOf(poi.city, poi.province).filter { it.isNotBlank() }.joinToString(),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        if (index != results.take(3).lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TraceToolSelector(
    current: TraceTool,
    onMove: () -> Unit,
    onDraw: () -> Unit,
    onGps: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolCard(
            modifier = Modifier.weight(1f),
            selected = current == TraceTool.Move,
            title = stringResource(R.string.action_move),
            icon = Icons.Default.TouchApp,
            onClick = onMove,
        )
        ToolCard(
            modifier = Modifier.weight(1f),
            selected = current == TraceTool.Draw,
            title = stringResource(R.string.action_draw),
            icon = Icons.Default.AddLocationAlt,
            onClick = onDraw,
        )
        ToolCard(
            modifier = Modifier.weight(1f),
            selected = current == TraceTool.Gps,
            title = stringResource(R.string.action_sample),
            icon = Icons.Default.GpsFixed,
            onClick = onGps,
        )
    }
}

@Composable
private fun ToolCard(
    modifier: Modifier = Modifier,
    selected: Boolean,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text = title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TraceStyleSelector(
    current: MapStyle,
    onChanged: (MapStyle) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = current == MapStyle.NORMAL,
            onClick = { onChanged(MapStyle.NORMAL) },
            label = { Text(stringResource(R.string.name_map_common)) },
            leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null) }
        )
        FilterChip(
            selected = current == MapStyle.SATELLITE,
            onClick = { onChanged(MapStyle.SATELLITE) },
            label = { Text(stringResource(R.string.name_map_satellite)) },
            leadingIcon = { Icon(Icons.Default.MyLocation, contentDescription = null) }
        )
        FilterChip(
            selected = current == MapStyle.NIGHT,
            onClick = { onChanged(MapStyle.NIGHT) },
            label = { Text(stringResource(R.string.name_map_night)) },
            leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null) }
        )
    }
}

@Composable
private fun ActiveTraceSummary(
    tool: TraceTool,
    points: List<Point>,
    gpsPaused: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = when (tool) {
                TraceTool.Move -> stringResource(R.string.action_move)
                TraceTool.Draw -> stringResource(R.string.text_draw_trace)
                TraceTool.Gps -> if (gpsPaused) stringResource(R.string.action_unpause) else stringResource(R.string.text_gps_pending)
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (points.isEmpty()) {
            Text(text = stringResource(R.string.text_empty_list), style = MaterialTheme.typography.bodySmall)
        } else {
            Text(
                text = stringResource(R.string.text_selected_items, points.size),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = points.takeLast(1).first().let { "${it.latitude.toFixed(6)}, ${it.longitude.toFixed(6)}" },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PendingTraceList(
    traces: List<PendingTrace>,
    onLocate: (PendingTrace) -> Unit,
) {
    if (traces.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = stringResource(R.string.title_trace), style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                traces.forEach { trace ->
                    FilterChip(
                        selected = false,
                        onClick = { onLocate(trace) },
                        label = {
                            Text("${trace.name} · ${trace.record.value.points.size}")
                        },
                        leadingIcon = {
                            Icon(Icons.Default.MyLocation, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private val locationPermissions = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION,
)

private fun Context.hasLocationPermission(): Boolean =
    locationPermissions.any {
        ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

@SuppressLint("MissingPermission")
private suspend fun Context.findBestLocation(): Location? {
    if (!hasLocationPermission()) return null
    val manager = getSystemService(LocationManager::class.java) ?: return null
    val providers = buildList {
        val best = manager.getBestProvider(
            Criteria().apply {
                accuracy = Criteria.ACCURACY_COARSE
                isSpeedRequired = false
            },
            true
        )
        if (best != null) add(best)
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.PASSIVE_PROVIDER)
    }.distinct()
    providers.forEach { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()?.let { return it }
    }
    val fallback = providers.firstOrNull() ?: return null
    return suspendCancellableCoroutine { continuation ->
        runCatching {
            manager.getCurrentLocation(fallback, null, mainExecutor) {
                continuation.resume(it)
            }
        }.onFailure {
            continuation.resume(null)
        }
    }
}

private fun android.content.SharedPreferences.readMapProvider(): UnifiedMapProvider {
    return when (getString("map_provider", "gcp_maps")) {
        "amap" -> UnifiedMapProvider.AMAP
        else -> UnifiedMapProvider.GCP_MAPS
    }
}

private fun android.content.SharedPreferences.readPoiProvider(mapProvider: UnifiedMapProvider): UnifiedMapProvider {
    return when (getString("poi_provider", null)) {
        "amap" -> UnifiedMapProvider.AMAP
        "gcp_maps" -> UnifiedMapProvider.GCP_MAPS
        else -> mapProvider
    }
}

private fun android.content.SharedPreferences.writeMapProviders(provider: UnifiedMapProvider) {
    edit {
        putString("map_provider", provider.prefValue)
        putString("poi_provider", provider.prefValue)
    }
}

private val UnifiedMapProvider.prefValue: String
    get() = when (this) {
        UnifiedMapProvider.AMAP -> "amap"
        UnifiedMapProvider.GCP_MAPS -> "gcp_maps"
    }