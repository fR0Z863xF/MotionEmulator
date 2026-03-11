package com.zhufucdev.motion_emulator.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zhufucdev.me.stub.CellMoment
import com.zhufucdev.me.stub.Metadata
import com.zhufucdev.me.stub.SensorMoment
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.MotionCallback
import com.zhufucdev.motion_emulator.data.MotionRecorder
import com.zhufucdev.motion_emulator.data.Motions
import com.zhufucdev.motion_emulator.data.Telephonies
import com.zhufucdev.motion_emulator.data.TelephonyRecordCallback
import com.zhufucdev.motion_emulator.data.TelephonyRecorder
import com.zhufucdev.motion_emulator.data.DataLoader
import com.zhufucdev.motion_emulator.data.WorkingData
import com.zhufucdev.motion_emulator.ui.component.VerticalSpacer
import com.zhufucdev.motion_emulator.ui.composition.LocalSnackbarProvider
import com.zhufucdev.motion_emulator.ui.model.ManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private fun buildRecordSensors() = linkedMapOf<Int, Int>().apply {
    put(Sensor.TYPE_ACCELEROMETER, R.string.name_sensor_acc)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        put(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED, R.string.name_sensor_acc_uncal)
    }
    put(Sensor.TYPE_STEP_DETECTOR, R.string.name_sensor_step_detec)
    put(Sensor.TYPE_STEP_COUNTER, R.string.name_sensor_step_counter)
    put(Sensor.TYPE_GYROSCOPE, R.string.name_sensor_gyroscope)
    put(Sensor.TYPE_GYROSCOPE_UNCALIBRATED, R.string.name_sensor_gyroscope_uncal)
    put(Sensor.TYPE_MAGNETIC_FIELD, R.string.name_sensor_magnetic)
    put(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, R.string.name_sensor_magnetic_uncal)
    put(Sensor.TYPE_LINEAR_ACCELERATION, R.string.name_sensor_linear_acc)
    put(Sensor.TYPE_LIGHT, R.string.name_sensor_light)
}

private enum class RecordStage {
    Select,
    Running,
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordApp(
    paddingValues: PaddingValues,
    initialMotionEnabled: Boolean = true,
    initialTelephonyEnabled: Boolean = false,
    managerModel: ManagerViewModel,
    returnToDataAfterSave: Boolean = false,
) {
    val navController = com.zhufucdev.motion_emulator.ui.composition.LocalNavControllerProvider.current
    val context = LocalContext.current
    val sensorManager = remember(context) { context.getSystemService(SensorManager::class.java) }
    val availableSensors = remember(sensorManager) {
        buildRecordSensors().filterKeys { type ->
            sensorManager?.getDefaultSensor(type) != null
        }.toMap(linkedMapOf())
    }
    val coroutine = rememberCoroutineScope()
    val snackbars = LocalSnackbarProvider.current
    var stage by remember { mutableStateOf(RecordStage.Select) }
    val selected = remember(initialMotionEnabled, availableSensors) {
        val defaultSensor = when {
            availableSensors.containsKey(Sensor.TYPE_ACCELEROMETER) -> Sensor.TYPE_ACCELEROMETER
            availableSensors.isNotEmpty() -> availableSensors.keys.first()
            else -> null
        }
        mutableStateMapOf<Int, Boolean>().apply {
            availableSensors.keys.forEach { sensorType ->
                put(sensorType, initialMotionEnabled && sensorType == defaultSensor)
            }
        }
    }
    var useTelephony by remember(initialTelephonyEnabled) { mutableStateOf(initialTelephonyEnabled) }
    var permissionGranted by remember { mutableStateOf(context.hasRecordPermissions()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionGranted = result.values.all { it }
        if (!permissionGranted) {
            coroutine.launch {
                val snackbarResult = snackbars?.showSnackbar(
                    message = context.getString(R.string.text_permission_not_granted),
                    actionLabel = context.getString(R.string.action_grant),
                    withDismissAction = true,
                )
                if (snackbarResult == SnackbarResult.ActionPerformed) {
                    context.openAppSettings()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(recordPermissions())
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (stage == RecordStage.Select) stringResource(R.string.title_record_sensor)
                        else stringResource(R.string.title_charts)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (stage == RecordStage.Select) {
                            navController?.popBackStack()
                        } else {
                            stage = RecordStage.Select
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        when (stage) {
            RecordStage.Select -> RecordSensorSelection(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(innerPadding),
                sensors = availableSensors,
                selected = selected,
                useTelephony = useTelephony,
                permissionGranted = permissionGranted,
                onRequestPermission = { permissionLauncher.launch(recordPermissions()) },
                onTelephonyChanged = { useTelephony = it },
                onStart = {
                    if (permissionGranted) {
                        stage = RecordStage.Running
                    } else {
                        permissionLauncher.launch(recordPermissions())
                    }
                }
            )

            RecordStage.Running -> RecordRunningScreen(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(innerPadding),
                sensors = selected.filterValues { it }.keys.toList(),
                sensorLabels = availableSensors,
                useTelephony = useTelephony,
                managerModel = managerModel,
                returnToDataAfterSave = returnToDataAfterSave,
                onFinished = {
                    stage = RecordStage.Select
                    if (returnToDataAfterSave) {
                        navController?.popBackStack()
                    } else {
                        navController?.navigate(PrimaryDestinations.Data.route)
                    }
                }
            )
        }
    }
}

@Composable
private fun RecordSensorSelection(
    modifier: Modifier = Modifier,
    sensors: Map<Int, Int>,
    selected: MutableMap<Int, Boolean>,
    useTelephony: Boolean,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onTelephonyChanged: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    val activeCount = selected.count { it.value }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.text_record),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        if (!permissionGranted) {
            item {
                PermissionBanner(onGrant = onRequestPermission)
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    sensors.forEach { (type, label) ->
                        ListItem(
                            headlineContent = { Text(stringResource(label)) },
                            leadingContent = {
                                Checkbox(
                                    checked = selected[type] == true,
                                    onCheckedChange = { selected[type] = it }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected[type] = !(selected[type] == true)
                                }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.name_cell)) },
                    supportingContent = { Text(stringResource(R.string.text_telephony_recording_location)) },
                    trailingContent = {
                        Switch(checked = useTelephony, onCheckedChange = onTelephonyChanged)
                    }
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { selected.keys.forEach { selected[it] = false } },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_clear))
                }
                Button(
                    onClick = onStart,
                    enabled = permissionGranted && (activeCount > 0 || useTelephony),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_continue))
                }
            }
        }
    }
}

