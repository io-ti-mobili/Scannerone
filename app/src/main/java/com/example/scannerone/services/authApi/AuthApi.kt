package com.example.scannerone.services.authApi

import retrofit2.Response
import retrofit2.http.POST

interface AuthApi {
    @POST("api/auth/register")
    suspend fun register(): Response<RegistrationResponseDto>
}
