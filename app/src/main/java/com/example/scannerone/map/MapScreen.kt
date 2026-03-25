package com.example.scannerone.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("osm_prefs", Context.MODE_PRIVATE)
    Configuration.getInstance().load(context, sharedPreferences)
    Configuration.getInstance().userAgentValue = context.packageName

    var locationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    //Controlliamo se abbiamo il permesso
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Launcher popup
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            // Questo blocco scatta quando l'utente clicca "Consenti" o "Rifiuta" nel popup
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            hasLocationPermission = fineGranted || coarseGranted

            if (hasLocationPermission) {
                // Se ha accettato, accendiamo l'antenna GPS sulla mappa e proviamo a centrare
                locationOverlay?.enableMyLocation()
                val myPos = locationOverlay?.myLocation
                if (myPos != null) {
                    mapView?.controller?.animateTo(myPos)
                    mapView?.controller?.setZoom(18.0)
                } else {
                    Toast.makeText(context, "Ricerca posizione in corso...", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Permesso negato. Impossibile trovarti.", Toast.LENGTH_SHORT).show()
            }
        }
    )

    Box(modifier = modifier.fillMaxSize()) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    val mapController = controller
                    mapController.setZoom(16.0)
                    val rome = GeoPoint(41.9027, 12.4963)
                    mapController.setCenter(rome)

                    val overlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)

                    // Se avevamo GIA' il permesso da prima, accendiamo subito il GPS
                    if (hasLocationPermission) {
                        overlay.enableMyLocation()
                    }

                    overlays.add(overlay)

                    //Inserire in futuro le icone per le treti wifi

                    locationOverlay = overlay
                    mapView = this
                }
            }
        )

        FloatingActionButton(
            onClick = {
                // 3. LOGICA DEL BOTTONE: Hai il permesso?
                if (hasLocationPermission) {
                    // Sì -> Centra la mappa
                    val myPos = locationOverlay?.myLocation
                    if (myPos != null) {
                        mapView?.controller?.animateTo(myPos)
                        mapView?.controller?.setZoom(18.0)
                    } else {
                        Toast.makeText(context, "Attendi, fix GPS in corso...", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // No -> Lancia il popup di sistema per chiedere i permessi!
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
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