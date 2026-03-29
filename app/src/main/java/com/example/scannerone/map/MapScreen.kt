package com.example.scannerone.map

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.scannerone.permissions.PermissionGroup
import com.example.scannerone.permissions.rememberPermissionState
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val permissionState = rememberPermissionState(PermissionGroup.LOCATION)

    // Configuriamo OSMDroid una volta sola
    LaunchedEffect(Unit) {
        val sharedPreferences = context.getSharedPreferences("osm_prefs", Context.MODE_PRIVATE)
        Configuration.getInstance().load(context, sharedPreferences)
        Configuration.getInstance().userAgentValue = context.packageName
    }

    if (!permissionState.allGranted) {
        // Permessi mancanti
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text("Per usare la mappa devi concedere i permessi di posizione.", textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionState.requestPermissions() }) {
                    Text("Concedi Permessi")
                }
            }
        }
    } else {
        // Permessi OK — controlliamo il GPS hardware
        GpsHardwareGate(modifier)
    }
}

/**
 * Controlla se il GPS hardware è attivo.
 * Se spento, mostra un messaggio con bottone per le impostazioni.
 * Se acceso, mostra la mappa.
 */
@Composable
private fun GpsHardwareGate(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    var isGpsEnabled by remember {
        mutableStateOf(
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        )
    }

    // Re-check al resume: se l'utente accende il GPS dalle impostazioni e torna
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!isGpsEnabled) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text("L'antenna GPS è disattivata. Accendila per visualizzare la mappa.", textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }) {
                    Text("Apri Impostazioni GPS")
                }
            }
        }
    } else {
        MapContent(modifier)
    }
}


@Composable
fun MapContent(modifier: Modifier = Modifier) {
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var locationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    onResume() // Sveglia la mappa

                    val provider = GpsMyLocationProvider(ctx)
                    val overlay = MyLocationNewOverlay(provider, this)

                    // Comanda a OSMDroid di accendere il tracciamento
                    overlay.enableMyLocation()
                    // Comanda alla telecamera di SEGUIRE l'utente quando si muove
                    overlay.enableFollowLocation()

                    overlay.runOnFirstFix {
                        post {
                            controller.setZoom(18.0)
                            controller.animateTo(overlay.myLocation)
                        }
                    }

                    overlays.add(overlay)
                    locationOverlay = overlay
                    mapView = this
                }
            },
            onRelease = { view ->
                //Buona norma: quando l'utente cambia schermata, spengiamo il GPS per salvare batteria
                locationOverlay?.disableMyLocation()
                locationOverlay?.disableFollowLocation()
                view.onPause()
            }
        )

        //Il bottone serve solo se l'utente scorre la mappa col dito per guardare altrove.
        //Premendolo, riattiva la funzione "Seguimi" (FollowLocation)
        FloatingActionButton(
            onClick = {
                val overlay = locationOverlay
                val currentPos = overlay?.myLocation
                if (overlay != null && currentPos != null) {
                    overlay.enableFollowLocation()
                    mapView?.controller?.animateTo(currentPos)
                    mapView?.controller?.setZoom(18.0)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Centra su di me"
            )
        }
    }
}