package com.example.fulluikotlin.domain.repository

interface IpInfoRepository {
    suspend fun getPublicIp(): Result<String>
}