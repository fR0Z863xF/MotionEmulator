package com.zhufucdev.motion_emulator.extension

import android.content.Context
import android.content.SharedPreferences
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.zhufucdev.me.stub.CoordinateSystem
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

fun Context.sharedPreferences() = PreferenceManager.getDefaultSharedPreferences(this)

fun Context.lazySharedPreferences() = lazy { this.sharedPreferences() }

fun Fragment.lazySharedPreferences() = lazy { requireContext().sharedPreferences() }

fun SharedPreferences.effectiveTimeFormat(): DateFormat {
    val useCustom = getBoolean("customize_time_format", false)
    return if (useCustom) {
        val format = getString("time_format", "dd-MM-yyyy hh:mm:ss")
        SimpleDateFormat(format, Locale.getDefault())
    } else {
        SimpleDateFormat.getDateTimeInstance()
    }
}

fun Context.effectiveTimeFormat(): DateFormat {
    val preferences by lazySharedPreferences()
    return preferences.effectiveTimeFormat()
}

fun SharedPreferences.exportCoordinateSystem(): CoordinateSystem? =
    when (getString("export_coord_sys", "auto")) {
        "wgs84" -> CoordinateSystem.WGS84
        "gcj02" -> CoordinateSystem.GCJ02
        else -> null
    }

