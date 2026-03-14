package com.zhufucdev.motion_emulator.provider

import android.content.Context
import android.util.Log
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.zhufucdev.me.stub.AgentState
import com.zhufucdev.me.stub.Emulation
import com.zhufucdev.me.stub.EmulationInfo
import com.zhufucdev.me.stub.EmulationResume
import com.zhufucdev.me.stub.Intermediate
import com.zhufucdev.motion_emulator.extension.sharedPreferences
import com.zhufucdev.motion_emulator.plugin.Plugins
import com.zhufucdev.motion_emulator.provider.Scheduler.incomingAgentStateOf
import com.zhufucdev.motion_emulator.provider.Scheduler.instance
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.deserialize
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.protobuf.protobuf
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.application.serverConfig
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.util.getOrFail
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.converter
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocketRaw
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.collections.set
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

private const val WS_PING_PERIOD_SECONDS = 15L
private const val WS_TIMEOUT_SECONDS = 30L

/**
 * The lifecycle of an agent:
 * - [AgentState.NOT_JOINED] when the agent is offline and unknown to all.
 * - [AgentState.PENDING] when it's found but doesn't respond a [EmulationInfo],
 * so it won't appear in the [instance].
 * - [AgentState.RUNNING] during the emulation.
 * During [AgentState.RUNNING], two possible following states:
 * - [AgentState.CANCELED] when the emulation is canceled by user.
 * - [AgentState.PAUSED] when the emulation is paused by user.
 * Finally, if no interruption occurred,
 * - [AgentState.COMPLETED] when the agent reports itself.
 * Otherwise,
 * - [AgentState.FAILURE] when the agent returns an exception.
 */
object Scheduler {
    private const val RESUME_TTL_MS = 2 * 60 * 1000L
    private val stateListeners = mutableSetOf<(String, AgentState) -> Boolean>()
    private val intermediateListeners = mutableSetOf<(String, Intermediate) -> Unit>()

    private var mEmulation: Emulation? = null
    private var mInfo: MutableMap<String, EmulationInfo> = hashMapOf()
    private val mState: MutableMap<String, AgentState> = hashMapOf()
    private val mIntermediate: MutableMap<String, Intermediate> = hashMapOf()
    private val mIntermediateUpdatedAt: MutableMap<String, Long> = hashMapOf()

    private var port = 20230
    private var tls = false
    private lateinit var server: EmbeddedServer<*, *>
    private var serverRunning = false

    fun init(context: Context) {
        if (serverRunning) {
            Log.w("Scheduler", "Reinitialize a running instance")
            return
        }
        val prefs = context.sharedPreferences()
        port = prefs.getString("provider_port", "")!!.toIntOrNull() ?: 20230
        tls = prefs.getBoolean("provider_tls", true)
        val environment = applicationEnvironment { }
        val config = serverConfig(environment) {
            developmentMode = false
            module(Application::eventServer)
        }
        server = embeddedServer(Netty, config) {
            if (tls) {
                configureSsl(port)
            } else {
                configure(port)
            }
        }

        server.start(false)
        serverRunning = true

        Plugins.notifyStart(context)
    }

    fun setIntermediate(id: String, info: Intermediate) {
        mIntermediate[id] = info
        mIntermediateUpdatedAt[id] = System.currentTimeMillis()
        intermediateListeners.forEach {
            try {
                it.invoke(id, info)
            } catch (e: Exception) {
                Log.w("Scheduler", "Error while notifying intermediate")
                e.printStackTrace()
            }
        }
    }

    val intermediate: Map<String, Intermediate> get() = mIntermediate

    fun startAgent(id: String) {
        notifyStateChanged(id, AgentState.PENDING)
    }

    fun cancelAgent(id: String) = stopAgent(id, AgentState.CANCELED)

    fun pauseAgent(id: String) = stopAgent(id, AgentState.PAUSED)

