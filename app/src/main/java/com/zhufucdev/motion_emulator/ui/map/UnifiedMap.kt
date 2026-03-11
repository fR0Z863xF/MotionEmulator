package com.zhufucdev.motion_emulator.ui.map

import android.os.Bundle
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.MapView as AMapView
import com.google.android.gms.maps.MapView as GoogleMapView
import com.zhufucdev.motion_emulator.extension.skipAmapFuckingLicense

enum class UnifiedMapProvider {
    AMAP,
    GCP_MAPS,
}

@Composable
fun UnifiedMap(
    provider: UnifiedMapProvider,
    modifier: Modifier = Modifier,
    displayStyle: MapStyle = MapStyle.NORMAL,
    displayType: MapDisplayType = MapDisplayType.INTERACTIVE,
    onReady: (MapController) -> Unit,
) {
    when (provider) {
        UnifiedMapProvider.AMAP -> AMapContainer(modifier, displayStyle, displayType, onReady)
        UnifiedMapProvider.GCP_MAPS -> GoogleMapContainer(modifier, displayStyle, displayType, onReady)
    }
}

@Composable
private fun AMapContainer(
    modifier: Modifier,
    displayStyle: MapStyle,
    displayType: MapDisplayType,
    onReady: (MapController) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    skipAmapFuckingLicense(context)
    val mapView = remember {
        AMapView(context).apply {
            id = android.view.View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            onCreate(Bundle())
        }
    }
    val controller = remember { AMapController(mapView.map, context) }
    RememberMapLifecycle(mapView, lifecycleOwner)

    LaunchedEffect(controller, displayStyle, displayType) {
        controller.displayStyle = displayStyle
        controller.displayType = displayType
        onReady(controller)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

@Composable
private fun GoogleMapContainer(
    modifier: Modifier,
    displayStyle: MapStyle,
    displayType: MapDisplayType,
    onReady: (MapController) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        GoogleMapView(context).apply {
            id = android.view.View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            onCreate(Bundle())
        }
    }
    RememberMapLifecycle(mapView, lifecycleOwner)
    var controller by remember { mutableStateOf<MapController?>(null) }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { googleMap ->
            controller = GoogleMapsController(context, googleMap)
        }
    }
    LaunchedEffect(controller, displayStyle, displayType) {
        controller?.let {
            it.displayStyle = displayStyle
            it.displayType = displayType
            onReady(it)
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

@Composable
private fun RememberMapLifecycle(mapView: Any, lifecycleOwner: LifecycleOwner) {
    DisposableEffect(mapView, lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (mapView is GoogleMapView) {
                    mapView.onStart()
                }
            }

            override fun onResume(owner: LifecycleOwner) {
                when (mapView) {
                    is AMapView -> mapView.onResume()
                    is GoogleMapView -> mapView.onResume()
                }
            }

            override fun onPause(owner: LifecycleOwner) {
                when (mapView) {
                    is AMapView -> mapView.onPause()
                    is GoogleMapView -> mapView.onPause()
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                if (mapView is GoogleMapView) {
                    mapView.onStop()
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                when (mapView) {
                    is AMapView -> mapView.onDestroy()
                    is GoogleMapView -> mapView.onDestroy()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
