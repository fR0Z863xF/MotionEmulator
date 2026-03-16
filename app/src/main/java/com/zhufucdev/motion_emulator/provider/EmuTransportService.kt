package com.zhufucdev.motion_emulator.provider

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.util.Log
import com.zhufucdev.me.aidl.EmulationInfoDto
import com.zhufucdev.me.aidl.IEmuAgentCallback
import com.zhufucdev.me.aidl.IEmuTransport
import com.zhufucdev.me.aidl.IntermediateDto
import com.zhufucdev.me.stub.Emulation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.protobuf.ProtoBuf

class EmuTransportService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val callbacks = ConcurrentHashMap<String, IEmuAgentCallback>()
    private val pipeJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private var stateListener: ListenCallback? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        stateListener = Scheduler.onAgentStateChanged { id, state ->
            val callback = callbacks[id] ?: return@onAgentStateChanged
            runCatching {
                callback.onStateChanged(state.ordinal)
            }.onFailure {
                callbacks.remove(id)
            }
        }
    }

    override fun onDestroy() {
        pipeJobs.values.forEach { it.cancel() }
        scope.cancel()
        callbacks.clear()
        stateListener?.pause()
        super.onDestroy()
    }

    private val binder = object : IEmuTransport.Stub() {
        override fun getVersion(): Int = TRANSPORT_VERSION

        override fun registerAgent(id: String?, cb: IEmuAgentCallback?) {
            if (id.isNullOrBlank() || cb == null) return
            callbacks[id] = cb
            Log.i(TAG, "Agent registered: $id")
        }

        override fun unregisterAgent(id: String?) {
            if (id.isNullOrBlank()) return
            callbacks.remove(id)
        }

        override fun openEmulationPipe(id: String?): ParcelFileDescriptor? {
            if (id.isNullOrBlank()) return null
            val emulation = Scheduler.emulation ?: return null
            val resume = Scheduler.resumeOf(id)
            val payload = if (resume == null) emulation else emulation.copy(resume = resume)
            Log.i(TAG, "Open emulation pipe for $id")

            val pipe = ParcelFileDescriptor.createPipe()
            val read = pipe[0]
            val write = pipe[1]

            pipeJobs[id]?.cancel()
            pipeJobs[id] = scope.launch {
                ParcelFileDescriptor.AutoCloseOutputStream(write).use { output ->
                    runCatching { output.write(encodeEmulation(payload)) }
                        .onFailure { error ->
                            notifyError(id, ERROR_PIPE_WRITE, error)
                        }
                }
            }

            return read
        }

        override fun sendEmulationInfo(id: String?, info: EmulationInfoDto?) {
            if (id.isNullOrBlank() || info == null) return
            Scheduler.notifyEmulationStarted(id, info.toStub())
            Log.i(TAG, "Emulation started from $id")
        }

        override fun sendIntermediate(id: String?, data: IntermediateDto?) {
            if (id.isNullOrBlank() || data == null) return
            Scheduler.setIntermediate(id, data.toStub())
        }

        override fun sendAgentState(id: String?, state: Int) {
            if (id.isNullOrBlank()) return
            val agentState = com.zhufucdev.me.stub.AgentState.values().getOrNull(state) ?: return
            when (agentState) {
                com.zhufucdev.me.stub.AgentState.CANCELED -> Scheduler.cancelAgent(id)
                com.zhufucdev.me.stub.AgentState.PAUSED -> Scheduler.pauseAgent(id)
                com.zhufucdev.me.stub.AgentState.PENDING -> Scheduler.startAgent(id)
                com.zhufucdev.me.stub.AgentState.NOT_JOINED -> Scheduler.notifyAgentDisconnected(id)
                else -> Unit
            }
            Log.i(TAG, "Agent state from $id: ${agentState.name}")
        }
    }

    private fun notifyError(id: String, code: Int, throwable: Throwable) {
        val callback = callbacks[id] ?: return
        try {
            callback.onError(code, throwable.message ?: "unknown")
        } catch (_: RemoteException) {
            callbacks.remove(id)
        }
        Log.w(TAG, "EmuTransportService error for $id", throwable)
    }

    private fun encodeEmulation(emulation: Emulation): ByteArray {
        return try {
            ProtoBuf.encodeToByteArray(Emulation.serializer(), emulation)
        } catch (e: Exception) {
            throw IOException("Failed to encode emulation", e)
        }
    }

    companion object {
        private const val TAG = "EmuTransportService"
        const val TRANSPORT_VERSION = 1
        const val ERROR_PIPE_WRITE = 1001
    }
}