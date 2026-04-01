package com.example.scannerone.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.scannerone.services.ScanService.WifiForegroundService

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    var isBgOn by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Questa è una home",
            fontSize = 24.sp
        )

        Button(
            onClick = {
                if (isBgOn) {
                    val intent = Intent(context, WifiForegroundService::class.java)
                    context.stopService(intent)
                    isBgOn = false
                    Toast.makeText(context, "Background test OFF", Toast.LENGTH_SHORT).show()
                } else {
                    val intent = Intent(context, WifiForegroundService::class.java)
                    ContextCompat.startForegroundService(context, intent)
                    isBgOn = true
                    Toast.makeText(context, "Background test ON", Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isBgOn) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = if (isBgOn) "Background test ON" else "Background test OFF"
            )
        }
    }
}
