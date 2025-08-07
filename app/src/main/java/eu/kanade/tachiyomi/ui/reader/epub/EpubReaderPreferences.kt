package eu.kanade.tachiyomi.ui.reader.epub

import eu.kanade.tachiyomi.util.epub.ReaderTheme
import eu.kanade.tachiyomi.util.epub.TextAlignment
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class EpubReaderPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun fontSize() = preferenceStore.getFloat(PREF_FONT_SIZE, DEFAULT_FONT_SIZE)
    fun fontFamily() = preferenceStore.getString(PREF_FONT_FAMILY, "")
    fun lineSpacing() = preferenceStore.getFloat(PREF_LINE_SPACING, DEFAULT_LINE_SPACING)
    fun textAlignment() = preferenceStore.getEnum(PREF_TEXT_ALIGNMENT, TextAlignment.LEFT)
    fun theme() = preferenceStore.getEnum(PREF_THEME, ReaderTheme.LIGHT)
    fun showProgressPercent() = preferenceStore.getBoolean(PREF_SHOW_PROGRESS_PERCENT, DEFAULT_SHOW_PROGRESS_PERCENT)
    fun volumeButtonScroll() = preferenceStore.getBoolean(PREF_VOLUME_BUTTON_SCROLL, DEFAULT_VOLUME_BUTTON_SCROLL)
    fun showBatteryAndTime() = preferenceStore.getBoolean(PREF_SHOW_BATTERY_AND_TIME, DEFAULT_SHOW_BATTERY_AND_TIME)

    companion object {
        const val PREF_FONT_SIZE = "epub_font_size"
        const val PREF_FONT_FAMILY = "epub_font_family"
        const val PREF_LINE_SPACING = "epub_line_spacing"
        const val PREF_TEXT_ALIGNMENT = "epub_text_alignment"
        const val PREF_THEME = "epub_theme"

        const val DEFAULT_FONT_SIZE = 18f
        const val DEFAULT_LINE_SPACING = 1.5f
        const val PREF_SHOW_PROGRESS_PERCENT = "epub_show_progress_percent"
        const val DEFAULT_SHOW_PROGRESS_PERCENT = false
        const val PREF_VOLUME_BUTTON_SCROLL = "epub_volume_button_scroll"
        const val DEFAULT_VOLUME_BUTTON_SCROLL = false
        const val PREF_SHOW_BATTERY_AND_TIME = "epub_show_battery_and_time"
        const val DEFAULT_SHOW_BATTERY_AND_TIME = false
    }
}
