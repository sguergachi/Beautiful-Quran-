package com.beautifulquran.ui.reader

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Loads bundled QPC/QCF V2 page fonts.
 */
class QcfFontProvider(private val context: Context) {
    private val lock = Mutex()
    private val memory = linkedMapOf<Int, FontFamily>()
    private val dir: File by lazy {
        File(context.noBackupFilesDir, "qcf-v2-fonts").apply { mkdirs() }
    }

    suspend fun fontFamily(page: Int): FontFamily? {
        if (page !in 1..604) return null
        memory[page]?.let { return it }
        return lock.withLock {
            memory[page]?.let { return@withLock it }
            val file = ensureFont(page) ?: return@withLock null
            val family = FontFamily(Typeface.createFromFile(file))
            memory[page] = family
            // Keep enough pages resident for the largest surah. If a page font
            // is evicted, Compose briefly has no font family for its glyphs,
            // which can cause visible text rearrangement while scrolling.
            if (memory.size > MAX_MEMORY_FONTS) {
                val firstKey = memory.keys.first()
                memory.remove(firstKey)
            }
            family
        }
    }

    fun cachedFontFamily(page: Int): FontFamily? = memory[page]

    suspend fun preload(pages: Collection<Int>): Set<Int> {
        val failed = mutableSetOf<Int>()
        pages
            .filter { it in 1..604 }
            .distinct()
            .forEach { page ->
                if (fontFamily(page) == null) failed += page
            }
        return failed
    }

    private suspend fun ensureFont(page: Int): File? = withContext(Dispatchers.IO) {
        val file = File(dir, fontFileName(page))
        if (file.isFile && file.length() > MIN_FONT_BYTES) return@withContext file
        val tmp = File(dir, "${file.name}.tmp")
        runCatching {
            context.assets.open("qcf-v2-fonts/${fontFileName(page)}").use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (tmp.length() <= MIN_FONT_BYTES) {
                tmp.delete()
                return@withContext null
            }
            if (file.exists()) file.delete()
            tmp.renameTo(file)
            file
        }.getOrElse {
            tmp.delete()
            null
        }
    }

    companion object {
        private const val MIN_FONT_BYTES = 100_000L
        private const val MAX_MEMORY_FONTS = 80

        fun fontFileName(page: Int): String = "QCF2${page.toString().padStart(3, '0')}.ttf"
    }
}
