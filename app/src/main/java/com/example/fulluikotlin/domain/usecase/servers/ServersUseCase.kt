package com.example.fulluikotlin.domain.usecase.servers

import com.example.fulluikotlin.domain.repository.ServersRepository

class ServersUseCase(
    private val serversRepository: ServersRepository
) {
    suspend operator fun invoke(protocol: String?, server: String?) {
        serversRepository.reportUsedProtocol(protocol, server)
    }
}