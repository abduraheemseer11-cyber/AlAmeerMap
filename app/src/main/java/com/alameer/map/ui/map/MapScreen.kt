package com.alameer.map.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alameer.map.R
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

@Composable
fun MapScreen(
    vm: MapViewModel,
    onAddAt: (lat: Double, lon: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val markers by vm.markers.collectAsStateWithLifecycle()
    val satellite by vm.satellite.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val focus by vm.focus.collectAsStateWithLifecycle()
    val addAt by rememberUpdatedState(onAddAt)

    val mapView = remember {
        MapView(ctx).apply {
            setTileSource(if (vm.satellite.value) TileSources.EsriSatellite else TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 3.0
            maxZoomLevel = 20.0
            controller.setZoom(vm.camera.zoom)
            controller.setCenter(GeoPoint(vm.camera.lat, vm.camera.lon))
            // Long-press anywhere to add a device there.
            overlays.add(
                MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?) = false
                    override fun longPressHelper(p: GeoPoint?): Boolean {
                        p?.let { addAt(it.latitude, it.longitude) }
                        return true
                    }
                }),
            )
        }
    }

    // Forward lifecycle to the MapView and remember the camera when leaving the tab.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val c = mapView.mapCenter
            vm.camera.lat = c.latitude
            vm.camera.lon = c.longitude
            vm.camera.zoom = mapView.zoomLevelDouble
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // First time markers arrive: zoom so that all of them are visible.
    LaunchedEffect(markers.isNotEmpty()) {
        if (!vm.camera.fitted && markers.isNotEmpty() && vm.focus.value == null) {
            vm.camera.fitted = true
            val box = BoundingBox.fromGeoPoints(markers.map { GeoPoint(it.latitude, it.longitude) })
            mapView.post { mapView.zoomToBoundingBox(box, false, 120, 17.0, null) }
        }
    }

    // Search result tapped: pan to it.
    LaunchedEffect(focus) {
        focus?.let { f ->
            vm.camera.fitted = true
            mapView.post { mapView.controller.animateTo(GeoPoint(f.lat, f.lon), 18.0, 800L) }
            vm.clearFocus()
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
            update = { map ->
                val source = if (satellite) TileSources.EsriSatellite else TileSourceFactory.MAPNIK
                if (map.tileProvider.tileSource !== source) map.setTileSource(source)

                map.overlays.removeAll { it is Marker }
                markers.forEach { m ->
                    map.overlays.add(
                        Marker(map).apply {
                            position = GeoPoint(m.latitude, m.longitude)
                            title = m.clientName.ifBlank { m.networkNumber }
                            icon = ContextCompat.getDrawable(ctx, R.drawable.ic_map_pin)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            setOnMarkerClickListener { _, _ ->
                                vm.select(m.id)
                                true
                            }
                        },
                    )
                }
                map.invalidate()
            },
        )

        // Crosshair: "Add here" uses the center of the screen.
        Icon(
            Icons.Default.Add, null,
            modifier = Modifier.align(Alignment.Center).size(28.dp),
            tint = Color.White,
        )

        Column(
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = vm::toggleSatellite) {
                Text(stringResource(if (satellite) R.string.map_streets else R.string.map_satellite))
            }
            FilledTonalIconButton(onClick = { vm.sync(manual = true) }, enabled = !syncing) {
                if (syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, stringResource(R.string.map_sync))
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                val c = mapView.mapCenter
                addAt(c.latitude, c.longitude)
            },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text(stringResource(R.string.map_add_here)) },
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
        )

        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(topStart = 8.dp),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            Text(
                stringResource(if (satellite) R.string.attribution_esri else R.string.attribution_osm),
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
