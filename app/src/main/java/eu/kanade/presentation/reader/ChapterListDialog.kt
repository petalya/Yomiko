package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.manga.components.MangaChapterListItem
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.reader.chapter.ReaderChapterItem
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import eu.kanade.tachiyomi.util.lang.toRelativeString
import exh.metadata.MetadataUtil
import exh.source.isEhBasedManga
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.model.ChapterSwipeAction
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@Composable
fun ChapterListDialog(
    onDismissRequest: () -> Unit,
    screenModel: ReaderSettingsScreenModel,
    chapters: ImmutableList<ReaderChapterItem>,
    onClickChapter: (Chapter) -> Unit,
    onBookmark: (Chapter) -> Unit,
    dateRelativeTime: Boolean,
) {
    val manga by screenModel.mangaFlow.collectAsState()
    val context = LocalContext.current
    val state = rememberLazyListState(chapters.indexOfFirst { it.isCurrent }.coerceAtLeast(0))
    val downloadManager: DownloadManager = remember { Injekt.get() }
    val downloadQueueState by downloadManager.queueState.collectAsState()
    val downloadProgressMap = remember { mutableStateMapOf<Long, Int>() }

    // Observe download progress
    LaunchedEffect(Unit) {
        downloadManager.progressFlow()
            .collect { download ->
                downloadProgressMap[download.chapter.id] = download.progress
            }
    }

    AdaptiveSheet(
        onDismissRequest = onDismissRequest,
    ) {
        LazyColumn(
            state = state,
            modifier = Modifier.heightIn(min = 200.dp, max = 500.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            items(
                items = chapters,
                key = { "chapter-${it.chapter.id}" },
            ) { chapterItem ->
                val activeDownload = downloadQueueState.find { it.chapter.id == chapterItem.chapter.id }
                val progress = activeDownload?.progress ?: downloadProgressMap[chapterItem.chapter.id] ?: 0
                val downloaded = if (manga?.isLocal() == true) {
                    true
                } else {
                    downloadManager.isChapterDownloaded(
                        chapterItem.chapter.name,
                        chapterItem.chapter.scanlator,
                        chapterItem.manga.ogTitle,
                        chapterItem.manga.source,
                    )
                }
                val downloadState = when {
                    activeDownload != null -> activeDownload.status
                    downloaded -> Download.State.DOWNLOADED
                    else -> Download.State.NOT_DOWNLOADED
                }

                MangaChapterListItem(
                    title = chapterItem.chapter.name,
                    date = chapterItem.chapter.dateUpload
                        .takeIf { it > 0L }
                        ?.let {
                            if (manga?.isEhBasedManga() == true) {
                                MetadataUtil.EX_DATE_FORMAT
                                    .format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()))
                            } else {
                                LocalDate.ofInstant(
                                    Instant.ofEpochMilli(it),
                                    ZoneId.systemDefault(),
                                ).toRelativeString(context, dateRelativeTime, chapterItem.dateFormat)
                            }
                        },
                    readProgress = null,
                    scanlator = chapterItem.chapter.scanlator,
                    sourceName = null,
                    read = chapterItem.chapter.read,
                    bookmark = chapterItem.chapter.bookmark,
                    selected = false,
                    downloadIndicatorEnabled = true,
                    downloadStateProvider = { downloadState },
                    downloadProgressProvider = { progress },
                    chapterSwipeStartAction = ChapterSwipeAction.ToggleBookmark,
                    chapterSwipeEndAction = ChapterSwipeAction.ToggleBookmark,
                    onLongClick = { /*TODO*/ },
                    onClick = { onClickChapter(chapterItem.chapter) },
                    onDownloadClick = { action ->
                        when (action) {
                            ChapterDownloadAction.START -> downloadManager.downloadChapters(chapterItem.manga, listOf(chapterItem.chapter))
                            ChapterDownloadAction.START_NOW -> downloadManager.startDownloadNow(chapterItem.chapter.id)
                            ChapterDownloadAction.CANCEL -> {
                                val queued = downloadQueueState.find { it.chapter.id == chapterItem.chapter.id }
                                if (queued != null) {
                                    downloadManager.cancelQueuedDownloads(listOf(queued))
                                    downloadProgressMap.remove(chapterItem.chapter.id)
                                }
                            }
                            ChapterDownloadAction.DELETE -> {
                                val sourceManager = Injekt.get<SourceManager>()
                                val source = sourceManager.get(chapterItem.manga.source)
                                if (source != null) {
                                    downloadManager.deleteChapters(listOf(chapterItem.chapter), chapterItem.manga, source)
                                    downloadProgressMap.remove(chapterItem.chapter.id)
                                }
                            }
                        }
                    },
                    onChapterSwipe = {
                        onBookmark(chapterItem.chapter)
                    },
                )
            }
        }
    }
}

@Composable
fun BottomChapterActionBar(
    modifier: Modifier = Modifier,
    onBookmark: () -> Unit,
    onMarkAsRead: () -> Unit,
    onMarkPreviousAsRead: () -> Unit,
    onDownload: () -> Unit,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
    contentPadding: Dp = 8.dp,
) {
    Surface(
        tonalElevation = 3.dp,
        color = backgroundColor,
        modifier = modifier.fillMaxWidth().navigationBarsPadding(),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBookmark) {
                Icon(Icons.Outlined.Bookmark, contentDescription = stringResource(MR.strings.action_filter_bookmarked))
            }
            IconButton(onClick = onMarkAsRead) {
                Icon(Icons.Outlined.Done, contentDescription = stringResource(MR.strings.action_mark_as_read))
            }
            IconButton(onClick = onMarkPreviousAsRead) {
                Icon(Icons.Outlined.KeyboardDoubleArrowUp, contentDescription = stringResource(MR.strings.action_mark_previous_as_read))
            }
            IconButton(onClick = onDownload) {
                Icon(Icons.Outlined.Download, contentDescription = stringResource(MR.strings.manga_download))
            }
        }
    }
}
