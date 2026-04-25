package com.example.scannerone.services.uploadApi

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface UploadApi {
    @POST("api/upload/batch")
    suspend fun uploadNetworks(@Body request: UploadRequestDto): Response<UploadResponseDto>
}
