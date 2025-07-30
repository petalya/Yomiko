// SPDX-License-Identifier: Apache-2.0
package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Activity
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.manga.components.MangaChapterListItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.reader.chapter.ReaderChapterItem
import eu.kanade.tachiyomi.ui.reader.model.getChapterWebUrl
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderSettingsScreenModel
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.library.model.ChapterSwipeAction
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.format.DateTimeFormatter

@Suppress("NAME_SHADOWING")
class NovelReaderScreen(
    private val novelId: Long,
    val chapterId: Long,
) : Screen {
    @Composable
    override fun Content() {
        val viewModel = rememberScreenModel { NovelReaderViewModel(novelId, chapterId) }
        val state by viewModel.state.collectAsState()
        val chapters = viewModel.chapters
        val context = LocalContext.current
        LocalView.current
        val window = remember { (context as? Activity)?.window }
        val navBarColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f).toArgb()
        // Save previous navigation bar color
        val prevNavBarColor = remember { window?.navigationBarColor }
        // Set navigation bar color to match EPUB reader style
        LaunchedEffect(Unit) {
            window?.navigationBarColor = navBarColor
        }
        val navigator = LocalNavigator.current

        // Restore system bars and behavior on exit (match EPUB reader)
        DisposableEffect(Unit) {
            onDispose {
                // Restore previous Discord RPC screen
                CoroutineScope(Dispatchers.IO).launch {
                    DiscordRPCService.setScreen(context, DiscordRPCService.lastUsedScreen)
                }

                // Restore previous navigation bar color
                window?.navigationBarColor = prevNavBarColor ?: window?.navigationBarColor ?: 0
                window?.let { win ->
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        win.insetsController?.let { controller ->
                            controller.show(
                                android.view.WindowInsets.Type.statusBars() or
                                    android.view.WindowInsets.Type.navigationBars(),
                            )
                            controller.systemBarsBehavior =
                                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        win.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                    }
                }
            }
        }
        val scrollState = rememberScrollState()
        // Download manager for chapter downloads
        remember { Injekt.get<DownloadManager>() }
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
        var lastScrollOffset by remember { mutableIntStateOf(0) }
        var accumulatedScroll by remember { mutableIntStateOf(0) }
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
        rememberModalBottomSheetState()
        rememberModalBottomSheetState()
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
        // Handle system UI bars based on loading state and barsVisible state
        LaunchedEffect(barsVisible, state is NovelReaderState.Loading) {
            val activity = context as? Activity ?: return@LaunchedEffect
            val isLoading = state is NovelReaderState.Loading
            val window = activity.window

            // Set up window insets controller behavior
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                window.insetsController?.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            // Determine if we should show system bars
            val shouldShowBars = !isLoading && barsVisible

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val controller = window.insetsController
                if (shouldShowBars) {
                    controller?.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                } else {
                    controller?.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (shouldShowBars) {
                    View.SYSTEM_UI_FLAG_VISIBLE
                } else {
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                }
            }
        }
        Surface(modifier = Modifier.fillMaxSize(), color = presetColorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Chapter content (tap to toggle bars)
                when (state) {
                    is NovelReaderState.Loading -> Box(
                        Modifier.fillMaxSize().background(presetColorScheme.background),
                        contentAlignment = Alignment.Center,
                    ) {
                        EpubShimmerSkeletonLoader(lineCount = 24)
                    }

                    is NovelReaderState.Error -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { Text((state as NovelReaderState.Error).message) }

                    is NovelReaderState.Success -> {
                        val s = state as NovelReaderState.Success
                        val richTextState = rememberRichTextState()
                        val htmlAlign = when (textAlignment) {
                            NovelReaderPreferences.TextAlignment.Left -> "left"
                            NovelReaderPreferences.TextAlignment.Center -> "center"
                            NovelReaderPreferences.TextAlignment.Right -> "right"
                            NovelReaderPreferences.TextAlignment.Justify -> "justify"
                        }
                        val cleanedHtml = s.content
                            .replace(Regex("text-align\\s*:\\s*[^;\"']*;?", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("align\\s*=\\s*\"[^\"]*\"", RegexOption.IGNORE_CASE), "")
                            // Add a <br> after each closing paragraph tag to create visual separation
                            .replace("</p>", "</p><br>")

                        // Create a complete HTML document with CSS for paragraph indentation
                        val alignedHtml = """
                            <!DOCTYPE html>
                            <html>
                                <head>
                                    <style>
                                        body {
                                            text-align: $htmlAlign;
                                            margin: 0;
                                            padding: 0;
                                        }
                                        p {
                                            text-indent: 1.2em;
                                            margin-top: 0.15em;
                                            margin-bottom: 0.15em;
                                        }
                                    </style>
                                </head>
                                <body>
                                    $cleanedHtml
                                </body>
                            </html>
                        """.trimIndent()

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

                        AnimatedContent(
                            targetState = contentReady,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                        ) { ready ->
                            if (!ready) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    EpubShimmerSkeletonLoader(lineCount = 24)
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
                                        .padding(
                                            top = 115.dp,
                                            start = 20.dp,
                                            end = 20.dp,
                                            bottom = 16.dp,
                                        )
                                        .pointerInput(barsVisible) {
                                            detectTapGestures(onTap = { barsVisible = !barsVisible })
                                        },
                                ) {
                                    // Compose-native HTML rendering
                                    val resolvedTextAlign = when (textAlignment) {
                                        NovelReaderPreferences.TextAlignment.Left -> TextAlign.Left
                                        NovelReaderPreferences.TextAlignment.Center -> TextAlign.Center
                                        NovelReaderPreferences.TextAlignment.Right -> TextAlign.Right
                                        NovelReaderPreferences.TextAlignment.Justify -> TextAlign.Justify
                                        // fallback for safety
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
                                                lineHeight = resolvedLineHeight.sp,
                                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                                    includeFontPadding = false,
                                                ),
                                            ),
                                        )
                                    }
                                    // Footer at the end of the chapter
                                    Spacer(modifier = Modifier.height(32.dp))
                                    viewModel.currentChapterIndex + 1
                                    val currentChapterTitle = s.chapterTitle
                                    val hasNext = s.hasNext
                                    val nextChapter = chapters.getOrNull(viewModel.currentChapterIndex + 1)
                                    val nextChapterTitle = nextChapter?.name
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            text = "Finished: $currentChapterTitle",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(bottom = 16.dp),
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (hasNext && nextChapterTitle != null) {
                                            androidx.compose.material3.Card(
                                                shape = MaterialTheme.shapes.large,
                                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth(0.85f)
                                                    .height(56.dp),
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
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Text(
                                                        "Next: $nextChapterTitle",
                                                        style = MaterialTheme.typography.titleMedium,
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
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter),
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
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    val coroutineScope = rememberCoroutineScope()
                    val maxScroll = scrollState.maxValue
                    var sliderProgress by remember { mutableFloatStateOf(if (maxScroll > 0) scrollState.value.toFloat() / maxScroll else 0f) }
                    // Sync slider with scroll unless dragging
                    LaunchedEffect(scrollState.value, maxScroll, barsVisible) {
                        if (!sliderInUse) {
                            sliderProgress = if (maxScroll > 0) scrollState.value.toFloat() / maxScroll else 0f
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                            .navigationBarsPadding()
                            .padding(bottom = 72.dp), // above bottom bar
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val percent = (sliderProgress * 100).toInt()
                        Text("$percent%", modifier = Modifier.padding(end = 8.dp), color = presetColorScheme.text)
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
                            modifier = Modifier.weight(1f),
                        )
                        Text("100%", modifier = Modifier.padding(start = 8.dp), color = presetColorScheme.text)
                    }
                }
                // Bottom bar anchored to the bottom, hides/shows with barsVisible
                androidx.compose.animation.AnimatedVisibility(
                    visible = barsVisible && (state !is NovelReaderState.Loading),
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Previous chapter button
                        val hasPrevChapter = viewModel.currentChapterIndex > 0
                        IconButton(
                            onClick = { viewModel.prevChapter() },
                            enabled = hasPrevChapter,
                            modifier = Modifier.graphicsLayer { alpha = if (hasPrevChapter) 1f else 0.5f },
                        ) {
                            Icon(
                                Icons.Filled.SkipPrevious,
                                contentDescription = "Previous chapter",
                                tint = if (hasPrevChapter) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = 0.5f,
                                    )
                                },
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
                                            manga.title,
                                        )
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Log.e("NovelReader", "Failed to open chapter URL", e)
                                        context.toast("Could not open chapter")
                                    }
                                }
                            },
                        ) {
                            Icon(Icons.Filled.Public, contentDescription = "Open in WebView")
                        }
                        IconButton(
                            onClick = {
                                // Scroll to top
                                coroutineScope.launch { scrollState.animateScrollTo(0) }
                            },
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
                            modifier = Modifier.graphicsLayer { alpha = if (hasNextChapter) 1f else 0.5f },
                        ) {
                            Icon(
                                Icons.Filled.SkipNext,
                                contentDescription = "Next chapter",
                                tint = if (hasNextChapter) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = 0.5f,
                                    )
                                },
                            )
                        }
                    }
                }
                // Chapter list bottom sheet
                if (showChapterListSheet) {
                    val manga = viewModel.manga
                    LocalContext.current
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
                                download.chapter.id to (download.status to download.progress)
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
                            downloadProgressMap,
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
                                    downloadProgressMap[chapterItem.chapter.id] ?: 0

// Get the current download state for this chapter
                                    val (downloadState, downloadProgress) = remember(
                                        chapterItem.chapter.id,
                                        downloadStates[chapterItem.chapter.id],
                                        downloadProgressMap[chapterItem.chapter.id],
                                    ) {
                                        val state = downloadStates[chapterItem.chapter.id]
                                        downloadProgressMap[chapterItem.chapter.id] ?: 0
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
                                            viewModel.jumpToChapter(chapters.indexOf(chapterItem.chapter))
                                            showChapterListSheet = false
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
                                                        downloadProgressMap.remove(chapterItem.chapter.id)
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
            }

            // Loading state handling
            state is NovelReaderState.Loading

            // Settings bottom sheet (outside of main Box)
            if (showSettingsSheet) {
                NovelReaderSettingsBottomSheet(
                    model = settingsModel,
                    onDismiss = { showSettingsSheet = false },
                )
            }
        }
        // Flush read timer when leaving the screen
        DisposableEffect(viewModel) {
            onDispose {
                viewModel.flushReadTimer()
            }
        }
    }
}
