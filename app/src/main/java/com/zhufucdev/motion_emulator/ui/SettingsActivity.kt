package com.zhufucdev.motion_emulator.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhufucdev.me.stub.Method
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.extension.sharedPreferences
import com.zhufucdev.motion_emulator.plugin.Plugins
import com.zhufucdev.motion_emulator.ui.component.SettingsChoiceOption
import com.zhufucdev.motion_emulator.ui.component.SettingsGroupTitle
import com.zhufucdev.motion_emulator.ui.component.SettingsSectionCard
import com.zhufucdev.motion_emulator.ui.component.SettingsSingleChoiceGroup
import com.zhufucdev.motion_emulator.ui.component.SettingsSwitchItem
import com.zhufucdev.motion_emulator.ui.component.SettingsTextFieldItem
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.edit

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MotionEmulatorTheme {
                SettingsScreen(onBack = { finish() })
            }
        }
    }
}

private enum class SettingsSection {
    MAPS,
    NAMING,
    EMULATION,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    var section by remember { mutableStateOf<SettingsSection?>(null) }
    val title = when (section) {
        SettingsSection.MAPS -> stringResource(R.string.title_settings_map)
        SettingsSection.NAMING -> stringResource(R.string.title_settings_naming)
        SettingsSection.EMULATION -> stringResource(R.string.title_settings_emulation)
        null -> stringResource(R.string.title_activity_settings)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (section == null) onBack() else section = null
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
            )
        }
    ) { paddingValues ->
        when (section) {
            null -> SettingsHome(
                modifier = Modifier.padding(paddingValues),
                onOpen = { section = it }
            )

            SettingsSection.MAPS -> MapsSettings(modifier = Modifier.padding(paddingValues))
            SettingsSection.NAMING -> NamingSettings(modifier = Modifier.padding(paddingValues))
            SettingsSection.EMULATION -> EmulationSettings(modifier = Modifier.padding(paddingValues))
        }
    }
}

@Composable
private fun SettingsHome(modifier: Modifier = Modifier, onOpen: (SettingsSection) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.sharedPreferences() }
    val mapProvider = prefs.getString("map_provider", "gcp_maps") ?: "gcp_maps"
    val methodValue = prefs.getString("method", Method.XPOSED_ONLY.name.lowercase())
        ?: Method.XPOSED_ONLY.name.lowercase()
    val transportValue = prefs.getString("transport", "aidl") ?: "aidl"
    val timeFormatSummary = prefs.getString("time_format", "dd-MM-yyyy hh:mm:ss")
        ?: "dd-MM-yyyy hh:mm:ss"
    val mapSummary = when (mapProvider) {
        "amap" -> stringResource(R.string.name_amap)
        else -> stringResource(R.string.name_google_maps)
    }
    val methodSummary = when (methodValue) {
        Method.HYBRID.name.lowercase() -> stringResource(R.string.title_method_hybrid)
        Method.TEST_PROVIDER_ONLY.name.lowercase() -> stringResource(R.string.title_method_test_provider_only)
        else -> stringResource(R.string.title_method_xposed_only)
    }
    val transportSummary = when (transportValue) {
        "ws" -> stringResource(R.string.title_transport_ws)
        else -> stringResource(R.string.title_transport_aidl)
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            SettingsSectionCard(
                title = stringResource(R.string.title_settings_map),
                description = stringResource(R.string.text_pending_map_provider),
                icon = Icons.Default.Map,
                summary = stringResource(
                    R.string.text_settings_current_value,
                    mapSummary
                ),
                onClick = { onOpen(SettingsSection.MAPS) }
            )
        }
        item {
            SettingsSectionCard(
                title = stringResource(R.string.title_settings_naming),
                description = stringResource(R.string.caption_settings_time_format),
                icon = Icons.Default.EditCalendar,
                summary = stringResource(
                    R.string.text_settings_current_value,
                    timeFormatSummary
                ),
                onClick = { onOpen(SettingsSection.NAMING) }
            )
        }
        item {
            SettingsSectionCard(
                title = stringResource(R.string.title_settings_emulation),
                description = stringResource(R.string.title_emulation_method),
                icon = Icons.Default.AutoFixHigh,
                summary = stringResource(
                    R.string.text_settings_emulation_summary,
                    methodSummary,
                    transportSummary
                ),
                onClick = { onOpen(SettingsSection.EMULATION) }
            )
        }
    }
}

