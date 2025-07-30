package eu.kanade.tachiyomi.ui.reader.novel

// --- State ---
sealed class NovelReaderState {
    data object Loading : NovelReaderState()
    data class Success(
        val novelTitle: String,
        val chapterTitle: String,
        val content: String,
        val hasNext: Boolean = false,
        val hasPrev: Boolean = false,
        val progress: Float = 0f,
        val bookmarked: Boolean = false,
    ) : NovelReaderState()
    data class Error(val message: String) : NovelReaderState()
}
