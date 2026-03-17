package com.zhufucdev.motion_emulator.ui.map

import android.content.Context
import android.location.Location
import com.amap.api.maps.AMap
import com.amap.api.maps.AMapUtils
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.Circle
import com.amap.api.maps.model.CircleOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.zhufucdev.me.stub.Point
import com.zhufucdev.me.stub.Trace
import com.zhufucdev.motion_emulator.extension.ensureAmapCoordinate
import com.zhufucdev.motion_emulator.extension.getAddressWithAmap
import com.zhufucdev.motion_emulator.extension.isDarkModeEnabled
import com.zhufucdev.motion_emulator.extension.toAmapLatLng
import com.zhufucdev.motion_emulator.extension.toPoint
import kotlin.math.pow

class AMapController(private val map: AMap, context: Context) : MapController(context) {
    init {
        map.apply {
            isMyLocationEnabled = false
            uiSettings.isZoomControlsEnabled = false
            if (isDarkModeEnabled(context.resources)) {
                mapType = AMap.MAP_TYPE_NIGHT
                displayStyle = MapStyle.NIGHT
            } else {
                displayStyle = MapStyle.NORMAL
            }
        }

        var zoom = map.cameraPosition.zoom
        map.addOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(cam: com.amap.api.maps.model.CameraPosition) {
                val indicator = locationIndicator ?: return
                if (cam.zoom != zoom) {
                    zoom = cam.zoom
                    redrawLocationIndicator(indicator.center)
                }
            }

            override fun onCameraChangeFinish(cam: com.amap.api.maps.model.CameraPosition?) = Unit
        })
    }

    private val lineColor
        get() = if (isDarkModeEnabled(context.resources)) android.graphics.Color.rgb(120, 180, 255)
        else android.graphics.Color.rgb(33, 150, 243)
    private val indicatorColor
        get() = if (isDarkModeEnabled(context.resources)) android.graphics.Color.rgb(255, 183, 77)
        else android.graphics.Color.rgb(255, 152, 0)
    private val indicatorStroke
        get() =
            if (map.mapType == AMap.MAP_TYPE_NORMAL) android.graphics.Color.rgb(55, 71, 79)
            else android.graphics.Color.rgb(250, 250, 250)

    override fun moveCamera(location: Point, focus: Boolean, animate: Boolean) {
        val zoom = if (focus) 19F else map.cameraPosition.zoom
        val camera = CameraUpdateFactory.newLatLngZoom(
            location.ensureAmapCoordinate(context).toAmapLatLng(),
            zoom
        )
        if (animate) map.animateCamera(camera) else map.moveCamera(camera)
    }

    override fun boundCamera(bounds: TraceBounds, animate: Boolean) {
        val update = CameraUpdateFactory.newLatLngBounds(bounds.amap(context), 400)
        if (animate) map.animateCamera(update) else map.moveCamera(update)
    }

    override fun usePen(): MapScrawl = object : MapScrawl {
        private var lastPos = LatLng(0.0, 0.0)
        private val polyline = PolylineOptions().color(lineColor)
        private val backStack = arrayListOf<ArrayList<LatLng>>()
        private var lastPolyline: Polyline? = null

        override val points: List<Point>
            get() = polyline.points.map { it.toPoint() }

        override fun addPoint(point: Point) {
            val mapped = point.ensureAmapCoordinate(context).toAmapLatLng()
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

    override fun project(x: Int, y: Int): Point =
        map.projection.fromScreenLocation(android.graphics.Point(x, y)).toPoint()

    override suspend fun getAddress(point: Point): String? =
        getAddressWithAmap(point.ensureAmapCoordinate(context).toAmapLatLng())

    override fun cameraCenter(): Point = map.cameraPosition.target.toPoint()

    override fun drawTrace(trace: Trace): MapTraceCallback {
        val points = trace.points.map { it.ensureAmapCoordinate(context).toAmapLatLng() }
        if (points.isEmpty()) {
            return object : MapTraceCallback {
                override fun remove() = Unit
            }
        }
        val line = map.addPolyline(
            PolylineOptions().addAll(points + listOf(points.first())).color(lineColor)
        )
        return object : MapTraceCallback {
            override fun remove() {
                line.remove()
            }
        }
    }

    private var locationIndicator: Circle? = null
    private var accuracyIndicator: Circle? = null

    override fun updateLocationIndicator(location: Location) {
        val point = location.toPoint().ensureAmapCoordinate(context).toAmapLatLng()
        accuracyIndicator?.remove()
        accuracyIndicator = map.addCircle(
            CircleOptions()
                .center(point)
                .strokeColor(0)
                .fillColor(android.graphics.Color.argb(100, 30, 136, 229))
                .radius(location.accuracy * 1.0)
        )
        redrawLocationIndicator(point)
    }

    private fun redrawLocationIndicator(point: LatLng) {
        locationIndicator?.remove()
        val radius = (1_048_576 / 2.0.pow(map.cameraPosition.zoom.toDouble())).coerceAtLeast(6.0)
        locationIndicator = map.addCircle(
            CircleOptions()
                .center(point)
                .fillColor(indicatorColor)
                .strokeColor(indicatorStroke)
                .strokeWidth(5F)
                .radius(radius)
                .zIndex(10F)
        )
    }

    private fun distance(a: LatLng, b: LatLng): Float = AMapUtils.calculateLineDistance(a, b)

    override var displayStyle: MapStyle = MapStyle.NORMAL
        set(value) {
            field = value
            map.mapType = when (value) {
                MapStyle.NORMAL -> AMap.MAP_TYPE_NORMAL
                MapStyle.NIGHT -> AMap.MAP_TYPE_NIGHT
                MapStyle.SATELLITE -> AMap.MAP_TYPE_SATELLITE
            }
        }

    override var displayType: MapDisplayType = MapDisplayType.INTERACTIVE
        set(value) {
            field = value
            when (value) {
                MapDisplayType.STILL -> map.uiSettings.apply {
                    isZoomGesturesEnabled = false
                    isScrollGesturesEnabled = false
                    isRotateGesturesEnabled = false
                }

                MapDisplayType.INTERACTIVE -> map.uiSettings.apply {
                    isZoomGesturesEnabled = true
                    isScrollGesturesEnabled = true
                    isRotateGesturesEnabled = true
                }
            }
        }
}
