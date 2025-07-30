package eu.kanade.tachiyomi.ui.reader.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarTitle

@Composable
fun NovelReaderTopBar(
    title: String,
    chapterTitle: String,
    bookmarked: Boolean,
    onBack: () -> Unit,
    onBookmark: () -> Unit,
) {
    AppBar(
        titleContent = {
            AppBarTitle(title = title, subtitle = chapterTitle)
        },
        navigateUp = onBack,
        actions = {
            IconButton(onClick = onBookmark) {
                Icon(
                    if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = "Bookmark",
                )
            }
        },
        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    )
}

@Composable
fun NovelReaderBottomBar(
    hasPrev: Boolean,
    hasNext: Boolean,
    progress: Float,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSliderChange: (Float) -> Unit,
    onChapterList: () -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    ) {
        NavigationBarItem(
            selected = false,
            onClick = onPrev,
            icon = { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous") },
            enabled = hasPrev,
        )
        NavigationBarItem(
            selected = false,
            onClick = onChapterList,
            icon = { Icon(Icons.Filled.MoreVert, contentDescription = "Chapters") },
            enabled = true,
        )
        NavigationBarItem(
            selected = false,
            onClick = onNext,
            icon = { Icon(Icons.Filled.SkipNext, contentDescription = "Next") },
            enabled = hasNext,
        )
        Slider(
            value = progress,
            onValueChange = onSliderChange,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
    }
}

@Composable
fun EpubShimmerSkeletonLoader(
    modifier: Modifier = Modifier,
    lineCount: Int = 24,
    lineHeight: Dp = 18.dp,
    lineSpacing: Dp = 18.dp,
    topPadding: Dp = 64.dp,
    cornerRadius: Dp = 8.dp,
    baseColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
) {
    val shimmerInstance = rememberShimmer(shimmerBounds = ShimmerBounds.View)
    val widthFractions = listOf(0.9f, 0.8f, 0.7f, 0.6f)
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset.Zero,
        end = Offset(x = 1000f, y = 0f),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = topPadding)
            .shimmer(shimmerInstance),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        repeat(lineCount) {
            val widthFraction = widthFractions[it % widthFractions.size]
            Box(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .height(lineHeight)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(shimmerBrush),
            )
            if ((it + 1) % 4 == 0) {
                Spacer(modifier = Modifier.height(lineSpacing * 1.5f))
            } else {
                Spacer(modifier = Modifier.height(lineSpacing))
            }
        }
    }
}
