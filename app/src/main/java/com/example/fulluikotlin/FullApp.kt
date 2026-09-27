package com.example.fulluikotlin

import android.app.Application
import com.blongho.country_data.World
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin
import com.example.fulluikotlin.di.appModule
import com.example.fulluikotlin.di.networkModule
import com.example.fulluikotlin.di.repositoryModule

class FullApp : Application() {
    override fun onCreate() {
        super.onCreate()
        World.init(this)
        startKoin {
            androidContext(this@FullApp)
            modules(
                appModule,
                networkModule,
                repositoryModule
            )
        }
    }
}