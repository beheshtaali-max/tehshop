package com.example.fulluikotlin.data.local.network.api

import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import com.example.fulluikotlin.data.local.network.interceptor.AuthInterceptor
import com.example.fulluikotlin.data.local.network.model.response.DeleteDeviceResponse
import com.example.fulluikotlin.data.local.network.model.response.LoginResponse
import java.net.URLEncoder

class AuthApi(
    client: HttpClient,
    authInterceptor: AuthInterceptor
) : BaseApiService(client, authInterceptor) {

    suspend fun login(
        username: String,
        password: String,
        os: String,
        device: String
    ): LoginResponse {
        return apiRequest {
            method = HttpMethod.Post
            url("index.php")
            parameter("ACTION", "AUTH")
            contentType(ContentType.Application.FormUrlEncoded)

            val body = buildString {
                append("user=").append(URLEncoder.encode(username, "UTF-8"))
                append("&pass=").append(URLEncoder.encode(password, "UTF-8"))
                append("&os=").append(URLEncoder.encode(os, "UTF-8"))
                append("&device=").append(URLEncoder.encode(device, "UTF-8"))
            }
            setBody(body)
        }
    }

    suspend fun usage(
        username: String,
        password: String,
        usage: String,
        device: String,
        os: String,
    ): LoginResponse {
        return apiRequest {
            method = HttpMethod.Post
            url("index.php")
            parameter("ACTION", "USAGE")

            contentType(ContentType.Application.FormUrlEncoded)

            val body = buildString {
                append("user=").append(URLEncoder.encode(username, "UTF-8"))
                append("&pass=").append(URLEncoder.encode(password, "UTF-8"))
                append("&usage=").append(URLEncoder.encode(usage, "UTF-8"))
                append("&os=").append(URLEncoder.encode(os, "UTF-8"))
                append("&device=").append(URLEncoder.encode(device, "UTF-8"))
            }
            setBody(body)
        }
    }

    suspend fun deleteDevice(username: String, device: String): DeleteDeviceResponse {
        return apiRequest {
            method = HttpMethod.Post
            url("index.php")
            parameter("ACTION", "DELDEVICE")
            contentType(ContentType.Application.FormUrlEncoded)
            val body = buildString {
                append("user=").append(URLEncoder.encode(username, "UTF-8"))
                append("&device=").append(URLEncoder.encode(device, "UTF-8"))
            }
            setBody(body)
        }

    }

}