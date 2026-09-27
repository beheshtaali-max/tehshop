package com.example.fulluikotlin.di


import com.example.fulluikotlin.domain.repository.AppRepository
import com.example.fulluikotlin.domain.repository.AuthRepository
import com.example.fulluikotlin.domain.repository.IpInfoRepository
import com.example.fulluikotlin.domain.repository.PopupRepository
import com.example.fulluikotlin.domain.repository.ServersRepository
import com.example.fulluikotlin.domain.usecase.app.GetAppDetailsUseCase
import com.example.fulluikotlin.domain.usecase.auth.DeleteDeviceUseCase
import com.example.fulluikotlin.domain.usecase.auth.LoginUseCase
import com.example.fulluikotlin.domain.usecase.popup.GetPopupAdsUseCase
import com.example.fulluikotlin.domain.usecase.servers.ServersUseCase
import org.koin.dsl.module
import com.example.fulluikotlin.data.local.repository.AppRepositoryImpl
import com.example.fulluikotlin.data.local.repository.AuthRepositoryImpl
import com.example.fulluikotlin.data.local.repository.IpInfoRepositoryImpl
import com.example.fulluikotlin.data.local.repository.PopupRepositoryImpl
import com.example.fulluikotlin.data.local.repository.ServersRepositoryImpl
import com.example.fulluikotlin.domain.usecase.auth.UsageUseCase

val repositoryModule = module {
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get()) }

    factory { UsageUseCase(get()) }
    factory { LoginUseCase(get()) }
    factory { DeleteDeviceUseCase(get()) }

    single<IpInfoRepository> { IpInfoRepositoryImpl() }

    single<ServersRepository> { ServersRepositoryImpl(get()) }
    factory { ServersUseCase(get()) }


    single<AppRepository> { AppRepositoryImpl(get()) }
    factory { GetAppDetailsUseCase(get()) }

    single<PopupRepository> { PopupRepositoryImpl(get()) }
    factory { GetPopupAdsUseCase(get()) }
}