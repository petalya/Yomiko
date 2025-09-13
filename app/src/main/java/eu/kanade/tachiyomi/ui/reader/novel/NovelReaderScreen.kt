// SPDX-License-Identifier: Apache-2.0
package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Activity
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
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
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.ui.reader.common.BatteryTimeBar
import eu.kanade.tachiyomi.ui.reader.common.ReaderBottomBar
import eu.kanade.tachiyomi.ui.reader.common.ReaderProgressSlider
import eu.kanade.tachiyomi.ui.reader.model.getChapterWebUrl
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderSettingsScreenModel
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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
        val coroutineScope = rememberCoroutineScope()
        val settingsModel = rememberScreenModel { NovelReaderSettingsScreenModel() }
        val fontSize by settingsModel.fontSize.collectAsState()
        val textAlignment by settingsModel.textAlignment.collectAsState()
        val lineSpacing by settingsModel.lineSpacing.collectAsState()
        val presetColorScheme by settingsModel.colorScheme.collectAsState()
        val fontFamilyPref by settingsModel.fontFamily.collectAsState()
        val showProgressPercent by settingsModel.showProgressPercent.collectAsState()
        val showBatteryAndTime by settingsModel.showBatteryAndTime.collectAsState()
        val volumeButtonScroll by settingsModel.volumeButtonScroll.collectAsState()
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

        // Handle scroll position reset during loading
        LaunchedEffect(state) {
            when (state) {
                is NovelReaderState.Loading -> {
                    // Reset scroll to 0 during loading state with delay
                    delay(300) // Delay to align with loading animation
                    scrollState.scrollTo(0)
                }
                else -> return@LaunchedEffect
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
                val progressBarHeight = 40.dp
                // Track bottom bar height to position the slider above it
                val density = LocalDensity.current
                var bottomBarHeightDp by remember { mutableStateOf(52.dp) }
                val sliderExtraBottomPadding = 16.dp
                // Collect from VM
                val filteredChaptersNav by viewModel.filteredChaptersWithCurrent.collectAsState()
                val currentIdNav = viewModel.currentChapterId
                val filteredIndexNav by remember(filteredChaptersNav, currentIdNav) {
                    derivedStateOf { filteredChaptersNav.indexOfFirst { it.id == currentIdNav } }
                }
                val hasPrevChapter = filteredIndexNav > 0
                val hasNextChapterNav = filteredIndexNav != -1 && filteredIndexNav < filteredChaptersNav.lastIndex
                val nextChapterTitleNav = if (hasNextChapterNav) filteredChaptersNav[filteredIndexNav + 1].name else null

                // Volume keys: scroll by one screen height (90%)
                val view = LocalView.current
                val scrollDownByScreen = rememberUpdatedState(newValue = {
                    val heightPx = (view.height * 0.90f).toInt()
                    if (heightPx > 0) {
                        val max = scrollState.maxValue
                        if (max > 0) {
                            val target = (scrollState.value + heightPx).coerceIn(0, max)
                            coroutineScope.launch { scrollState.animateScrollTo(target) }
                        }
                    }
                })
                val scrollUpByScreen = rememberUpdatedState(newValue = {
                    val heightPx = (view.height * 0.90f).toInt()
                    if (heightPx > 0) {
                        val max = scrollState.maxValue
                        if (max > 0) {
                            val target = (scrollState.value - heightPx).coerceIn(0, max)
                            coroutineScope.launch { scrollState.animateScrollTo(target) }
                        }
                    }
                })
                DisposableEffect(window, volumeButtonScroll) {
                    val prev = window?.callback
                    if (prev == null) {
                        onDispose { }
                    } else {
                        val newCallback = object : Window.Callback by prev {
                            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                                if (!volumeButtonScroll) return prev.dispatchKeyEvent(event)
                                when (event.keyCode) {
                                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                                        if (event.action == KeyEvent.ACTION_UP) {
                                            scrollDownByScreen.value.invoke()
                                        }
                                        return true
                                    }
                                    KeyEvent.KEYCODE_VOLUME_UP -> {
                                        if (event.action == KeyEvent.ACTION_UP) {
                                            scrollUpByScreen.value.invoke()
                                        }
                                        return true
                                    }
                                }
                                return prev.dispatchKeyEvent(event)
                            }
                        }
                        window?.callback = newCallback
                        onDispose {
                            if (window?.callback === newCallback) {
                                window?.callback = prev
                            }
                        }
                    }
                }
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

                            // After content is ready, restore scroll position
                            val progress = s.progress
                            if (progress > 0f) {
                                // Wait longer for layout to be complete
                                delay(200)

                                // Retry scroll restoration with multiple attempts
                                val MAX_ATTEMPTS = 5
                                var attempts = 0
                                while (attempts < MAX_ATTEMPTS && scrollState.maxValue <= 0) {
                                    delay(100)
                                    attempts++
                                }

                                if (scrollState.maxValue > 0) {
                                    val target = (progress * scrollState.maxValue).toInt().coerceIn(0, scrollState.maxValue)
                                    scrollState.animateScrollTo(target)
                                } else {
                                    Log.d("NovelReader", "Scroll restoration skipped: content not laid out after attempts")
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
                                            bottom = if (showProgressPercent && contentReady) progressBarHeight else 16.dp,
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
                                    val currentChapterTitle = s.chapterTitle
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.label_finished_chapter, currentChapterTitle),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(bottom = 16.dp),
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (hasNextChapterNav && nextChapterTitleNav != null) {
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
                                                        stringResource(id = R.string.label_next_chapter, nextChapterTitleNav),
                                                        style = MaterialTheme.typography.titleMedium,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Stationary progress percentage UI at the bottom of the screen, synced with content readiness
                        if (showProgressPercent && contentReady) {
                            val maxScroll = scrollState.maxValue
                            val progress = if (maxScroll > 0) scrollState.value.toFloat() / maxScroll else 0f
                            val progressPercent = (progress * 100).toInt().coerceIn(0, 100)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(progressBarHeight)
                                    .background(presetColorScheme.background),
                            ) {
                                BatteryTimeBar(
                                    progressPercent = progressPercent,
                                    showBatteryAndTime = showBatteryAndTime,
                                    textColor = presetColorScheme.text,
                                    modifier = Modifier.fillMaxSize(),
                                    backgroundColor = presetColorScheme.background,
                                )
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
                        title = (state as? NovelReaderState.Success)?.novelTitle
                            ?: stringResource(id = R.string.novel_default_title),
                        chapterTitle = (state as? NovelReaderState.Success)?.chapterTitle
                            ?: stringResource(id = R.string.chapter_default_title),
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
                    val maxScroll = scrollState.maxValue
                    var sliderProgress by remember { mutableFloatStateOf(if (maxScroll > 0) scrollState.value.toFloat() / maxScroll else 0f) }
                    // Sync slider with scroll unless dragging
                    LaunchedEffect(scrollState.value, maxScroll, barsVisible) {
                        if (!sliderInUse) {
                            sliderProgress = if (maxScroll > 0) scrollState.value.toFloat() / maxScroll else 0f
                        }
                    }
                    val textColor = LocalContentColor.current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                            .navigationBarsPadding()
                            .padding(bottom = bottomBarHeightDp + sliderExtraBottomPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ReaderProgressSlider(
                            progress = sliderProgress,
                            onProgressChange = { newProgress ->
                                sliderInUse = true
                                sliderProgress = newProgress
                                if (maxScroll > 0) {
                                    val target = (sliderProgress * maxScroll).toInt()
                                    coroutineScope.launch { scrollState.scrollTo(target) }
                                }
                            },
                            onProgressChangeFinished = { sliderInUse = false },
                            percentTextColor = textColor,
                            modifier = Modifier,
                        )
                    }
                }
                // Bottom bar anchored to the bottom, hides/shows with barsVisible
                androidx.compose.animation.AnimatedVisibility(
                    visible = barsVisible && (state !is NovelReaderState.Loading),
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    // Reuse the same filtered list computed above to avoid duplicate work
                    val hasPrev = hasPrevChapter
                    val hasNext = hasNextChapterNav
                    ReaderBottomBar(
                        hasPrev = hasPrev,
                        hasNext = hasNext,
                        onPrev = { viewModel.prevChapter() },
                        onNext = { viewModel.nextChapter() },
                        onChapterList = { showChapterListSheet = true },
                        onScrollTop = { coroutineScope.launch { scrollState.animateScrollTo(0) } },
                        onSettings = { showSettingsSheet = true },
                        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        modifier = Modifier,
                        additionalActions = {
                            IconButton(
                                onClick = {
                                    val chapter = chapters.getOrNull(viewModel.currentChapterIndex)
                                    val mangaObj = viewModel.manga
                                    val source = mangaObj?.let { Injekt.get<SourceManager>().get(it.source) }
                                    val url = if (chapter != null && mangaObj != null) {
                                        getChapterWebUrl(mangaObj, chapter, source)
                                    } else {
                                        null
                                    }
                                    if (url != null) {
                                        WebViewActivity.newIntent(
                                            context = context,
                                            url = url,
                                            sourceId = source?.id,
                                            title = chapter?.name,
                                        )
                                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            .let(context::startActivity)
                                    } else {
                                        context.toast(context.getString(R.string.msg_no_url_for_chapter))
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Public,
                                    contentDescription = stringResource(id = R.string.desc_open_in_webview),
                                )
                            }
                        },
                        swapNavOrder = true,
                        additionalAfterPrev = false,
                        additionalAfterChapterList = true,
                    )
                }
                // Chapter list bottom sheet
                NovelChapterListSheet(
                    viewModel = viewModel,
                    show = showChapterListSheet,
                    onDismiss = { showChapterListSheet = false },
                )
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
