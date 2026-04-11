package com.example.scannerone.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.location.LocationManager
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.lazy.items
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
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.views.overlay.infowindow.InfoWindow


@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    targetLat: Double? = null,
    targetLon: Double? = null,
    targetSsid: String? = null,
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
        GpsHardwareGate(modifier, mapViewModel, targetLat, targetLon, targetSsid)
    }
}

/**
 * Controlla se il GPS hardware è attivo.
 * Se spento, mostra un messaggio con bottone per le impostazioni.
 * Se acceso, mostra la mappa.
 */
@Composable
private fun GpsHardwareGate(
    modifier: Modifier = Modifier,
    mapViewModel: MapViewModel = viewModel(),
    targetLat: Double? = null,
    targetLon: Double? = null,
    targetSsid: String? = null
) {
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
        MapContent(modifier, mapViewModel, targetLat, targetLon, targetSsid)
    }
}


@Composable
fun MapContent(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel,
    targetLat: Double? = null,
    targetLon: Double? = null,
    targetSsid: String? = null
) {
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var locationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    val visibleNetworks by viewModel.visibleNetworks.collectAsState()
    var clusterer by remember { mutableStateOf<RadiusMarkerClusterer?>(null) }

    var shouldShowTarget by remember(targetSsid) { mutableStateOf(targetSsid != null) }

    // Variabili per la ricerca con autocompletamento
    val suggestions by viewModel.searchSuggestions.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    // Quando il ViewModel emette un GeoPoint, la mappa si anima in quella posizione
    LaunchedEffect(Unit) {
        viewModel.moveToLocation.collect { geoPoint ->
            // Disabilitiamo il "Seguimi" del GPS per permettere di esplorare il luogo cercato
            locationOverlay?.disableFollowLocation()
            mapView?.controller?.animateTo(geoPoint)
            mapView?.controller?.setZoom(16.0) // Zoom adeguato per una città/strada


        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val lastLocation = try {
                    locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                } catch (e: SecurityException) {
                    null
                }

                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    onResume() // Sveglia la mappa

                    val provider = GpsMyLocationProvider(ctx)
                    val overlay = MyLocationNewOverlay(provider, this)

                    overlay.enableMyLocation()

                    if (targetLat != null && targetLon != null) {
                        val targetPoint = GeoPoint(targetLat, targetLon)
                        controller.setZoom(18.0)
                        controller.setCenter(targetPoint)

                        post {
                            val limitiMappa = this.boundingBox
                            viewModel.recuperaRetiInZona(
                                limitiMappa.actualNorth,
                                limitiMappa.actualSouth,
                                limitiMappa.lonEast,
                                limitiMappa.lonWest
                            )
                        }
                    } else {
                        lastLocation?.let {
                            val lastPoint = GeoPoint(it.latitude, it.longitude)
                            controller.setZoom(18.0)
                            controller.setCenter(lastPoint)
                        }

                        overlay.enableFollowLocation()
                        overlay.runOnFirstFix {
                            post {
                                controller.setZoom(18.0)
                                controller.setCenter(overlay.myLocation)
                                val limitiMappa = this.boundingBox
                                viewModel.recuperaRetiInZona(
                                    limitiMappa.actualNorth,
                                    limitiMappa.actualSouth,
                                    limitiMappa.lonEast,
                                    limitiMappa.lonWest
                                )
                            }
                        }
                    }

                    overlays.add(overlay)
                    locationOverlay = overlay

                    val newClusterer = RadiusMarkerClusterer(ctx)
                    personalizzaIconaCluster(newClusterer)
                    overlays.add(newClusterer)
                    clusterer = newClusterer

                    // Aggiorna le reti visibili quando l'utente scorre o zooma (con debounce)
                    val mapListener = DelayedMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            val limiti = this@apply.boundingBox
                            viewModel.recuperaRetiInZona(limiti.actualNorth, limiti.actualSouth, limiti.lonEast, limiti.lonWest)
                            return true
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            val limiti = this@apply.boundingBox
                            viewModel.recuperaRetiInZona(limiti.actualNorth, limiti.actualSouth, limiti.lonEast, limiti.lonWest)
                            return true
                        }
                    }, 500)

                    this.addMapListener(mapListener)
                    mapView = this
                }
            },
            onRelease = { view ->
                locationOverlay?.disableMyLocation()
                locationOverlay?.disableFollowLocation()
                view.onPause()
            },
            update = { view ->
                clusterer?.let { cls ->
                    for (item in cls.items) {
                        item.closeInfoWindow()
                    }
                    cls.items.clear()

                    var targetMarker: Marker? = null
                    val sharedInfoWindow = BoldInfoWindow(view)

                    for (rete in visibleNetworks) {
                        val lat = rete.realLatitude ?: 0.0
                        val lon = rete.realLongitude ?: 0.0

                        if (lat != 0.0 && lon != 0.0) {
                            val startMarker = Marker(view).apply {
                                icon = ContextCompat.getDrawable(view.context, R.drawable.wifi_icon)
                                position = GeoPoint(lat, lon)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = rete.ssid
                                snippet = rete.bssid
                                infoWindow = sharedInfoWindow
                            }
                            if (targetSsid != null && rete.ssid.replace("/", "_") == targetSsid) {
                                targetMarker = startMarker
                                startMarker.setOnMarkerClickListener { marker, _ ->
                                    if (marker.isInfoWindowShown) {
                                        marker.closeInfoWindow()
                                        shouldShowTarget = false
                                    } else {
                                        marker.showInfoWindow()
                                        shouldShowTarget = true
                                    }
                                    true
                                }
                            } else {
                                startMarker.setOnMarkerClickListener { marker, _ ->
                                    if (marker.isInfoWindowShown) marker.closeInfoWindow()
                                    else marker.showInfoWindow()
                                    true
                                }
                            }

                            cls.add(startMarker)
                        }
                    }

                    view.invalidate()

                    if (targetMarker != null && shouldShowTarget) {
                        val markerToOpen = targetMarker
                        view.post { markerToOpen.showInfoWindow() }
                    }
                }
            }
        )

        // --- 2. BARRA DI RICERCA CON SUGGERIMENTI (In primo piano, in alto) ---
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { nuovoTesto ->
                        searchQuery = nuovoTesto
                        // Chiama il ViewModel che gestisce il Debounce e Nominatim
                        viewModel.onSearchQueryChanged(nuovoTesto)
                    },
                    placeholder = { Text("Cerca città, via...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.White,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.White,
                        focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedTextColor = androidx.compose.ui.graphics.Color.Black,
                        cursorColor = androidx.compose.ui.graphics.Color.Black,
                        unfocusedTextColor = androidx.compose.ui.graphics.Color.Black
                    ),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Cerca")
                    },
                    trailingIcon = {
                        // Mostra la "X" solo se c'è del testo
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                viewModel.onSearchQueryChanged("") // Svuota la ricerca
                                focusManager.clearFocus() // Nasconde la tastiera
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Cancella")
                            }
                        }
                    }
                )
            }

            // TENDINA DEI SUGGERIMENTI
            if (suggestions.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(suggestions) { address ->
                            // Estraiamo il nome formattato da Nominatim
                            val displayName = address.extras?.getString("display_name") ?: address.getAddressLine(0)

                            Text(
                                text = displayName ?: "Indirizzo sconosciuto",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchQuery = displayName ?: ""
                                        viewModel.selectLocation(address) // Lancia l'evento di movimento
                                        focusManager.clearFocus() // Nasconde la tastiera
                                    }
                                    .padding(16.dp)
                            )
                            androidx.compose.material3.HorizontalDivider(
                                color = androidx.compose.ui.graphics.Color.LightGray,
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }

        //puldante per riportare nella posizione attuale
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
        color = Color.WHITE
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
        color = Color.WHITE
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
        setBackgroundColor(Color.WHITE)

        addView(TextView(context).apply {
            tag = "ssid"
            setTypeface(null, Typeface.BOLD)
            textSize = 14f
        })
        addView(TextView(context).apply {
            tag = "bssid"
            textSize = 12f
            setTextColor(Color.GRAY)
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