    private fun stopAgent(id: String, state: AgentState) {

        if (!mInfo.containsKey(id)) {
            throw IllegalStateException("No agent: $id")
        }
        mIntermediate.remove(id)
        mIntermediateUpdatedAt.remove(id)
        notifyStateChanged(id, state)
    }

    fun cancelAll() {
        notifyAll(AgentState.CANCELED)
    }

    fun startAll() {
        notifyAll(AgentState.PENDING)
    }

    private fun notifyAll(target: AgentState) {
        mState.forEach { (id, state) ->
            if (state != target)
                notifyStateChanged(id, target)
        }
    }

    internal fun notifyEmulationStarted(id: String, info: EmulationInfo) {
        mInfo[id] = info
        notifyStateChanged(id, AgentState.RUNNING)
    }

    internal fun notifyEmulationCompleted(id: String) {
        notifyStateChanged(id, AgentState.COMPLETED)
    }

    internal fun notifyEmulationFailed(id: String) {
        notifyStateChanged(id, AgentState.FAILURE)
    }

    internal fun notifyAgentDisconnected(id: String) {
        mInfo.remove(id)
        notifyStateChanged(id, AgentState.NOT_JOINED)
    }

    private fun cleanupStaleIntermediate() {
        val now = System.currentTimeMillis()
        val expired = mIntermediateUpdatedAt
            .filterValues { now - it > RESUME_TTL_MS }
            .keys
        if (expired.isEmpty()) return
        expired.forEach {
            mIntermediate.remove(it)
            mIntermediateUpdatedAt.remove(it)
        }
    }

    private fun intermediateToResume(intermediate: Intermediate) = EmulationResume(
        loopIndex = intermediate.loopIndex,
        progress = intermediate.progress,
        elapsed = intermediate.elapsed,
    )

    fun latestResume(): EmulationResume? {
        cleanupStaleIntermediate()
        val latestId = mIntermediateUpdatedAt.maxByOrNull { it.value }?.key ?: return null
        val intermediate = mIntermediate[latestId] ?: return null
        return intermediateToResume(intermediate)
    }

    fun resumeOf(id: String): EmulationResume? {
        cleanupStaleIntermediate()
        val intermediate = mIntermediate[id]
        val updatedAt = mIntermediateUpdatedAt[id]
        if (intermediate != null && updatedAt != null) {
            val expired = System.currentTimeMillis() - updatedAt > RESUME_TTL_MS
            if (!expired) {
                return intermediateToResume(intermediate)
            }
            mIntermediate.remove(id)
            mIntermediateUpdatedAt.remove(id)
        }
        return latestResume()
    }

