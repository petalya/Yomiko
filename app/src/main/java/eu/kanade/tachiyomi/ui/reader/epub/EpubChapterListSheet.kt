package eu.kanade.tachiyomi.ui.reader.epub

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.manga.components.MangaChapterListItem
import eu.kanade.tachiyomi.data.download.model.Download
import tachiyomi.domain.library.model.ChapterSwipeAction

@Composable
fun EpubChapterListSheet(
    viewModel: EpubReaderViewModel,
    show: Boolean,
    onDismiss: () -> Unit,
) {
    if (!show) return

    val chapters = viewModel.chapters
    val currentChapterId = viewModel.currentChapterId

    val chapterItems = chapters.map { chapter ->
        val isCurrent = chapter.id == currentChapterId
        EpubChapterRow(
            chapter = chapter,
            isCurrent = isCurrent,
        )
    }

    AdaptiveSheet(onDismissRequest = onDismiss) {
        val state = rememberLazyListState(chapterItems.indexOfFirst { it.isCurrent }.coerceAtLeast(0))
        LazyColumn(
            state = state,
            modifier = Modifier.heightIn(min = 200.dp, max = 500.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            items(
                items = chapterItems,
                key = { "chapter-${it.chapter.id}" },
            ) { epubChapterRow ->

                MangaChapterListItem(
                    title = epubChapterRow.chapter.name,
                    date = null,
                    readProgress = viewModel.getSavedProgress(epubChapterRow.chapter.id)
                        .let { percent ->
                            val pct = (percent * 100).toInt().coerceIn(0, 100)
                            if (pct > 0) "Progress $pct%" else null
                        },
                    scanlator = epubChapterRow.chapter.scanlator,
                    sourceName = null,
                    read = epubChapterRow.chapter.read,
                    bookmark = epubChapterRow.chapter.bookmark,
                    selected = epubChapterRow.isCurrent,
                    downloadIndicatorEnabled = false,
                    downloadStateProvider = { Download.State.DOWNLOADED },
                    downloadProgressProvider = { 0 },
                    chapterSwipeStartAction = ChapterSwipeAction.ToggleRead,
                    chapterSwipeEndAction = ChapterSwipeAction.ToggleBookmark,
                    onLongClick = {},
                    onClick = {
                        viewModel.jumpToChapter(chapters.indexOf(epubChapterRow.chapter))
                        onDismiss()
                    },
                    // EPUB downloads are not driven here; keep empty
                    onDownloadClick = {},
                    onChapterSwipe = { action ->
                        when (action) {
                            ChapterSwipeAction.ToggleRead -> viewModel.toggleRead(epubChapterRow.chapter)
                            ChapterSwipeAction.ToggleBookmark -> viewModel.toggleBookmark(epubChapterRow.chapter)
                            else -> {}
                        }
                    },
                )
            }
        }
    }
}

data class EpubChapterRow(
    val chapter: tachiyomi.domain.chapter.model.Chapter,
    val isCurrent: Boolean,
)
