package com.zhufucdev.motion_emulator.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import com.zhufucdev.me.stub.CoordinateSystem
import com.zhufucdev.me.stub.Point
import com.zhufucdev.me.stub.Trace
import com.zhufucdev.motion_emulator.extension.dateString
import com.zhufucdev.motion_emulator.extension.effectiveTimeFormat
import com.zhufucdev.motion_emulator.extension.toPoint
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

abstract class MapController(protected val context: Context) {
    abstract fun moveCamera(location: Point, focus: Boolean = false, animate: Boolean = false)
    abstract fun boundCamera(bounds: TraceBounds, animate: Boolean = false)
    abstract fun project(x: Int, y: Int): Point
    abstract suspend fun getAddress(point: Point): String?
    abstract fun cameraCenter(): Point
    abstract fun usePen(): MapScrawl
    abstract fun drawTrace(trace: Trace): MapTraceCallback
    abstract fun updateLocationIndicator(location: Location)
    abstract var displayStyle: MapStyle
    abstract var displayType: MapDisplayType

    @SuppressLint("MissingPermission")
    suspend fun beginGpsSampling(
        onSample: (Point, firstFix: Boolean, points: List<Point>) -> Unit,
        onLocation: (Location) -> Unit = {},
    ): GpsSamplingSession = suspendCoroutine { continuation ->
        val locationManager = context.getSystemService(LocationManager::class.java)
        val provider = LocationManager.GPS_PROVIDER
        if (!locationManager.isProviderEnabled(provider)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !locationManager.hasProvider(provider)) {
                continuation.resume(GpsSamplingSession.Unsupported)
                return@suspendCoroutine
            }
        }

        var paused = false
        var started = false
        var segmentStarted = false
        val pen = usePen()
        lateinit var session: GpsSamplingSession.Active
        val listener = LocationListener { location ->
            onLocation(location)
            if (paused) return@LocationListener

            val point = location.toPoint()
            if (!segmentStarted) {
                session.pen.markBegin()
                segmentStarted = true
            }
            session.pen.addPoint(point)
            onSample(point, !started, session.pen.points)
            if (!started) {
                started = true
            }
        }

        session = GpsSamplingSession.Active(
            pen = pen,
            pause = { paused = true },
            unpause = {
                paused = false
                segmentStarted = false
            },
            stop = { locationManager.removeUpdates(listener) },
        )
        locationManager.requestLocationUpdates(provider, 0L, mapCaptureAccuracy, listener)
        continuation.resume(session)
    }

    suspend fun buildTraceName(point: Point? = null): String {
        val actualPoint = point ?: runCatching { cameraCenter() }.getOrNull()
        val address = actualPoint?.let {
            runCatching { getAddress(it) }.getOrNull()
        }
        return address?.let { context.getString(com.zhufucdev.motion_emulator.R.string.text_near, it) }
            ?: context.effectiveTimeFormat().dateString()
    }
}

interface MapTraceCallback {
    fun remove()
}

interface MapScrawl {
    val points: List<Point>
    fun addPoint(point: Point)
    fun markBegin()
    fun undo()
    fun clear()
}

sealed interface GpsSamplingSession {
    data object Unsupported : GpsSamplingSession

    class Active(
        val pen: MapScrawl,
        private val pause: () -> Unit,
        private val unpause: () -> Unit,
        private val stop: () -> Unit,
    ) : GpsSamplingSession {
        var isPaused: Boolean = false
            private set

        fun pause() {
            pause.invoke()
            isPaused = true
        }

        fun unpause() {
            unpause.invoke()
            isPaused = false
        }

        fun stop() {
            stop.invoke()
        }
    }
}

const val mapCaptureAccuracy = 0.5F

enum class MapStyle {
    NORMAL,
    NIGHT,
    SATELLITE,
}

enum class MapDisplayType {
    STILL,
    INTERACTIVE,
}
