package com.zhufucdev.motion_emulator.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme

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
    val sections = listOf(
        Quadruple(
            SettingsSection.MAPS,
            R.string.title_settings_map,
            R.string.text_pending_map_provider,
            Icons.Default.Map,
        ),
        Quadruple(
            SettingsSection.NAMING,
            R.string.title_settings_naming,
            R.string.caption_settings_time_format,
            Icons.Default.EditCalendar,
        ),
        Quadruple(
            SettingsSection.EMULATION,
            R.string.title_settings_emulation,
            R.string.title_emulation_method,
            Icons.Default.AutoFixHigh,
        ),
    )

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(sections) { (section, title, description, icon) ->
            ListItem(
                headlineContent = { Text(stringResource(title)) },
                leadingContent = { Icon(icon, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                supportingContent = { Text(stringResource(description)) }
            )
            TextButton(
                onClick = { onOpen(section) },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(stringResource(R.string.action_continue))
            }
            HorizontalDivider()
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
            SettingsGroupTitle(stringResource(R.string.title_settings_map_provider))
        }
        items(mapProviders) { (provider, title) ->
            SettingsChoiceItem(
                title = title,
                selected = mapProvider == provider,
                onClick = {
                    mapProvider = provider
                    prefs.edit().putString("map_provider", provider).apply()
                }
            )
        }
        item {
            SettingsGroupTitle(stringResource(R.string.title_settings_poi_provider))
        }
        items(mapProviders) { (provider, title) ->
            SettingsChoiceItem(
                title = title,
                selected = poiProvider == provider,
                onClick = {
                    poiProvider = provider
                    prefs.edit().putString("poi_provider", provider).apply()
                }
            )
        }

        item {
            SettingsGroupTitle(stringResource(R.string.title_settings_export_coord))
        }
        items(coordSystems) { (value, title) ->
            SettingsChoiceItem(
                title = title,
                selected = exportCoord == value,
                onClick = {
                    exportCoord = value
                    prefs.edit().putString("export_coord_sys", value).apply()
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.caption_settings_time_format),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.title_settings_use_custom_time_format)) },
            trailingContent = {
                Switch(
                    checked = customFormat,
                    onCheckedChange = {
                        customFormat = it
                        prefs.edit().putBoolean("customize_time_format", it).apply()
                    }
                )
            }
        )
        OutlinedTextField(
            value = format,
            onValueChange = {
                format = it
                prefs.edit().putString("time_format", it).apply()
            },
            label = { Text(stringResource(R.string.settings_title_time_format)) },
            enabled = customFormat,
            modifier = Modifier.fillMaxWidth()
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
        "aidl" to "AIDL",
        "ws" to "WebSocket",
    )
    var method by remember { mutableStateOf(prefs.getString("method", Method.XPOSED_ONLY.name.lowercase()) ?: Method.XPOSED_ONLY.name.lowercase()) }
    var port by remember { mutableStateOf(prefs.getString("provider_port", "20230") ?: "20230") }
    var useTls by remember { mutableStateOf(prefs.getBoolean("provider_tls", true)) }
    var transport by remember { mutableStateOf(prefs.getString("transport", "aidl") ?: "aidl") }
    val isPortValid = remember(port) { port.toIntOrNull()?.let { it in 1024..65535 } == true }

    fun notifyPlugins() {
        if (Plugins.initialized) {
            Plugins.notifySettingsChanged(context)
        }
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            SettingsGroupTitle(stringResource(R.string.title_emulation_method))
        }
        items(methods) { (item, title) ->
            SettingsChoiceItem(
                title = title,
                selected = method == item,
                onClick = {
                    method = item
                    prefs.edit().putString("method", item).apply()
                    notifyPlugins()
                }
            )
        }
        item {
            SettingsGroupTitle("Transport")
        }
        items(transports) { (item, title) ->
            SettingsChoiceItem(
                title = title,
                selected = transport == item,
                onClick = {
                    transport = item
                    prefs.edit().putString("transport", item).apply()
                    notifyPlugins()
                }
            )
        }
        item {
            SettingsGroupTitle(stringResource(R.string.caption_server))
            OutlinedTextField(
                value = port,
                onValueChange = {
                    port = it
                    if (it.toIntOrNull()?.let { p -> p in 1024..65535 } == true) {
                        prefs.edit().putString("provider_port", it).apply()
                        notifyPlugins()
                    }
                },
                label = { Text(stringResource(R.string.title_server_port)) },
                isError = !isPortValid,
                supportingText = {
                    if (!isPortValid) {
                        Text(stringResource(R.string.text_input_invalid))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.title_use_tls)) },
                trailingContent = {
                    Switch(
                        checked = useTls,
                        onCheckedChange = {
                            useTls = it
                            prefs.edit().putBoolean("provider_tls", it).apply()
                            notifyPlugins()
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun SettingsGroupTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingsChoiceItem(title: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            if (selected) {
                Text(text = "Selected")
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
    TextButton(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Text(if (selected) "Current" else "Use")
    }
    HorizontalDivider()
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)
