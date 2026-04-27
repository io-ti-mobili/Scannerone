package com.example.scannerone.services.authApi

import com.example.scannerone.config.AppConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object AuthClient {
    fun createApi(baseUrl: String): AuthApi {
        val cleanUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(cleanUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }

    // Default instance for backward compatibility or simple uses
    val api: AuthApi by lazy {
        createApi(AppConfig.BACKEND_URL)
    }
}
