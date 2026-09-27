package com.example.fulluikotlin.domain.usecase.auth

import com.example.fulluikotlin.domain.repository.AuthRepository
import com.example.fulluikotlin.domain.utils.Resource

class DeleteDeviceUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(username: String, device: String): Resource<Unit> {
        return authRepository.deleteDevice(username, device)
    }
}