// SPDX-License-Identifier: Apache-2.0
package eu.kanade.tachiyomi.ui.reader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import eu.kanade.domain.connections.service.ConnectionsPreferences
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.ReaderData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import android.util.Log
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.domain.chapter.model.toSChapter
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import eu.kanade.presentation.manga.components.MangaChapterListItem
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import tachiyomi.domain.library.model.ChapterSwipeAction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatAlignJustify
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderSettingsScreenModel
import eu.kanade.tachiyomi.ui.reader.NovelReaderSettingsBottomSheet
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderPreferences
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import android.widget.Toast
import tachiyomi.domain.manga.model.asMangaCover
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.tachiyomi.source.model.Page
import android.text.Html
import android.widget.TextView
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarTitle
import com.mohamedrejeb.richeditor.ui.material3.RichText
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.ui.reader.chapter.ReaderChapterItem
import java.time.format.DateTimeFormatter
import tachiyomi.domain.chapter.service.getChapterSort
import eu.kanade.tachiyomi.ui.reader.model.getChapterWebUrl
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// --- State ---
sealed class NovelReaderState {
    object Loading : NovelReaderState()
    data class Success(
        val novelTitle: String,
        val chapterTitle: String,
        val content: String,
        val hasNext: Boolean = false,
        val hasPrev: Boolean = false,
        val progress: Float = 0f,
        val bookmarked: Boolean = false
    ) : NovelReaderState()
    data class Error(val message: String) : NovelReaderState()
}

data class NovelReaderChapterItem(
    val chapter: Chapter,
    val manga: Manga,
    val isCurrent: Boolean,
    val downloadState: Download.State,
    val downloadProgress: Int,
)