@Composable
private fun RecordRunningScreen(
    modifier: Modifier = Modifier,
    sensors: List<Int>,
    sensorLabels: Map<Int, Int>,
    useTelephony: Boolean,
    managerModel: ManagerViewModel,
    returnToDataAfterSave: Boolean,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val coroutine = rememberCoroutineScope()
    var motionCallback by remember { mutableStateOf<MotionCallback?>(null) }
    var telephonyCallback by remember { mutableStateOf<TelephonyRecordCallback?>(null) }
    val lastSensorMoment = remember { mutableStateMapOf<Int, SensorMoment>() }
    val sensorHistories = remember { mutableStateMapOf<Int, List<SensorMoment>>() }
    var telephonyUpdates by remember { mutableStateOf(0) }
    var latestTelephonyMoment by remember { mutableStateOf<CellMoment?>(null) }

    LaunchedEffect(sensors, useTelephony) {
        if (sensors.isNotEmpty()) {
            val motion = MotionRecorder.start(sensors)
            sensors.forEach { type ->
                motion.onUpdate(type) { moment ->
                    lastSensorMoment[type] = moment
                    val history = sensorHistories[type].orEmpty().toMutableList()
                    history.add(moment)
                    while (history.size > 50) {
                        history.removeAt(0)
                    }
                    sensorHistories[type] = history
                }
            }
            motionCallback = motion
        }
        if (useTelephony) {
            val telephony = TelephonyRecorder.start()
            telephony.onUpdate {
                telephonyUpdates += 1
                latestTelephonyMoment = it
            }
            telephonyCallback = telephony
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.title_charts),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        VerticalSpacer()
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(sensors) { type ->
                val moment = lastSensorMoment[type]
                RecordMetricCard(
                    title = stringResource(sensorLabels[type] ?: R.string.title_unknown),
                    value = moment?.data?.joinToString(prefix = "[", postfix = "]") { it.toString() }
                        ?: stringResource(R.string.title_emulation_pending),
                    chart = {
                        MiniLineChart(
                            moments = sensorHistories[type] ?: emptyList(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        )
                    }
                )
            }
            if (useTelephony) {
                item {
                    RecordMetricCard(
                        title = stringResource(R.string.name_cell),
                        value = buildString {
                            append(context.getString(R.string.text_selected_items, telephonyUpdates))
                            latestTelephonyMoment?.let { moment ->
                                if (moment.neighboring.isNotEmpty()) {
                                    append("\n")
                                    append(
                                        context.getString(
                                            R.string.text_telephony_recording_neighboring,
                                            moment.neighboring.size
                                        )
                                    )
                                }
                                if (moment.location != null) {
                                    append("\n")
                                    append(context.getString(R.string.text_telephony_recording_location))
                                }
                            }
                        },
                        chart = {
                            MiniBarChart(
                                values = latestTelephonyMoment?.cell?.map {
                                    it.cellSignalStrength.dbm.toFloat()
                                }.orEmpty(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                            )
                        }
                    )
                }
            }
        }
        VerticalSpacer()
        Button(
            onClick = {
                coroutine.launch(Dispatchers.IO) {
                    val saved = mutableListOf<DataLoader<*>>()
                    motionCallback?.summarize()?.let {
                        Motions.require(context)
                        Motions.put(
                            WorkingData(
                                it,
                                Metadata(0, name = context.getString(R.string.title_motion))
                            ),
                            overwrite = true
                        )?.let(saved::add)
                    }
                    telephonyCallback?.summarize()?.let {
                        Telephonies.require(context)
                        Telephonies.put(
                            WorkingData(
                                it,
                                Metadata(0, name = context.getString(R.string.title_cells))
                            ),
                            overwrite = true
                        )?.let(saved::add)
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
                        onFinished()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.action_stop))
        }
    }
}

@Composable
private fun RecordMetricCard(
    title: String,
    value: String,
    chart: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            VerticalSpacer(6.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                    .padding(12.dp)
            ) {
                Text(text = value, style = MaterialTheme.typography.bodyMedium)
            }
            if (chart != null) {
                VerticalSpacer(10.dp)
                chart()
            }
        }
    }
}

