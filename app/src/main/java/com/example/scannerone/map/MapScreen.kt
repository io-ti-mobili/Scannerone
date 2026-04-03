package com.example.scannerone.map

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.location.LocationManager
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
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
import androidx.compose.runtime.collectAsState
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.scannerone.permissions.PermissionGroup
import com.example.scannerone.permissions.rememberPermissionState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scannerone.viewmodel.MapViewModel
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer
import org.osmdroid.bonuspack.utils.BonusPackHelper
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import com.example.scannerone.R
import org.osmdroid.views.overlay.infowindow.InfoWindow


@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    mapViewModel: MapViewModel = viewModel() //serve a creare il viewModel la prima volta o a riutilizzare lo stesso già creato
)
{

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
private fun GpsHardwareGate(modifier: Modifier = Modifier, mapViewModel: MapViewModel = viewModel()) {
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
        //SCHERMATA 3: Tutto in regola, mostra la mappa e auto-centra
        MapContent(modifier, mapViewModel)
    }
}


@Composable
fun MapContent(modifier: Modifier = Modifier,viewModel: MapViewModel) {
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var locationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    val visibleNetworks by viewModel.visibleNetworks.collectAsState()
    var clusterer by remember { mutableStateOf<RadiusMarkerClusterer?>(null) }
    

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
                            //ora che ho la posizione esatta, posso richiamare la funzione per prelevare i limiti della mappa
                            val limitiMappa = this.boundingBox
                            viewModel.recuperaRetiInZona(
                                limitiMappa.actualNorth,
                                limitiMappa.actualSouth,
                                limitiMappa.lonEast,
                                limitiMappa.lonWest
                            )
                        }
                    }


                    overlays.add(overlay)

                    locationOverlay = overlay

                    val newClusterer = RadiusMarkerClusterer(ctx)
                    personalizzaIconaCluster(newClusterer)
                    overlays.add(newClusterer)
                    clusterer = newClusterer
                    //creo il sensore per monitorare quando l'utnete si sposta con il dito
                    val mapListener = org.osmdroid.events.DelayedMapListener(object : org.osmdroid.events.MapListener {
                        override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                            val limiti = this@apply.boundingBox
                            viewModel.recuperaRetiInZona(limiti.actualNorth, limiti.actualSouth, limiti.lonEast, limiti.lonWest)
                            return true
                        }

                        override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                            val limiti = this@apply.boundingBox
                            viewModel.recuperaRetiInZona(limiti.actualNorth, limiti.actualSouth, limiti.lonEast, limiti.lonWest)
                            return true
                        }
                    }, 500) //ritardo per non intasare il database mentre si sta ancora trascinando

                    this.addMapListener(mapListener)
                    mapView = this
                }
            },
            onRelease = { view ->
                //Buona norma: quando l'utente cambia schermata, spengiamo il GPS per salvare batteria
                locationOverlay?.disableMyLocation()
                locationOverlay?.disableFollowLocation()
                view.onPause()
            },
            update = { view ->
                clusterer?.let { cls ->

                    //vengono rimossi i vecchi marker per evitare di avere duplicati o robe sparse in giro
                    for (item in cls.items){
                        item.closeInfoWindow()
                    }
                    cls.items.clear()

                    for (rete in visibleNetworks) {
                        val lat = rete.realLatitude ?: 0.0
                        val lon = rete.realLongitude ?: 0.0

                        if (lat != 0.0 && lon != 0.0) {
                            val pointMarker = GeoPoint(lat, lon)
                            val startMarker = Marker(view)
                            startMarker.icon = ContextCompat.getDrawable(view.context, R.drawable.wifi_icon)
                            startMarker.position = pointMarker
                            startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            startMarker.title = rete.ssid //aggiungere ulteriori info se necessario
                            startMarker.snippet = rete.bssid
                            startMarker.infoWindow = BoldInfoWindow(view) // ← aggiungi qui
                            startMarker.setOnMarkerClickListener { marker, _ ->
                                if (marker.isInfoWindowShown) marker.closeInfoWindow()
                                else marker.showInfoWindow()
                                true
                            }
                            cls.add(startMarker)
                        }
                    }

                    cls.invalidate()
                    view.invalidate()
                }
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

private fun personalizzaIconaCluster(newClusterer: RadiusMarkerClusterer){
    // Creiamo una tela virtuale (Bitmap)
    val clusterBitmap = createBitmap(120, 120)
    val canvas = Canvas(clusterBitmap)

    // Disegniamo il riempimento azzurro moderno
    val paintFill = Paint().apply {
        color = "#1976D2".toColorInt()
        isAntiAlias = true
    }
    // Disegniamo un bordino bianco pulito attorno
    val paintBorder = Paint().apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    // Dipingiamo il cerchio e il bordo
    canvas.drawCircle(60f, 60f, 50f, paintFill)
    canvas.drawCircle(60f, 60f, 50f, paintBorder)

    // Assegniamo la nostra opera d'arte al cluster!
    newClusterer.setIcon(clusterBitmap)

    // Miglioriamo anche il testo del numeretto dentro il cluster
    newClusterer.textPaint.apply {
        color = android.graphics.Color.WHITE
        textSize = 40f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
}
class BoldInfoWindow(mapView: MapView) : InfoWindow(
    LinearLayout(mapView.context).apply {
        isClickable = false
        isFocusable = false
        orientation = LinearLayout.VERTICAL
        setPadding(24, 12, 24, 12)
        setBackgroundColor(android.graphics.Color.WHITE)

        addView(TextView(context).apply {
            tag = "ssid"
            setTypeface(null, android.graphics.Typeface.BOLD)
            textSize = 14f
        })
        addView(TextView(context).apply {
            tag = "bssid"
            textSize = 12f
            setTextColor(android.graphics.Color.GRAY)
        })
    }, mapView
) {
    override fun onOpen(item: Any?) {
        val marker = item as? Marker ?: return
        val layout = mView as LinearLayout
        (layout.findViewWithTag("ssid") as TextView).text = marker.title
        (layout.findViewWithTag("bssid") as TextView).text = marker.snippet
    }
    override fun onClose() {}
}