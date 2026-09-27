package com.example.fulluikotlin.domain.usecase.app

import com.example.fulluikotlin.domain.model.AppDetails
import com.example.fulluikotlin.domain.repository.AppRepository
import com.example.fulluikotlin.domain.utils.Resource

class GetAppDetailsUseCase(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(): Resource<AppDetails> {
        return appRepository.getAppDetails()
    }
}