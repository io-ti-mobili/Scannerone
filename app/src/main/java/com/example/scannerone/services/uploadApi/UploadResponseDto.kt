package com.example.scannerone.services.uploadApi

data class UploadResponseDto(
    val processed: Int,
    val errors: Int,
    val message: String?
)
