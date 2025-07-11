package eu.kanade.tachiyomi.ui.reader.setting

import androidx.compose.ui.graphics.Color
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class NovelReaderPreferences(
    private val preferenceStore: PreferenceStore,
) {
    // Font size (sp)
    fun fontSize() = preferenceStore.getInt(PREF_FONT_SIZE, DEFAULT_FONT_SIZE)

    // Text color (ARGB Int)
    fun textColor() = preferenceStore.getInt(PREF_TEXT_COLOR, DEFAULT_TEXT_COLOR)

    // Background color (ARGB Int)
    fun backgroundColor() = preferenceStore.getInt(PREF_BACKGROUND_COLOR, DEFAULT_BACKGROUND_COLOR)

    // Text alignment
    fun textAlignment() = preferenceStore.getEnum(PREF_TEXT_ALIGNMENT, TextAlignment.Left)

    // Line spacing (multiplier * 100, e.g. 120 = 1.2x)
    fun lineSpacing() = preferenceStore.getInt(PREF_LINE_SPACING, DEFAULT_LINE_SPACING)

    // Font family
    fun fontFamily() = preferenceStore.getEnum(PREF_FONT_FAMILY, FontFamilyPref.ORIGINAL)

    data class ReaderColorScheme(val background: Color, val text: Color)

    fun colorSchemeIndex() = preferenceStore.getInt(PREF_COLOR_SCHEME_INDEX, DEFAULT_COLOR_SCHEME_INDEX)

    enum class TextAlignment {
        Left,
        Center,
        Justify,
        Right,
    }

    enum class FontFamilyPref {
        ORIGINAL,
        LORA,
        NOTO_SANS,
        OPEN_SANS,
        ARBUTUS_SLAB,
        LATO,
    }

    companion object {
        const val PREF_FONT_SIZE = "novel_font_size"
        const val PREF_TEXT_COLOR = "novel_text_color"
        const val PREF_BACKGROUND_COLOR = "novel_background_color"
        const val PREF_TEXT_ALIGNMENT = "novel_text_alignment"
        const val PREF_LINE_SPACING = "novel_line_spacing"
        const val PREF_COLOR_SCHEME_INDEX = "novel_color_scheme_index"
        const val PREF_FONT_FAMILY = "novel_font_family"

        const val DEFAULT_FONT_SIZE = 18 // sp
        const val DEFAULT_TEXT_COLOR = 0xFFE6E6F2.toInt() // light text
        const val DEFAULT_BACKGROUND_COLOR = 0xFF2B2B38.toInt() // blue gray bg
        const val DEFAULT_LINE_SPACING = 150 // 1.5x
        const val DEFAULT_COLOR_SCHEME_INDEX = 3 // Default to Blue gray bg, light text

        // Preset colors for readability
        val PresetTextColors = listOf(
            Color(0xFF222222), // dark gray
            Color(0xFF000000), // black
            Color(0xFF444444), // medium dark
            Color(0xFFFFFFFF), // white (for dark bg)
            Color(0xFFECECEC), // light gray
        )
        val PresetBackgroundColors = listOf(
            Color(0xFFFFFFFF), // white
            Color(0xFFF5F5DC), // beige
            Color(0xFFFAF0E6), // linen
            Color(0xFF222222), // dark gray
            Color(0xFF000000), // black
            Color(0xFFECECEC), // light gray
        )

        // Preset color pairs matching the screenshot (background, text)
        val PresetColorSchemes = listOf(
            ReaderColorScheme(Color(0xFFFFFFFF), Color(0xFF222222)), // White bg, dark text
            ReaderColorScheme(Color(0xFFFFE4C7), Color(0xFF6B4F1D)), // Sepia bg, brown text
            ReaderColorScheme(Color(0xFFDDE7E3), Color(0xFF2B3A35)), // Mint bg, dark green text
            ReaderColorScheme(Color(0xFF2B2B38), Color(0xFFE6E6F2)), // Blue gray bg, light text
            ReaderColorScheme(Color(0xFF000000), Color(0xFFECECEC)), // Black bg, light gray text
        )
    }
}