@Composable
private fun MapsSettings(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.sharedPreferences() }
    val mapProviders = listOf(
        "gcp_maps" to stringResource(R.string.name_google_maps),
        "amap" to stringResource(R.string.name_amap),
    )
    val coordSystems = listOf(
        "auto" to stringResource(R.string.name_coord_sys_auto),
        "wgs84" to "WGS84",
        "gcj02" to "GCJ02",
    )
    var mapProvider by remember { mutableStateOf(prefs.getString("map_provider", "gcp_maps") ?: "gcp_maps") }
    var poiProvider by remember { mutableStateOf(prefs.getString("poi_provider", "gcp_maps") ?: "gcp_maps") }
    var exportCoord by remember { mutableStateOf(prefs.getString("export_coord_sys", "auto") ?: "auto") }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            SettingsSingleChoiceGroup(
                title = stringResource(R.string.title_settings_map_provider),
                options = mapProviders.map { (provider, title) ->
                    SettingsChoiceOption(
                        title = title,
                        description = if (provider == "gcp_maps") {
                            stringResource(R.string.text_provider_google_maps)
                        } else {
                            stringResource(R.string.text_provider_amap)
                        },
                        selected = mapProvider == provider,
                        onSelect = {
                            mapProvider = provider
                            prefs.edit { putString("map_provider", provider) }
                        }
                    )
                }
            )
        }
        item {
            SettingsSingleChoiceGroup(
                title = stringResource(R.string.title_settings_poi_provider),
                options = mapProviders.map { (provider, title) ->
                    SettingsChoiceOption(
                        title = title,
                        description = if (provider == "gcp_maps") {
                            stringResource(R.string.text_provider_google_poi)
                        } else {
                            stringResource(R.string.text_provider_amap_poi)
                        },
                        selected = poiProvider == provider,
                        onSelect = {
                            poiProvider = provider
                            prefs.edit { putString("poi_provider", provider) }
                        }
                    )
                }
            )
        }

        item {
            SettingsSingleChoiceGroup(
                title = stringResource(R.string.title_settings_export_coord),
                description = stringResource(R.string.text_settings_export_coord),
                options = coordSystems.map { (value, title) ->
                    SettingsChoiceOption(
                        title = title,
                        description = if (value == "auto") {
                            stringResource(R.string.text_coord_auto)
                        } else {
                            stringResource(R.string.text_coord_fixed, title)
                        },
                        selected = exportCoord == value,
                        onSelect = {
                            exportCoord = value
                            prefs.edit { putString("export_coord_sys", value) }
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun NamingSettings(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.sharedPreferences() }
    var customFormat by remember { mutableStateOf(prefs.getBoolean("customize_time_format", false)) }
    var format by remember { mutableStateOf(prefs.getString("time_format", "dd-MM-yyyy hh:mm:ss") ?: "dd-MM-yyyy hh:mm:ss") }
    var previewText by remember { mutableStateOf("") }
    var isFormatValid by remember { mutableStateOf(true) }
    val invalidText = stringResource(R.string.text_time_format_invalid)

    fun updatePreview(value: String) {
        runCatching {
            val formatter = SimpleDateFormat(value, Locale.getDefault())
            previewText = formatter.format(Date())
            isFormatValid = true
        }.onFailure {
            previewText = invalidText
            isFormatValid = false
        }
    }

    LaunchedEffect(format) {
        updatePreview(format)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.caption_settings_time_format),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        SettingsSwitchItem(
            title = stringResource(R.string.title_settings_use_custom_time_format),
            checked = customFormat,
            description = stringResource(R.string.text_time_format_hint),
            onCheckedChange = {
                customFormat = it
                prefs.edit { putBoolean("customize_time_format", it) }
            }
        )
        SettingsTextFieldItem(
            label = stringResource(R.string.settings_title_time_format),
            value = format,
            onValueChange = {
                format = it
                updatePreview(it)
                if (isFormatValid) {
                    prefs.edit { putString("time_format", it) }
                }
            },
            enabled = customFormat,
            isError = customFormat && !isFormatValid,
            supportingText = when {
                !customFormat -> stringResource(R.string.text_time_format_disabled)
                !isFormatValid -> invalidText
                else -> stringResource(R.string.text_time_format_preview, previewText)
            }
        )
    }
}

@Composable
private fun EmulationSettings(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.sharedPreferences() }
    val methods = listOf(
        Method.XPOSED_ONLY.name.lowercase() to stringResource(R.string.title_method_xposed_only),
        Method.HYBRID.name.lowercase() to stringResource(R.string.title_method_hybrid),
        Method.TEST_PROVIDER_ONLY.name.lowercase() to stringResource(R.string.title_method_test_provider_only),
    )
    val transports = listOf(
        "aidl" to stringResource(R.string.title_transport_aidl),
        "ws" to stringResource(R.string.title_transport_ws),
    )
    var method by remember { mutableStateOf(prefs.getString("method", Method.XPOSED_ONLY.name.lowercase()) ?: Method.XPOSED_ONLY.name.lowercase()) }
    var port by remember { mutableStateOf(prefs.getString("provider_port", "20230") ?: "20230") }
    var useTls by remember { mutableStateOf(prefs.getBoolean("provider_tls", true)) }
    var transport by remember { mutableStateOf(prefs.getString("transport", "aidl") ?: "aidl") }
    val isPortValid = remember(port) { port.toIntOrNull()?.let { it in 1024..65535 } == true }
    val portHint = stringResource(R.string.text_port_hint)

    fun notifyPlugins() {
        if (Plugins.initialized) {
            Plugins.notifySettingsChanged(context)
        }
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            SettingsSingleChoiceGroup(
                title = stringResource(R.string.title_emulation_method),
                options = methods.map { (item, title) ->
                    SettingsChoiceOption(
                        title = title,
                        selected = method == item,
                        onSelect = {
                            method = item
                            prefs.edit { putString("method", item) }
                            notifyPlugins()
                        }
                    )
                }
            )
        }
        item {
            SettingsSingleChoiceGroup(
                title = stringResource(R.string.title_transport),
                options = transports.map { (item, title) ->
                    SettingsChoiceOption(
                        title = title,
                        selected = transport == item,
                        onSelect = {
                            transport = item
                            prefs.edit { putString("transport", item) }
                            notifyPlugins()
                        }
                    )
                }
            )
        }
        item {
            SettingsGroupTitle(stringResource(R.string.caption_server))
            SettingsTextFieldItem(
                label = stringResource(R.string.title_server_port),
                value = port,
                onValueChange = {
                    port = it
                    if (it.toIntOrNull()?.let { p -> p in 1024..65535 } == true) {
                        prefs.edit { putString("provider_port", it) }
                        notifyPlugins()
                    }
                },
                isError = !isPortValid,
                supportingText = if (isPortValid) portHint else stringResource(R.string.text_input_invalid)
            )
        }
        item {
            SettingsSwitchItem(
                title = stringResource(R.string.title_use_tls),
                checked = useTls,
                description = stringResource(R.string.text_tls_hint),
                onCheckedChange = {
                    useTls = it
                    prefs.edit { putBoolean("provider_tls", it) }
                    notifyPlugins()
                }
            )
        }
    }
}
