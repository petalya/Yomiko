package eu.kanade.tachiyomi.ui.reader.epub

import eu.kanade.tachiyomi.util.epub.EpubContentBlock

/**
 * Represents different states of the EPUB reader
 */
sealed class EpubReaderState {
    /**
     * Initial loading state
     */
    object Loading : EpubReaderState()

    /**
     * Error state with a message
     */
    data class Error(val message: String) : EpubReaderState()

    /**
     * Success state for reflowable content using Jetpack Compose
     */
    data class ReflowSuccess(
        val bookTitle: String,
        val chapterTitle: String,
        val contentBlocks: List<EpubContentBlock>,
        val hasPrev: Boolean,
        val hasNext: Boolean,
        val progress: Float,
    ) : EpubReaderState()

    /**
     * Success state for HTML content using WebView
     */
    data class HtmlSuccess(
        val bookTitle: String,
        val chapterTitle: String,
        val content: String,
        val hasPrev: Boolean,
        val hasNext: Boolean,
        val progress: Float,
    ) : EpubReaderState()
} 