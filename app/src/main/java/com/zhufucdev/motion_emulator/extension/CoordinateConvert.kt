package com.zhufucdev.motion_emulator.extension

import com.zhufucdev.me.stub.CoordinateSystem
import com.zhufucdev.me.stub.MapProjector
import com.zhufucdev.me.stub.Point
import com.zhufucdev.me.stub.Trace
import com.zhufucdev.me.stub.toPoint

fun Point.toCoordinateSystem(target: CoordinateSystem): Point {
    if (coordinateSystem == target) return this
    return when (target) {
        CoordinateSystem.WGS84 ->
            with(MapProjector) { this@toCoordinateSystem.toIdeal() }.toPoint(CoordinateSystem.WGS84)

        CoordinateSystem.GCJ02 ->
            with(MapProjector) { this@toCoordinateSystem.toTarget() }.toPoint(CoordinateSystem.GCJ02)
    }
}

fun Trace.toCoordinateSystem(target: CoordinateSystem): Trace {
    if (coordinateSystem == target) return this
    val convertedPoints = points.map { it.toCoordinateSystem(target) }
    return copy(points = convertedPoints, coordinateSystem = target)
}