package com.example.fulluikotlin.data.local.network.interceptor

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter

class AuthInterceptor {
    private val apiKey = "0c8ac86b-7712-4cd1-94bc-a7c6b12df4c7"

    suspend fun addApiKey(builder: HttpRequestBuilder) {
        builder.parameter("APIKEY", apiKey)
    }
}