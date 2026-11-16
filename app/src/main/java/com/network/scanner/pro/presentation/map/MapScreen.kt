package com.network.scanner.pro.presentation.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.network.scanner.pro.R
import com.network.scanner.pro.domain.models.Installation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import kotlin.coroutines.resume

/** Esri "World Imagery" satellite tiles (URL order is z/y/x, unlike OSM's z/x/y). */
private object EsriSatelliteTileSource : OnlineTileSourceBase(
    "EsriWorldImagery",
    0,
    19,
    256,
    "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/" +
            MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + "/" +
            MapTileIndex.getX(pMapTileIndex)
}

/** Where the add/edit dialog should open. */
private data class DialogTarget(
    val latitude: Double,
    val longitude: Double,
    val editing: Installation?
)

private val SANAA = GeoPoint(15.3694, 44.1910)

@Composable
fun MapScreen(
    onNavigateToAuth: () -> Unit,
    viewModel: MapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val installations by viewModel.installations.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()

    var satellite by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<Installation?>(null) }
    var dialogTarget by remember { mutableStateOf<DialogTarget?>(null) }
    var focusPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var locating by remember { mutableStateOf(false) }

    // Latest callbacks for the map overlays (they outlive a single recomposition).
    val currentLoggedIn by rememberUpdatedState(isLoggedIn)

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(12.0)
            controller.setCenter(SANAA) // Sana'a, Yemen
        }
    }

    // Forward lifecycle events to the MapView (tile threads, memory).
    DisposableEffect(lifecycleOwner, mapView) {
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
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(focusPoint) {
        focusPoint?.let {
            mapView.controller.animateTo(it)
            mapView.controller.setZoom(17.0)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                val wanted = if (satellite) EsriSatelliteTileSource else TileSourceFactory.MAPNIK
                if (map.tileProvider.tileSource !== wanted) map.setTileSource(wanted)

                map.overlays.clear()
                // Long press on the map = add an installation at that point.
                map.overlays.add(
                    MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            if (p == null) return false
                            if (currentLoggedIn) {
                                dialogTarget = DialogTarget(p.latitude, p.longitude, null)
                            } else {
                                onNavigateToAuth()
                            }
                            return true
                        }
                    })
                )
                installations.forEach { installation ->
                    val marker = Marker(map)
                    marker.position = GeoPoint(installation.latitude, installation.longitude)
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.icon = ContextCompat.getDrawable(context, R.drawable.ic_map_pin)
                    marker.title = installation.networkNumber
                    marker.relatedObject = installation
                    marker.setOnMarkerClickListener { _, _ ->
                        selected = installation
                        true
                    }
                    map.overlays.add(marker)
                }
                map.invalidate()
            }
        )

        // ---- top: sign-in banner + search ---------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isLoggedIn) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(R.string.map_login_required),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = onNavigateToAuth) {
                            Text(stringResource(R.string.auth_login))
                        }
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.map_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    val found = viewModel.search(query)
                    if (found != null) {
                        focusPoint = GeoPoint(found.latitude, found.longitude)
                        selected = found
                    } else {
                        Toast.makeText(context, R.string.map_search_not_found, Toast.LENGTH_SHORT).show()
                    }
                }),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ---- bottom end: map controls --------------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallFloatingActionButton(onClick = { viewModel.syncNow() }) {
                if (syncing) {
                    CircularProgressIndicator(modifier = Modifier.padding(10.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Sync, contentDescription = stringResource(R.string.map_sync))
                }
            }
            SmallFloatingActionButton(onClick = { satellite = !satellite }) {
                Icon(Icons.Default.Layers, contentDescription = stringResource(R.string.map_satellite))
            }
            FloatingActionButton(
                onClick = {
                    if (!isLoggedIn) {
                        onNavigateToAuth()
                    } else if (!locating) {
                        scope.launch {
                            locating = true
                            val location = awaitCurrentLocation(context)
                            locating = false
                            if (location != null) {
                                dialogTarget = DialogTarget(location.latitude, location.longitude, null)
                                focusPoint = GeoPoint(location.latitude, location.longitude)
                            } else {
                                Toast.makeText(context, R.string.map_location_unavailable, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            ) {
                if (locating) {
                    CircularProgressIndicator(modifier = Modifier.padding(14.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.map_add_here))
                }
            }
        }
    }

    // ---- dialogs / sheets ---------------------------------------------------
    dialogTarget?.let { target ->
        AddInstallationDialog(
            latitude = target.latitude,
            longitude = target.longitude,
            editing = target.editing,
            onDismiss = { dialogTarget = null },
            onConfirm = { input ->
                viewModel.addInstallation(input)
                dialogTarget = null
            }
        )
    }

    selected?.let { installation ->
        // Always show the freshest copy (it may have been edited or re-synced).
        val current = installations.firstOrNull { it.id == installation.id } ?: installation
        InstallationDetailsSheet(
            installation = current,
            onDismiss = { selected = null },
            onRevealPassword = { viewModel.revealPassword(it) },
            onEdit = {
                dialogTarget = DialogTarget(current.latitude, current.longitude, current)
                selected = null
            },
            onDelete = {
                viewModel.deleteInstallation(current.id)
                selected = null
            }
        )
    }
}

/**
 * One-shot location fix: uses GPS when enabled, otherwise the network provider,
 * and falls back to the last known position.
 */
@SuppressLint("MissingPermission")
private suspend fun awaitCurrentLocation(context: Context): Location? {
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (!granted) return null

    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val provider = when {
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> return null
    }

    val fresh: Location? = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        LocationManagerCompat.getCurrentLocation(
            manager,
            provider,
            signal,
            ContextCompat.getMainExecutor(context)
        ) { location ->
            if (continuation.isActive) continuation.resume(location)
        }
    }
    return fresh ?: manager.getLastKnownLocation(provider)
}