class NovelReaderViewModel(
    private val novelId: Long,
    private val initialChapterId: Long,
) : ScreenModel {
    private val chapterRepo: ChapterRepository = Injekt.get()
    private val getManga: GetManga = Injekt.get()
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get()
    internal val sourceManager: SourceManager = Injekt.get()
    private val _state = MutableStateFlow<NovelReaderState>(NovelReaderState.Loading)
    val state: StateFlow<NovelReaderState> = _state.asStateFlow()

    var chapters by mutableStateOf<List<Chapter>>(emptyList())
        private set
    var manga: Manga? = null
        private set
    internal var currentChapterIndex: Int = -1
    val currentChapterId: Long?
        get() = chapters.getOrNull(currentChapterIndex)?.id

    // Robust download state tracking
    private val _downloadedChapterIds = MutableStateFlow<Set<Long>>(emptySet())
    val downloadedChapterIds: StateFlow<Set<Long>> = _downloadedChapterIds.asStateFlow()

    // Debounce job for saving progress
    private var saveProgressJob: kotlinx.coroutines.Job? = null
    private var lastSavedProgress: Float = -1f

    fun updateScrollProgressDebounced(progress: Float) {
        val chapterId = currentChapterId ?: return
        if (kotlin.math.abs(progress - lastSavedProgress) < 0.001) return
        lastSavedProgress = progress
        saveProgressJob?.cancel()
        saveProgressJob = CoroutineScope(Dispatchers.IO).launch {
            delay(500) // 0.5s debounce
            val percentInt = (progress * 1000).toLong()
            chapterRepo.update(tachiyomi.domain.chapter.model.ChapterUpdate(id = chapterId, lastPageRead = percentInt))
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
        CoroutineScope(Dispatchers.IO).launch {
            chapterRepo.update(tachiyomi.domain.chapter.model.ChapterUpdate(id = chapter.id, bookmark = newValue))
        }
        // Optionally update state if current chapter is affected
        val currentState = state.value
        if (currentState is NovelReaderState.Success && idx == currentChapterIndex) {
            _state.value = currentState.copy(bookmarked = newValue)
        }
    }
    fun isBookmarked(chapterId: Long): Boolean = chapters.find { it.id == chapterId }?.bookmark == true
    fun nextChapter() {
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
        if (index in chapters.indices) {
            currentChapterIndex = index;
            _state.value = NovelReaderState.Loading
            loadCurrentChapter(chapters)
            updateDiscordRPC()
        }
    }
    fun markChapterDownloaded(chapterId: Long) {
        _downloadedChapterIds.value = _downloadedChapterIds.value + chapterId
    }
    fun unmarkChapterDownloaded(chapterId: Long) {
        _downloadedChapterIds.value = _downloadedChapterIds.value - chapterId
    }
    fun markCurrentChapterReadIfNeeded() {
        val idx = currentChapterIndex
        val chapter = chapters.getOrNull(idx) ?: return
        if (!chapter.read) {
            chapters = chapters.toMutableList().apply {
                this[idx] = chapter.copy(read = true)
            }
            CoroutineScope(Dispatchers.IO).launch {
                chapterRepo.update(tachiyomi.domain.chapter.model.ChapterUpdate(id = chapter.id, read = true))
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
            CoroutineScope(Dispatchers.IO).launch {
                chapterRepo.update(tachiyomi.domain.chapter.model.ChapterUpdate(id = chapter.id, read = newRead))
            }
        }
    }
    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                manga = getManga.await(novelId)
                if (manga == null) { _state.value = NovelReaderState.Error("Novel not found"); return@launch }
                val loadedChapters = getChaptersByMangaId.await(novelId)
                val sortedChapters = if (manga!!.source == 10001L) {
                    loadedChapters.sortedBy { chapter -> chapter.chapterNumber }
                } else {
                    loadedChapters.sortedWith(getChapterSort(manga!!))
                }
                if (sortedChapters.isEmpty()) { _state.value = NovelReaderState.Error("No chapters found"); return@launch }
                currentChapterIndex = sortedChapters.indexOfFirst { chapter -> chapter.id == initialChapterId }.takeIf { idx -> idx != -1 } ?: 0
                chapters = sortedChapters
                loadCurrentChapter(sortedChapters)
            } catch (e: Exception) { _state.value = NovelReaderState.Error(e.message ?: "Unknown error") }
        }
    }
    internal fun loadCurrentChapter(chapters: List<Chapter>) {
        val chapter = chapters.getOrNull(currentChapterIndex)
        val manga = manga
        if (chapter == null || manga == null) { _state.value = NovelReaderState.Error("Chapter not found"); return }
        val savedProgress = if (chapter.lastPageRead > 0) chapter.lastPageRead / 1000f else 0f
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
                    pages[0].imageUrl ?: pages[0].url ?: "No content found."
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
            } catch (e: Exception) { _state.value = NovelReaderState.Error(e.message ?: "Failed to load chapter content") }
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
                DiscordRPCService.setReaderActivity(
                    context = context,
                    readerData = ReaderData(
                        incognitoMode = latestManga.incognitoMode,
                        mangaId = latestManga.id,
                        mangaTitle = latestManga.title,
                        thumbnailUrl = coverUrl,
                        chapterNumber = Pair(chapterNumberFloat, chapters.size),
                        chapterTitle = if (connectionsPreferences.useChapterTitles().get()) chapter.name else chapter.chapterNumber.toString()
                    )
                )
            }
        }
    }
}

@Composable
fun SetSystemBarsColor(navigationBarColor: Color) {
    val context = LocalContext.current as? ComponentActivity ?: return
    val isDarkMode = isSystemInDarkTheme()
    DisposableEffect(isDarkMode, navigationBarColor) {
        context.window.navigationBarColor = navigationBarColor.toArgb()
        onDispose { }
    }
}

