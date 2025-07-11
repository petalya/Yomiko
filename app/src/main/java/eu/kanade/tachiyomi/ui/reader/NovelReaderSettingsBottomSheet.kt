package eu.kanade.tachiyomi.ui.reader

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderSettingsScreenModel

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
    val colorScheme = model.colorScheme
    val fontFamily by model.fontFamily.collectAsState()
    val haptic = LocalHapticFeedback.current
    val lastSliderValue = remember { mutableStateOf(fontSize.toFloat()) }

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
            Text("Reader Settings", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            // Text size
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Text size", modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = fontSize.toFloat(),
                    onValueChange = {
                        model.setFontSize(it.toInt())
                        if (lastSliderValue.value.toInt() != it.toInt()) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastSliderValue.value = it
                        }
                    },
                    valueRange = 12f..32f,
                    steps = 10, // Fewer steps for smoother adjustment
                    modifier = Modifier.weight(2f),
                )
            }
            // Color
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Color", modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.weight(2f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    NovelReaderPreferences.PresetColorSchemes.forEachIndexed { idx, scheme ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(scheme.background)
                                .border(
                                    width = if (colorSchemeIndex == idx) 3.dp else 1.dp,
                                    color = if (colorSchemeIndex == idx) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape,
                                )
                                .clickable { model.setColorSchemeIndex(idx) },
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
                    AlignmentButton(
                        icon = Icons.Default.FormatAlignLeft,
                        selected = textAlignment == NovelReaderPreferences.TextAlignment.Left,
                        onClick = { model.setTextAlignment(NovelReaderPreferences.TextAlignment.Left) },
                        contentDescription = "Left",
                    )
                    AlignmentButton(
                        icon = Icons.Default.FormatAlignCenter,
                        selected = textAlignment == NovelReaderPreferences.TextAlignment.Center,
                        onClick = { model.setTextAlignment(NovelReaderPreferences.TextAlignment.Center) },
                        contentDescription = "Center",
                    )
                    AlignmentButton(
                        icon = Icons.Default.FormatAlignJustify,
                        selected = textAlignment == NovelReaderPreferences.TextAlignment.Justify,
                        onClick = { model.setTextAlignment(NovelReaderPreferences.TextAlignment.Justify) },
                        contentDescription = "Justify",
                    )
                    AlignmentButton(
                        icon = Icons.Default.FormatAlignRight,
                        selected = textAlignment == NovelReaderPreferences.TextAlignment.Right,
                        onClick = { model.setTextAlignment(NovelReaderPreferences.TextAlignment.Right) },
                        contentDescription = "Right",
                    )
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
                    IconButton(onClick = { model.setLineSpacing((lineSpacing - 10).coerceAtLeast(100)) }) {
                        Text("-", fontSize = 20.sp)
                    }
                    Text(String.format("%.1f%%", lineSpacing / 100f), modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                    IconButton(onClick = { model.setLineSpacing((lineSpacing + 10).coerceAtMost(200)) }) {
                        Text("+", fontSize = 20.sp)
                    }
                }
            }
            // Font style
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Font style", modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                Box(modifier = Modifier.weight(2f)) {
                    val scrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .horizontalScroll(scrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FontPill(
                            label = "Original",
                            selected = fontFamily == NovelReaderPreferences.FontFamilyPref.ORIGINAL,
                            onClick = { model.setFontFamily(NovelReaderPreferences.FontFamilyPref.ORIGINAL) },
                            fontFamily = null,
                        )
                        FontPill(
                            label = "Lora",
                            selected = fontFamily == NovelReaderPreferences.FontFamilyPref.LORA,
                            onClick = { model.setFontFamily(NovelReaderPreferences.FontFamilyPref.LORA) },
                            fontFamily = FontFamily(Font(R.font.lora)),
                        )
                        FontPill(
                            label = "Open Sans",
                            selected = fontFamily == NovelReaderPreferences.FontFamilyPref.OPEN_SANS,
                            onClick = { model.setFontFamily(NovelReaderPreferences.FontFamilyPref.OPEN_SANS) },
                            fontFamily = FontFamily(Font(R.font.open_sans)),
                        )
                        FontPill(
                            label = "Arbutus Slab",
                            selected = fontFamily == NovelReaderPreferences.FontFamilyPref.ARBUTUS_SLAB,
                            onClick = { model.setFontFamily(NovelReaderPreferences.FontFamilyPref.ARBUTUS_SLAB) },
                            fontFamily = FontFamily(Font(R.font.arbutus_slab)),
                        )
                        FontPill(
                            label = "Lato",
                            selected = fontFamily == NovelReaderPreferences.FontFamilyPref.LATO,
                            onClick = { model.setFontFamily(NovelReaderPreferences.FontFamilyPref.LATO) },
                            fontFamily = FontFamily(Font(R.font.lato)),
                        )
                    }
                }
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
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.Gray),
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
