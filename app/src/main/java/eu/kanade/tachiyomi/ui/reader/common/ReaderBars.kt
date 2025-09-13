package eu.kanade.tachiyomi.ui.reader.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarTitle

@Composable
fun ReaderTopBar(
    title: String,
    subtitle: String?,
    bookmarked: Boolean,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    backgroundColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
) {
    AppBar(
        titleContent = {
            AppBarTitle(title = title, subtitle = subtitle)
        },
        navigateUp = onBack,
        actions = {
            IconButton(onClick = onToggleBookmark) {
                Icon(
                    imageVector = if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = if (bookmarked) "Remove bookmark" else "Add bookmark",
                )
            }
        },
        backgroundColor = backgroundColor,
    )
}

@Composable
fun ReaderBottomBar(
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onChapterList: () -> Unit,
    onScrollTop: () -> Unit,
    onSettings: () -> Unit,
    backgroundColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    modifier: Modifier = Modifier,
    additionalActions: (@Composable RowScope.() -> Unit)? = null,
    swapNavOrder: Boolean = false,
    additionalAfterPrev: Boolean = false,
    additionalAfterChapterList: Boolean = false,
) {
    Surface(
        color = backgroundColor,
        shape = RectangleShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (swapNavOrder) {
                // Order: Prev, (additional?), ChapterList, ScrollTop, Settings, Next
                IconButton(onClick = onPrev, enabled = hasPrev, modifier = Modifier.alpha(if (hasPrev) 1f else 0.3f)) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous chapter",
                        tint = if (hasPrev) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    )
                }
                if (additionalAfterPrev && additionalActions != null) {
                    additionalActions()
                }
                IconButton(onClick = onChapterList) {
                    Icon(Icons.Filled.FormatListNumbered, contentDescription = "Chapter list")
                }
                if (additionalAfterChapterList && additionalActions != null) {
                    additionalActions()
                }
                IconButton(onClick = onScrollTop) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Scroll to top")
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
                if (!additionalAfterPrev && additionalActions != null && !additionalAfterChapterList) {
                    additionalActions()
                }
                IconButton(onClick = onNext, enabled = hasNext, modifier = Modifier.alpha(if (hasNext) 1f else 0.3f)) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next chapter",
                        tint = if (hasNext) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    )
                }
            } else {
                // Default order: Next, ChapterList, ScrollTop, Settings, (additional?), Prev
                IconButton(onClick = onNext, enabled = hasNext, modifier = Modifier.alpha(if (hasNext) 1f else 0.3f)) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Next chapter",
                        tint = if (hasNext) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    )
                }
                IconButton(onClick = onChapterList) {
                    Icon(Icons.Filled.FormatListNumbered, contentDescription = "Chapter list")
                }
                IconButton(onClick = onScrollTop) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Scroll to top")
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
                if (additionalActions != null) {
                    additionalActions()
                }
                IconButton(onClick = onPrev, enabled = hasPrev, modifier = Modifier.alpha(if (hasPrev) 1f else 0.3f)) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Previous chapter",
                        tint = if (hasPrev) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    )
                }
            }
        }
    }
}

@Composable
fun ReaderProgressSlider(
    progress: Float, // 0f..1f
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: () -> Unit,
    percentTextColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val percent = (progress * 100).toInt().coerceIn(0, 100)
        Text("$percent%", color = percentTextColor)
        Slider(
            value = progress,
            onValueChange = onProgressChange,
            onValueChangeFinished = onProgressChangeFinished,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
        )
        Text("100%", color = percentTextColor)
    }
}
