package com.zhufucdev.motion_emulator.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.Motions
import com.zhufucdev.motion_emulator.data.Telephonies
import com.zhufucdev.motion_emulator.data.Traces
import com.zhufucdev.motion_emulator.extension.sharedPreferences
import com.zhufucdev.motion_emulator.ui.component.TooltipHost
import com.zhufucdev.motion_emulator.ui.composition.DefaultFloatingActionButtonManipulator
import com.zhufucdev.motion_emulator.ui.composition.LocalNavControllerProvider
import com.zhufucdev.motion_emulator.ui.composition.LocalNestedScrollConnectionProvider
import com.zhufucdev.motion_emulator.ui.composition.LocalSnackbarProvider
import com.zhufucdev.motion_emulator.ui.model.ManagerViewModel
import com.zhufucdev.update.AppUpdater

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHome(windowSize: WindowSizeClass, updater: AppUpdater) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val showTopBar = backStackEntry?.destination.showGlobalTopBar()
    val showNavigationChrome = backStackEntry?.destination.showPrimaryNavigationChrome()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbars = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { context.sharedPreferences() }
    val updateFoundText = stringResource(R.string.text_update_found)
    val upgradeText = stringResource(R.string.action_upgrade)
    var pendingMapRoute by remember { mutableStateOf<String?>(null) }
    var pendingMapProvider by remember { mutableStateOf("gcp_maps") }

    fun navigateWithRequirement(route: String, requiresMap: Boolean = false) {
        val mapProvider = preferences.getString("map_provider", null)
        if (requiresMap && mapProvider.isNullOrBlank()) {
            pendingMapRoute = route
            pendingMapProvider = "gcp_maps"
        } else {
            navController.navigate(route)
        }
    }

    LaunchedEffect(updater.update) {
        if (updater.update != null) {
            val result = snackbars.showSnackbar(
                message = updateFoundText,
                actionLabel = upgradeText,
                withDismissAction = true
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                navController.navigate(HomeDestinations.Updater.route)
            }
        }
    }

    pendingMapRoute?.let { targetRoute ->
        AlertDialog(
            onDismissRequest = { pendingMapRoute = null },
            title = { Text(stringResource(R.string.title_is_gcs_accessible)) },
            text = {
                androidx.compose.foundation.layout.Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProviderOptionCard(
                        title = stringResource(R.string.title_gcs_accessible),
                        description = stringResource(R.string.text_gcs_accessible),
                        selected = pendingMapProvider == "gcp_maps",
                        onClick = { pendingMapProvider = "gcp_maps" }
                    )
                    ProviderOptionCard(
                        title = stringResource(R.string.title_gcs_inaccessible),
                        description = stringResource(R.string.text_gcs_inaccessible),
                        selected = pendingMapProvider == "amap",
                        onClick = { pendingMapProvider = "amap" }
                    )
                    Text(
                        text = stringResource(R.string.text_pending_map_provider),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    preferences.edit()
                        .putString("map_provider", pendingMapProvider)
                        .putString("poi_provider", pendingMapProvider)
                        .apply()
                    pendingMapRoute = null
                    navController.navigate(targetRoute)
                }) {
                    Text(stringResource(R.string.action_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMapRoute = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    CompositionLocalProvider(
        LocalSnackbarProvider provides snackbars,
        LocalNestedScrollConnectionProvider provides scrollBehavior.nestedScrollConnection,
        LocalNavControllerProvider provides navController
    ) {
        when (windowSize.widthSizeClass) {
            WindowWidthSizeClass.Compact -> {
                Scaffold(
                    topBar = {
                        if (showTopBar) {
                            TopBar(scrollBehavior)
                        }
                    },
                    floatingActionButton = {
                        DefaultFloatingActionButtonManipulator.CurrentFloatingActionButton()
                    },
                    snackbarHost = { SnackbarHost(snackbars) },
                    bottomBar = {
                        if (showNavigationChrome) {
                            NavigationBar {
                                PrimaryDestinations.entries.forEach { dest ->
                                    val selected =
                                        backStackEntry?.destination?.let { dest.selected(it) } == true
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            if (!selected) {
                                                navigateWithRequirement(
                                                    dest.route,
                                                    requiresMap = dest == PrimaryDestinations.Emulate
                                                )
                                            }
                                        },
                                        icon = dest.icon,
                                        label = dest.label
                                    )
                                }
                            }
                        }
                    }
                ) {
                    NavContent(it)
                }
            }

            WindowWidthSizeClass.Medium -> {
                Row {
                    if (showNavigationChrome) {
                        NavigationRail {
                            PrimaryDestinations.entries.forEach { dest ->
                                val selected =
                                    backStackEntry?.destination?.let { dest.selected(it) } == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        if (!selected) {
                                            navigateWithRequirement(
                                                dest.route,
                                                requiresMap = dest == PrimaryDestinations.Emulate
                                            )
                                        }
                                    },
                                    icon = dest.icon,
                                    label = dest.label
                                )
                            }
                        }
                    }
                    Scaffold(
                        topBar = {
                            if (showTopBar) {
                                TopBar(scrollBehavior)
                            }
                        },
                        floatingActionButton = { DefaultFloatingActionButtonManipulator.CurrentFloatingActionButton() },
                        snackbarHost = { SnackbarHost(snackbars) }
                    ) {
                        NavContent(it)
                    }
                }
            }

            WindowWidthSizeClass.Expanded -> {
                val content: @Composable () -> Unit = {
                    Scaffold(
                        topBar = {
                            if (showTopBar) {
                                TopBar(scrollBehavior)
                            }
                        },
                        floatingActionButton = { DefaultFloatingActionButtonManipulator.CurrentFloatingActionButton() },
                        snackbarHost = { SnackbarHost(snackbars) }
                    ) {
                        NavContent(it)
                    }
                }
                if (showNavigationChrome) {
                    PermanentNavigationDrawer(
                        drawerContent = {
                            PermanentDrawerSheet {
                                PrimaryDestinations.entries.forEach { dest ->
                                    val selected =
                                        backStackEntry?.destination?.let { dest.selected(it) } == true
                                    NavigationDrawerItem(
                                        selected = selected,
                                        onClick = {
                                            if (!selected) {
                                                navigateWithRequirement(
                                                    dest.route,
                                                    requiresMap = dest == PrimaryDestinations.Emulate
                                                )
                                            }
                                        },
                                        icon = dest.icon,
                                        label = dest.label
                                    )
                                }
                            }
                        }
                    ) {
                        content()
                    }
                } else {
                    content()
                }
            }
        }
    }
}

