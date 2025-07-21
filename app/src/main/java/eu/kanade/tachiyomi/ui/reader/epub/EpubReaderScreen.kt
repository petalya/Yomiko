// SPDX-License-Identifier: Apache-2.0
package eu.kanade.tachiyomi.ui.reader.epub

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.util.Base64
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.manga.components.MangaChapterListItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderSettingsScreenModel
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.util.epub.EpubTableOfContentsEntry
import eu.kanade.tachiyomi.util.epub.ReaderTheme
import kotlinx.coroutines.launch
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.model.ChapterSwipeAction
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream

class EpubReaderScreen(
    private val mangaId: Long,
    private val chapterId: Long,
    private val chapterUrl: String = "",
) : Screen {
    @Composable
    override fun Content() {
        val viewModel = rememberScreenModel { EpubReaderViewModel(mangaId, chapterId, chapterUrl) }
        val state by viewModel.state.collectAsState()
        val readerSettings by viewModel.settings.collectAsState()
        val chapters = viewModel.chapters
        val scrollState = rememberScrollState()
        val coroutineScope = rememberCoroutineScope()
        var barsVisible by remember { mutableStateOf(true) }
        val navigator = LocalNavigator.current
        val settingsModel = rememberScreenModel { NovelReaderSettingsScreenModel() }
        val fontSize by settingsModel.fontSize.collectAsState()
        val textAlignment by settingsModel.textAlignment.collectAsState()
        val lineSpacing by settingsModel.lineSpacing.collectAsState()
        val colorSchemeIndex by settingsModel.colorSchemeIndex.collectAsState()
        val fontFamilyPref by settingsModel.fontFamily.collectAsState()

        // Set system UI colors - ensure it matches the bottom bar
        val surfaceColor = MaterialTheme.colorScheme.surface
        val view = LocalView.current
        val window = remember { view.context.getActivity()?.window }
        val windowInsetsController = remember { window?.let { WindowCompat.getInsetsController(it, view) } }

        // Set navigation bar color
        val bottomBarColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)

        // Apply immersive mode based on bars visibility
        LaunchedEffect(Unit) {
            windowInsetsController?.let {
                // Always enable immersive mode when entering the reader
                it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                if (barsVisible) {
                    it.show(WindowInsetsCompat.Type.systemBars())
                } else {
                    it.hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        // Update system bars when barsVisible changes
        LaunchedEffect(barsVisible) {
            windowInsetsController?.let {
                if (!barsVisible) {
                    it.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    it.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        // Restore system UI when leaving the screen
        DisposableEffect(Unit) {
            onDispose {
                windowInsetsController?.let {
                    it.show(WindowInsetsCompat.Type.systemBars())
                    it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
                window?.navigationBarColor = bottomBarColor.toArgb()
            }
        }

        // Hide-on-scroll state
        var lastScrollOffset = remember { 0 }
        var accumulatedScroll = remember { 0 }
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

        // derived slider progress
        var sliderProgress = remember { mutableStateOf(0f) }
        LaunchedEffect(scrollState.value) {
            if (scrollState.maxValue > 0) {
                sliderProgress.value = scrollState.value.toFloat() / scrollState.maxValue
            }
        }

        // save progress debounced
        LaunchedEffect(scrollState.value, viewModel.currentChapterId) {
            if (scrollState.maxValue > 0) {
                val percent = scrollState.value.toFloat() / scrollState.maxValue
                viewModel.updateScrollProgressDebounced(percent)
            }
        }

        // Smooth scroll to last saved progress when chapter loads
        LaunchedEffect(state) {
            val progress = when (state) {
                is EpubReaderState.ReflowSuccess -> (state as EpubReaderState.ReflowSuccess).progress
                is EpubReaderState.HtmlSuccess -> (state as EpubReaderState.HtmlSuccess).progress
                else -> 0f
            }
            // Wait for content to be measured
            kotlinx.coroutines.delay(100)
            if (scrollState.maxValue > 0) {
                if (progress > 0f) {
                    val target = (progress * scrollState.maxValue).toInt().coerceIn(0, scrollState.maxValue)
                    scrollState.animateScrollTo(target)
                } else {
                    scrollState.scrollTo(0)
                }
            }
        }

        // Hide system UI bars during loading
        LaunchedEffect(state is EpubReaderState.Loading) {
            val activity = view.context.getActivity() as? android.app.Activity ?: return@LaunchedEffect
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val controller = activity.window.insetsController
                if (state is EpubReaderState.Loading) {
                    controller?.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                } else {
                    controller?.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                    controller?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                if (state is EpubReaderState.Loading) {
                    activity.window.decorView.systemUiVisibility = (
                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                            android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        )
                } else {
                    activity.window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
                }
            }
        }

        // Bottom sheets state
        var showChapterListSheet = remember { mutableStateOf(false) }
        var showSettingsSheet = remember { mutableStateOf(false) }

        val backgroundColor = when (readerSettings.theme) {
            ReaderTheme.LIGHT -> Color.White
            ReaderTheme.SEPIA -> Color(0xFFFFE4C7)
            ReaderTheme.MINT -> Color(0xFFDDE7E3)
            ReaderTheme.BLUE_GRAY -> Color(0xFF2B2B38)
            ReaderTheme.BLACK -> Color(0xFF000000)
        }

        // Fullscreen image state
        var fullscreenImage by remember { mutableStateOf<FullscreenImageData?>(null) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor),
        ) {
            // Main content area with fade transition
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.fillMaxSize(),
            ) { animatedState ->
                when (animatedState) {
                    is EpubReaderState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EpubShimmerSkeletonLoader(lineCount = 24)
                    }
                    is EpubReaderState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error: ${animatedState.message}")
                    }
                    is EpubReaderState.ReflowSuccess -> {
                        val verticalPadding = 16.dp
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { _ ->
                                            barsVisible = !barsVisible
                                        },
                                    )
                                },
                        ) {
                            Column(modifier = Modifier.verticalScroll(scrollState)) {
                                Spacer(modifier = Modifier.height(verticalPadding))
                                EpubReflowableContent(
                                    contentBlocks = animatedState.contentBlocks,
                                    settings = readerSettings,
                                    modifier = Modifier.fillMaxWidth(),
                                    chapterTitle = animatedState.chapterTitle ?: "",
                                    onTap = { _ -> barsVisible = !barsVisible },
                                    onImageClick = { imageData -> fullscreenImage = imageData },
                                )
                                Spacer(modifier = Modifier.height(verticalPadding))
                            }
                        }
                    }
                    is EpubReaderState.HtmlSuccess -> {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )

                                    settings.apply {
                                        javaScriptEnabled = true
                                        defaultTextEncodingName = "UTF-8"
                                        allowFileAccess = true
                                        domStorageEnabled = true
                                        cacheMode = WebSettings.LOAD_DEFAULT
                                        setSupportMultipleWindows(false)
                                        builtInZoomControls = true
                                        displayZoomControls = false
                                    }

                                    loadDataWithBaseURL(
                                        null,
                                        animatedState.content,
                                        "text/html",
                                        "UTF-8",
                                        null,
                                    )

                                    @SuppressLint("ClickableViewAccessibility")
                                    setOnTouchListener { _, event ->
                                        if (event.action == MotionEvent.ACTION_UP) {
                                            barsVisible = !barsVisible
                                        }
                                        false
                                    }
                                }
                            },
                        )
                    }
                }
            }

            // Top bar overlays content, does NOT push it down
            AnimatedVisibility(
                visible = barsVisible && (state !is EpubReaderState.Loading),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                val s = when (state) {
                    is EpubReaderState.ReflowSuccess -> state as EpubReaderState.ReflowSuccess
                    is EpubReaderState.HtmlSuccess -> state as EpubReaderState.HtmlSuccess
                    else -> return@AnimatedVisibility
                }

                AppBar(
                    titleContent = {
                        when (val currentState = state) {
                            is EpubReaderState.ReflowSuccess -> {
                                AppBarTitle(title = currentState.bookTitle, subtitle = currentState.chapterTitle)
                            }
                            is EpubReaderState.HtmlSuccess -> {
                                AppBarTitle(title = currentState.bookTitle, subtitle = currentState.chapterTitle)
                            }
                            else -> {
                                AppBarTitle(title = "EPUB Reader")
                            }
                        }
                    },
                    navigateUp = { navigator?.pop() },
                    actions = {
                        val currentChapter = viewModel.chapters.find { it.id == viewModel.currentChapterId }
                        IconButton(onClick = {
                            currentChapter?.let { viewModel.toggleBookmark(it) }
                        }) {
                            val isBookmarked = currentChapter?.bookmark == true
                            Icon(
                                imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                            )
                        }
                    },
                    backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                )
            }

            // Progress bar above bottom bar, hides/shows with barsVisible or while interacting
            var sliderInUse by remember { mutableStateOf(false) }
            AnimatedVisibility(
                visible = (barsVisible || sliderInUse) && (state !is EpubReaderState.Loading),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                val maxScroll = scrollState.maxValue
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 90.dp, start = 16.dp, end = 16.dp, top = 8.dp), // above bottom bar
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val percent = (sliderProgress.value * 100).toInt()
                    Text("$percent%", modifier = Modifier.padding(end = 8.dp))
                    Slider(
                        value = sliderProgress.value,
                        onValueChange = { newProgress ->
                            sliderInUse = true
                            sliderProgress.value = newProgress
                            if (maxScroll > 0) {
                                val target = (sliderProgress.value * maxScroll).toInt()
                                coroutineScope.launch { scrollState.scrollTo(target) }
                            }
                        },
                        onValueChangeFinished = {
                            sliderInUse = false
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                    )
                    Text("100%", modifier = Modifier.padding(start = 8.dp))
                }
            }

            // Bottom bar anchored to the bottom, hides/shows with barsVisible
            AnimatedVisibility(
                visible = barsVisible && (state !is EpubReaderState.Loading),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                val s = when (state) {
                    is EpubReaderState.ReflowSuccess -> state as EpubReaderState.ReflowSuccess
                    is EpubReaderState.HtmlSuccess -> state as EpubReaderState.HtmlSuccess
                    else -> return@AnimatedVisibility
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Next chapter button
                        IconButton(
                            onClick = { viewModel.nextChapter() },
                            enabled = when (val currentState = s) {
                                is EpubReaderState.ReflowSuccess -> currentState.hasNext
                                is EpubReaderState.HtmlSuccess -> currentState.hasNext
                                else -> false
                            },
                            modifier = Modifier.alpha(
                                if (when (val currentState = s) {
                                        is EpubReaderState.ReflowSuccess -> currentState.hasNext
                                        is EpubReaderState.HtmlSuccess -> currentState.hasNext
                                        else -> false
                                    }
                                ) {
                                    1f
                                } else {
                                    0.3f
                                },
                            ),
                        ) {
                            val hasNext = when (val currentState = s) {
                                is EpubReaderState.ReflowSuccess -> currentState.hasNext
                                is EpubReaderState.HtmlSuccess -> currentState.hasNext
                                else -> false
                            }
                            Icon(
                                Icons.Filled.SkipPrevious,
                                contentDescription = "Next chapter",
                                tint = if (hasNext) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            )
                        }
                        IconButton(onClick = { showChapterListSheet.value = true }) {
                            Icon(Icons.Filled.FormatListNumbered, contentDescription = "Chapter list")
                        }
                        IconButton(
                            onClick = {
                                // Scroll to top
                                coroutineScope.launch { scrollState.animateScrollTo(0) }
                            },
                        ) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Scroll to top")
                        }
                        IconButton(onClick = { showSettingsSheet.value = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                        // Previous chapter button (moved to the right)
                        IconButton(
                            onClick = { viewModel.prevChapter() },
                            enabled = when (val currentState = s) {
                                is EpubReaderState.ReflowSuccess -> currentState.hasPrev
                                is EpubReaderState.HtmlSuccess -> currentState.hasPrev
                                else -> false
                            },
                            modifier = Modifier.alpha(
                                if (when (val currentState = s) {
                                        is EpubReaderState.ReflowSuccess -> currentState.hasPrev
                                        is EpubReaderState.HtmlSuccess -> currentState.hasPrev
                                        else -> false
                                    }
                                ) {
                                    1f
                                } else {
                                    0.3f // Make disabled more visually distinct
                                },
                            ),
                        ) {
                            val hasPrev = when (val currentState = s) {
                                is EpubReaderState.ReflowSuccess -> currentState.hasPrev
                                is EpubReaderState.HtmlSuccess -> currentState.hasPrev
                                else -> false
                            }
                            Icon(
                                Icons.Filled.SkipNext,
                                contentDescription = "Previous chapter",
                                tint = if (hasPrev) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            )
                        }
                    }
                }
            }

            // Chapter list bottom sheet
            if (showChapterListSheet.value) {
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
                val downloadStates = remember(downloadQueue) {
                    derivedStateOf {
                        downloadQueue.associate { download ->
                            download.chapter.id to (download.status to (download.progress ?: 0))
                        }
                    }
                }

                val chapterItems = chapters.mapIndexed { _, chapter ->
                    val isCurrent = chapter.id == viewModel.currentChapterId
                    val downloadState = downloadStates.value[chapter.id]?.first ?: Download.State.NOT_DOWNLOADED
                    val progress = downloadStates.value[chapter.id]?.second ?: 0

                    ChapterItem(
                        chapter = chapter,
                        isCurrent = isCurrent,
                        downloadState = downloadState,
                        downloadProgress = progress,
                    )
                }

                AdaptiveSheet(
                    onDismissRequest = { showChapterListSheet.value = false },
                ) {
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
                            val progress = downloadProgressMap[chapterItem.chapter.id] ?: 0

                            // Get the current download state for this chapter
                            val (downloadState, downloadProgress) = remember(
                                chapterItem.chapter.id,
                                downloadStates.value[chapterItem.chapter.id],
                                downloadProgressMap[chapterItem.chapter.id],
                            ) {
                                val state = downloadStates.value[chapterItem.chapter.id]
                                val progress = downloadProgressMap[chapterItem.chapter.id] ?: 0
                                val isDownloaded = viewModel.manga?.let { manga ->
                                    downloadManager.isChapterDownloaded(
                                        chapterItem.chapter.name,
                                        chapterItem.chapter.scanlator,
                                        manga.ogTitle,
                                        manga.source,
                                    )
                                } ?: false

                                when {
                                    state != null -> state.first to state.second
                                    isDownloaded -> Download.State.DOWNLOADED to 0
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
                                    viewModel.jumpToChapter(chapters.indexOf(chapterItem.chapter))
                                    showChapterListSheet.value = false
                                },
                                onDownloadClick = {},
                                onChapterSwipe = { action ->
                                    when (action) {
                                        ChapterSwipeAction.ToggleRead -> viewModel.toggleRead(chapterItem.chapter)
                                        ChapterSwipeAction.ToggleBookmark -> viewModel.toggleBookmark(chapterItem.chapter)
                                        else -> {}
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        // Settings bottom sheet (outside of main Box)
        if (showSettingsSheet.value) {
            EpubReaderSettingsSheet(
                viewModel = viewModel,
                onDismiss = { showSettingsSheet.value = false },
            )
        }

        // Fullscreen image overlay
        if (fullscreenImage != null) {
            FullscreenImageOverlay(
                imageData = fullscreenImage!!,
                onDismiss = { fullscreenImage = null },
            )
        }
    }
}

@Composable
private fun TocItem(
    tocEntry: EpubTableOfContentsEntry,
    onClick: () -> Unit,
) {
    Column {
        Text(
            text = tocEntry.title,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(
                    start = (16 + tocEntry.level * 16).dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 12.dp,
                ),
            style = MaterialTheme.typography.bodyLarge,
        )

        // Render children
        tocEntry.children.forEach { child ->
            TocItem(
                tocEntry = child,
                onClick = onClick,
            )
        }
    }
}

// Helper function to determine if a color is light
private fun isColorLight(color: Color): Boolean {
    val red = color.red * 255
    val green = color.green * 255
    val blue = color.blue * 255
    // Using standard formula to determine if a color is "light"
    return (red * 0.299 + green * 0.587 + blue * 0.114) > 128
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun WebContent(html: String, onTap: () -> Unit) {
    val context = LocalContext.current

    // Load font files as base64 strings
    val loraFontBase64 = remember { context.loadFontAsBase64(R.font.lora) }
    val openSansFontBase64 = remember { context.loadFontAsBase64(R.font.open_sans) }
    val arbutusFontBase64 = remember { context.loadFontAsBase64(R.font.arbutus_slab) }
    val latoFontBase64 = remember { context.loadFontAsBase64(R.font.lato) }

    // Create font face definitions
    val fontFaceStyles = """
        @font-face {
            font-family: 'Lora';
            src: url('data:font/ttf;base64,$loraFontBase64') format('truetype');
            font-weight: normal;
            font-style: normal;
        }
        @font-face {
            font-family: 'Open Sans';
            src: url('data:font/ttf;base64,$openSansFontBase64') format('truetype');
            font-weight: normal;
            font-style: normal;
        }
        @font-face {
            font-family: 'Arbutus Slab';
            src: url('data:font/ttf;base64,$arbutusFontBase64') format('truetype');
            font-weight: normal;
            font-style: normal;
        }
        @font-face {
            font-family: 'Lato';
            src: url('data:font/ttf;base64,$latoFontBase64') format('truetype');
            font-weight: normal;
            font-style: normal;
        }
    """.trimIndent()

    // Add font faces to the HTML
    val htmlWithFonts = html.replace("<style>", "<style>\n$fontFaceStyles\n")

    AndroidView(
        factory = {
            WebView(context).apply {
                settings.javaScriptEnabled = true // Enable JavaScript to prevent text selection
                settings.setDisplayZoomControls(false)
                settings.setSupportZoom(false)
                settings.textZoom = 100
                settings.cacheMode = WebSettings.LOAD_NO_CACHE

                // Make WebView transparent to allow the background color to show through
                setBackgroundColor(android.graphics.Color.TRANSPARENT)

                // Set layout parameters
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )

                // Disable long press
                isLongClickable = false
                setOnLongClickListener { true } // Consume all long clicks

                // Handle touch events to allow tap gestures to pass through
                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // Clear any existing selection
                            clearFocus()
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            // Only handle tap up events if it's a simple tap (not a long press or drag)
                            if (event.eventTime - event.downTime < 200) { // Less than 200ms is considered a tap
                                performClick()
                                onTap()
                                true
                            } else {
                                false
                            }
                        }
                        else -> false // Let other touch events pass through
                    }
                }

                loadDataWithBaseURL(null, htmlWithFonts, "text/html", "UTF-8", null)
            }
        },
        update = {
            it.loadDataWithBaseURL(null, htmlWithFonts, "text/html", "UTF-8", null)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

// Helper function to load a font resource as base64 string
private fun android.content.Context.loadFontAsBase64(fontResId: Int): String {
    return try {
        val typeface = ResourcesCompat.getFont(this, fontResId)
        val file = ResourcesCompat.getFont(this, fontResId)?.let {
            resources.openRawResource(fontResId)
        }

        file?.use { inputStream ->
            val buffer = ByteArrayOutputStream()
            val data = ByteArray(1024)
            var count: Int

            while (inputStream.read(data).also { count = it } != -1) {
                buffer.write(data, 0, count)
            }

            buffer.flush()
            Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP)
        } ?: ""
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }
}

// Extension function to convert Color to CSS color string
private fun Color.toCssColor(): String {
    return String.format(
        "#%02X%02X%02X",
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )
}

// Extract body content from HTML
private fun extractBodyContent(html: String): String {
    val bodyStart = html.indexOf("<body")
    val bodyContentStart = html.indexOf(">", bodyStart) + 1
    val bodyEnd = html.lastIndexOf("</body>")

    return if (bodyStart >= 0 && bodyEnd > bodyContentStart) {
        html.substring(bodyContentStart, bodyEnd)
    } else {
        html // Return the original if we can't extract the body
    }
}

// Extension function to get Activity from Context
private fun android.content.Context.getActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) {
            return context
        }
        context = context.baseContext
    }
    return null
}

