package com.example.viko

import android.app.Application
import android.content.pm.ApplicationInfo
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

        // Listening starts with the process, not with a screen: a badge registers
        // as soon as it joins the Wi-Fi, whether or not anyone has the app open
        // on the Team tab.
        // Debug builds capture every badge utterance to files/captures/*.wav.
        // Badge audio that transcribes wrongly cannot be diagnosed by ear over a
        // factory floor; this is the only way to get the exact bytes ASR saw onto
        // a workstation. Never in release — it is unbounded and it is speech.
        // ApplicationInfo rather than BuildConfig, which AGP does not generate
        // unless the buildConfig feature is switched on — not worth enabling for
        // one boolean.
        container.badgeAudio.dumpUtterances =
            applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

        container.badgeRegistry.start(applicationScope)

        if (SEED_DEMO_DATA) {
            applicationScope.launch { container.databaseSeeder.seedIfEmpty() }
        }
    }

    private companion object {
        /**
         * Whether a first launch pre-fills the factory.
         *
         * `false` — a genuine first run: no members, no channels. Home opens the
         * pairing sheet, "Add new channel" means something, and Select Channel's
         * *No channel yet* state is reachable. This is what the design describes,
         * and it is the default because onboarding is a screen we have to be able
         * to demo.
         *
         * `true` — the populated demo: 4 channels, 20 members, one active alert
         * ([com.example.viko.data.local.SeedData]). Flip it to show Home full of
         * cards without pairing anything first.
         *
         * Either way it goes away once badges enrol themselves over the wire.
         */
        const val SEED_DEMO_DATA = false
    }
}
