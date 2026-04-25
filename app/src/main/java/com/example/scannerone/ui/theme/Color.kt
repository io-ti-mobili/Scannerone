package com.example.scannerone.ui.theme

import androidx.compose.ui.graphics.Color

// ── Struttura superfici (VS Code dark, invariata) ────────────────────────────
val VsBackground        = Color(0xFF1E1E1E)  // editor background
val VsSurface           = Color(0xFF252526)  // sidebar / panel
val VsSurfaceVariant    = Color(0xFF2D2D30)  // card sezioni
val VsSurfaceHighlight  = Color(0xFF37373D)  // secondaryContainer
val VsBorder            = Color(0xFF3E3E42)  // bordi
val VsError             = Color(0xFFF44747)  // errori

// ── Scala monocromatica — Dark theme (bianco → grigio) ───────────────────────
val MonoDarkPrimary         = Color(0xFFD8D8D8)  // grigio chiaro — icone, titoli
val MonoDarkSecondary       = Color(0xFF9A9A9A)  // grigio medio — secondary/label
val MonoDarkTertiary        = Color(0xFF6A6A6A)  // grigio scuro — tertiary/hint
val MonoDarkOnBackground    = Color(0xFFC0C0C0)  // testo principale
val MonoDarkOnSurface       = Color(0xFFADADAD)  // testo su superfici ← calibrato
val MonoDarkOnSurfaceVariant= Color(0xFF858585)  // testo secondario su variante

// ── Scala monocromatica — Light theme (nero → grigio chiaro) ─────────────────
val MonoLightPrimary        = Color(0xFF3B3B3B)  // grigio molto scuro ma non nero totale
val MonoLightSecondary      = Color(0xFF5A5A5A)  // grigio scuro
val MonoLightTertiary       = Color(0xFF8B8B8B)  // grigio medio
val MonoLightBackground     = Color(0xFFF9F9F9)  // sfondo off-white morbido
val MonoLightSurface        = Color(0xFFFFFFFF)
val MonoLightSurfaceVariant = Color(0xFFF0F0F0)
val MonoLightBorder         = Color(0xFFD1D1D1)
val MonoLightError          = Color(0xFFCC5A5A)  // rosso desaturato e più elegante