@Composable
private fun ProviderOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NavContent(paddingValues: PaddingValues) {
    val provider = LocalViewModelStoreOwner.current!!
    val managerModel: ManagerViewModel = viewModel()
    NavHost(
        navController = LocalNavControllerProvider.current!!,
        startDestination = PrimaryDestinations.Data.route
    ) {
        composable(PrimaryDestinations.Plugins.route) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides provider) {
                PluginsApp(paddingValues)
            }
        }
        composable(PrimaryDestinations.Emulate.route) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides provider) {
                EmulateHome(paddingValues)
            }
        }
        composable(
            route = "${HomeDestinations.Record.route}?motion={motion}&telephony={telephony}&returnToData={returnToData}",
            arguments = listOf(
                navArgument("motion") {
                    type = NavType.BoolType
                    defaultValue = true
                },
                navArgument("telephony") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("returnToData") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) {
            val enableMotion = it.arguments?.getBoolean("motion") ?: true
            val enableTelephony = it.arguments?.getBoolean("telephony") ?: false
            val returnToData = it.arguments?.getBoolean("returnToData") ?: false
            CompositionLocalProvider(LocalViewModelStoreOwner provides provider) {
                RecordApp(
                    paddingValues = paddingValues,
                    initialMotionEnabled = enableMotion,
                    initialTelephonyEnabled = enableTelephony,
                    managerModel = managerModel,
                    returnToDataAfterSave = returnToData,
                )
            }
        }
        composable(
            route = "${HomeDestinations.Trace.route}?returnToData={returnToData}",
            arguments = listOf(
                navArgument("returnToData") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) {
            val returnToData = it.arguments?.getBoolean("returnToData") ?: false
            CompositionLocalProvider(LocalViewModelStoreOwner provides provider) {
                TraceApp(
                    paddingValues = paddingValues,
                    managerModel = managerModel,
                    returnToDataAfterSave = returnToData,
                )
            }
        }
        composable(HomeDestinations.Updater.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val navController = LocalNavControllerProvider.current
            LaunchedEffect(Unit) {
                context.startActivity(Intent(context, UpdaterActivity::class.java))
                navController?.popBackStack()
            }
        }
        navigation(
            startDestination = "home",
            route = PrimaryDestinations.Data.route
        ) {
            composable("home") {
                CompositionLocalProvider(LocalViewModelStoreOwner provides provider) {
                    ManagerApp(
                        paddingValues = paddingValues,
                        managerModel = managerModel,
                    )
                }
            }
            composable(
                route = "telephony/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) {
                val targetId = it.arguments?.getString("id")
                val target = targetId?.let { id -> Telephonies[id] }
                if (target != null) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides provider) {
                        CellEditor(target, paddingValues)
                    }
                } else {
                    MissingDataFallback(paddingValues)
                }
            }
            composable(
                route = "cell/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) {
                val targetId = it.arguments?.getString("id")
                val target = targetId?.let { id -> Telephonies[id] }
                if (target != null) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides provider) {
                        CellEditor(target, paddingValues)
                    }
                } else {
                    MissingDataFallback(paddingValues)
                }
            }
            composable(
                route = "motion/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) {
                val targetId = it.arguments?.getString("id")
                val target = targetId?.let { id -> Motions[id] }
                if (target != null) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides provider) {
                        MotionEditor(target, paddingValues)
                    }
                } else {
                    MissingDataFallback(paddingValues)
                }
            }
            composable(
                route = "trace/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) {
                val targetId = it.arguments?.getString("id")
                val target = targetId?.let { id -> Traces[id] }
                if (target != null) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides provider) {
                        TraceEditor(target, paddingValues)
                    }
                } else {
                    MissingDataFallback(paddingValues)
                }
            }
        }
    }
}

