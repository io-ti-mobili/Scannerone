package com.example.scannerone.services.uploadApi

import com.example.scannerone.config.AppConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object UploadClient {
    val api: UploadApi by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.BACKEND_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UploadApi::class.java)
    }
}
