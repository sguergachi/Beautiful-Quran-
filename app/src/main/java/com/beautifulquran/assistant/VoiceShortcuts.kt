package com.beautifulquran.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.beautifulquran.MainActivity
import com.beautifulquran.R

/** Builds, publishes, and pins launcher shortcuts via VIEW deep links. */
object VoiceShortcuts {

    /**
     * Adaptive launcher icons (`mipmap-anydpi-v26`) crash ShortcutManager.
     * Use the plain vector foreground instead.
     */
    private val shortcutIcon = R.drawable.ic_launcher_foreground

    fun publishDynamic(context: Context) {
        // Never crash cold start — shortcuts are convenience only.
        runCatching {
            val pinable = VoiceRoutines.all.filter { it.pinable }.mapNotNull { build(context, it) }
            if (pinable.isEmpty()) return
            ShortcutManagerCompat.setDynamicShortcuts(context, pinable)
        }
    }

    fun pin(context: Context, shortcut: VoiceShortcut): Boolean {
        if (!shortcut.pinable) return false
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val info = build(context, shortcut) ?: return false
        return runCatching {
            ShortcutManagerCompat.requestPinShortcut(context, info, null)
        }.getOrDefault(false)
    }

    fun build(context: Context, shortcut: VoiceShortcut): ShortcutInfoCompat? = runCatching {
        // Prefer VIEW + deep link so the manifest scheme filter matches cleanly
        // (custom actions + data confuse some launchers).
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(shortcut.deepLink)).apply {
            setClass(context, MainActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (shortcut.id == "verse_example") {
                putExtra(AssistantIntents.EXTRA_SURAH, 2)
                putExtra(AssistantIntents.EXTRA_AYAH, 255)
            }
        }
        ShortcutInfoCompat.Builder(context, shortcut.id)
            .setShortLabel(shortcut.label)
            .setLongLabel(shortcut.does)
            .setIcon(IconCompat.createWithResource(context, shortcutIcon))
            .setIntent(intent)
            .build()
    }.getOrNull()
}
