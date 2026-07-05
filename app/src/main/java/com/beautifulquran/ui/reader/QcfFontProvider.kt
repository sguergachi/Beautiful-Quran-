package com.beautifulquran.ui.reader

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.InputStream
import java.io.SequenceInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import kotlin.math.min

/**
 * Loads bundled QPC/QCF V2 page fonts.
 */
class QcfFontProvider(private val context: Context) {
    private val _extractionProgress = MutableStateFlow(QcfFontExtractionProgress())

    val extractionProgress: StateFlow<QcfFontExtractionProgress> =
        _extractionProgress.asStateFlow()

    private val lock = Mutex()
    private val extractionLock = Mutex()
    private val memory = linkedMapOf<Int, FontFamily>()
    private val dir: File by lazy {
        File(context.noBackupFilesDir, "qcf-v2-fonts").apply { mkdirs() }
    }
    private val marker: File by lazy {
        File(dir, ".${ARCHIVE_VERSION}.ready")
    }

    fun prepareOnStartup(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            ensureArchiveExtracted()
        }
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
        ensureArchiveExtracted()
        val file = File(dir, fontFileName(page))
        if (file.isFile && file.length() > MIN_FONT_BYTES) return@withContext file
        null
    }

    private suspend fun ensureArchiveExtracted() {
        if (isArchiveExtracted()) {
            markExtractionComplete()
            return
        }
        extractionLock.withLock {
            if (isArchiveExtracted()) {
                markExtractionComplete()
                return
            }
            runCatching {
                _extractionProgress.value = QcfFontExtractionProgress(isRunning = true)
                dir.mkdirs()
                dir.listFiles { file ->
                    file.name.endsWith(".tmp") || file.name.startsWith(".qcf-v2-fonts-")
                }?.forEach { it.delete() }
                openArchiveParts().use { assetInput ->
                    XZInputStream(assetInput).use { xzInput -> extractTar(xzInput) }
                }
                val expectedLastPage = File(dir, fontFileName(604))
                if (!expectedLastPage.isFile || expectedLastPage.length() <= MIN_FONT_BYTES) {
                    throw IllegalStateException("QCF font archive extraction was incomplete")
                }
                dir.listFiles { file ->
                    file.name.startsWith(".qcf-v2-fonts-") && file.name != marker.name
                }?.forEach { it.delete() }
                marker.writeText("ready\n")
                markExtractionComplete()
            }.onFailure {
                marker.delete()
                _extractionProgress.value = QcfFontExtractionProgress(
                    isRunning = false,
                    error = it.message ?: "Could not prepare Mushaf fonts",
                )
            }
        }
    }

    private fun isArchiveExtracted(): Boolean =
        marker.isFile && File(dir, fontFileName(1)).isFile && File(dir, fontFileName(604)).isFile

    private fun openArchiveParts(): InputStream {
        val streams = ARCHIVE_ASSETS.map { asset -> context.assets.open(asset) }
        return object : SequenceInputStream(Collections.enumeration(streams)) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    streams.forEach { stream -> runCatching { stream.close() } }
                }
            }
        }
    }

    private fun extractTar(input: InputStream) {
        val header = ByteArray(TAR_BLOCK_BYTES)
        var extractedFonts = 0
        while (true) {
            val read = input.readFullyOrEnd(header)
            if (read == -1 || header.all { it == 0.toByte() }) return
            if (read != TAR_BLOCK_BYTES) {
                throw IllegalStateException("Truncated QCF font archive header")
            }
            val name = header.extractString(0, 100)
            val size = header.extractOctal(124, 12)
            val type = header[156].toInt().toChar()
            if (type == '0' || type == '\u0000') {
                val output = safeOutputFile(name)
                val tmp = File(output.parentFile, "${output.name}.tmp")
                tmp.outputStream().use { fileOutput ->
                    input.copyExactlyTo(fileOutput, size)
                }
                if (output.exists()) output.delete()
                if (!tmp.renameTo(output)) {
                    tmp.delete()
                    throw IllegalStateException("Could not move extracted QCF font into place")
                }
                extractedFonts++
                _extractionProgress.value = QcfFontExtractionProgress(
                    extractedFonts = extractedFonts,
                    isRunning = true,
                )
            } else {
                input.skipExactly(size)
            }
            input.skipExactly(paddingFor(size))
        }
    }

    private fun safeOutputFile(tarName: String): File {
        val name = tarName.substringAfterLast('/')
        require(name.matches(FONT_FILE_PATTERN)) { "Unexpected QCF font archive entry: $tarName" }
        return File(dir, name)
    }

    private fun InputStream.copyExactlyTo(output: java.io.OutputStream, byteCount: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var remaining = byteCount
        while (remaining > 0) {
            val read = read(buffer, 0, min(buffer.size.toLong(), remaining).toInt())
            if (read == -1) throw IllegalStateException("Truncated QCF font archive entry")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun InputStream.skipExactly(byteCount: Long) {
        var remaining = byteCount
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else if (read() != -1) {
                remaining--
            } else {
                throw IllegalStateException("Truncated QCF font archive padding")
            }
        }
    }

    private fun InputStream.readFullyOrEnd(buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = read(buffer, offset, buffer.size - offset)
            if (read == -1) return if (offset == 0) -1 else offset
            offset += read
        }
        return offset
    }

    private fun ByteArray.extractString(offset: Int, length: Int): String {
        val end = (offset until offset + length)
            .firstOrNull { this[it] == 0.toByte() }
            ?: (offset + length)
        return decodeToString(offset, end)
    }

    private fun ByteArray.extractOctal(offset: Int, length: Int): Long {
        if (this[offset] == 0x80.toByte()) {
            val bytes = copyOfRange(offset, offset + length)
            bytes[0] = bytes[0].toInt().and(0x7F).toByte()
            return ByteBuffer.wrap(bytes)
                .order(ByteOrder.BIG_ENDIAN)
                .long
        }
        return extractString(offset, length)
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.toLong(8)
            ?: 0L
    }

    private fun paddingFor(size: Long): Long =
        (TAR_BLOCK_BYTES - (size % TAR_BLOCK_BYTES)) % TAR_BLOCK_BYTES

    private fun markExtractionComplete() {
        _extractionProgress.value = QcfFontExtractionProgress(
            extractedFonts = QCF_FONT_COUNT,
            isRunning = false,
            isComplete = true,
        )
    }

    companion object {
        private const val MIN_FONT_BYTES = 100_000L
        private const val MAX_MEMORY_FONTS = 80
        private const val QCF_FONT_COUNT = 604
        private const val ARCHIVE_VERSION = "qcf-v2-fonts-v1"
        private const val TAR_BLOCK_BYTES = 512
        private val ARCHIVE_ASSETS = listOf(
            "qcf-v2-fonts.tar.xz.part0",
            "qcf-v2-fonts.tar.xz.part1",
        )
        private val FONT_FILE_PATTERN = Regex("""QCF2\d{3}\.ttf""")

        fun fontFileName(page: Int): String = "QCF2${page.toString().padStart(3, '0')}.ttf"
    }
}

data class QcfFontExtractionProgress(
    val extractedFonts: Int = 0,
    val totalFonts: Int = 604,
    val isRunning: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null,
) {
    val fraction: Float
        get() = if (totalFonts > 0) {
            (extractedFonts.toFloat() / totalFonts).coerceIn(0f, 1f)
        } else {
            0f
        }

    val percent: Int
        get() = (fraction * 100).toInt().coerceIn(0, 100)
}
