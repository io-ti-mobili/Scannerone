package com.example.scannerone.map

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.location.LocationManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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


@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    mapViewModel: MapViewModel = viewModel() //serve a creare il viewModel la prima volta o a riutilizzare lo stesso già creato
)
{
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    //Configuriamo OSMDroid una volta sola
    LaunchedEffect(Unit) {
        val sharedPreferences = context.getSharedPreferences("osm_prefs", Context.MODE_PRIVATE)
        Configuration.getInstance().load(context, sharedPreferences)
        Configuration.getInstance().userAgentValue = context.packageName
    }

    //Variabili di Stato: Permessi e GPS Hardware
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isGpsEnabled by remember {
        mutableStateOf(
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        }
    )

    //Osservatore di ciclo di vita: se esci per accendere il GPS e torni, l'app se ne accorge
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    //Se mancano i permessi all'apertura, chiedili in automatico la prima volta
    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    //Blocca la mappa se non ci sono i permessi o l'antenna del gps è spenta
    if (!hasLocationPermission) {
        // BLOCCO 1: Mancano permessi
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text("Per usare la mappa devi concedere i permessi di posizione.", textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }) {
                    Text("Concedi Permessi")
                }
            }
        }
    } else if (!isGpsEnabled) {
        // BLOCCO 2: GPS spento fisicamente
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text("L'antenna GPS è disattivata. Accendila per visualizzare la mappa.", textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    // Manda l'utente direttamente alle impostazioni del telefono!
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }) {
                    Text("Apri Impostazioni GPS")
                }
            }
        }
    } else {
        //SCHERMATA 3: Tutto in regola, mostra la mappa e auto-centra
        MapContent(modifier,mapViewModel)
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