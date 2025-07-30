package eu.kanade.tachiyomi.ui.reader.novel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.connections.service.ConnectionsPreferences
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.data.connections.discord.ReaderData
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.toChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelReaderViewModel(
    private val novelId: Long,
    private val initialChapterId: Long,
) : ScreenModel {
    private val chapterRepo: ChapterRepository = Injekt.get()
    private val getManga: GetManga = Injekt.get()
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get()
    internal val sourceManager: SourceManager = Injekt.get()
    private val upsertHistory: UpsertHistory = Injekt.get()
    private val getIncognitoState: GetIncognitoState = Injekt.get()
    private val updateChapter: UpdateChapter = Injekt.get()
    private val _state = MutableStateFlow<NovelReaderState>(NovelReaderState.Loading)
    val state: StateFlow<NovelReaderState> = _state.asStateFlow()

    var chapters by mutableStateOf<List<Chapter>>(emptyList())
        private set
    var manga: Manga? = null
        private set

    internal val incognitoMode: Boolean by lazy { getIncognitoState.await(manga?.source, manga?.id) }
    internal var currentChapterIndex: Int = -1
    val currentChapterId: Long?
        get() = chapters.getOrNull(currentChapterIndex)?.id

    // Robust download state tracking

    // Debounce job for saving progress
    private var saveProgressJob: kotlinx.coroutines.Job? = null
    private var lastSavedProgress: Float = -1f

    private var chapterReadStartTime: Long? = null

    fun updateScrollProgressDebounced(progress: Float) {
        val chapterId = currentChapterId ?: return
        if (kotlin.math.abs(progress - lastSavedProgress) < 0.001) return
        lastSavedProgress = progress
        saveProgressJob?.cancel()
        saveProgressJob = CoroutineScope(Dispatchers.IO).launch {
            delay(500) // 0.5s debounce
            val percentInt = (progress * 1000).toLong()
            if (!incognitoMode) {
                chapterRepo.update(tachiyomi.domain.chapter.model.ChapterUpdate(id = chapterId, lastPageRead = percentInt))
            }
            recordHistory(chapterId)
        }
        val current = state.value
        if (current is NovelReaderState.Success) {
            _state.value = current.copy(progress = progress)
        }
    }
    fun getSavedProgress(chapterId: Long): Float {
        val chapter = chapters.find { it.id == chapterId }
        return if (chapter != null && chapter.lastPageRead > 0) chapter.lastPageRead / 1000f else 0f
    }
    fun toggleBookmark(chapter: Chapter) {
        val idx = chapters.indexOfFirst { it.id == chapter.id }
        if (idx == -1) return
        val newValue = !chapters[idx].bookmark
        chapters = chapters.toMutableList().apply {
            this[idx] = chapters[idx].copy(bookmark = newValue)
        }
        if (!incognitoMode) {
            CoroutineScope(Dispatchers.IO).launch {
                chapterRepo.update(tachiyomi.domain.chapter.model.ChapterUpdate(id = chapter.id, bookmark = newValue))
            }
        }
        // Optionally update state if current chapter is affected
        val currentState = state.value
        if (currentState is NovelReaderState.Success && currentState.chapterTitle == chapter.name) {
            _state.value = currentState.copy(bookmarked = newValue)
        }
    }

    fun restartReadTimer() {
        chapterReadStartTime = System.currentTimeMillis()
    }

    fun flushReadTimer() {
        val chapterId = currentChapterId ?: return
        val startTime = chapterReadStartTime ?: return
        val now = System.currentTimeMillis()
        val duration = now - startTime
        recordHistory(chapterId, duration)
        chapterReadStartTime = null
    }

    fun nextChapter() {
        flushReadTimer()
        // For novels (source ID 10001L), we want to go to the next chapter in the sorted list
        // which should be the next higher chapter number
        if (currentChapterIndex < chapters.lastIndex) {
            currentChapterIndex++
            _state.value = NovelReaderState.Loading
            loadCurrentChapter(chapters)
            updateDiscordRPC()
        }
    }

    fun prevChapter() {
        flushReadTimer()
        // For novels (source ID 10001L), we want to go to the previous chapter in the sorted list
        // which should be the previous lower chapter number
        if (currentChapterIndex > 0) {
            currentChapterIndex--
            _state.value = NovelReaderState.Loading
            loadCurrentChapter(chapters)
            updateDiscordRPC()
        }
    }
    fun jumpToChapter(index: Int) {
        flushReadTimer()
        if (index in chapters.indices) {
            currentChapterIndex = index
            _state.value = NovelReaderState.Loading
            loadCurrentChapter(chapters)
            updateDiscordRPC()
        }
    }

    fun markCurrentChapterReadIfNeeded() {
        val idx = currentChapterIndex
        val chapter = chapters.getOrNull(idx) ?: return
        if (!chapter.read) {
            chapters = chapters.toMutableList().apply {
                this[idx] = chapter.copy(read = true)
            }
            if (!incognitoMode) {
                CoroutineScope(Dispatchers.IO).launch {
                    chapterRepo.update(tachiyomi.domain.chapter.model.ChapterUpdate(id = chapter.id, read = true))
                }
            }
        }
    }
    fun toggleRead(chapter: Chapter) {
        val idx = chapters.indexOfFirst { it.id == chapter.id }
        if (idx != -1) {
            val newRead = !chapters[idx].read
            chapters = chapters.toMutableList().apply {
                this[idx] = chapters[idx].copy(read = newRead)
            }
            if (!incognitoMode) {
                CoroutineScope(Dispatchers.IO).launch {
                    chapterRepo.update(tachiyomi.domain.chapter.model.ChapterUpdate(id = chapter.id, read = newRead))
                }
            }
        }
    }
    private fun recordHistory(chapterId: Long, sessionReadDuration: Long = 0L) {
        if (incognitoMode) return
        val chapter = chapters.find { it.id == chapterId } ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val now = java.util.Date()
            updateChapter.await(chapter.toChapterUpdate())
            upsertHistory.await(HistoryUpdate(chapterId, now, sessionReadDuration))
        }
    }
    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                manga = getManga.await(novelId)
                if (manga == null) {
                    _state.value = NovelReaderState.Error("Novel not found")
                    return@launch
                }
                val loadedChapters = getChaptersByMangaId.await(novelId)
                val sortedChapters = if (manga!!.source in 10001L..10100L) {
                    loadedChapters.sortedBy { chapter -> chapter.chapterNumber }
                } else {
                    loadedChapters.sortedWith(getChapterSort(manga!!))
                }
                if (sortedChapters.isEmpty()) {
                    _state.value = NovelReaderState.Error("No chapters found")
                    return@launch
                }
                currentChapterIndex = sortedChapters.indexOfFirst { chapter -> chapter.id == initialChapterId }.takeIf { idx -> idx != -1 } ?: 0
                chapters = sortedChapters
                loadCurrentChapter(sortedChapters)
            } catch (e: Exception) {
                _state.value = NovelReaderState.Error(e.message ?: "Unknown error")
            }
        }
    }
    private fun loadCurrentChapter(chapters: List<Chapter>) {
        restartReadTimer()
        val chapter = chapters.getOrNull(currentChapterIndex)
        val manga = manga
        if (chapter == null || manga == null) {
            _state.value = NovelReaderState.Error("Chapter not found")
            return
        }
        @Suppress("UNUSED_EXPRESSION")
        if (chapter.lastPageRead > 0) chapter.lastPageRead / 1000f else 0f
        CoroutineScope(Dispatchers.IO).launch {
            _state.value = NovelReaderState.Loading
            delay(600) // Reduced delay for skeleton UI
            try {
                val source = sourceManager.get(manga.source) as? CatalogueSource
                if (source == null) {
                    _state.value = NovelReaderState.Error("Source not found")
                    return@launch
                }
                val sChapter = chapter.toSChapter()
                val pages = source.getPageList(sChapter)
                val content = if (pages.size == 1 && isHtmlOrTextContent(pages[0].imageUrl)) {
                    pages[0].imageUrl ?: pages[0].url
                } else {
                    "This chapter is not in a supported novel format."
                }
                val progress = if (chapter.lastPageRead > 0) chapter.lastPageRead / 1000f else 0f
                _state.value = NovelReaderState.Success(
                    novelTitle = manga.title,
                    chapterTitle = chapter.name,
                    content = content,
                    hasNext = currentChapterIndex < chapters.lastIndex,
                    hasPrev = currentChapterIndex > 0,
                    progress = progress,
                    bookmarked = chapter.bookmark,
                )
                updateDiscordRPC()
            } catch (e: Exception) {
                _state.value = NovelReaderState.Error(e.message ?: "Failed to load chapter content")
            }
        }
    }

    private fun isHtmlOrTextContent(content: String?): Boolean {
        if (content == null) return false
        val trimmed = content.trim()
        return trimmed.startsWith("<html", ignoreCase = true) ||
            trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) ||
            (!trimmed.startsWith("http://") && !trimmed.startsWith("https://"))
    }

    private fun updateDiscordRPC() {
        val connectionsPreferences = Injekt.get<ConnectionsPreferences>()
        val chapter = chapters.getOrNull(currentChapterIndex)
        val context = Injekt.get<Context>()
        val chapterNumberFloat = chapter?.chapterNumber?.toFloat() ?: -1f
        CoroutineScope(Dispatchers.IO).launch {
            val latestManga = getManga.await(novelId)
            val mangaCover = latestManga?.asMangaCover()
            val coverUrl = mangaCover?.url
            if (latestManga != null && chapter != null && connectionsPreferences.enableDiscordRPC().get() && coverUrl != null) {
                val incognito = getIncognitoState.await(latestManga.source, latestManga.id)

                if (incognito) {
                    // Show simplified status when incognito mode is enabled
                    DiscordRPCService.setScreen(
                        context = context,
                        discordScreen = DiscordScreen.MANGA,
                        readerData = ReaderData(
                            incognitoMode = true,
                            mangaId = latestManga.id,
                            mangaTitle = context.getString(R.string.novel_incognito_title),
                            thumbnailUrl = coverUrl,
                            chapterNumber = Pair(-1f, -1),
                            chapterTitle = context.getString(R.string.novel_incognito_subtitle),
                        ),
                    )
                } else {
                    DiscordRPCService.setReaderActivity(
                        context = context,
                        readerData = ReaderData(
                            incognitoMode = incognito,
                            mangaId = latestManga.id,
                            mangaTitle = latestManga.title,
                            thumbnailUrl = coverUrl,
                            chapterNumber = Pair(chapterNumberFloat, chapters.size),
                            chapterTitle = if (connectionsPreferences.useChapterTitles().get()) chapter.name else chapter.chapterNumber.toString(),
                        ),
                    )
                }
            }
        }
    }
}
