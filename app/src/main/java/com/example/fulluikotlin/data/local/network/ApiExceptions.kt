package com.example.fulluikotlin.data.local.network

import java.io.IOException

class ApiHttpException(val statusCode: Int) : IOException("HTTP request failed")

class ApiResponseException : IOException("Server response was not accepted")
