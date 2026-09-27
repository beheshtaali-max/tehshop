package com.example.fulluikotlin.domain.usecase.auth

import com.example.fulluikotlin.domain.model.User
import com.example.fulluikotlin.domain.repository.AuthRepository
import com.example.fulluikotlin.domain.utils.Resource

class UsageUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        username: String,
        password: String,
        usage: String,
        device: String,
        os: String
    ): Resource<User> {
        return authRepository.usage(username, password, usage, device, os)
    }
}