@Composable
private fun MissingDataFallback(paddingValues: PaddingValues) {
    val navController = LocalNavControllerProvider.current
    val snackbars = LocalSnackbarProvider.current
    val message = stringResource(R.string.text_empty_list)

    LaunchedEffect(Unit) {
        snackbars?.showSnackbar(message)
        navController?.popBackStack()
    }

    FeaturePlaceholder(
        paddingValues = paddingValues,
        title = stringResource(R.string.text_empty_list),
        description = stringResource(R.string.text_empty_list)
    )
}

@Composable
private fun FeaturePlaceholder(paddingValues: PaddingValues, title: String, description: String) {
    Scaffold(
        modifier = Modifier.padding(paddingValues)
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = title, style = MaterialTheme.typography.headlineMedium)
                androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
                Text(text = description, style = MaterialTheme.typography.bodyLarge)
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                Text(text = stringResource(R.string.title_overview), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(scrollBehavior: TopAppBarScrollBehavior) {
    val context = androidx.compose.ui.platform.LocalContext.current
    TooltipHost {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = stringResource(id = R.string.app_name),
                    fontFamily = FontFamily.Serif
                )
            },
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_monochrome),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            actions = {
                IconButton(
                    onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    },
                    content = {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                    },
                    modifier = Modifier.tooltip {
                        Text(text = stringResource(id = R.string.title_activity_settings))
                    }
                )
            }
        )
    }
}

enum class PrimaryDestinations(
    val label: @Composable () -> Unit,
    val icon: @Composable () -> Unit,
    val route: String
) {
    Plugins(
        label = { Text(text = stringResource(id = R.string.title_activity_plugin)) },
        icon = { Icon(imageVector = Icons.Default.Extension, contentDescription = null) },
        route = "plugins"
    ),
    Emulate(
        label = { Text(text = stringResource(id = R.string.title_emulate)) },
        icon = { Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null) },
        route = "emulate"
    ),
    Data(
        label = { Text(text = stringResource(id = R.string.title_data)) },
        icon = { Icon(imageVector = Icons.Default.Storage, contentDescription = null) },
        route = "data"
    )
}

enum class HomeDestinations(val route: String) {
    Record("record"),
    Trace("trace_drawing"),
    Updater("updater")
}

fun PrimaryDestinations.selected(currentDest: NavDestination): Boolean =
    isCurrent(currentDest) || currentDest.hierarchy.any { isCurrent(it) }

fun PrimaryDestinations.isCurrent(currentDest: NavDestination) =
    currentDest.route == route

private fun NavDestination?.showGlobalTopBar(): Boolean {
    val route = this?.route?.substringBefore('?') ?: return true
    return route != HomeDestinations.Record.route && route != HomeDestinations.Trace.route
}

private fun NavDestination?.showPrimaryNavigationChrome(): Boolean {
    val destination = this ?: return false
    return PrimaryDestinations.entries.any { it.selected(destination) }
}
