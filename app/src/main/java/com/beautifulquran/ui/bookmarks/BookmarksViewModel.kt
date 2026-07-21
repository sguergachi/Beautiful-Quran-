package com.beautifulquran.ui.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beautifulquran.data.BookmarkRepository
import com.beautifulquran.data.NoteRepository
import com.beautifulquran.data.QuranRepository
import com.beautifulquran.data.model.BookmarkedAyah
import com.beautifulquran.data.model.Surah
import com.beautifulquran.domain.normalizeArabicForSearch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BookmarkSection(
    val surah: Surah,
    val ayahs: List<BookmarkedAyah>,
)

data class BookmarksUiState(
    val query: String = "",
    val totalCount: Int = 0,
    val sections: List<BookmarkSection> = emptyList(),
    /** Note text keyed by "surahId:ayah" for quick lookup in the index. */
    val notes: Map<String, String> = emptyMap(),
    val loading: Boolean = true,
)

internal const val BOOKMARK_SECTION_PREVIEW_LIMIT = 5

internal fun isBookmarkSectionCollapsed(
    surahId: Int,
    collapsedSurahs: Set<Int>,
    searching: Boolean,
): Boolean = !searching && surahId in collapsedSurahs

internal fun visibleBookmarkAyahs(
    ayahs: List<BookmarkedAyah>,
    expanded: Boolean,
    searching: Boolean,
): List<BookmarkedAyah> =
    if (expanded || searching) ayahs else ayahs.take(BOOKMARK_SECTION_PREVIEW_LIMIT)

internal fun hiddenBookmarkCount(
    ayahs: List<BookmarkedAyah>,
    expanded: Boolean,
    searching: Boolean,
): Int = if (expanded || searching) 0 else (ayahs.size - BOOKMARK_SECTION_PREVIEW_LIMIT).coerceAtLeast(0)

internal fun bookmarkDisclosureLabel(hiddenCount: Int, expanded: Boolean): String = when {
    expanded -> "Show fewer bookmarks"
    hiddenCount == 1 -> "Show 1 more bookmark"
    else -> "Show $hiddenCount more bookmarks"
}

/** Filters bookmark text and groups matches under their chapter heading. */
internal fun bookmarkSections(
    ayahs: List<BookmarkedAyah>,
    query: String,
): List<BookmarkSection> {
    val needle = query.trim()
    val normalizedArabic = normalizeArabicForSearch(needle)
    val matches = if (needle.isEmpty()) {
        ayahs
    } else {
        ayahs.filter { bookmark ->
            bookmark.surah.nameTransliteration.contains(needle, ignoreCase = true) ||
                bookmark.surah.nameTranslation.contains(needle, ignoreCase = true) ||
                bookmark.surah.nameArabic.contains(needle) ||
                bookmark.surah.id.toString() == needle ||
                bookmark.translation.contains(needle, ignoreCase = true) ||
                (normalizedArabic.isNotEmpty() &&
                    normalizeArabicForSearch(bookmark.text).contains(normalizedArabic)) ||
                "${bookmark.surah.id}:${bookmark.ayahNumber}" == needle
        }
    }
    return matches
        .groupBy { it.surah }
        .map { (surah, verses) -> BookmarkSection(surah, verses) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksViewModel(
    private val repository: QuranRepository,
    private val bookmarks: BookmarkRepository,
    private val notes: NoteRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val resolved = bookmarks.bookmarks.flatMapLatest { saved ->
        flow { emit(saved to repository.bookmarkedAyahs(saved)) }
    }

    /** Kept separate so typing in search never recomposes the whole paper stack. */
    val bookmarkCount = bookmarks.bookmarks
        .map { it.size }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            bookmarks.bookmarks.value.size,
        )

    val uiState = combine(query, resolved, notes.notes) { search, (saved, ayahs), noteList ->
        BookmarksUiState(
            query = search,
            totalCount = saved.size,
            sections = bookmarkSections(ayahs, search),
            notes = noteList.associate { "${it.surahId}:${it.ayah}" to it.text },
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BookmarksUiState(),
    )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun removeBookmark(surahId: Int, ayah: Int) {
        if (bookmarks.isBookmarked(surahId, ayah)) bookmarks.toggle(surahId, ayah)
    }
}
