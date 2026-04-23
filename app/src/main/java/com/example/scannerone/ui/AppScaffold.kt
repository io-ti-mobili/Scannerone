package com.example.scannerone.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.scannerone.ui.screens.MapScreen
import com.example.scannerone.ui.screens.DatabaseScreen
import com.example.scannerone.ui.screens.HomeScreen
import com.example.scannerone.ui.screens.WifiScreen
import com.example.scannerone.ui.screens.SettingsScreen
import com.example.scannerone.ui.screens.RiepilogoScreen
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.size

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    isDark: Boolean,
    onThemeChange: (Boolean?) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.HOME) }


    var mapTargetLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var mapTargetLon by rememberSaveable { mutableStateOf<Double?>(null) }
    var mapTargetId by rememberSaveable { mutableStateOf<Int?>(null) }


    val larghezzaMenu = 0.7f


    val configuration = LocalConfiguration.current
    val drawerWidth = (configuration.screenWidthDp * larghezzaMenu).dp

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = false,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(drawerWidth)
                ) {
                    Text(
                        text = "Menu",
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        style = MaterialTheme.typography.titleMedium
                    )

                    AppDestination.entries.forEach { destination ->
                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    painterResource(destination.icon),
                                    contentDescription = destination.label,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = { Text(destination.label) },
                            selected = destination == currentDestination,
                            onClick = {
                                if (destination == AppDestination.MAP) {
                                    mapTargetLat = null
                                    mapTargetLon = null
                                    mapTargetId = null
                                }
                                currentDestination = destination
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
            }
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = { Text(currentDestination.label) },
                        navigationIcon = {
                            IconButton(onClick = {
                                scope.launch { drawerState.open() }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Apri menu"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            ) { innerPadding ->
                val modifier = Modifier.padding(innerPadding)
                when (currentDestination) {
                    AppDestination.HOME -> HomeScreen(modifier)
                    AppDestination.DATABASESCREEN -> DatabaseScreen(
                        modifier = modifier,
                        onOpenMap = { lat, lon, id ->
                            mapTargetLat = lat
                            mapTargetLon = lon
                            mapTargetId = id
                            currentDestination = AppDestination.MAP
                        }
                    )
                    AppDestination.WIFISCAN -> WifiScreen(modifier)
                    AppDestination.MAP -> MapScreen(
                        modifier = modifier,
                        targetLat = mapTargetLat,
                        targetLon = mapTargetLon,
                        targetId = mapTargetId
                    )
                    AppDestination.RIEPILOGO -> RiepilogoScreen(modifier)
                    AppDestination.SETTINGS -> SettingsScreen(
                        modifier = modifier,
                        isDark = isDark,
                        onThemeChange = onThemeChange
                    )
                }
            }
        }

        if (drawerState.isOpen) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .padding(start = drawerWidth)
                    .align(Alignment.CenterEnd)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        scope.launch { drawerState.close() }
                    }
            )
        }
    }
}