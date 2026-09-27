package com.example.fulluikotlin.domain.repository

import com.example.fulluikotlin.domain.model.AppDetails
import com.example.fulluikotlin.domain.utils.Resource

interface AppRepository {
    suspend fun getAppDetails(): Resource<AppDetails>
}