package com.example.scannerone.services.uploadApi

data class UploadRequestDto(
    val username: String,
    val uuid: String,
    val password: String? = null,
    val networks: List<WifiNetworkUploadDto>
)
