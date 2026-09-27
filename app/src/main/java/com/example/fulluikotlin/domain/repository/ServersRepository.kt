package com.example.fulluikotlin.domain.repository

interface ServersRepository {
    suspend fun reportUsedProtocol(protocol: String?, server: String?)
}