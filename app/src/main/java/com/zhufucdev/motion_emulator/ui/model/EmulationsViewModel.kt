package com.zhufucdev.motion_emulator.ui.model

import android.content.Context
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.ViewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.zhufucdev.me.stub.EMPTY_REF
import com.zhufucdev.motion_emulator.data.DataLoader
import com.zhufucdev.motion_emulator.data.Emulations
import com.zhufucdev.motion_emulator.data.Motions
import com.zhufucdev.motion_emulator.data.Telephonies
import com.zhufucdev.motion_emulator.data.Traces
import com.zhufucdev.motion_emulator.data.WorkingData
import com.zhufucdev.me.stub.Metadata
import com.zhufucdev.motion_emulator.extension.sharedPreferences
import com.zhufucdev.motion_emulator.provider.EmulationMonitorWorker
import com.zhufucdev.motion_emulator.provider.Scheduler
import com.zhufucdev.motion_emulator.provider.WORK_NAME_MONITOR
import kotlinx.serialization.json.Json

class EmulationsViewModel(
    configs: List<DataLoader<EmulationRef>>,
    private val context: Context
) : ViewModel() {
    val configs = configs.toMutableStateList()
    private val preferences by lazy { context.sharedPreferences() }

    fun createDefault(): DataLoader<EmulationRef> {
        val template = defaultConfig() ?: EmulationRef(
            id = NanoIdUtils.randomNanoId(),
            name = "New Emulation",
            trace = EMPTY_REF,
            motion = EMPTY_REF,
            cells = EMPTY_REF,
            velocity = 3.0,
            repeat = 1,
            satelliteCount = 12,
        )
        val record = WorkingData(
            template.copy(
                id = NanoIdUtils.randomNanoId(),
                name = "New Emulation",
            ),
            Metadata(0, name = "New Emulation")
        )
        configs.add(record)
        return record
    }

    suspend fun save(config: DataLoader<EmulationRef>) {
        Emulations.require(context)
        Emulations.put(config, overwrite = true)
        val index = configs.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            configs[index] = config
        } else {
            configs.add(config)
        }
    }

    suspend fun remove(config: DataLoader<EmulationRef>) {
        Emulations.require(context)
        Emulations.delete(config, context)
        configs.removeAll { it.id == config.id }
    }

    fun availableTraces() = Traces.list()
    fun availableMotions() = Motions.list()
    fun availableCells() = Telephonies.list()

    fun defaultConfig(): EmulationRef? {
        val text = preferences.getString(KEY_DEFAULT_CONFIG, null) ?: return null
        return runCatching { Json.decodeFromString<EmulationRef>(text) }.getOrNull()
    }

    fun saveDefaultConfig(config: DataLoader<EmulationRef>) {
        preferences.edit()
            .putString(KEY_DEFAULT_CONFIG, Json.encodeToString(EmulationRef.serializer(), config.value))
            .apply()
    }

    fun start(config: EmulationRef) {
        Traces.require(context)
        Motions.require(context)
        Telephonies.require(context)
        Scheduler.init(context)
        Scheduler.emulation = config.emulation()
        val request = OneTimeWorkRequestBuilder<EmulationMonitorWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME_MONITOR, ExistingWorkPolicy.REPLACE, request)
    }

    fun stop() {
        Scheduler.emulation = null
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_MONITOR)
    }

    private companion object {
        const val KEY_DEFAULT_CONFIG = "default_config"
    }
}
