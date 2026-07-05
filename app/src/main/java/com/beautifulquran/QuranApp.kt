package com.beautifulquran

import android.app.Application
import com.beautifulquran.data.QuranDatabase
import com.beautifulquran.data.QuranRepository
import com.beautifulquran.data.SettingsRepository
import com.beautifulquran.playback.PlayerController
import com.beautifulquran.ui.reader.QcfFontProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class QuranApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var repository: QuranRepository
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var player: PlayerController
        private set
    lateinit var qcfFontProvider: QcfFontProvider
        private set

    override fun onCreate() {
        super.onCreate()
        repository = QuranRepository(QuranDatabase(this))
        settings = SettingsRepository(this)
        player = PlayerController(this)
        qcfFontProvider = QcfFontProvider(this)
        qcfFontProvider.prepareOnStartup(appScope)
    }
}
