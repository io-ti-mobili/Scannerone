package com.example.scannerone.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Tema scuro — sfumature di bianco su sfondo VS Code
private val DarkColorScheme = darkColorScheme(
    primary             = MonoDarkPrimary,          // #ECECEC quasi bianco
    secondary           = MonoDarkSecondary,         // #B0B0B0 grigio medio
    tertiary            = MonoDarkTertiary,          // #787878 grigio hint
    primaryContainer    = VsSurface,
    onPrimaryContainer  = MonoDarkOnBackground,
    background          = VsBackground,             // #1E1E1E
    surface             = VsSurface,                // #252526
    surfaceVariant      = VsSurfaceVariant,         // #2D2D30
    onPrimary           = VsBackground,             // testo su bottoni primarycolor
    onSecondary         = VsBackground,
    onTertiary          = MonoDarkOnBackground,
    onBackground        = MonoDarkOnBackground,     // #D4D4D4
    onSurface           = MonoDarkOnSurface,        // #CCCCCC
    onSurfaceVariant    = MonoDarkOnSurfaceVariant, // #9E9E9E
    outline             = VsBorder,                 // #3E3E42
    outlineVariant      = VsBorder.copy(alpha = 0.4f),
    error               = VsError,                  // #F44747
    onError             = Color.White,
    secondaryContainer  = VsSurfaceHighlight,       // #37373D
    onSecondaryContainer = MonoDarkOnBackground,
    tertiaryContainer   = VsSurfaceVariant,
    onTertiaryContainer = MonoDarkSecondary
)

// Tema chiaro — sfumature di nero su sfondo off-white
private val LightColorScheme = lightColorScheme(
    primary             = MonoLightPrimary,         // #1A1A1A quasi nero
    secondary           = MonoLightSecondary,        // #424242
    tertiary            = MonoLightTertiary,         // #757575
    primaryContainer    = MonoLightSurface,
    onPrimaryContainer  = MonoLightPrimary,
    background          = MonoLightBackground,      // #F5F5F5 off-white
    surface             = MonoLightSurface,         // #FFFFFF
    surfaceVariant      = MonoLightSurfaceVariant,  // #EEEEEE
    onPrimary           = Color.White,
    onSecondary         = Color.White,
    onTertiary          = Color.White,
    onBackground        = MonoLightPrimary,
    onSurface           = MonoLightSecondary,
    onSurfaceVariant    = MonoLightTertiary,
    outline             = MonoLightBorder,
    outlineVariant      = MonoLightBorder.copy(alpha = 0.5f),
    error               = MonoLightError,
    onError             = Color.White,
    secondaryContainer  = MonoLightSurfaceVariant,
    onSecondaryContainer = MonoLightPrimary,
    tertiaryContainer   = Color(0xFFE5E5E5),
    onTertiaryContainer = MonoLightSecondary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}