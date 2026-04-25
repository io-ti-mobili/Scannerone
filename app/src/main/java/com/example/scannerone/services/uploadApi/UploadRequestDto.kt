package com.example.scannerone.services.uploadApi

data class UploadRequestDto(
    val username: String,
    val uuid: String,
    val networks: List<WifiNetworkUploadDto>
)
