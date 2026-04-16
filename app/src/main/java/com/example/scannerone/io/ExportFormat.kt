package com.example.scannerone.io

enum class ExportFormat { JSON, CSV }

data class ExportSelection(
    val includiNetworks: Boolean = true,
    val includiSessions: Boolean = false,
    val includiRecords: Boolean = true
)
