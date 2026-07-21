package com.beautifulquran

import android.app.Application
import com.beautifulquran.assistant.AssistantAction
import com.beautifulquran.assistant.VoiceShortcuts
import com.beautifulquran.data.BookmarkRepository
import com.beautifulquran.data.AnnotationRepository
import com.beautifulquran.data.QuranDatabase
import com.beautifulquran.data.QuranRepository
import com.beautifulquran.data.SettingsRepository
import com.beautifulquran.ornamentslab.OrnamentSeedStore
import com.beautifulquran.playback.PlayerController
import com.beautifulquran.timingslab.TimingOverrides
import kotlinx.coroutines.flow.MutableSharedFlow

class QuranApp : Application() {

    /** One-shot OS actions that should also move an already-open reader. */
    val assistantActions = MutableSharedFlow<AssistantAction>(extraBufferCapacity = 1)

    lateinit var repository: QuranRepository
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var bookmarks: BookmarkRepository
        private set
    lateinit var annotations: AnnotationRepository
        private set
    lateinit var player: PlayerController
        private set
    lateinit var timingOverrides: TimingOverrides
        private set
    lateinit var ornamentSeeds: OrnamentSeedStore
        private set

    override fun onCreate() {
        super.onCreate()
        DevProfiling.install(this)
        val overrides = TimingOverrides(this)
        repository = QuranRepository(QuranDatabase(this), overrides)
        settings = SettingsRepository(this)
        bookmarks = BookmarkRepository(this)
        annotations = AnnotationRepository(this)
        player = PlayerController(this)
        timingOverrides = overrides
        ornamentSeeds = OrnamentSeedStore(this)
        // Long-press app icon → Continue / Bookmarks (works without App Actions review).
        VoiceShortcuts.publishDynamic(this)
    }
}
