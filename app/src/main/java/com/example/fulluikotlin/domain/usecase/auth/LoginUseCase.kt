package com.example.fulluikotlin.domain.usecase.auth

import com.example.fulluikotlin.domain.model.User
import com.example.fulluikotlin.domain.repository.AuthRepository
import com.example.fulluikotlin.domain.utils.Resource

class LoginUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        username: String,
        password: String,
        os: String,
        device: String
    ): Resource<User> {
        return authRepository.login(username, password, os, device)
    }
}