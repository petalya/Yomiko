package eu.kanade.tachiyomi.ui.reader.epub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.util.epub.ReaderTheme
import eu.kanade.tachiyomi.util.epub.TextAlignment
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderPreferences
import androidx.compose.ui.text.font.Font

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubReaderSettingsSheet(
    viewModel: EpubReaderViewModel,
    onDismiss: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val haptic = LocalHapticFeedback.current
    val lastSliderValue = remember { mutableStateOf(settings.fontSize) }
    val colorSchemes = NovelReaderPreferences.PresetColorSchemes
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Text("EPUB Reader Settings", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            // Text size
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Text size", modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.fontSize,
                    onValueChange = {
                        viewModel.setFontSize(it)
                        if (lastSliderValue.value.toInt() != it.toInt()) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastSliderValue.value = it
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
                Row(Modifier.weight(2f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val themes = ReaderTheme.values()
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
                    }
                }
            }
            // Text align
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Text align", modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.weight(2f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(onClick = { viewModel.setTextAlignment(TextAlignment.LEFT) }) {
                        Icon(
                            imageVector = Icons.Default.FormatAlignLeft,
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
                            imageVector = Icons.Default.FormatAlignRight,
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
                    Text(String.format("%.1f", settings.lineSpacing), modifier = Modifier.width(48.dp))
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
                            "Lato" to "lato"
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
                                    .clickable { viewModel.setFontFamily(value) }
                            ) {
                                Text(
                                    label,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    fontSize = 14.sp,
                                    fontFamily = fontFamily
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
