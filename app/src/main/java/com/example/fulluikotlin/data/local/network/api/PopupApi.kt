package com.example.fulluikotlin.data.local.network.api

import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import com.example.fulluikotlin.data.local.network.interceptor.AuthInterceptor
import com.example.fulluikotlin.data.local.network.model.response.PopupAdsResponse

class PopupApi(
    client: HttpClient,
    authInterceptor: AuthInterceptor
) : BaseApiService(client, authInterceptor) {

    suspend fun getPopupAds(): PopupAdsResponse {
        return apiRequest {
            method = HttpMethod.Get
            url("index.php")
            parameter("ACTION", "GETPOPUPADSDETAILS")
        }
    }
}