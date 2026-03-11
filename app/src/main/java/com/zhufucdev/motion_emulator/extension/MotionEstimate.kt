package com.zhufucdev.motion_emulator.extension

import android.hardware.Sensor
import com.zhufucdev.me.stub.Motion
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun Motion.estimateSpeed(): Double? {
    fun containsType(type: Int) = timelines.containsKey(type)
    val counter = containsType(Sensor.TYPE_STEP_COUNTER)
    val detector = containsType(Sensor.TYPE_STEP_DETECTOR)
    if (!counter && !detector) return null

    var sum = 0.0
    var count = 0

    if (counter) {
        val moments = timelines[Sensor.TYPE_STEP_COUNTER].orEmpty()
        for (index in 1 until moments.size) {
            val current = moments[index]
            val last = moments[index - 1]
            val steps = current.data.firstOrNull()?.minus(last.data.firstOrNull() ?: continue) ?: continue
            val time = current.elapsed - last.elapsed
            if (time <= 0f || steps < 0f) continue

            sum += 1.2 * steps / time
            count++
        }
    } else {
        val moments = timelines[Sensor.TYPE_STEP_DETECTOR].orEmpty()
        for (index in 1 until moments.size) {
            val current = moments[index]
            val last = moments[index - 1]
            val time = current.elapsed - last.elapsed
            if (time <= 0f) continue

            sum += 1.2 / time
            count++
        }
    }

    if (sum < 0 || sum.isNaN() || count <= 0) return null
    return sum / count
}

fun Motion.estimateTimespan(): Duration {
    val moments = timelines.values.flatten()
    if (moments.size < 2) return Duration.ZERO

    val earliest = moments.minOf { it.elapsed }
    val latest = moments.maxOf { it.elapsed }
    return (latest - earliest).toDouble().seconds
}
