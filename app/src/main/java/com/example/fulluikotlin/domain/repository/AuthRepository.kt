package com.example.fulluikotlin.domain.repository

import com.example.fulluikotlin.domain.model.User
import com.example.fulluikotlin.domain.utils.Resource

interface AuthRepository {
    suspend fun login(
        username: String,
        password: String,
        os: String,
        device: String
    ): Resource<User>

    suspend fun usage(
        username: String,
        password: String,
        usage: String,
        device: String,
        os: String
    ): Resource<User>

    suspend fun deleteDevice(username: String, device: String): Resource<Unit>
}