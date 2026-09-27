package com.example.fulluikotlin.di

import android.util.Log
import com.example.fulluikotlin.data.local.network.api.AppApi
import com.example.fulluikotlin.data.local.network.api.AuthApi
import com.example.fulluikotlin.data.local.network.api.PopupApi
import com.example.fulluikotlin.data.local.network.api.ServerApi
import com.example.fulluikotlin.data.local.network.interceptor.AuthInterceptor
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val networkModule = module {

    //
    single { AuthInterceptor() }
    single { createKtorClient() }


    single { AuthApi(get(), get()) }
    single { ServerApi(get(), get()) }
    single { AppApi(get(), get()) }
    single { PopupApi(get(), get()) }
}

fun createKtorClient(): HttpClient {
    return HttpClient(CIO) {
        install(ContentEncoding) {
            gzip()
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            })
        }
        install(Logging) {
            level = LogLevel.BODY
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d("Ktor", message)
                }
            }
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000L
            requestTimeoutMillis = 60_000L
            socketTimeoutMillis = 30_000L
        }
        defaultRequest {
            url("http://panel-msn.ir-app20.com/api/")
            header("Content-Type", "application/x-www-form-urlencoded")
        }
    }
}