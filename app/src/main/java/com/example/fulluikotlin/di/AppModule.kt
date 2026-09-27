package com.example.fulluikotlin.di

import com.example.fulluikotlin.infrastructure.vpn.VpnAdapterFactory
import com.example.fulluikotlin.infrastructure.network.NetworkConnectivityChecker
import com.example.fulluikotlin.infrastructure.vpn.VpnOrchestrator
import com.example.fulluikotlin.infrastructure.vpn.traffic.VpnUsageTracker
import com.example.fulluikotlin.infrastructure.vpn.adapters.V2rayAdapter
import com.example.fulluikotlin.infrastructure.vpn.adapters.SSHAdapter
import com.example.fulluikotlin.ui.main.MainViewModel
import com.example.fulluikotlin.ui.screens.activeservice.ActiveServiceViewModel
import com.example.fulluikotlin.ui.screens.devicesactive.DevicesViewModel
import com.example.fulluikotlin.ui.screens.home.HomeViewModel
import com.example.fulluikotlin.ui.screens.home.V2rayViewModel
import com.example.fulluikotlin.ui.screens.login.LoginViewModel
import com.example.fulluikotlin.ui.screens.profile.ProfileViewModel
import com.example.fulluikotlin.ui.screens.questions.QuestionsViewModel
import com.example.fulluikotlin.ui.screens.servers.ServersViewModel
import com.example.fulluikotlin.ui.screens.splash.SplashViewModel
import com.example.fulluikotlin.ui.screens.splittunnel.SplitTunnelViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import com.example.fulluikotlin.data.local.datastore.AppDetailsDataStore
import com.example.fulluikotlin.data.local.datastore.ServerDataStore
import com.example.fulluikotlin.data.local.datastore.SettingsDataStore
import com.example.fulluikotlin.data.local.datastore.SplitTunnelDataStore
import com.example.fulluikotlin.data.local.datastore.UserDataStore
import pw.fullvpn.android.ui.screens.aboutus.AboutUsViewModel

val appModule = module {

    // VPN Adapters
    factory { V2rayAdapter(androidContext(), get(), get()) }
    factory { SSHAdapter(androidContext(), get(), get()) }

    // Factory و Orchestrator
    single { VpnAdapterFactory() }

    single { VpnUsageTracker(androidContext()) }

    single { VpnOrchestrator(androidContext(), get()) }

    single { NetworkConnectivityChecker(androidContext()) }

    // DataStore
    single { UserDataStore(androidContext()) }

    single { SettingsDataStore(androidContext()) }

    single { ServerDataStore(androidContext()) }

    single { SplitTunnelDataStore(get()) }

    single { AppDetailsDataStore(androidContext()) }


    // ViewModel
    single { DevicesViewModel(get(), get()) }

    viewModel { SplashViewModel(get(), get(), get(), get(), get(), get(), get()) }

    viewModel { LoginViewModel(get(), get(), get(), get()) }

    viewModel { ProfileViewModel(get(), get(), get(), get(), get()) }

    viewModel { SplitTunnelViewModel(get(), get()) }

    viewModel { V2rayViewModel(get(), get(), get(), get()) }

    viewModel { ServersViewModel(get()) }

    viewModel { HomeViewModel(androidApplication(), get(), get(), get(), get(), get()) } // Application, VpnOrchestrator, ServerDataStore, ServersUseCase, UserDataStore, LoginUseCase

    viewModel { ProfileViewModel(get(), get(), get(), get(), get()) }

    viewModel { ActiveServiceViewModel(get(), get()) }

    single { MainViewModel(androidContext(), get(), get(), get()) }

    viewModel { QuestionsViewModel(get()) }

    viewModel { AboutUsViewModel(get()) }

}
