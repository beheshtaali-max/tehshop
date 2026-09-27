package com.example.fulluikotlin.data.local.network.api

import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import com.example.fulluikotlin.data.local.network.interceptor.AuthInterceptor
import com.example.fulluikotlin.data.local.network.model.response.LoginResponse
import com.example.fulluikotlin.data.local.network.model.response.UsedProtocolResponse
import java.net.URLEncoder

class ServerApi(
    client: HttpClient,
    authInterceptor: AuthInterceptor
) : BaseApiService(client, authInterceptor) {

    suspend fun reportUsedProtocol(protocol: String?, server: String?): UsedProtocolResponse {
        return apiRequest(isSuccessful = { response ->
            response.status.equals("SUCCESSFULL", ignoreCase = true)
        }) {
            method = HttpMethod.Post
            url("index.php")
            parameter("ACTION", "USEDCONFIG")
            contentType(ContentType.Application.FormUrlEncoded)
            val body = buildString {
                append("&protocol=").append(URLEncoder.encode(protocol, "UTF-8"))
                append("&server=").append(URLEncoder.encode(server, "UTF-8"))
            }
            setBody(body)
        }
    }
}