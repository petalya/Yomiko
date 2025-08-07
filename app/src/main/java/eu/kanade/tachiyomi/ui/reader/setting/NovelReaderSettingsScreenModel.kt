package eu.kanade.tachiyomi.ui.reader.setting

import cafe.adriel.voyager.core.model.ScreenModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelReaderSettingsScreenModel(
    private val preferences: NovelReaderPreferences = Injekt.get(),
) : ScreenModel {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _fontSize = MutableStateFlow(preferences.fontSize().get())
    val fontSize: StateFlow<Int> = _fontSize.asStateFlow()

    private val _colorSchemeIndex = MutableStateFlow(preferences.colorSchemeIndex().get())
    val colorSchemeIndex: StateFlow<Int> = _colorSchemeIndex.asStateFlow()

    private val _colorScheme = MutableStateFlow(
        NovelReaderPreferences.PresetColorSchemes[preferences.colorSchemeIndex().get()],
    )
    val colorScheme: StateFlow<NovelReaderPreferences.ReaderColorScheme> = _colorScheme.asStateFlow()

    private val _textAlignment = MutableStateFlow(preferences.textAlignment().get())
    val textAlignment: StateFlow<NovelReaderPreferences.TextAlignment> = _textAlignment.asStateFlow()

    private val _lineSpacing = MutableStateFlow(preferences.lineSpacing().get())
    val lineSpacing: StateFlow<Int> = _lineSpacing.asStateFlow()

    private val _fontFamily = MutableStateFlow(preferences.fontFamily().get())
    val fontFamily: StateFlow<NovelReaderPreferences.FontFamilyPref> = _fontFamily.asStateFlow()

    private val _showProgressPercent = MutableStateFlow(preferences.showProgressPercent().get())
    val showProgressPercent: StateFlow<Boolean> = _showProgressPercent.asStateFlow()

    private val _volumeButtonScroll = MutableStateFlow(preferences.volumeButtonScroll().get())
    val volumeButtonScroll: StateFlow<Boolean> = _volumeButtonScroll.asStateFlow()

    private val _showBatteryAndTime = MutableStateFlow(preferences.showBatteryAndTime().get())
    val showBatteryAndTime: StateFlow<Boolean> = _showBatteryAndTime.asStateFlow()

    fun setFontSize(size: Int) {
        _fontSize.value = size
        scope.launch { preferences.fontSize().set(size) }
    }

    fun setTextAlignment(alignment: NovelReaderPreferences.TextAlignment) {
        _textAlignment.value = alignment
        scope.launch { preferences.textAlignment().set(alignment) }
    }

    fun setLineSpacing(spacing: Int) {
        _lineSpacing.value = spacing
        scope.launch { preferences.lineSpacing().set(spacing) }
    }

    fun setColorSchemeIndex(index: Int) {
        _colorSchemeIndex.value = index
        _colorScheme.value = NovelReaderPreferences.PresetColorSchemes[index]
        scope.launch { preferences.colorSchemeIndex().set(index) }
    }

    fun setFontFamily(font: NovelReaderPreferences.FontFamilyPref) {
        _fontFamily.value = font
        scope.launch { preferences.fontFamily().set(font) }
    }

    fun setShowProgressPercent(enabled: Boolean) {
        _showProgressPercent.value = enabled
        scope.launch { preferences.showProgressPercent().set(enabled) }
    }

    fun setVolumeButtonScroll(enabled: Boolean) {
        _volumeButtonScroll.value = enabled
        scope.launch { preferences.volumeButtonScroll().set(enabled) }
    }

    fun setShowBatteryAndTime(enabled: Boolean) {
        _showBatteryAndTime.value = enabled
        scope.launch { preferences.showBatteryAndTime().set(enabled) }
    }
}