class NovelReaderScreen(
    val novelId: Long,
    val chapterId: Long,
) : Screen {
    @Composable
    override fun Content() {
        val viewModel = rememberScreenModel { NovelReaderViewModel(novelId, chapterId) }
        val state by viewModel.state.collectAsState()
        val chapters = viewModel.chapters
        val context = LocalContext.current
        val view = LocalView.current
        val navigator = LocalNavigator.current
        var showSettings by remember { mutableStateOf(false) }
        var showChapterList by remember { mutableStateOf(false) }
        val scrollState = rememberScrollState()
        // Download manager for chapter downloads
        val downloadManager = remember { Injekt.get<DownloadManager>() }
        val downloadQueue by downloadManager.queueState.collectAsState()
        val coroutineScope = rememberCoroutineScope()
        val settingsModel = rememberScreenModel { NovelReaderSettingsScreenModel() }
        val fontSize by settingsModel.fontSize.collectAsState()
        val textAlignment by settingsModel.textAlignment.collectAsState()
        val lineSpacing by settingsModel.lineSpacing.collectAsState()
        val presetColorScheme by settingsModel.colorScheme.collectAsState()
        val fontFamilyPref by settingsModel.fontFamily.collectAsState()
        val fontFamily = when (fontFamilyPref) {
            NovelReaderPreferences.FontFamilyPref.ORIGINAL -> null
            NovelReaderPreferences.FontFamilyPref.LORA -> FontFamily(Font(R.font.lora))
            NovelReaderPreferences.FontFamilyPref.OPEN_SANS -> FontFamily(Font(R.font.open_sans))
            NovelReaderPreferences.FontFamilyPref.ARBUTUS_SLAB -> FontFamily(Font(R.font.arbutus_slab))
            NovelReaderPreferences.FontFamilyPref.LATO -> FontFamily(Font(R.font.lato))
            else -> null
        }
        // Collapsible bars state
        var barsVisible by remember { mutableStateOf(true) }
        // Hide-on-scroll state
        var lastScrollOffset by remember { mutableStateOf(0) }
        var accumulatedScroll by remember { mutableStateOf(0) }
        val hideThresholdPx = with(LocalDensity.current) { 24.dp.roundToPx() }
        LaunchedEffect(scrollState.value) {
            val delta = scrollState.value - lastScrollOffset
            accumulatedScroll += delta
            if (delta > 0 && accumulatedScroll > hideThresholdPx) {
                // Scrolling down, hide bars
                if (barsVisible) barsVisible = false
                accumulatedScroll = 0
            } else if (delta < 0 && kotlin.math.abs(accumulatedScroll) > hideThresholdPx) {
                // Scrolling up, show bars
                if (!barsVisible) barsVisible = true
                accumulatedScroll = 0
            } else if (scrollState.value <= hideThresholdPx) {
                // Near top, always show bars
                if (!barsVisible) barsVisible = true
                accumulatedScroll = 0
            }
            lastScrollOffset = scrollState.value
        }
        // Chapter list bottom sheet state
        var showChapterListSheet by remember { mutableStateOf(false) }
        var showSettingsSheet by remember { mutableStateOf(false) }
        val chapterListSheetState = rememberModalBottomSheetState()
        val settingsSheetState = rememberModalBottomSheetState()
        // Reset scroll position when chapter changes
        LaunchedEffect(viewModel.currentChapterId) {
            // Always start from top when chapter changes
            scrollState.scrollTo(0)
        }
        // Save scroll progress as percent as the user scrolls
        LaunchedEffect(scrollState.value, viewModel.currentChapterId) {
            val max = scrollState.maxValue
            if (max > 0 && viewModel.currentChapterId != null) {
                val percent = scrollState.value.toFloat() / max
                viewModel.updateScrollProgressDebounced(percent)
                // Automatically mark as read if progress >= 98%
                if (percent >= 0.98f) {
                    viewModel.markCurrentChapterReadIfNeeded()
                }
            }
        }
        // Immersive mode: hide/show system bars with reader bars
        LaunchedEffect(barsVisible) {
            val activity = context as? Activity ?: return@LaunchedEffect
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val controller = activity.window.insetsController
                if (barsVisible) {
                    controller?.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                } else {
                    controller?.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                    controller?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                val flags = if (barsVisible) {
                    View.SYSTEM_UI_FLAG_VISIBLE
                } else {
                    (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
                }
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = flags
            }
        }
        // Always restore system bars when leaving the reader
        DisposableEffect(Unit) {
            onDispose {
                val activity = context as? Activity ?: return@onDispose
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val controller = activity.window.insetsController
                    controller?.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                } else {
                    @Suppress("DEPRECATION")
                    activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                }
            }
        }
        SetSystemBarsColor(MaterialTheme.colorScheme.surface)
        Surface(modifier = Modifier.fillMaxSize(), color = presetColorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Chapter content (tap to toggle bars)
                when (state) {
                    is NovelReaderState.Loading -> Box(
                        Modifier.fillMaxSize().background(presetColorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        ShimmerSkeletonLoader(lineCount = 24)
                    }

                    is NovelReaderState.Error -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { Text((state as NovelReaderState.Error).message) }

                    is NovelReaderState.Success -> {
                        val s = state as NovelReaderState.Success
                        val richTextState = rememberRichTextState()
                        val htmlAlign = when (textAlignment) {
                            NovelReaderPreferences.TextAlignment.Left -> "left"
                            NovelReaderPreferences.TextAlignment.Center -> "center"
                            NovelReaderPreferences.TextAlignment.Right -> "right"
                            NovelReaderPreferences.TextAlignment.Justify -> "justify"
                            else -> "left"
                        }
                        val cleanedHtml = s.content
                            .replace(Regex("text-align\\s*:\\s*[^;\"']*;?", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("align\\s*=\\s*\"[^\"]*\"", RegexOption.IGNORE_CASE), "")
                        val alignedHtml = "<div style=\"text-align:$htmlAlign;\">$cleanedHtml</div>"

                        var contentReady by remember { mutableStateOf(false) }
                        var currentChapterId by remember { mutableStateOf(viewModel.currentChapterId) }
                        val savedProgress = viewModel.getSavedProgress(viewModel.currentChapterId ?: 0)

                        // Handle content loading and scroll position restoration
                        LaunchedEffect(s.content, textAlignment, viewModel.currentChapterId) {
                            val chapterChanged = currentChapterId != viewModel.currentChapterId
                            currentChapterId = viewModel.currentChapterId
                            
                            // Reset content ready state when chapter changes
                            if (chapterChanged) {
                                contentReady = false
                            }
                            
                            // Set HTML content
                            richTextState.setHtml(alignedHtml)
                            contentReady = true
                            
                            // After content is loaded, restore scroll position
                            // Only restore scroll if we have saved progress and either:
                            // 1. This is the initial load (savedProgress > 0)
                            // 2. We're changing chapters (chapterChanged)
                            if (savedProgress > 0) {
                                // Wait for layout to be complete
                                delay(50)
                                
                                // Calculate target position based on saved progress
                                val targetPosition = (scrollState.maxValue * savedProgress).toInt()
                                if (targetPosition > 0) {
                                    // Scroll to position
                                    scrollState.scrollTo(targetPosition)
                                    
                                    // Smooth scroll to ensure we're at the exact position
                                    delay(50)
                                    scrollState.animateScrollTo(targetPosition)
                                }
                            }
                        }

                        @OptIn(ExperimentalAnimationApi::class)
                        AnimatedContent(
                            targetState = contentReady,
                            transitionSpec = { fadeIn() togetherWith fadeOut() }
                        ) { ready ->
                            if (!ready) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    ShimmerSkeletonLoader(lineCount = 24)
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
                                        .padding(
                                            top = 115.dp, 
                                            start = 16.dp,
                                            end = 16.dp,
                                            bottom = 16.dp
                                        )
                                        .pointerInput(barsVisible) {
                                            detectTapGestures(onTap = { barsVisible = !barsVisible })
                                        }
                                ) {
                                    // Compose-native HTML rendering
                                    val resolvedTextAlign = when (textAlignment) {
                                        NovelReaderPreferences.TextAlignment.Left -> TextAlign.Left
                                        NovelReaderPreferences.TextAlignment.Center -> TextAlign.Center
                                        NovelReaderPreferences.TextAlignment.Right -> TextAlign.Right
                                        NovelReaderPreferences.TextAlignment.Justify -> TextAlign.Justify
                                        else -> TextAlign.Left // fallback for safety
                                    }
                                    val resolvedLineHeight = (lineSpacing / 100f) * fontSize.sp.value
                                    CompositionLocalProvider(LocalContentColor provides presetColorScheme.text) {
                                        RichText(
                                            modifier = Modifier.fillMaxWidth(),
                                            state = richTextState,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = fontSize.sp,
                                                fontFamily = fontFamily,
                                                textAlign = resolvedTextAlign,
                                                lineHeight = resolvedLineHeight.sp
                                            )
                                        )
                                    }
                                    // Footer at the end of the chapter
                                    Spacer(modifier = Modifier.height(32.dp))
                                    val currentChapterNumber = viewModel.currentChapterIndex + 1
                                    val currentChapterTitle = s.chapterTitle
                                    val hasNext = s.hasNext
                                    val nextChapter = chapters.getOrNull(viewModel.currentChapterIndex + 1)
                                    val nextChapterTitle = nextChapter?.name
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Finished: $currentChapterTitle",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(bottom = 16.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (hasNext && nextChapterTitle != null) {
                                            androidx.compose.material3.Card(
                                                shape = MaterialTheme.shapes.large,
                                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth(0.85f)
                                                    .height(56.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clickable {
                                                            contentReady = false
                                                            viewModel.nextChapter()
                                                            // Reset scroll to top after loading next chapter
                                                            coroutineScope.launch { scrollState.scrollTo(0) }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        "Next: $nextChapterTitle",
                                                        style = MaterialTheme.typography.titleMedium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Top bar overlays content, does NOT push it down
                androidx.compose.animation.AnimatedVisibility(
                    visible = barsVisible && (state !is NovelReaderState.Loading),
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    NovelReaderTopBar(
                        title = (state as? NovelReaderState.Success)?.novelTitle ?: "Novel",
                        chapterTitle = (state as? NovelReaderState.Success)?.chapterTitle ?: "Chapter",
                        bookmarked = (state as? NovelReaderState.Success)?.bookmarked ?: false,
                        onBack = { navigator?.pop() },
                        onBookmark = {
                            val chapter = chapters.getOrNull(viewModel.currentChapterIndex)
                            if (chapter != null) {
                                viewModel.toggleBookmark(chapter)
                            }
                        },
                    )
                }
                // Progress bar above bottom bar, hides/shows with barsVisible or while interacting
                var sliderInUse by remember { mutableStateOf(false) }
                androidx.compose.animation.AnimatedVisibility(
                    visible = (barsVisible || sliderInUse) && (state !is NovelReaderState.Loading),
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    val coroutineScope = rememberCoroutineScope()
                    val maxScroll = scrollState.maxValue
                    var sliderProgress by remember { mutableStateOf(if (maxScroll > 0) scrollState.value.toFloat() / maxScroll else 0f) }
                    // Sync slider with scroll unless dragging
                    LaunchedEffect(scrollState.value, maxScroll, barsVisible) {
                        if (!sliderInUse) {
                            sliderProgress = if (maxScroll > 0) scrollState.value.toFloat() / maxScroll else 0f
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 90.dp, start = 16.dp, end = 16.dp, top = 8.dp), // above bottom bar
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val percent = (sliderProgress * 100).toInt()
                        Text("$percent%", modifier = Modifier.padding(end = 8.dp))
                        Slider(
                            value = sliderProgress,
                            onValueChange = { newProgress ->
                                sliderInUse = true
                                sliderProgress = newProgress
                                if (maxScroll > 0) {
                                    val target = (sliderProgress * maxScroll).toInt()
                                    coroutineScope.launch { scrollState.scrollTo(target) }
                                }
                            },
                            onValueChangeFinished = {
                                sliderInUse = false
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f)
                        )
                        Text("100%", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                // Bottom bar anchored to the bottom, hides/shows with barsVisible
                androidx.compose.animation.AnimatedVisibility(
                    visible = barsVisible && (state !is NovelReaderState.Loading),
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous chapter button
                        val hasPrevChapter = viewModel.currentChapterIndex > 0
                        IconButton(
                            onClick = { viewModel.prevChapter() },
                            enabled = hasPrevChapter,
                            modifier = Modifier.graphicsLayer { alpha = if (hasPrevChapter) 1f else 0.5f }
                        ) {
                            Icon(
                                Icons.Filled.SkipPrevious, 
                                contentDescription = "Previous chapter",
                                tint = if (hasPrevChapter) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        IconButton(onClick = { showChapterListSheet = true }) {
                            Icon(Icons.Filled.FormatListNumbered, contentDescription = "Chapter list")
                        }
                        IconButton(
                            onClick = {
                                val chapter = chapters.getOrNull(viewModel.currentChapterIndex)
                                val manga = viewModel.manga
                                if (chapter != null && manga != null) {
                                    try {
                                        // Get the source and construct the chapter URL
                                        val source = viewModel.sourceManager.getOrStub(manga.source)
                                        if (source == null) {
                                            context.toast("Source not found")
                                            return@IconButton
                                        }
                                        
                                        // For online sources, use the chapter URL directly
                                        val chapterUrl = getChapterWebUrl(manga, chapter, source)
                                        if (chapterUrl.isNullOrBlank()) {
                                            context.toast("No URL available for this chapter")
                                            return@IconButton
                                        }
                                        
                                        // Use the in-app WebViewActivity
                                        val intent = WebViewActivity.newIntent(
                                            context,
                                            chapterUrl,
                                            manga.source,
                                            manga.title
                                        )
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Log.e("NovelReader", "Failed to open chapter URL", e)
                                        context.toast("Could not open chapter")
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Public, contentDescription = "Open in WebView")
                        }
                        IconButton(
                            onClick = {
                                // Scroll to top
                                coroutineScope.launch { scrollState.animateScrollTo(0) }
                            }
                        ) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Scroll to top")
                        }
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                        // Next chapter button
                        val hasNextChapter = viewModel.currentChapterIndex < chapters.lastIndex
                        IconButton(
                            onClick = { viewModel.nextChapter() },
                            enabled = hasNextChapter,
                            modifier = Modifier.graphicsLayer { alpha = if (hasNextChapter) 1f else 0.5f }
                        ) {
                            Icon(
                                Icons.Filled.SkipNext, 
                                contentDescription = "Next chapter",
                                tint = if (hasNextChapter) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                // Chapter list bottom sheet
                if (showChapterListSheet) {
                    val manga = viewModel.manga
                    val context = LocalContext.current
                    val downloadManager: DownloadManager = Injekt.get()
                    val downloadQueue by downloadManager.queueState.collectAsState()
                    val downloadProgressMap = remember { mutableStateMapOf<Long, Int>() }
                    
                    // Collect download progress
                    LaunchedEffect(Unit) {
                        downloadManager.progressFlow().collect { download ->
                            downloadProgressMap[download.chapter.id] = download.progress
                        }
                    }
                    
                    // Create a derived state for the current download states
                    val downloadStates by remember(downloadQueue) {
                        derivedStateOf {
                            downloadQueue.associate { download ->
                                download.chapter.id to (download.status to (download.progress ?: 0))
                            }
                        }
                    }

                    if (manga != null) {
                        // Build ReaderChapterItem list
                        val currentChapterId = viewModel.currentChapterId
                        
                        // Create a list of chapter items that will update when any dependency changes
                        val chapterItems = remember(
                            chapters,
                            currentChapterId,
                            downloadQueue,
                            downloadProgressMap
                        ) {
                            chapters.map { chapter ->
                                val isCurrent = chapter.id == currentChapterId
                                val activeDownload = downloadQueue.find { it.chapter.id == chapter.id }
                                val progress = activeDownload?.progress ?: downloadProgressMap[chapter.id] ?: 0
                                val downloaded = downloadManager.isChapterDownloaded(
                                    chapter.name,
                                    chapter.scanlator,
                                    manga.ogTitle,
                                    manga.source,
                                )
                                val downloadState = when {
                                    activeDownload != null -> activeDownload.status
                                    downloaded -> Download.State.DOWNLOADED
                                    else -> Download.State.NOT_DOWNLOADED
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

                        AdaptiveSheet(
                            onDismissRequest = { showChapterListSheet = false },
                        ) {
                            val state =
                                rememberLazyListState(chapterItems.indexOfFirst { it.isCurrent }.coerceAtLeast(0))
                            LazyColumn(
                                state = state,
                                modifier = Modifier.heightIn(min = 200.dp, max = 500.dp),
                                contentPadding = PaddingValues(vertical = 16.dp),
                            ) {
                                items(
                                    items = chapterItems,
                                    key = { "chapter-${it.chapter.id}" },
                                ) { chapterItem ->
                                    val progress = downloadProgressMap[chapterItem.chapter.id] ?: 0

// Get the current download state for this chapter
                                    val (downloadState, downloadProgress) = remember(
                                        chapterItem.chapter.id,
                                        downloadStates[chapterItem.chapter.id],
                                        downloadProgressMap[chapterItem.chapter.id]
                                    ) {
                                        val state = downloadStates[chapterItem.chapter.id]
                                        val progress = downloadProgressMap[chapterItem.chapter.id] ?: 0
                                        val isDownloaded = downloadManager.isChapterDownloaded(
                                            chapterItem.chapter.name,
                                            chapterItem.chapter.scanlator,
                                            manga.ogTitle,
                                            manga.source,
                                        )
                                        
                                        when {
                                            state != null -> state.first to state.second
                                            isDownloaded -> Download.State.DOWNLOADED to 0
                                            else -> Download.State.NOT_DOWNLOADED to 0
                                        }
                                    }

                                    MangaChapterListItem(
                                        title = chapterItem.chapter.name,
                                        date = null, // Add date formatting if needed
                                        readProgress = viewModel.getSavedProgress(chapterItem.chapter.id)
                                            .let { percent ->
                                                val pct = (percent * 100).toInt().coerceIn(0, 100)
                                                if (pct > 0) "Progress ${pct}%" else null
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
                                            viewModel.jumpToChapter(chapters.indexOf(chapterItem.chapter))
                                            showChapterListSheet = false
                                        },
                                        onDownloadClick = { action ->
                                            when (action) {
                                                ChapterDownloadAction.START -> downloadManager.downloadChapters(
                                                    chapterItem.manga,
                                                    listOf(chapterItem.chapter)
                                                )

                                                ChapterDownloadAction.START_NOW -> downloadManager.startDownloadNow(
                                                    chapterItem.chapter.id
                                                )

                                                ChapterDownloadAction.CANCEL -> {
                                                    val queued =
                                                        downloadQueue.find { it.chapter.id == chapterItem.chapter.id }
                                                    if (queued != null) {
                                                        downloadManager.cancelQueuedDownloads(listOf(queued))
                                                        downloadProgressMap.remove(chapterItem.chapter.id)
                                                    }
                                                }

                                                ChapterDownloadAction.DELETE -> {
                                                    val source = viewModel.sourceManager.get(chapterItem.manga.source)
                                                    if (source != null) {
                                                        downloadManager.deleteChapters(
                                                            listOf(chapterItem.chapter),
                                                            chapterItem.manga,
                                                            source
                                                        )
                                                        downloadProgressMap.remove(chapterItem.chapter.id)
                                                    }
                                                }

                                                else -> { /* no-op for exhaustiveness */
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
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Loading state handling
            val isLoading = state is NovelReaderState.Loading
            
            // Always hide bars when loading
            LaunchedEffect(isLoading) {
                if (isLoading) barsVisible = false
                else barsVisible = true
            }
            
            // Hide system UI during loading, restore after
            LaunchedEffect(isLoading) {
                val activity = context as? Activity ?: return@LaunchedEffect
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val controller = activity.window.insetsController
                    if (isLoading) {
                        controller?.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                    } else {
                        controller?.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                        controller?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    if (isLoading) {
                        activity.window.decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        )
                    } else {
                        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                    }
                }
            }
            
            // Observe download queue and update downloadedChapterIds in ViewModel
            LaunchedEffect(downloadQueue) {
                downloadQueue.forEach { download ->
                    if (download.status == Download.State.DOWNLOADED) {
                        viewModel.markChapterDownloaded(download.chapter.id)
                    }
                }
            }
        }
        
        // Settings bottom sheet (outside of main Box)
        if (showSettingsSheet) {
            NovelReaderSettingsBottomSheet(
                model = settingsModel,
                onDismiss = { showSettingsSheet = false }
            )
        }
    }

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
                        contentDescription = "Bookmark"
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
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ) {
            NavigationBarItem(
                selected = false,
                onClick = onPrev,
                icon = { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous") },
                enabled = hasPrev
            )
            NavigationBarItem(
                selected = false,
                onClick = onChapterList,
                icon = { Icon(Icons.Filled.MoreVert, contentDescription = "Chapters") },
                enabled = true
            )
            NavigationBarItem(
                selected = false,
                onClick = onNext,
                icon = { Icon(Icons.Filled.SkipNext, contentDescription = "Next") },
                enabled = hasNext
            )
            Slider(
                value = progress,
                onValueChange = onSliderChange,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
        }
    }

    @Composable
    fun ShimmerSkeletonLoader(
        modifier: Modifier = Modifier,
        lineCount: Int = 24,
        lineHeight: Dp = 18.dp,
        lineSpacing: Dp = 18.dp,
        topPadding: Dp = 64.dp,
        cornerRadius: Dp = 8.dp,
        baseColor: Color = MaterialTheme.colorScheme.surfaceVariant,
        highlightColor: Color = MaterialTheme.colorScheme.surface,
    ) {
        val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
        val shimmerTranslate by shimmerTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmerTranslate"
        )
        val brush = Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset.Zero,
            end = Offset(x = 600f * shimmerTranslate + 1f, y = 0f)
        )
        val widthFractions = listOf(0.9f, 0.8f, 0.7f, 0.6f)
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = topPadding),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start // align left
        ) {
            repeat(lineCount) {
                val widthFraction = widthFractions[it % widthFractions.size]
                Box(
                    modifier = Modifier
                        .fillMaxWidth(widthFraction)
                        .height(lineHeight)
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(brush)
                )
                // Add extra space after every 4th line for paragraph effect
                if ((it + 1) % 4 == 0) {
                    Spacer(modifier = Modifier.height(lineSpacing * 1.5f))
                } else {
                    Spacer(modifier = Modifier.height(lineSpacing))
                }
            }
        }
    }
}