    private fun notifyStateChanged(id: String, state: AgentState) {
        mState[id] = state
        stateListeners.removeAll {
            try {
                it.invoke(id, state)
            } catch (e: Exception) {
                Log.w("Scheduler", "Error while notifying changes to emulation info")
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Known emulation agents
     */
    val instance: Map<String, EmulationInfo> get() = mInfo

    var emulation: Emulation?
        set(value) {
            if (mEmulation != value) {
                mIntermediate.clear()
                mIntermediateUpdatedAt.clear()
                if (value == null) {
                    val original = mInfo
                    mInfo = mutableMapOf()
                    original.forEach { (id, _) ->
                        notifyStateChanged(id, AgentState.CANCELED)
                    }
                }
                mState.clear()
            }
            mEmulation = value
        }
        get() = mEmulation

    /**
     * Controller state is somewhat global emulation state tends to be
     */
    val controllerState: AgentState
        get() =
            if (emulation == null) AgentState.NOT_JOINED
            else if (instance.isEmpty() || mState.size == 1 && mState.containsValue(AgentState.PENDING)) AgentState.PENDING
            else if (mState.containsValue(AgentState.RUNNING)) AgentState.RUNNING
            else if (mState.values.all { it == AgentState.PAUSED }) AgentState.PAUSED
            else if (mState.values.all { it.fin }) AgentState.COMPLETED
            else AgentState.CANCELED

    fun onAgentStateChanged(l: (id: String, state: AgentState) -> Unit): ListenCallback {
        val actual: (String, AgentState) -> Boolean = { id, state ->
            l.invoke(id, state)
            false
        }
        stateListeners.add(actual)

        return object : ListenCallback {
            override fun pause() {
                if (!stateListeners.contains(actual)) {
                    throw IllegalStateException("Already paused")
                }

                stateListeners.remove(actual)
            }

            override fun resume() {
                if (stateListeners.contains(actual)) {
                    throw IllegalStateException("Already resumed")
                }

                stateListeners.add(actual)
            }
        }
    }

    fun currentEmulationState(targetId: String): AgentState {
        return mState[targetId] ?: AgentState.NOT_JOINED
    }

    /**
     * The coroutine way of [onAgentStateChanged],
     * which registers a state listener and removes itself ever since
     * a new state of the [targetId] is posted
     *
     * In the case [targetId] is null, it waits for any state
     * change.
     *
     * @see [incomingAgentStateOf] in favor of channels
     */
    suspend fun nextStateChange(targetId: String? = null) =
        suspendCancellableCoroutine {
            stateListeners.add { target, state ->
                if (targetId == null || target == targetId) {
                    if (!it.isCancelled)
                        it.resume(state)
                    return@add true
                }
                false
            }
        }

    /**
     * The channel way of [onAgentStateChanged].
     * @see [nextStateChange] in favor of suspend functions
     */
    fun CoroutineScope.incomingAgentStateOf(targetId: String? = null): ReceiveChannel<AgentState> {
        val channel = Channel<AgentState>()
        stateListeners.add { target, state ->
            if (targetId == null || target == targetId) {
                channel.trySend(state)
            }
            !isActive
        }
        return channel
    }

    fun addIntermediateListener(l: (String, Intermediate) -> Unit): ListenCallback {
        intermediateListeners.add(l)

        return object : ListenCallback {
            override fun pause() {
                if (!intermediateListeners.contains(l)) {
                    throw IllegalStateException("Already paused")
                }

                intermediateListeners.remove(l)
            }

            override fun resume() {
                if (intermediateListeners.contains(l)) {
                    throw IllegalStateException("Already resumed")
                }

                intermediateListeners.add(l)
            }
        }
    }

    fun stop(context: Context) {
        Plugins.notifyStop(context)
        notifyAll(AgentState.NOT_JOINED)
        server.stop()
        serverRunning = false
    }
}

interface ListenCallback {
    fun pause()
    fun resume()
}

fun ApplicationEngine.Configuration.configureSsl(port: Int) {
    val keyAlis = "motion_provider"
    val keyPassword = NanoIdUtils.randomNanoId().toCharArray()
    val keyStore = generateSelfSignedKeyStore(keyAlis, keyPassword)

    sslConnector(
        keyStore = keyStore,
        privateKeyPassword = { keyPassword },
        keyStorePassword = { keyPassword },
        keyAlias = keyAlis
    ) {
        host = "127.0.0.1"
        this.port = port
    }
}

fun ApplicationEngine.Configuration.configure(port: Int) {
    connector {
        host = "127.0.0.1"
        this.port = port
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun Application.eventServer() {
    install(ContentNegotiation) {
        protobuf()
    }

    install(WebSockets) {
        pingPeriod = WS_PING_PERIOD_SECONDS.seconds
        timeout = WS_TIMEOUT_SECONDS.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(ProtoBuf)
    }

    routing {
        webSocketRaw("/join/{id}") {
            val id = call.parameters.getOrFail("id")
            Scheduler.startAgent(id)

            var worker = launchWorker(id)

            for (req in incomingAgentStateOf(id)) {
                when (req) {
                    AgentState.NOT_JOINED -> {
                        sendCommandSafely(id, AgentState.NOT_JOINED)
                        worker.cancelAndJoin()
                        break
                    }

                    AgentState.PENDING -> {
                        worker.cancelAndJoin()
                        worker = launchWorker(id)
                        sendCommandSafely(id, AgentState.PENDING)
                    }

                    AgentState.RUNNING -> {}

                    else -> {
                        if (!req.fin) {
                            sendCommandSafely(id, req)
                        }
                        worker.cancelAndJoin()
                    }
                }
            }
        }

        get("/current/{id?}") {
            val id = call.parameters["id"]
            if (
                id != null &&
                Scheduler.currentEmulationState(id) !in setOf(AgentState.PENDING, AgentState.NOT_JOINED)
            ) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }
            val emulation = Scheduler.emulation
            if (emulation == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                val resume = if (id == null) Scheduler.latestResume() else Scheduler.resumeOf(id)
                val response = if (resume == null) emulation else emulation.copy(resume = resume)
                call.respond(response)
            }
        }
    }
}

private suspend fun WebSocketServerSession.launchWorker(id: String) = launch {
    var finishedByProtocol = false
    var closedByPeer = false
    try {
        var info: EmulationInfo? = null
        while (info == null) {
            when (val frame = incoming.receive()) {
                is Frame.Close -> {
                    closedByPeer = true
                    Log.i("Scheduler", "Agent sent close frame before started: $id")
                    return@launch
                }

                is Frame.Ping -> {
                    send(Frame.Pong(frame.data))
                }

                is Frame.Pong -> Unit
                else -> {
                    val single = frame.data.singleOrNull()
                    if (single == 0x7f.toByte() || single == (-0x7f).toByte()) {
                        Log.w(
                            "Scheduler",
                            "Unexpected control frame before started: $single (id=$id, type=${frame.frameType.name})"
                        )
                        continue
                    }
                    runCatching {
                        info = converter!!.deserialize<EmulationInfo>(frame)
                    }.onFailure { error ->
                        Log.w(
                            "Scheduler",
                            "Failed to decode EmulationInfo (id=$id, type=${frame.frameType.name}, size=${frame.data.size})",
                            error
                        )
                        throw error
                    }
                }
            }
        }
        Scheduler.notifyEmulationStarted(id, info)
        for (frame in incoming) {
            when (frame) {
                is Frame.Close -> {
                    closedByPeer = true
                    Log.i("Scheduler", "Agent sent close frame: $id")
                    break
                }

                is Frame.Ping -> {
                    send(Frame.Pong(frame.data))
                }

                is Frame.Pong -> Unit
                else -> {
                    when (frame.data.singleOrNull()) {
                        0x7f.toByte() -> {
                            finishedByProtocol = true
                            Scheduler.notifyEmulationCompleted(id)
                            break
                        }

                        (-0x7f).toByte() -> {
                            finishedByProtocol = true
                            Scheduler.notifyEmulationFailed(id)
                            break
                        }

                        else -> {
                            val data = runCatching {
                                converter!!.deserialize<Intermediate>(frame)
                            }.onFailure { error ->
                                Log.w(
                                    "Scheduler",
                                    "Failed to decode Intermediate (id=$id, type=${frame.frameType.name}, size=${frame.data.size})",
                                    error
                                )
                            }.getOrNull()
                            if (data != null) {
                                Scheduler.setIntermediate(id, data)
                            }
                        }
                    }
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: ClosedReceiveChannelException) {
        Unit
    } catch (e: Exception) {
        Log.w("Scheduler", "Error while receiving agent updates: $id", e)
    } finally {
        if (isActive && !finishedByProtocol) {
            Scheduler.notifyAgentDisconnected(id)
            if (closedByPeer) {
                Log.i("Scheduler", "Agent data channel closed by peer: $id")
            } else {
                Log.w("Scheduler", "Agent data channel disconnected unexpectedly: $id")
            }
        }
    }
}

private suspend fun WebSocketSession.sendCommand(state: AgentState) {
    send(byteArrayOf(state.ordinal.toByte()))
}

private suspend fun WebSocketSession.sendCommandSafely(id: String, state: AgentState) {
    runCatching { sendCommand(state) }
        .onFailure {
            Log.d("Scheduler", "Skip sending ${state.name} to disconnected agent: $id")
        }
}