data class ChapterItem(
    val chapter: Chapter,
    val isCurrent: Boolean,
    val downloadState: Download.State,
    val downloadProgress: Int,
)

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

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun FullscreenImageOverlay(
    imageData: FullscreenImageData,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dragThresholdPx = with(density) { 80.dp.toPx() }
    val offsetY = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(0.92f) }
    var isDismissing by remember { mutableStateOf(false) }
    val backgroundAlpha = animateFloatAsState(
        targetValue = (0.6f * (1f - (kotlin.math.abs(offsetY.value) / (dragThresholdPx * 2))).coerceIn(0.2f, 0.6f)),
        animationSpec = tween(durationMillis = 120), label = "fullscreenImageBgAlpha",
    )
    // Dismiss on back
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) onDismiss()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Animate in on first composition
    LaunchedEffect(Unit) {
        scaleAnim.snapTo(0.92f)
        scaleAnim.animateTo(1f, tween(180))
    }
    fun dismissWithScale() {
        if (!isDismissing) {
            isDismissing = true
            scope.launch {
                scaleAnim.animateTo(0.92f, tween(180))
                onDismiss()
            }
        }
    }
    Dialog(
        onDismissRequest = { dismissWithScale() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true,
        ),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backgroundAlpha.value)),
        ) {
            // Blurred background (under image)
            Box(
                Modifier
                    .matchParentSize()
                    .blur(32.dp)
                    .background(Color.Black.copy(alpha = backgroundAlpha.value)),
            )
            // Image with pan/zoom and scale animation
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                if (kotlin.math.abs(offsetY.value) > dragThresholdPx) {
                                    dismissWithScale()
                                } else {
                                    scope.launch { offsetY.animateTo(0f, tween(220)) }
                                }
                            },
                            onDragCancel = {
                                scope.launch { offsetY.animateTo(0f, tween(220)) }
                            },
                            onDrag = { change, dragAmount ->
                                scope.launch { offsetY.snapTo(offsetY.value + dragAmount.y) }
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { dismissWithScale() },
                        )
                    },
            ) {
                AndroidView(
                    factory = { ctx ->
                        ReaderPageImageView(ctx).apply {
                            onViewClicked = { dismissWithScale() }
                        }
                    },
                    update = { view ->
                        val config = ReaderPageImageView.Config(zoomDuration = 300)
                        when {
                            imageData.data != null -> {
                                val drawable = BitmapDrawable(context.resources, BitmapFactory.decodeByteArray(imageData.data, 0, imageData.data.size))
                                view.setImage(drawable, config)
                            }
                            imageData.src != null -> {
                                val request = ImageRequest.Builder(context)
                                    .data(imageData.src)
                                    .size(Size.ORIGINAL)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .target { image ->
                                        val drawable = image.asDrawable(context.resources)
                                        view.setImage(drawable, config)
                                    }
                                    .build()
                                context.imageLoader.enqueue(request)
                            }
                        }
                    },
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            translationY = offsetY.value
                            scaleX = scaleAnim.value
                            scaleY = scaleAnim.value
                        },
                )
            }
        }
    }
}
