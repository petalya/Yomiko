package eu.kanade.tachiyomi.ui.reader.epub

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
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.util.epub.ReaderTheme
import eu.kanade.tachiyomi.util.epub.TextAlignment
import kotlinx.coroutines.launch

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubReaderSettingsSheet(
    viewModel: EpubReaderViewModel,
    onDismiss: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val haptic = LocalHapticFeedback.current
    val lastSliderValue = remember { mutableFloatStateOf(settings.fontSize) }
    val colorSchemes = NovelReaderPreferences.PresetColorSchemes
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
            HorizontalPager(modifier = Modifier.animateContentSize(), state = pagerState, verticalAlignment = Alignment.Top) { page ->
                Column(modifier = Modifier.padding(horizontal = TabbedDialogPaddings.Horizontal)) {
                    when (page) {
                        0 -> {
                            // Text size
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text("Text size", modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                                Slider(
                                    value = settings.fontSize,
                                    onValueChange = {
                                        viewModel.setFontSize(it)
                                        if (lastSliderValue.floatValue.toInt() != it.toInt()) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            lastSliderValue.floatValue = it
                                        }
                                    },
                                    valueRange = 10f..30f,
                                    steps = 10,
                                    modifier = Modifier.weight(2f),
                                )
                            }
                            // Color
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text("Color", modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                                Row(Modifier.weight(2f), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    val themes = ReaderTheme.entries.toTypedArray()
                                    colorSchemes.forEachIndexed { idx, scheme ->
                                        val theme = themes.getOrNull(idx)
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(scheme.background)
                                                .border(
                                                    width = if (settings.theme.ordinal == idx) 3.dp else 1.dp,
                                                    color = if (settings.theme.ordinal == idx) MaterialTheme.colorScheme.primary else Color.Gray,
                                                    shape = CircleShape,
                                                )
                                                .let { mod ->
                                                    if (theme != null) mod.clickable { viewModel.setTheme(theme) } else mod
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text("A", color = scheme.text, fontSize = 18.sp)
                                        }
                                        Spacer(Modifier.height(24.dp))
                                    }
                                }
                            }
                            // Text align
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text("Text align", modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                                Row(Modifier.weight(2f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    IconButton(onClick = { viewModel.setTextAlignment(TextAlignment.LEFT) }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.FormatAlignLeft,
                                            contentDescription = "Left",
                                            tint = if (settings.alignment == TextAlignment.LEFT) MaterialTheme.colorScheme.primary else Color.Gray,
                                        )
                                    }
                                    IconButton(onClick = { viewModel.setTextAlignment(TextAlignment.CENTER) }) {
                                        Icon(
                                            imageVector = Icons.Default.FormatAlignCenter,
                                            contentDescription = "Center",
                                            tint = if (settings.alignment == TextAlignment.CENTER) MaterialTheme.colorScheme.primary else Color.Gray,
                                        )
                                    }
                                    IconButton(onClick = { viewModel.setTextAlignment(TextAlignment.JUSTIFY) }) {
                                        Icon(
                                            imageVector = Icons.Default.FormatAlignJustify,
                                            contentDescription = "Justify",
                                            tint = if (settings.alignment == TextAlignment.JUSTIFY) MaterialTheme.colorScheme.primary else Color.Gray,
                                        )
                                    }
                                    IconButton(onClick = { viewModel.setTextAlignment(TextAlignment.RIGHT) }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.FormatAlignRight,
                                            contentDescription = "Right",
                                            tint = if (settings.alignment == TextAlignment.RIGHT) MaterialTheme.colorScheme.primary else Color.Gray,
                                        )
                                    }
                                }
                            }
                            // Line Height
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text("Line Height", modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                                Row(
                                    modifier = Modifier.weight(2f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    IconButton(onClick = { viewModel.setLineSpacing((settings.lineSpacing - 0.1f).coerceAtLeast(1.0f)) }) {
                                        Text("-", fontSize = 20.sp)
                                    }
                                    Text(
                                        String.format("%.1f%%", settings.lineSpacing),
                                        modifier = Modifier.width(60.dp),
                                        textAlign = TextAlign.Center,
                                    )
                                    IconButton(onClick = { viewModel.setLineSpacing((settings.lineSpacing + 0.1f).coerceAtMost(2.0f)) }) {
                                        Text("+", fontSize = 20.sp)
                                    }
                                }
                            }
                            // Font style selector
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text("Font", modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                                Box(modifier = Modifier.weight(2f)) {
                                    val scrollState = rememberScrollState()
                                    Row(
                                        modifier = Modifier.horizontalScroll(scrollState),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        val fonts = listOf(
                                            "Default" to null,
                                            "Lora" to "lora",
                                            "Open Sans" to "open_sans",
                                            "Arbutus Slab" to "arbutus_slab",
                                            "Lato" to "lato",
                                        )
                                        fonts.forEach { (label, value) ->
                                            val fontFamily = when (value) {
                                                "lora" -> FontFamily(Font(eu.kanade.tachiyomi.R.font.lora))
                                                "open_sans" -> FontFamily(Font(eu.kanade.tachiyomi.R.font.open_sans))
                                                "arbutus_slab" -> FontFamily(Font(eu.kanade.tachiyomi.R.font.arbutus_slab))
                                                "lato" -> FontFamily(Font(eu.kanade.tachiyomi.R.font.lato))
                                                else -> FontFamily.Default
                                            }
                                            Surface(
                                                shape = CircleShape,
                                                color = if (settings.fontFamily == value || (value == null && settings.fontFamily == null)) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                                                border = BorderStroke(1.dp, if (settings.fontFamily == value || (value == null && settings.fontFamily == null)) MaterialTheme.colorScheme.primary else Color.Gray),
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .clickable { viewModel.setFontFamily(value) },
                                            ) {
                                                Text(
                                                    label,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    fontSize = 14.sp,
                                                    fontFamily = fontFamily,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            // General tab
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            ) {
                                Text(
                                    "Volume Button Scroll",
                                    modifier = Modifier.weight(1f).padding(start = 16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                androidx.compose.material3.Switch(
                                    checked = settings.volumeButtonScroll,
                                    onCheckedChange = { viewModel.setVolumeButtonScroll(it) },
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            ) {
                                Text(
                                    "Show Progress Percent",
                                    modifier = Modifier.weight(1f).padding(start = 16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                androidx.compose.material3.Switch(
                                    checked = settings.showProgressPercent,
                                    onCheckedChange = { viewModel.setShowProgressPercent(it) },
                                )
                            }
                            if (settings.showProgressPercent) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                ) {
                                    Text(
                                        "Show Battery and Time",
                                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    androidx.compose.material3.Switch(
                                        checked = settings.showBatteryAndTime,
                                        onCheckedChange = { viewModel.setShowBatteryAndTime(it) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