@Composable
private fun PermissionBanner(onGrant: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.text_permission_not_granted),
                style = MaterialTheme.typography.titleMedium,
            )
            Button(onClick = onGrant) {
                Text(stringResource(R.string.action_grant))
            }
        }
    }
}

@Composable
private fun MiniLineChart(
    moments: List<SensorMoment>,
    modifier: Modifier = Modifier,
) {
    val series = remember(moments) {
        val size = moments.maxOfOrNull { it.data.size } ?: 0
        List(size) { axis ->
            moments.mapIndexedNotNull { index, moment ->
                moment.data.getOrNull(axis)?.let { index.toFloat() to it }
            }
        }
    }
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
    )

    Canvas(
        modifier = modifier.background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
    ) {
        if (series.isEmpty() || series.all { it.isEmpty() }) return@Canvas

        val allValues = series.flatten().map { it.second }
        val minValue = allValues.minOrNull() ?: 0f
        val maxValue = allValues.maxOrNull() ?: 0f
        val range = max(maxValue - minValue, 1f)
        val widthStep = if (moments.size > 1) size.width / (moments.size - 1) else 0f

        series.forEachIndexed { index, values ->
            if (values.size < 2) return@forEachIndexed
            val path = Path()
            values.forEachIndexed { pointIndex, (x, value) ->
                val px = x * widthStep
                val py = size.height - ((value - minValue) / range) * size.height
                if (pointIndex == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(
                path = path,
                color = palette[index % palette.size],
                style = Stroke(width = 3f)
            )
        }
    }
}

@Composable
private fun MiniBarChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier.background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
    ) {
        if (values.isEmpty()) return@Canvas
        val minValue = min(values.minOrNull() ?: 0f, 0f)
        val maxValue = max(values.maxOrNull() ?: 0f, 1f)
        val range = max(maxValue - minValue, 1f)
        val barWidth = size.width / (values.size * 1.5f)
        values.forEachIndexed { index, value ->
            val left = index * barWidth * 1.5f + barWidth * 0.25f
            val normalized = (value - minValue) / range
            val top = size.height - normalized * size.height
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barWidth, size.height - top)
            )
        }
    }
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
    )
}

private fun Context.hasRecordPermissions(): Boolean {
    return recordPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
}

private fun recordPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        add(Manifest.permission.ACTIVITY_RECOGNITION)
    }
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.READ_PHONE_STATE)
}.toTypedArray()
