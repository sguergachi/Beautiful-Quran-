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

    fun publishDynamic(context: Context) {
        val pinable = VoiceRoutines.all.filter { it.pinable }.mapNotNull { build(context, it) }
        if (pinable.isEmpty()) return
        ShortcutManagerCompat.setDynamicShortcuts(context, pinable)
    }

    fun pin(context: Context, shortcut: VoiceShortcut): Boolean {
        if (!shortcut.pinable) return false
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val info = build(context, shortcut) ?: return false
        return ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }

    fun build(context: Context, shortcut: VoiceShortcut): ShortcutInfoCompat? {
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
        return ShortcutInfoCompat.Builder(context, shortcut.id)
            .setShortLabel(shortcut.label)
            .setLongLabel(shortcut.does)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
    }
}
