package com.zhufucdev.motion_emulator.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhufucdev.motion_emulator.data.Telephonies
import com.zhufucdev.motion_emulator.data.DataLoader
import com.zhufucdev.motion_emulator.data.Emulations
import com.zhufucdev.motion_emulator.data.Motions
import com.zhufucdev.motion_emulator.data.Traces
import com.zhufucdev.motion_emulator.extension.setUpStatusBar
import com.zhufucdev.motion_emulator.plugin.Plugins
import com.zhufucdev.motion_emulator.ui.model.EmulationsViewModel
import com.zhufucdev.motion_emulator.ui.model.ManagerViewModel
import com.zhufucdev.motion_emulator.ui.model.PluginViewModel
import com.zhufucdev.motion_emulator.ui.model.PluginItemState
import com.zhufucdev.motion_emulator.ui.model.toPluginItem
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpStatusBar()

        setContent {
            MotionEmulatorTheme {
                AppHome(calculateWindowSizeClass(this))
            }
        }
    }

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            Emulations.require(this@MainActivity)
            EmulationsViewModel(
                configs = Emulations.list(),
                context = this@MainActivity
            )
        }

        initializer {
            Plugins.init(this@MainActivity)
            val enabled = Plugins.enabled
            val all = Plugins.available
            val plugins = enabled.map { it.toPluginItem(true) } + (all - enabled.toSet()).map {
                it.toPluginItem(false)
            }
            PluginViewModel(
                plugins = plugins,
                downloadable = flow {
                    emit(
                        plugins.filter { it.state is PluginItemState.NotInstalled }
                    )
                }
            )
        }

        initializer {
            val stores = listOf(Traces, Motions, Telephonies)
            val data = mutableStateListOf<DataLoader<*>>()
            ManagerViewModel(
                data = data,
                dataLoader = flow {
                    emit(false)
                    if (data.isEmpty()) {
                        withContext(Dispatchers.IO) {
                            data.addAll(
                                stores.flatMap {
                                    it.require(this@MainActivity)
                                    it.list()
                                }.sortedBy { it.id }
                            )
                        }
                    }
                    emit(true)
                },
                stores = stores,
                context = this@MainActivity
            )
        }
    }
}