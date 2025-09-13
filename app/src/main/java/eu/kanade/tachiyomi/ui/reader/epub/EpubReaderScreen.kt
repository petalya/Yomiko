// SPDX-License-Identifier: Apache-2.0
package eu.kanade.tachiyomi.ui.reader.epub

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.util.Base64
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.Window
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.reader.common.BatteryTimeBar
import eu.kanade.tachiyomi.ui.reader.common.ReaderBottomBar
import eu.kanade.tachiyomi.ui.reader.common.ReaderProgressSlider
import eu.kanade.tachiyomi.ui.reader.common.ReaderTopBar
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderSettingsScreenModel
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.util.epub.EpubTableOfContentsEntry
import eu.kanade.tachiyomi.util.epub.ReaderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tachiyomi.domain.chapter.model.Chapter
import java.io.ByteArrayOutputStream

@Suppress("NAME_SHADOWING")
class EpubReaderScreen(
    private val mangaId: Long,
    private val chapterId: Long,
    private val chapterUrl: String = "",
) : Screen {
    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val viewModel = rememberScreenModel { EpubReaderViewModel(mangaId, chapterId) }
        val state by viewModel.state.collectAsState()
        val readerSettings by viewModel.settings.collectAsState()
        val chapters = viewModel.chapters
        val scrollState = rememberScrollState()
        val coroutineScope = rememberCoroutineScope()
        var webViewRef by remember { mutableStateOf<WebView?>(null) }
        var barsVisible by remember { mutableStateOf(true) }
        val navigator = LocalNavigator.current
        rememberScreenModel { NovelReaderSettingsScreenModel() }

        // Set system UI colors - ensure consistent transparency
        MaterialTheme.colorScheme.surface
        val localView = LocalView.current
        val window = remember { localView.context.getActivity()?.window }
        val windowInsetsController = remember { window?.let { WindowCompat.getInsetsController(it, localView) } }

        // Set transparent navigation bar color (0.92f alpha for transparency)
        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)

        // Apply transparent navigation bar on first load using modern API
        LaunchedEffect(Unit) {
            window?.let { win ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val controller = win.insetsController
                    controller?.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    )
                    // Set semi-transparent navigation bar background
                    win.isNavigationBarContrastEnforced = false
                }
                // Use WindowCompat for backward compatibility
                WindowCompat.setDecorFitsSystemWindows(win, false)
            }
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
                // Restore previous Discord RPC screen
                CoroutineScope(Dispatchers.IO).launch {
                    DiscordRPCService.setScreen(context, DiscordRPCService.lastUsedScreen)
                }
                windowInsetsController?.let {
                    it.show(WindowInsetsCompat.Type.systemBars())
                    it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
                // Let system handle navigation bar appearance on exit
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
            } else if (scrollState.value <= hideThresholdPx) {
                // Near top, always show bars
                if (!barsVisible) barsVisible = true
                accumulatedScroll = 0
            }
            lastScrollOffset = scrollState.value
        }

        // Volume keys: scroll by one screen height
        val view = LocalView.current
        val scrollDownByScreen: State<() -> Unit> = rememberUpdatedState(newValue = {
            val heightPx = (view.height * 0.90f).toInt()
            if (heightPx > 0) {
                when (state) {
                    is EpubReaderState.ReflowSuccess -> {
                        val max = scrollState.maxValue
                        val target = (scrollState.value + heightPx).coerceIn(0, max)
                        coroutineScope.launch { scrollState.animateScrollTo(target) }
                    }
                    is EpubReaderState.HtmlSuccess -> {
                        webViewRef?.scrollBy(0, heightPx)
                    }
                    else -> {}
                }
            }
        })
        val scrollUpByScreen: State<() -> Unit> = rememberUpdatedState(newValue = {
            val heightPx = (view.height * 0.90f).toInt()
            if (heightPx > 0) {
                when (state) {
                    is EpubReaderState.ReflowSuccess -> {
                        val target = (scrollState.value - heightPx).coerceIn(0, scrollState.maxValue)
                        coroutineScope.launch { scrollState.animateScrollTo(target) }
                    }
                    is EpubReaderState.HtmlSuccess -> {
                        webViewRef?.scrollBy(0, -heightPx)
                    }
                    else -> {}
                }
            }
        })
        DisposableEffect(window) {
            val prev = window?.callback
            if (prev == null) {
                onDispose { }
            } else {
                val newCallback = object : Window.Callback by prev {
                    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                        if (!readerSettings.volumeButtonScroll) return prev.dispatchKeyEvent(event)
                        when (event.keyCode) {
                            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                                if (event.action == KeyEvent.ACTION_UP) {
                                    scrollDownByScreen.value.invoke()
                                }
                                return true // consume both down and up to prevent system UI volume
                            }
                            KeyEvent.KEYCODE_VOLUME_UP -> {
                                if (event.action == KeyEvent.ACTION_UP) {
                                    scrollUpByScreen.value.invoke()
                                }
                                return true // consume both down and up to prevent system UI volume
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

        // derived slider progress
        val sliderProgress = remember { mutableFloatStateOf(0f) }
        LaunchedEffect(scrollState.value) {
            if (scrollState.maxValue > 0) {
                sliderProgress.floatValue = scrollState.value.toFloat() / scrollState.maxValue
            }
        }

        // save progress debounced
        LaunchedEffect(scrollState.value, viewModel.currentChapterId) {
            if (scrollState.maxValue > 0) {
                val percent = scrollState.value.toFloat() / scrollState.maxValue
                viewModel.updateScrollProgressDebounced(percent)
            }
        }

        // Handle scroll position for chapter loading
        LaunchedEffect(state) {
            when (val currentState = state) {
                is EpubReaderState.Loading -> {
                    // reset scroll to 0 during loading state with delay
                    kotlinx.coroutines.delay(300) // delay to align with loading animation, surely this doesn't happen again
                    scrollState.scrollTo(0)
                }
                is EpubReaderState.ReflowSuccess -> {
                    val progress = currentState.progress
                    if (progress > 0f) {
                        // smooth scroll to saved progress when available
                        kotlinx.coroutines.delay(100)
                        if (scrollState.maxValue > 0) {
                            val target = (progress * scrollState.maxValue).toInt().coerceIn(0, scrollState.maxValue)
                            scrollState.animateScrollTo(target)
                        }
                    }
                }
                is EpubReaderState.HtmlSuccess -> {
                    val progress = currentState.progress
                    if (progress > 0f) {
                        // Smooth scroll to saved progress when available
                        kotlinx.coroutines.delay(100)
                        if (scrollState.maxValue > 0) {
                            val target = (progress * scrollState.maxValue).toInt().coerceIn(0, scrollState.maxValue)
                            scrollState.animateScrollTo(target)
                        }
                    }
                    // For 0 progress, content already starts at top - no action needed
                }
                else -> return@LaunchedEffect
            }
        }

        // Hide system UI bars during loading - maintain transparency
        LaunchedEffect(state is EpubReaderState.Loading) {
            val activity = view.context.getActivity() ?: return@LaunchedEffect

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
                            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        )
                } else {
                    activity.window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
                }
            }
        }

        // Bottom sheets state
        val showChapterListSheet = remember { mutableStateOf(false) }
        val showSettingsSheet = remember { mutableStateOf(false) }

        val backgroundColor = when (readerSettings.theme) {
            ReaderTheme.LIGHT -> Color.White
            ReaderTheme.SEPIA -> Color(0xFFFFE4C7)
            ReaderTheme.MINT -> Color(0xFFDDE7E3)
            ReaderTheme.BLUE_GRAY -> Color(0xFF2B2B38)
            ReaderTheme.BLACK -> Color(0xFF000000)
        }

        // Fullscreen image state
        var fullscreenImage by remember { mutableStateOf<FullscreenImageData?>(null) }

        val progressBarHeight = 40.dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor),
        ) {
            // Stationary progress percentage and optional battery/time at the bottom of the screen
            if (readerSettings.showProgressPercent && (state is EpubReaderState.ReflowSuccess || state is EpubReaderState.HtmlSuccess)) {
                val progress = sliderProgress.floatValue
                val progressPercent = (progress * 100).toInt().coerceIn(0, 100)
                // Determine text color based on reader theme
                val percentTextColor = when (readerSettings.theme) {
                    ReaderTheme.LIGHT, ReaderTheme.SEPIA, ReaderTheme.MINT -> Color.Black
                    ReaderTheme.BLUE_GRAY, ReaderTheme.BLACK -> Color.White
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(progressBarHeight)
                        .background(backgroundColor),
                ) {
                    BatteryTimeBar(
                        progressPercent = progressPercent,
                        showBatteryAndTime = readerSettings.showBatteryAndTime,
                        textColor = percentTextColor,
                        modifier = Modifier.fillMaxSize(),
                        backgroundColor = backgroundColor,
                    )
                }
            }
            // Main content area with fade transition
            AnimatedContent(
                targetState = state,
                modifier = Modifier.padding(bottom = if (readerSettings.showProgressPercent && (state is EpubReaderState.ReflowSuccess || state is EpubReaderState.HtmlSuccess)) progressBarHeight else 0.dp),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
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
                                    chapterTitle = animatedState.chapterTitle,
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
                                }.also { created -> webViewRef = created }
                            },
                            update = { wv -> webViewRef = wv },
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
                val (titleText, subtitleText) = when (val s = state) {
                    is EpubReaderState.ReflowSuccess -> s.bookTitle to s.chapterTitle
                    is EpubReaderState.HtmlSuccess -> s.bookTitle to s.chapterTitle
                    else -> "EPUB Reader" to null
                }
                val currentChapter = viewModel.chapters.find { it.id == viewModel.currentChapterId }
                val bookmarked = currentChapter?.bookmark == true
                ReaderTopBar(
                    title = titleText,
                    subtitle = subtitleText,
                    bookmarked = bookmarked,
                    onBack = { navigator?.pop() },
                    onToggleBookmark = { currentChapter?.let { viewModel.toggleBookmark(it) } },
                    backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                )
            }

            // Progress bar above bottom bar, hides/shows with barsVisible or while interacting
            var sliderInUse by remember { mutableStateOf(false) }
            // Measure bottom bar height for dynamic slider padding
            val density = LocalDensity.current
            var bottomBarHeightDp by remember { mutableStateOf(52.dp) }
            val sliderExtraBottomPadding = 16.dp
            AnimatedVisibility(
                visible = (barsVisible || sliderInUse) && (state !is EpubReaderState.Loading),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                val maxScroll = scrollState.maxValue
                val textColor = when (readerSettings.theme) {
                    ReaderTheme.LIGHT -> Color(0xFF222222)
                    ReaderTheme.SEPIA -> Color(0xFF6B4F1D)
                    ReaderTheme.MINT -> Color(0xFF2B3A35)
                    ReaderTheme.BLUE_GRAY -> Color(0xFFE6E6F2)
                    ReaderTheme.BLACK -> Color(0xFFECECEC)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                        .navigationBarsPadding()
                        .padding(bottom = bottomBarHeightDp + sliderExtraBottomPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReaderProgressSlider(
                        progress = sliderProgress.floatValue,
                        onProgressChange = { newProgress ->
                            sliderInUse = true
                            sliderProgress.floatValue = newProgress
                            if (maxScroll > 0) {
                                val target = (sliderProgress.floatValue * maxScroll).toInt()
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

                ReaderBottomBar(
                    hasPrev = when (s) {
                        is EpubReaderState.ReflowSuccess -> s.hasPrev
                        is EpubReaderState.HtmlSuccess -> s.hasPrev
                        else -> false
                    },
                    hasNext = when (s) {
                        is EpubReaderState.ReflowSuccess -> s.hasNext
                        is EpubReaderState.HtmlSuccess -> s.hasNext
                        else -> false
                    },
                    onPrev = { viewModel.prevChapter() },
                    onNext = { viewModel.nextChapter() },
                    onChapterList = { showChapterListSheet.value = true },
                    onScrollTop = { coroutineScope.launch { scrollState.animateScrollTo(0) } },
                    onSettings = { showSettingsSheet.value = true },
                    backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    modifier = Modifier.onGloballyPositioned { layoutCoordinates ->
                    },
                )
            }

            // Chapter list bottom sheet
            EpubChapterListSheet(
                viewModel = viewModel,
                show = showChapterListSheet.value,
                onDismiss = { showChapterListSheet.value = false },
            )
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

        // Flush read timer when leaving the screen
        DisposableEffect(viewModel) {
            onDispose {
                viewModel.flushReadTimer()
            }
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
                settings.displayZoomControls = false
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
        ResourcesCompat.getFont(this, fontResId)
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

@SuppressLint("UnusedBoxWithConstraintsScope", "UseKtx")
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
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
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
                            onDrag = { _, dragAmount ->
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
