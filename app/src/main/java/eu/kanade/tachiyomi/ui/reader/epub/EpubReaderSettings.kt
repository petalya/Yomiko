package eu.kanade.tachiyomi.ui.reader.epub

import eu.kanade.tachiyomi.util.epub.ReaderTheme
import eu.kanade.tachiyomi.util.epub.TextAlignment

/**
 * Data class representing all settings for the EPUB reader.
 */
data class EpubReaderSettings(
    val fontSize: Float = 18f,
    val fontFamily: String? = null,
    val lineSpacing: Float = 1.5f,
    val alignment: TextAlignment = TextAlignment.LEFT,
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val isScrollMode: Boolean = false,
    val showProgressPercent: Boolean = false,
    val volumeButtonScroll: Boolean = false,
    val showBatteryAndTime: Boolean = false,
)
