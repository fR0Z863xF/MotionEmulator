package com.zhufucdev.motion_emulator.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.SphericalUtil
import com.google.maps.android.ktx.addPolyline
import com.zhufucdev.me.stub.Point
import com.zhufucdev.me.stub.Trace
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.extension.ensureGoogleCoordinate
import com.zhufucdev.motion_emulator.extension.getAddressWithGoogle
import com.zhufucdev.motion_emulator.extension.isDarkModeEnabled
import com.zhufucdev.motion_emulator.extension.toGoogleLatLng
import com.zhufucdev.motion_emulator.extension.toPoint
import kotlin.math.pow

@SuppressLint("MissingPermission")
class GoogleMapsController(context: Context, private val map: GoogleMap) : MapController(context) {
    override var displayStyle: MapStyle = MapStyle.NORMAL
        set(value) {
            field = value
            when (value) {
                MapStyle.NORMAL -> {
                    map.setMapStyle(null)
                    map.mapType = GoogleMap.MAP_TYPE_NORMAL
                }

                MapStyle.NIGHT -> {
                    map.setMapStyle(MapStyleOptions.loadRawResourceStyle(context, R.raw.mapstyle_night))
                    map.mapType = GoogleMap.MAP_TYPE_NORMAL
                }

                MapStyle.SATELLITE -> {
                    map.setMapStyle(null)
                    map.mapType = GoogleMap.MAP_TYPE_SATELLITE
                }
            }
        }

    override var displayType: MapDisplayType = MapDisplayType.INTERACTIVE
        set(value) {
            field = value
            when (value) {
                MapDisplayType.STILL -> map.uiSettings.apply {
                    isScrollGesturesEnabled = false
                    isZoomGesturesEnabled = false
                    isRotateGesturesEnabled = false
                }

                MapDisplayType.INTERACTIVE -> map.uiSettings.apply {
                    isScrollGesturesEnabled = true
                    isZoomGesturesEnabled = true
                    isRotateGesturesEnabled = true
                }
            }
        }

    init {
        displayStyle = if (isDarkModeEnabled(context.resources)) MapStyle.NIGHT else MapStyle.NORMAL
        map.isMyLocationEnabled = false
        map.uiSettings.isZoomControlsEnabled = false
    }

    override fun moveCamera(location: Point, focus: Boolean, animate: Boolean) {
        val zoom = if (focus) 19F else map.cameraPosition.zoom
        val update = CameraUpdateFactory.newLatLngZoom(
            location.ensureGoogleCoordinate().toGoogleLatLng(),
            zoom
        )
        if (animate) map.animateCamera(update) else map.moveCamera(update)
    }

    override fun boundCamera(bounds: TraceBounds, animate: Boolean) {
        val update = CameraUpdateFactory.newLatLngBounds(bounds.google(), 40)
        if (animate) map.animateCamera(update) else map.moveCamera(update)
    }

    override fun project(x: Int, y: Int): Point =
        map.projection.fromScreenLocation(android.graphics.Point(x, y)).toPoint()

    override suspend fun getAddress(point: Point): String? =
        getAddressWithGoogle(point.ensureGoogleCoordinate().toGoogleLatLng(), context)

    override fun cameraCenter(): Point = map.cameraPosition.target.toPoint()

    private val lineColor
        get() = if (isDarkModeEnabled(context.resources)) android.graphics.Color.rgb(120, 180, 255)
        else android.graphics.Color.rgb(33, 150, 243)
    private val indicatorColor
        get() = if (isDarkModeEnabled(context.resources)) android.graphics.Color.rgb(120, 180, 255)
        else android.graphics.Color.rgb(33, 150, 243)
    private val indicatorStroke
        get() = if (isDarkModeEnabled(context.resources)) android.graphics.Color.rgb(250, 250, 250)
        else android.graphics.Color.rgb(55, 71, 79)

    override fun usePen() = object : MapScrawl {
        private val polyline = PolylineOptions().color(lineColor)
        private var lastPos = LatLng(0.0, 0.0)
        private val backStack = arrayListOf<ArrayList<LatLng>>()
        private var lastPolyline: Polyline? = null

        override val points: List<Point>
            get() = polyline.points.map { it.toPoint() }

        override fun addPoint(point: Point) {
            val mapped = point.ensureGoogleCoordinate().toGoogleLatLng()
            if (distance(lastPos, mapped) >= mapCaptureAccuracy) {
                polyline.add(mapped)
                backStack.lastOrNull()?.add(mapped)
                lastPolyline?.remove()
                lastPolyline = map.addPolyline(polyline)
            }
            lastPos = mapped
        }

        override fun markBegin() {
            backStack.add(arrayListOf())
        }

        override fun undo() {
            backStack.removeLastOrNull()?.let { removed ->
                removed.forEach { point ->
                    if (polyline.points.contains(point)) polyline.points.remove(point)
                    else polyline.add(point)
                }
            } ?: return
            lastPolyline?.remove()
            lastPolyline = map.addPolyline(polyline)
            lastPos = polyline.points.lastOrNull() ?: LatLng(0.0, 0.0)
        }

        override fun clear() {
            val summary = arrayListOf<LatLng>()
            backStack.forEach { summary.addAll(it) }
            backStack.add(summary)
            polyline.points.clear()
            lastPolyline?.remove()
            lastPolyline = null
            lastPos = LatLng(0.0, 0.0)
        }
    }

    override fun drawTrace(trace: Trace): MapTraceCallback {
        val points = trace.points.map { it.ensureGoogleCoordinate().toGoogleLatLng() }
        if (points.isEmpty()) {
            return object : MapTraceCallback {
                override fun remove() = Unit
            }
        }
        val line = map.addPolyline {
            color(lineColor)
            addAll(points + listOf(points.first()))
        }
        return object : MapTraceCallback {
            override fun remove() {
                line.remove()
            }
        }
    }

    private var locationIndicator: Circle? = null
    private var accuracyIndicator: Circle? = null

    override fun updateLocationIndicator(location: Location) {
        val point = location.toPoint().ensureGoogleCoordinate().toGoogleLatLng()
        accuracyIndicator?.remove()
        accuracyIndicator = map.addCircle(
            CircleOptions()
                .center(point)
                .strokeColor(0)
                .fillColor(android.graphics.Color.argb(100, 30, 136, 229))
                .radius(location.accuracy.toDouble())
        )
        redrawLocationIndicator(point)
    }

    private fun redrawLocationIndicator(point: LatLng) {
        locationIndicator?.remove()
        locationIndicator = map.addCircle(
            CircleOptions()
                .center(point)
                .fillColor(indicatorColor)
                .strokeColor(indicatorStroke)
                .strokeWidth(4F)
                .radius(1_048_576 / 2.0.pow(map.cameraPosition.zoom.toDouble()))
                .zIndex(10F)
        )
    }

    private fun distance(a: LatLng, b: LatLng) = SphericalUtil.computeDistanceBetween(a, b)

}
