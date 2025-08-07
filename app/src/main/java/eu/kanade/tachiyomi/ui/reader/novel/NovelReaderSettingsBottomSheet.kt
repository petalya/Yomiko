package eu.kanade.tachiyomi.ui.reader.novel

import android.annotation.SuppressLint
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderSettingsScreenModel
import kotlinx.coroutines.launch

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelReaderSettingsBottomSheet(
    model: NovelReaderSettingsScreenModel,
    onDismiss: () -> Unit,
) {
    val fontSize by model.fontSize.collectAsState()
    val textAlignment by model.textAlignment.collectAsState()
    val lineSpacing by model.lineSpacing.collectAsState()
    val colorSchemeIndex by model.colorSchemeIndex.collectAsState()
    model.colorScheme
    val fontFamily by model.fontFamily.collectAsState()
    val haptic = LocalHapticFeedback.current
    val lastSliderValue = remember { mutableFloatStateOf(fontSize.toFloat()) }
    val showProgressPercent by model.showProgressPercent.collectAsState()
    val volumeButtonScroll by model.volumeButtonScroll.collectAsState()
    val showBatteryAndTime by model.showBatteryAndTime.collectAsState()

    AdaptiveSheet(onDismissRequest = onDismiss) {
        val pagerState = rememberPagerState { 2 }
        val scope = rememberCoroutineScope()
        Column(modifier = Modifier.fillMaxWidth()) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                divider = {},
            ) {
                Tab(selected = pagerState.currentPage == 0, onClick = { scope.launch { pagerState.animateScrollToPage(0) } }, text = { Text("Reader") })
                Tab(selected = pagerState.currentPage == 1, onClick = { scope.launch { pagerState.animateScrollToPage(1) } }, text = { Text("General") })
            }
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            HorizontalPager(
                modifier = Modifier.animateContentSize(),
                state = pagerState,
                verticalAlignment = Alignment.Top,
            ) { page ->
                Column(modifier = Modifier.padding(horizontal = TabbedDialogPaddings.Horizontal)) {
                    when (page) {
                        0 -> ReaderTab(
                            fontSize = fontSize,
                            onFontSizeChange = {
                                model.setFontSize(it)
                                if (lastSliderValue.floatValue.toInt() != it) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    lastSliderValue.floatValue = it.toFloat()
                                }
                            },
                            colorSchemeIndex = colorSchemeIndex,
                            onColorSchemeChange = { model.setColorSchemeIndex(it) },
                            textAlignment = textAlignment,
                            onTextAlignmentChange = { model.setTextAlignment(it) },
                            lineSpacing = lineSpacing,
                            onLineSpacingChange = { model.setLineSpacing(it) },
                            fontFamily = fontFamily,
                            onFontFamilyChange = { model.setFontFamily(it) },
                        )
                        1 -> GeneralTab(
                            showProgressPercent = showProgressPercent,
                            onShowProgressPercentChange = { model.setShowProgressPercent(it) },
                            volumeButtonScroll = volumeButtonScroll,
                            onVolumeButtonScrollChange = { model.setVolumeButtonScroll(it) },
                            showBatteryAndTime = showBatteryAndTime,
                            onShowBatteryAndTimeChange = { model.setShowBatteryAndTime(it) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ReaderTab(
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    colorSchemeIndex: Int,
    onColorSchemeChange: (Int) -> Unit,
    textAlignment: NovelReaderPreferences.TextAlignment,
    onTextAlignmentChange: (NovelReaderPreferences.TextAlignment) -> Unit,
    lineSpacing: Int,
    onLineSpacingChange: (Int) -> Unit,
    fontFamily: NovelReaderPreferences.FontFamilyPref,
    onFontFamilyChange: (NovelReaderPreferences.FontFamilyPref) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Text size", modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
            Slider(value = fontSize.toFloat(), onValueChange = { onFontSizeChange(it.toInt()) }, valueRange = 12f..32f, steps = 10, modifier = Modifier.weight(2f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Color", modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.weight(2f), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                NovelReaderPreferences.PresetColorSchemes.forEachIndexed { idx, scheme ->
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(scheme.background).border(width = if (colorSchemeIndex == idx) 3.dp else 1.dp, color = if (colorSchemeIndex == idx) MaterialTheme.colorScheme.primary else Color.Gray, shape = CircleShape).clickable { onColorSchemeChange(idx) },
                        contentAlignment = Alignment.Center,
                    ) { Text("A", color = scheme.text, fontSize = 18.sp) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Text align", modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.weight(2f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AlignmentButton(icon = Icons.AutoMirrored.Filled.FormatAlignLeft, selected = textAlignment == NovelReaderPreferences.TextAlignment.Left, onClick = { onTextAlignmentChange(NovelReaderPreferences.TextAlignment.Left) }, contentDescription = "Left")
                AlignmentButton(icon = Icons.Default.FormatAlignCenter, selected = textAlignment == NovelReaderPreferences.TextAlignment.Center, onClick = { onTextAlignmentChange(NovelReaderPreferences.TextAlignment.Center) }, contentDescription = "Center")
                AlignmentButton(icon = Icons.Default.FormatAlignJustify, selected = textAlignment == NovelReaderPreferences.TextAlignment.Justify, onClick = { onTextAlignmentChange(NovelReaderPreferences.TextAlignment.Justify) }, contentDescription = "Justify")
                AlignmentButton(icon = Icons.AutoMirrored.Filled.FormatAlignRight, selected = textAlignment == NovelReaderPreferences.TextAlignment.Right, onClick = { onTextAlignmentChange(NovelReaderPreferences.TextAlignment.Right) }, contentDescription = "Right")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Line Height", modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.weight(2f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { onLineSpacingChange((lineSpacing - 10).coerceAtLeast(100)) }) { Text("-", fontSize = 20.sp) }
                Text(String.format("%.1f%%", lineSpacing / 100f), modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                IconButton(onClick = { onLineSpacingChange((lineSpacing + 10).coerceAtMost(200)) }) { Text("+", fontSize = 20.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Font style", modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
            Box(modifier = Modifier.weight(2f)) {
                val scrollState = rememberScrollState()
                Row(modifier = Modifier.horizontalScroll(scrollState), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FontPill(label = "Original", selected = fontFamily == NovelReaderPreferences.FontFamilyPref.ORIGINAL, onClick = { onFontFamilyChange(NovelReaderPreferences.FontFamilyPref.ORIGINAL) }, fontFamily = null)
                    FontPill(label = "Lora", selected = fontFamily == NovelReaderPreferences.FontFamilyPref.LORA, onClick = { onFontFamilyChange(NovelReaderPreferences.FontFamilyPref.LORA) }, fontFamily = FontFamily(Font(R.font.lora)))
                    FontPill(label = "Open Sans", selected = fontFamily == NovelReaderPreferences.FontFamilyPref.OPEN_SANS, onClick = { onFontFamilyChange(NovelReaderPreferences.FontFamilyPref.OPEN_SANS) }, fontFamily = FontFamily(Font(R.font.open_sans)))
                    FontPill(label = "Arbutus Slab", selected = fontFamily == NovelReaderPreferences.FontFamilyPref.ARBUTUS_SLAB, onClick = { onFontFamilyChange(NovelReaderPreferences.FontFamilyPref.ARBUTUS_SLAB) }, fontFamily = FontFamily(Font(R.font.arbutus_slab)))
                    FontPill(label = "Lato", selected = fontFamily == NovelReaderPreferences.FontFamilyPref.LATO, onClick = { onFontFamilyChange(NovelReaderPreferences.FontFamilyPref.LATO) }, fontFamily = FontFamily(Font(R.font.lato)))
                }
            }
        }
    }
}

@Composable
private fun GeneralTab(
    showProgressPercent: Boolean,
    onShowProgressPercentChange: (Boolean) -> Unit,
    volumeButtonScroll: Boolean,
    onVolumeButtonScrollChange: (Boolean) -> Unit,
    showBatteryAndTime: Boolean,
    onShowBatteryAndTimeChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        // Volume Button Scroll first
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text("Volume Button Scroll", modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = volumeButtonScroll, onCheckedChange = onVolumeButtonScrollChange)
        }
        // Show Progress Percent second
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text("Show Progress Percent", modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = showProgressPercent, onCheckedChange = onShowProgressPercentChange)
        }
        if (showProgressPercent) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text("Show Battery and Time", modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = showBatteryAndTime, onCheckedChange = onShowBatteryAndTimeChange)
            }
        }
    }
}

@Composable
private fun ColorCircle(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray,
                shape = CircleShape,
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {}
}

@Composable
private fun AlignmentButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.primary else Color.Gray,
        )
    }
}

@Composable
private fun FontPill(label: String, selected: Boolean, onClick: () -> Unit, fontFamily: FontFamily?) {
    Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Gray),
        modifier = Modifier
            .height(36.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                text = label,
                fontFamily = fontFamily,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
