package eu.kanade.tachiyomi.ui.reader.novel

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.manga.components.MangaChapterListItem
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.reader.chapter.ReaderChapterItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.library.model.ChapterSwipeAction
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.format.DateTimeFormatter

@Composable
fun NovelChapterListSheet(
    viewModel: NovelReaderViewModel,
    show: Boolean,
    onDismiss: () -> Unit,
) {
    if (!show) return

    val manga = viewModel.manga
    val downloadManager: DownloadManager = Injekt.get()
    val downloadQueue by downloadManager.queueState.collectAsState()
    val isDownloaderRunning by downloadManager.isDownloaderRunning.collectAsState(initial = false)
    var statusVersion by remember { mutableIntStateOf(0) }
    val downloadProgressMap = remember { mutableStateMapOf<Long, Int>() }
    val downloadedCache = remember { mutableStateMapOf<Long, Boolean>() }

    // Collect download progress
    LaunchedEffect(Unit) {
        downloadManager.progressFlow().collect { download ->
            downloadProgressMap[download.chapter.id] = download.progress
        }
    }
    // Collect download status to immediately reflect completion
    LaunchedEffect(Unit) {
        downloadManager.statusFlow().collect { download ->
            if (download.status == Download.State.DOWNLOADED) {
                downloadedCache[download.chapter.id] = true
                downloadProgressMap[download.chapter.id] = 100
            }
            statusVersion++
        }
    }

    // Derive current download states
    val downloadStates by remember(downloadQueue, statusVersion) {
        derivedStateOf {
            downloadQueue.associate { it.chapter.id to (it.status to it.progress) }
        }
    }

    if (manga != null) {
        val currentChapterId = viewModel.currentChapterId
        val filteredChapters = viewModel.getFilteredChapters()

        // Build items with merged download state/progress
        val chapterItems = remember(
            filteredChapters,
            currentChapterId,
            downloadQueue,
            downloadProgressMap,
        ) {
            filteredChapters.map { chapter ->
                val isCurrent = chapter.id == currentChapterId
                val state = downloadStates[chapter.id]
                val queueStatus = state?.first
                val queueProgress = state?.second ?: 0
                val observedProgress = downloadProgressMap[chapter.id] ?: 0
                val mergedProgress = kotlin.math.max(observedProgress, queueProgress)
                val downloaded = downloadedCache[chapter.id] ?: downloadManager.isChapterDownloaded(
                    chapter.name,
                    chapter.scanlator,
                    manga.ogTitle,
                    manga.source,
                ).also { downloadedCache[chapter.id] = it }
                val (downloadState, progress) = when {
                    queueStatus == Download.State.ERROR ->
                        Download.State.ERROR to 0
                    !isDownloaderRunning && queueStatus == Download.State.QUEUE ->
                        Download.State.ERROR to 0
                    queueStatus == Download.State.DOWNLOADED || mergedProgress >= 100 || downloaded ->
                        Download.State.DOWNLOADED to 100
                    // Only show ring when actually queued or downloading
                    queueStatus == Download.State.QUEUE || queueStatus == Download.State.DOWNLOADING ->
                        Download.State.DOWNLOADING to mergedProgress
                    else -> Download.State.NOT_DOWNLOADED to 0
                }

                ReaderChapterItem(
                    chapter = chapter,
                    manga = manga,
                    isCurrent = isCurrent,
                    dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                    downloadState = downloadState,
                    downloadProgress = progress,
                )
            }
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
                ) { chapterItem ->
                    // Force recomposition when these change
                    downloadProgressMap[chapterItem.chapter.id] ?: 0

                    val (downloadState, downloadProgress) = run {
                        val state = downloadStates[chapterItem.chapter.id]
                        val queueStatus = state?.first
                        val queueProgress = state?.second ?: 0
                        val observedProgress = downloadProgressMap[chapterItem.chapter.id] ?: 0
                        val mergedProgress = kotlin.math.max(observedProgress, queueProgress)
                        val isDownloaded = downloadedCache[chapterItem.chapter.id]
                            ?: downloadManager.isChapterDownloaded(
                                chapterItem.chapter.name,
                                chapterItem.chapter.scanlator,
                                chapterItem.manga.ogTitle,
                                chapterItem.manga.source,
                            ).also { downloadedCache[chapterItem.chapter.id] = it }

                        when {
                            queueStatus == Download.State.ERROR ->
                                Download.State.ERROR to 0
                            !isDownloaderRunning && queueStatus == Download.State.QUEUE ->
                                Download.State.ERROR to 0
                            queueStatus == Download.State.DOWNLOADED || mergedProgress >= 100 || isDownloaded ->
                                Download.State.DOWNLOADED to 100
                            // Only show ring when actually queued or downloading
                            queueStatus == Download.State.QUEUE || queueStatus == Download.State.DOWNLOADING ->
                                Download.State.DOWNLOADING to mergedProgress
                            else -> Download.State.NOT_DOWNLOADED to 0
                        }
                    }

                    MangaChapterListItem(
                        title = chapterItem.chapter.name,
                        date = null,
                        readProgress = viewModel.getSavedProgress(chapterItem.chapter.id)
                            .let { percent ->
                                val pct = (percent * 100).toInt().coerceIn(0, 100)
                                if (pct > 0) "Progress $pct%" else null
                            },
                        scanlator = chapterItem.chapter.scanlator,
                        sourceName = null,
                        read = chapterItem.chapter.read,
                        bookmark = chapterItem.chapter.bookmark,
                        selected = chapterItem.isCurrent,
                        downloadIndicatorEnabled = true,
                        downloadStateProvider = { downloadState },
                        downloadProgressProvider = { downloadProgress },
                        chapterSwipeStartAction = ChapterSwipeAction.ToggleRead,
                        chapterSwipeEndAction = ChapterSwipeAction.ToggleBookmark,
                        onLongClick = {},
                        onClick = {
                            viewModel.jumpToChapterId(chapterItem.chapter.id)
                            onDismiss()
                        },
                        onDownloadClick = { action ->
                            when (action) {
                                ChapterDownloadAction.START -> downloadManager.downloadChapters(
                                    chapterItem.manga,
                                    listOf(chapterItem.chapter),
                                )

                                ChapterDownloadAction.START_NOW -> downloadManager.startDownloadNow(
                                    chapterItem.chapter.id,
                                )

                                ChapterDownloadAction.CANCEL -> {
                                    val queued =
                                        downloadQueue.find { it.chapter.id == chapterItem.chapter.id }
                                    if (queued != null) {
                                        downloadManager.cancelQueuedDownloads(listOf(queued))
                                        downloadProgressMap.remove(chapterItem.chapter.id)
                                        downloadedCache.remove(chapterItem.chapter.id)
                                    }
                                }

                                ChapterDownloadAction.DELETE -> {
                                    val source = viewModel.sourceManager.get(chapterItem.manga.source)
                                    if (source != null) {
                                        downloadManager.deleteChapters(
                                            listOf(chapterItem.chapter),
                                            chapterItem.manga,
                                            source,
                                        )
                                        // Immediately reflect deletion in UI
                                        downloadProgressMap[chapterItem.chapter.id] = 0
                                        downloadedCache[chapterItem.chapter.id] = false
                                        // Re-check after a brief delay to catch async FS changes
                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                            delay(400)
                                            val stillDownloaded = downloadManager.isChapterDownloaded(
                                                chapterItem.chapter.name,
                                                chapterItem.chapter.scanlator,
                                                chapterItem.manga.ogTitle,
                                                chapterItem.manga.source,
                                                skipCache = true,
                                            )
                                            downloadedCache[chapterItem.chapter.id] = stillDownloaded
                                            if (!stillDownloaded) downloadProgressMap[chapterItem.chapter.id] = 0
                                        }
                                    }
                                }
                            }
                        },
                        onChapterSwipe = { action ->
                            when (action) {
                                ChapterSwipeAction.ToggleRead -> {
                                    viewModel.toggleRead(chapterItem.chapter)
                                }

                                ChapterSwipeAction.ToggleBookmark -> {
                                    viewModel.toggleBookmark(chapterItem.chapter)
                                }

                                else -> {}
                            }
                        },
                    )
                }
            }
        }
    }
}
