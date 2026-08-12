package com.example.viko

import android.app.Application
import com.example.viko.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Owns the one [AppContainer] for the process. ViewModels reach it through
 * [com.example.viko.di.AppViewModelProvider], never by touching Room directly.
 */
class VikoApplication : Application() {

    lateinit var container: AppContainer
        private set

    /** Outlives any screen — used for work that must not die with a ViewModel. */
    private val applicationScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // First launch starts with an empty database; the demo dataset is what
        // the competition build shows off, so seed it before the UI collects.
        applicationScope.launch { container.databaseSeeder.seedIfEmpty() }
    }
}
