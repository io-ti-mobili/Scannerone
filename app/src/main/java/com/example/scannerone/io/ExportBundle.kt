package com.example.scannerone.io

import com.example.scannerone.entities.ScanSession
import com.example.scannerone.entities.WifiNetwork
import com.example.scannerone.entities.WifiScanRecord

/**
 * Contenitore delle entità da esportare/importare.
 * Ogni campo è nullable: null = l'utente non ha selezionato quella entità.
 * Le Sequence sono lazy — il DB viene letto a chunk solo quando iterate.
 */
data class ExportBundle(
    val networks: Sequence<WifiNetwork>? = null,
    val sessions: Sequence<ScanSession>? = null,
    val records: Sequence<WifiScanRecord>? = null
)
