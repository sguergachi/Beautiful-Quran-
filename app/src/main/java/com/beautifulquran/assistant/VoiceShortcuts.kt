package com.beautifulquran.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.beautifulquran.MainActivity
import com.beautifulquran.R

/** Builds and pins launcher shortcuts that open the reader via explicit intents. */
object VoiceShortcuts {

    fun pin(context: Context, shortcut: VoiceShortcut): Boolean {
        if (!shortcut.pinable) return false
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val info = build(context, shortcut) ?: return false
        return ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }

    fun build(context: Context, shortcut: VoiceShortcut): ShortcutInfoCompat? {
        val intent = Intent(shortcut.intentAction).apply {
            setClass(context, MainActivity::class.java)
            data = Uri.parse(shortcut.deepLink)
            // Avoid stacking a second task when already open.
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
