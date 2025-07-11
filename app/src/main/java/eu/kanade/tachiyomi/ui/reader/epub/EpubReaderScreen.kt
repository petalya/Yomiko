// SPDX-License-Identifier: Apache-2.0
package eu.kanade.tachiyomi.ui.reader.epub

import android.annotation.SuppressLint
import android.util.Base64
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.manga.components.MangaChapterListItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.reader.NovelReaderSettingsBottomSheet
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderSettingsScreenModel
import kotlinx.coroutines.launch
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.model.ChapterSwipeAction
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.util.plus
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter

class EpubReaderScreen(
    private val mangaId: Long,
    private val chapterId: Long,
    private val chapterUrl: String = "",
) : Screen {
    @Composable
    override fun Content() {
        val viewModel = rememberScreenModel { EpubReaderViewModel(mangaId, chapterId, chapterUrl) }
        val state by viewModel.state.collectAsState()
        val chapters = viewModel.chapters
        val scrollState = rememberScrollState()
        val coroutineScope = rememberCoroutineScope()
        var barsVisible by remember { mutableStateOf(true) }
        val navigator = LocalNavigator.current
        val settingsModel = rememberScreenModel { NovelReaderSettingsScreenModel() }
        val fontSize by settingsModel.fontSize.collectAsState()
        val textAlignment by settingsModel.textAlignment.collectAsState()
        val lineSpacing by settingsModel.lineSpacing.collectAsState()
        val colorScheme by settingsModel.colorScheme.collectAsState()
        val fontFamilyPref by settingsModel.fontFamily.collectAsState()
        val fontFamily = when (fontFamilyPref) {
            NovelReaderPreferences.FontFamilyPref.ORIGINAL -> null
            NovelReaderPreferences.FontFamilyPref.LORA -> FontFamily(Font(R.font.lora))
            NovelReaderPreferences.FontFamilyPref.OPEN_SANS -> FontFamily(Font(R.font.open_sans))
            NovelReaderPreferences.FontFamilyPref.ARBUTUS_SLAB -> FontFamily(Font(R.font.arbutus_slab))
            NovelReaderPreferences.FontFamilyPref.LATO -> FontFamily(Font(R.font.lato))
            else -> null
        }

        // Set system UI colors - ensure it matches the bottom bar
        val surfaceColor = MaterialTheme.colorScheme.surface
        val view = LocalView.current
        val window = remember { view.context.getActivity()?.window }
        val windowInsetsController = remember { window?.let { WindowCompat.getInsetsController(it, view) } }

        // Apply immersive mode based on bars visibility
        LaunchedEffect(barsVisible) {
            windowInsetsController?.let {
                if (!barsVisible) {
                    // Hide system bars when our UI bars are hidden (immersive mode)
                    it.hide(WindowInsetsCompat.Type.systemBars())
                    it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    // Show system bars when our UI bars are visible
                    it.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        // Restore system UI when leaving the screen
        DisposableEffect(Unit) {
            onDispose {
                windowInsetsController?.let {
                    it.show(WindowInsetsCompat.Type.systemBars())
                    it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                }
                window?.navigationBarColor = Color.Transparent.toArgb()
            }
        }

        // Set navigation bar color
        LaunchedEffect(surfaceColor) {
            window?.navigationBarColor = surfaceColor.toArgb()
            windowInsetsController?.isAppearanceLightNavigationBars = isColorLight(surfaceColor)
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

        // Chapter list and settings bottom sheet state
        var showChapterListSheet = remember { mutableStateOf(false) }
        var showSettingsSheet = remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxSize()) {
            when (val s = state) {
                is EpubReaderState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is EpubReaderState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.message)
                }
                is EpubReaderState.Success -> {
                    // Use a single-column scrollable column containing WebView for now
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorScheme.background)
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    barsVisible = !barsVisible
                                }
                            },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState),
                        ) {
                            // Create a CSS style based on user settings
                            val cssStyle = buildString {
                                append("body{")
                                append("color:${colorScheme.text.toCssColor()}!important;")
                                append("background:${colorScheme.background.toCssColor()}!important;")
                                append("margin:0;padding:120px 20px 32px 20px;") // Top: 120px, Right/Left: 20px, Bottom: 32px
                                append("font-size:${fontSize}px!important;")
                                append("line-height:${lineSpacing / 100f}!important;")
                                append("max-width:800px;")
                                append("margin-left:auto;")
                                append("margin-right:auto;")
                                append("-webkit-tap-highlight-color:transparent;") // Prevent tap highlight
                                append("-webkit-touch-callout:none;") // Prevent callout
                                append("user-select:none;") // Prevent text selection

                                // Font family
                                when (fontFamilyPref) {
                                    NovelReaderPreferences.FontFamilyPref.LORA -> append("font-family:'Lora',serif!important;")
                                    NovelReaderPreferences.FontFamilyPref.OPEN_SANS -> append("font-family:'Open Sans',sans-serif!important;")
                                    NovelReaderPreferences.FontFamilyPref.ARBUTUS_SLAB -> append("font-family:'Arbutus Slab',serif!important;")
                                    NovelReaderPreferences.FontFamilyPref.LATO -> append("font-family:'Lato',sans-serif!important;")
                                    else -> {} // Use original font
                                }

                                // Text alignment
                                when (textAlignment) {
                                    NovelReaderPreferences.TextAlignment.Left -> append("text-align:left!important;")
                                    NovelReaderPreferences.TextAlignment.Center -> append("text-align:center!important;")
                                    NovelReaderPreferences.TextAlignment.Right -> append("text-align:right!important;")
                                    NovelReaderPreferences.TextAlignment.Justify -> append("text-align:justify!important;")
                                }

                                append("}")
                                append("img{max-width:100%;height:auto}")
                                append("p{margin:0.5em 0; text-indent:1.2em}") // Add paragraph indentation

                                // Add JavaScript to prevent text selection
                                append("</style><script>")
                                append("document.addEventListener('click', function(e) { e.preventDefault(); }, false);")
                                append("document.addEventListener('mousedown', function(e) { e.preventDefault(); }, false);")
                                append("document.addEventListener('mouseup', function(e) { e.preventDefault(); }, false);")
                                append("document.addEventListener('selectionchange', function() { ")
                                append("  if (window.getSelection) { window.getSelection().removeAllRanges(); }")
                                append("  else if (document.selection) { document.selection.empty(); }")
                                append("}, false);")
                                append("</script><style>")
                            }

                            // Apply the CSS to the HTML content
                            val htmlWithStyles = """
                                <html>
                                    <head>
                                        <style>
                                            $cssStyle
                                        </style>
                                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                    </head>
                                    <body>${extractBodyContent(s.content)}</body>
                                </html>
                            """.trimIndent()

                            WebContent(
                                html = htmlWithStyles,
                                onTap = { barsVisible = !barsVisible },
                            )
                        }
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
                val s = state as? EpubReaderState.Success ?: return@AnimatedVisibility
                AppBar(
                    titleContent = {
                        AppBarTitle(title = s.bookTitle, subtitle = s.chapterTitle)
                    },
                    navigateUp = { navigator?.pop() },
                    actions = {
                        IconButton(onClick = {
                            viewModel.currentChapterId?.let { id ->
                                chapters.find { it.id == id }?.let { ch -> viewModel.toggleBookmark(ch) }
                            }
                        }) {
                            Icon(
                                if (s.bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = "Bookmark",
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
                val s = state as? EpubReaderState.Success ?: return@AnimatedVisibility
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
                        // Previous chapter button
                        IconButton(
                            onClick = { viewModel.prevChapter() },
                            enabled = s.hasPrev,
                            modifier = Modifier.alpha(if (s.hasPrev) 1f else 0.5f),
                        ) {
                            Icon(
                                Icons.Filled.SkipPrevious,
                                contentDescription = "Previous chapter",
                                tint = if (s.hasPrev) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
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
                        // Next chapter button
                        IconButton(
                            onClick = { viewModel.nextChapter() },
                            enabled = s.hasNext,
                            modifier = Modifier.alpha(if (s.hasNext) 1f else 0.5f),
                        ) {
                            Icon(
                                Icons.Filled.SkipNext,
                                contentDescription = "Next chapter",
                                tint = if (s.hasNext) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
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
            NovelReaderSettingsBottomSheet(
                model = settingsModel,
                onDismiss = { showSettingsSheet.value = false },
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

@Composable
private fun FontPill(label: String, selected: Boolean, onClick: () -> Unit, fontFamily: FontFamily?) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.Gray),
        modifier = Modifier
            .height(36.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                text = label,
                fontFamily = fontFamily,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

data class ChapterItem(
    val chapter: Chapter,
    val isCurrent: Boolean,
    val downloadState: Download.State,
    val downloadProgress: Int,
)
