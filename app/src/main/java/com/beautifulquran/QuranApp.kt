package com.beautifulquran

import android.app.Application
import com.beautifulquran.data.BookmarkRepository
import com.beautifulquran.data.QuranDatabase
import com.beautifulquran.data.QuranRepository
import com.beautifulquran.data.SettingsRepository
import com.beautifulquran.playback.PlayerController
import com.beautifulquran.timingslab.TimingOverrides

class QuranApp : Application() {

    lateinit var repository: QuranRepository
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var bookmarks: BookmarkRepository
        private set
    lateinit var player: PlayerController
        private set
    lateinit var timingOverrides: TimingOverrides
        private set

    override fun onCreate() {
        super.onCreate()
        val overrides = TimingOverrides(this)
        repository = QuranRepository(QuranDatabase(this), overrides)
        settings = SettingsRepository(this)
        bookmarks = BookmarkRepository(this)
        player = PlayerController(this)
        timingOverrides = overrides
    }
}
