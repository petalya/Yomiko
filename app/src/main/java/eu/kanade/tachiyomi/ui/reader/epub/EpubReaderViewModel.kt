package eu.kanade.tachiyomi.ui.reader.epub

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.data.connections.discord.ReaderData
import eu.kanade.tachiyomi.ui.reader.epub.EpubReaderSettings
import eu.kanade.tachiyomi.util.epub.EpubChapter
import eu.kanade.tachiyomi.util.epub.EpubContentBlock
import eu.kanade.tachiyomi.util.epub.EpubDocument
import eu.kanade.tachiyomi.util.epub.EpubParser
import eu.kanade.tachiyomi.util.epub.EpubTableOfContentsEntry
import eu.kanade.tachiyomi.util.epub.ReaderTheme
import eu.kanade.tachiyomi.util.epub.TextAlignment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.math.absoluteValue

/**
 * Screen-independent state-holder for reading local EPUB files.
 * Supports both reflowable and fixed-layout EPUBs.
 */
class EpubReaderViewModel(
    private val mangaId: Long,
    private val initialChapterId: Long,
    private val initialChapterUrl: String,
) : ScreenModel {
    private val chapterRepo: ChapterRepository = Injekt.get()
    private val getManga: GetManga = Injekt.get()
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get()
    private val epubParser = EpubParser()
    private val epubPreferences = EpubReaderPreferences(Injekt.get())
    private val upsertHistory: UpsertHistory = Injekt.get()

    // Define a coroutine scope for the view model
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var saveProgressJob: Job? = null
    private var lastSavedProgress = 0f
    private val readThreshold = 0.9f

    // No longer needed - we use the chapter's actual saved progress

    private val _state = MutableStateFlow<EpubReaderState>(EpubReaderState.Loading)
    val state: StateFlow<EpubReaderState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(EpubReaderSettings())
    val settings: StateFlow<EpubReaderSettings> = _settings.asStateFlow()

    private var _chapters = mutableStateOf<List<Chapter>>(emptyList())
    val chapters: List<Chapter> get() = _chapters.value

    var manga: Manga? = null
        private set

    var currentChapterId: Long? = null
        private set

    private var currentChapterIndex: Int = -1

    // Cache the parsed EPUB document
    private var parsedEpub: EpubDocument? = null

    // Cache the extracted content blocks for the current chapter
    private var currentChapterBlocks = mutableListOf<EpubContentBlock>()

    // Current chapter in the epub document
    private var currentEpubChapterIndex: Int = 0

    init {
        coroutineScope.launch {
            // Load persisted settings from preferences
            _settings.value = EpubReaderSettings(
                fontSize = epubPreferences.fontSize().get(),
                fontFamily = epubPreferences.fontFamily().get().ifEmpty { null },
                lineSpacing = epubPreferences.lineSpacing().get(),
                alignment = epubPreferences.textAlignment().get(),
                theme = epubPreferences.theme().get(),
                showProgressPercent = epubPreferences.showProgressPercent().get(),
            )
            try {
                // Load manga and chapter data
                manga = getManga.await(mangaId)
                if (manga == null) {
                    _state.value = EpubReaderState.Error("Manga not found")
                    return@launch
                }

                // Load all chapters for this manga
                _chapters.value = getChaptersByMangaId.await(mangaId).sortedBy { it.sourceOrder }

                // Find the initial chapter
                val initialChapter = _chapters.value.find { it.id == initialChapterId }
                if (initialChapter == null) {
                    _state.value = EpubReaderState.Error("Chapter not found")
                    return@launch
                }

                // Set current chapter and load it
                currentChapterIndex = _chapters.value.indexOf(initialChapter)
                currentChapterId = initialChapter.id

                // Parse the EPUB file
                val epubFile = getEpubFile(initialChapter.url)
                if (epubFile == null || !epubFile.exists()) {
                    _state.value = EpubReaderState.Error("EPUB file not found")
                    return@launch
                }

                try {
                    parsedEpub = epubParser.parseFile(epubFile)
                    loadChapter(initialChapter)
                } catch (e: Exception) {
                    _state.value = EpubReaderState.Error("Failed to parse EPUB: ${e.message}")
                }
            } catch (e: Exception) {
                _state.value = EpubReaderState.Error("Failed to load: ${e.message}")
            }
        }
    }

    fun updateSettings(newSettings: EpubReaderSettings) {
        _settings.value = newSettings
        // No need to reload/reprocess chapter for visual-only settings changes
    }

    fun setShowProgressPercent(enabled: Boolean) {
        _settings.value = _settings.value.copy(showProgressPercent = enabled)
        coroutineScope.launch { epubPreferences.showProgressPercent().set(enabled) }
    }

    fun setFontSize(size: Float) {
        _settings.value = _settings.value.copy(fontSize = size)
        coroutineScope.launch { epubPreferences.fontSize().set(size) }
    }

    fun setFontFamily(family: String?) {
        _settings.value = _settings.value.copy(fontFamily = family)
        coroutineScope.launch { epubPreferences.fontFamily().set(family ?: "") }
    }

    fun setLineSpacing(spacing: Float) {
        _settings.value = _settings.value.copy(lineSpacing = spacing)
        coroutineScope.launch { epubPreferences.lineSpacing().set(spacing) }
    }

    fun setTextAlignment(alignment: TextAlignment) {
        _settings.value = _settings.value.copy(alignment = alignment)
        coroutineScope.launch { epubPreferences.textAlignment().set(alignment) }
    }

    fun setTheme(theme: ReaderTheme) {
        _settings.value = _settings.value.copy(theme = theme)
        coroutineScope.launch { epubPreferences.theme().set(theme) }
    }

    fun toggleScrollMode() {
        _settings.value = _settings.value.copy(isScrollMode = !_settings.value.isScrollMode)
    }

    /**
     * Load a specific chapter from the book
     */
    private fun loadChapter(chapter: Chapter) {
        Log.d("EpubReaderVM", "loadChapter called: chapterId=${chapter.id}, chapterIndex=${_chapters.value.indexOf(chapter)}")
        coroutineScope.launch {
            try {
                _state.value = EpubReaderState.Loading
                val epub = parsedEpub ?: throw IllegalStateException("EPUB not parsed yet")
                val internalHref = chapter.url.substringAfter("::", "")
                currentEpubChapterIndex = if (internalHref.isNotEmpty()) {
                    epub.chapters.indexOfFirst { it.href == internalHref }
                } else {
                    0
                }
                if (currentEpubChapterIndex < 0) currentEpubChapterIndex = 0
                val epubChapter = epub.chapters.getOrNull(currentEpubChapterIndex)
                    ?: throw IllegalStateException("Chapter not found in EPUB")
                currentChapterId = chapter.id
                currentChapterIndex = _chapters.value.indexOf(chapter)
                recordHistory(chapter.id)
                updateDiscordRPC()
                Log.d("EpubReaderVM", "After load: currentEpubChapterIndex=$currentEpubChapterIndex, currentChapterIndex=$currentChapterIndex, currentChapterId=$currentChapterId")
                currentChapterBlocks = epubParser.extractContentBlocks(epubChapter.content).toMutableList()
                // --- Enhancement: Add section headings and <hr> for nested TOC entries ---
                val tocEntry = epub.tableOfContents.find { it.href == epubChapter.href }
                val allEmbeddedResources = mutableMapOf<String, ByteArray>()
                allEmbeddedResources.putAll(epubChapter.embeddedResources)
                if (tocEntry != null && tocEntry.children.isNotEmpty()) {
                    tocEntry.children.forEach { section ->
                        // Add <hr> separator (as a blank line or custom block)
                        currentChapterBlocks.add(EpubContentBlock.Text("\n"))
                        // Add heading for the section
                        currentChapterBlocks.add(EpubContentBlock.Paragraph(section.title))
                        // Find the corresponding chapter for the section
                        val sectionChapter = epub.chapters.find { it.href == section.href }
                        if (sectionChapter != null) {
                            val sectionBlocks = epubParser.extractContentBlocks(sectionChapter.content)
                            currentChapterBlocks.addAll(sectionBlocks)
                            allEmbeddedResources.putAll(sectionChapter.embeddedResources)
                        }
                    }
                }
                // Use a copy of epubChapter with merged resources for image processing
                val mergedChapter = epubChapter.copy(embeddedResources = allEmbeddedResources)
                processEmbeddedResources(mergedChapter)
                if (epubChapter.isHtml) {
                    _state.value = EpubReaderState.ReflowSuccess(
                        bookTitle = epub.title,
                        chapterTitle = epubChapter.title ?: "Chapter " + (currentEpubChapterIndex + 1),
                        contentBlocks = currentChapterBlocks,
                        hasPrev = currentChapterIndex > 0,
                        hasNext = currentChapterIndex < _chapters.value.size - 1,
                        progress = getSavedProgress(chapter.id),
                    )
                } else {
                    _state.value = EpubReaderState.HtmlSuccess(
                        bookTitle = epub.title,
                        chapterTitle = epubChapter.title ?: "Chapter " + (currentEpubChapterIndex + 1),
                        content = epubChapter.content,
                        hasPrev = currentChapterIndex > 0,
                        hasNext = currentChapterIndex < _chapters.value.size - 1,
                        progress = getSavedProgress(chapter.id),
                    )
                }
                refreshChapters()
            } catch (e: Exception) {
                _state.value = EpubReaderState.Error("Failed to load chapter: " + e.message)
            }
        }
    }

    /**
     * Process embedded resources (images, CSS) and add them to the content blocks
     */
    private fun processEmbeddedResources(chapter: EpubChapter) {
        // Create a list to hold unresolved image references for logging
        val unresolvedImages = mutableListOf<String>()

        // Get app context for creating cache files
        val context = Injekt.get<Context>()
        val cacheDir = context.cacheDir
        val epubImagesDir = File(cacheDir, "epub_images").also { it.mkdirs() }

        for (i in currentChapterBlocks.indices) {
            val block = currentChapterBlocks[i]
            when (block) {
                is EpubContentBlock.Image -> {
                    val src = block.src

                    // For base64 encoded images
                    if (src.startsWith("data:")) {
                        // These are handled directly by the parser
                        continue
                    }

                    // Always decode the src before lookup
                    val decodedSrc = try {
                        java.net.URLDecoder.decode(src, "UTF-8")
                    } catch (e: Exception) {
                        src
                    }
                    var imageData = chapter.embeddedResources[decodedSrc]

                    // If direct path fails, try with various path manipulations
                    if (imageData == null) {
                        // Try with just the filename
                        val filename = decodedSrc.substringAfterLast('/')
                        imageData = chapter.embeddedResources.entries.find { it.key.endsWith(filename) }?.value
                    }

                    if (imageData != null) {
                        // Convert image data to a file URI for better handling
                        val imageUri = saveImageToCache(imageData, src, epubImagesDir)
                        if (imageUri != null) {
                            // Create a new block with the image data and URI
                            currentChapterBlocks[i] = block.copy(data = imageData, src = imageUri.toString())
                        } else {
                            // Fall back to using raw data
                            currentChapterBlocks[i] = block.copy(data = imageData)
                        }
                    } else {
                        unresolvedImages.add(src)
                    }
                }
                is EpubContentBlock.Embed -> {
                    val src = block.src
                    val decodedSrc = try {
                        java.net.URLDecoder.decode(src, "UTF-8")
                    } catch (e: Exception) {
                        src
                    }
                    val embedData = chapter.embeddedResources[decodedSrc]
                        ?: chapter.embeddedResources.entries.find { it.key.endsWith(decodedSrc.substringAfterLast('/')) }?.value

                    if (embedData != null) {
                        // For image embeds, create a file URI for better handling
                        if (block.mediaType.startsWith("image/")) {
                            val imageUri = saveImageToCache(embedData, src, epubImagesDir)
                            if (imageUri != null) {
                                currentChapterBlocks[i] = block.copy(data = embedData, src = imageUri.toString())
                            } else {
                                currentChapterBlocks[i] = block.copy(data = embedData)
                            }
                        } else {
                            currentChapterBlocks[i] = block.copy(data = embedData)
                        }
                    }
                }
                else -> {} // No processing needed
            }
        }

        // Log unresolved images for debugging
        if (unresolvedImages.isNotEmpty()) {
            logcat { "Unresolved image paths: $unresolvedImages" }
        }
    }

    /**
     * Save image data to a cache file and return its URI
     */
    private fun saveImageToCache(imageData: ByteArray, src: String, cacheDir: File): Uri? {
        try {
            val extension = when {
                src.lowercase().endsWith(".jpg") || src.lowercase().endsWith(".jpeg") -> ".jpg"
                src.lowercase().endsWith(".png") -> ".png"
                src.lowercase().endsWith(".gif") -> ".gif"
                src.lowercase().endsWith(".webp") -> ".webp"
                src.lowercase().endsWith(".svg") -> ".svg"
                else -> {
                    // Try to detect from the first few bytes
                    when {
                        imageData.size > 2 && imageData[0] == 0xFF.toByte() && imageData[1] == 0xD8.toByte() -> ".jpg"
                        imageData.size > 4 &&
                            imageData[0] == 0x89.toByte() &&
                            imageData[1] == 0x50.toByte() &&
                            imageData[2] == 0x4E.toByte() &&
                            imageData[3] == 0x47.toByte() -> ".png"
                        imageData.size > 3 &&
                            imageData[0] == 0x47.toByte() &&
                            imageData[1] == 0x49.toByte() &&
                            imageData[2] == 0x46.toByte() -> ".gif"
                        else -> ".bin" // Fallback
                    }
                }
            }

            // Create a unique filename based on the source path
            val filename = "${src.hashCode().absoluteValue}$extension"
            val imageFile = File(cacheDir, filename)

            // Only write if file doesn't exist yet
            if (!imageFile.exists()) {
                FileOutputStream(imageFile).use { it.write(imageData) }
            }

            return Uri.fromFile(imageFile)
        } catch (e: Exception) {
            logcat { "Failed to save image to cache: ${e.message}" }
            return null
        }
    }

    /**
     * Navigate to previous chapter or previous internal chapter
     */
    fun prevChapter() {
        if (currentChapterIndex > 0) {
            currentChapterIndex--
            currentChapterId = _chapters.value.getOrNull(currentChapterIndex)?.id
            _state.value = EpubReaderState.Loading
            coroutineScope.launch {
                kotlinx.coroutines.delay(600)
                loadChapter(_chapters.value[currentChapterIndex])
            }
        }
    }

    /**
     * Navigate to next chapter or next internal chapter
     */
    fun nextChapter() {
        if (currentChapterIndex < _chapters.value.size - 1) {
            currentChapterIndex++
            currentChapterId = _chapters.value.getOrNull(currentChapterIndex)?.id
            _state.value = EpubReaderState.Loading
            coroutineScope.launch {
                kotlinx.coroutines.delay(600)
                loadChapter(_chapters.value[currentChapterIndex])
            }
        }
    }

    /**
     * Jump to a specific chapter from the chapters list
     */
    fun jumpToChapter(index: Int) {
        if (index in _chapters.value.indices) {
            currentChapterIndex = index
            currentChapterId = _chapters.value.getOrNull(currentChapterIndex)?.id
            _state.value = EpubReaderState.Loading
            coroutineScope.launch {
                kotlinx.coroutines.delay(600)
                loadChapter(_chapters.value[currentChapterIndex])
            }
        }
    }

    /**
     * Jump to a specific table of contents entry
     */
    fun jumpToTableOfContentsEntry(href: String) {
        val epub = parsedEpub ?: return

        // Find which chapter contains this TOC entry
        val targetChapterIndex = epub.chapters.indexOfFirst { it.href == href }
        if (targetChapterIndex >= 0) {
            currentEpubChapterIndex = targetChapterIndex
            currentChapterId?.let { id ->
                chapters.find { it.id == id }?.let { loadChapter(it) }
            }
        }
    }

    /**
     * Toggle read state for a chapter
     */
    fun toggleRead(chapter: Chapter) {
        CoroutineScope(Dispatchers.IO).launch {
            val newReadState = !chapter.read
            chapterRepo.update(ChapterUpdate(id = chapter.id, read = newReadState))

            // Refresh chapters list to reflect the change
            refreshChapters()
        }
    }

    /**
     * Toggle bookmark state for a chapter
     */
    fun toggleBookmark(chapter: Chapter) {
        CoroutineScope(Dispatchers.IO).launch {
            val newBookmark = !chapter.bookmark
            chapterRepo.update(ChapterUpdate(id = chapter.id, bookmark = newBookmark))
            refreshChapters()
        }
    }

    /**
     * Update reading progress with debounce
     */
    fun updateScrollProgressDebounced(progress: Float) {
        val chapterId = currentChapterId ?: return
        if (kotlin.math.abs(progress - lastSavedProgress) < 0.001) return
        lastSavedProgress = progress
        saveProgressJob?.cancel()
        saveProgressJob = CoroutineScope(Dispatchers.IO).launch {
            delay(500) // 0.5s debounce
            val percentInt = (progress * 1000).toLong()
            chapterRepo.update(ChapterUpdate(id = chapterId, lastPageRead = percentInt))
            recordHistory(chapterId)
            // Mark as read if progress exceeds threshold
            if (progress >= readThreshold) {
                val chapter = _chapters.value.find { it.id == chapterId }
                if (chapter != null && !chapter.read) {
                    chapterRepo.update(ChapterUpdate(id = chapterId, read = true))
                    refreshChapters()
                }
            }
        }
    }

    private suspend fun refreshChapters() {
        val updatedChapters = getChaptersByMangaId.await(mangaId).sortedBy { it.sourceOrder }
        _chapters.value = updatedChapters
    }

    /**
     * Get saved progress for a chapter
     */
    fun getSavedProgress(chapterId: Long): Float {
        val chapter = _chapters.value.find { it.id == chapterId }
        return if (chapter != null && chapter.lastPageRead > 0) chapter.lastPageRead / 1000f else 0f
    }

    /**
     * Get the File object for the EPUB from the chapter URL
     */
    private fun getEpubFile(chapterUrl: String): File? {
        try {
            // Load EPUB file from URL
            val epubPath = resolveEpubPath(chapterUrl)
            val inputStream = openEpubInputStream(epubPath) ?: return null

            // Create a temporary file for the EPUB
            val cacheFile = File(
                Injekt.get<Context>().cacheDir,
                "epub_" + UUID.randomUUID().toString() + ".epub",
            )

            // Copy the input stream to the cache file
            FileOutputStream(cacheFile).use { output ->
                inputStream.copyTo(output)
            }

            return cacheFile
        } catch (e: Exception) {
            return null
        }
    }

    private fun resolveEpubPath(raw: String): String {
        // Keep only the path to the .epub archive (strip chapter anchor)
        var path = raw.substringBefore("::")

        // For SAF URIs we leave them untouched
        if (path.startsWith("content://")) return path

        // Strip file:// scheme
        if (path.startsWith("file://")) {
            path = Uri.parse(path).path ?: path.removePrefix("file://")
        }

        // For absolute filesystem paths just return them
        if (path.startsWith("/")) return path

        // Otherwise leave as relative path (mangaDir/book.epub) to be resolved via StorageManager
        return path
    }

    /**
     * Opens an InputStream to the given EPUB path, supporting:
     *  – SAF content URIs
     *  – Absolute filesystem paths
     *  – Relative paths inside the Local source directory (handled via StorageManager/UniFile).
     */
    private fun openEpubInputStream(path: String): java.io.InputStream? {
        return when {
            path.startsWith("content://") -> {
                val uri = Uri.parse(path)
                Injekt.get<Context>().contentResolver.openInputStream(uri)
            }
            path.startsWith("/") -> {
                java.io.FileInputStream(File(path))
            }
            else -> {
                // Relative path within Local source folder
                val storageManager = Injekt.get<StorageManager>()
                var file: UniFile? = storageManager.getLocalSourceDirectory()
                path.split('/')
                    .filter { it.isNotEmpty() }
                    .forEach { segment ->
                        file = file?.findFile(segment)
                    }
                file?.openInputStream()
            }
        }
    }

    fun getTableOfContents(): List<EpubTableOfContentsEntry> {
        return parsedEpub?.tableOfContents ?: emptyList()
    }

    private fun recordHistory(chapterId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            val now = java.util.Date()
            upsertHistory.await(HistoryUpdate(chapterId, now, 0L))
        }
    }

    private fun updateDiscordRPC() {
        val connectionsPreferences = Injekt.get<eu.kanade.domain.connections.service.ConnectionsPreferences>()
        val chapter = _chapters.value.getOrNull(currentChapterIndex)
        val context = Injekt.get<android.content.Context>()
        val chapterNumberFloat = chapter?.chapterNumber?.toFloat() ?: -1f
        CoroutineScope(Dispatchers.IO).launch {
            val latestManga = getManga.await(mangaId)
            val mangaCover = latestManga?.asMangaCover()
            val coverUrl = mangaCover?.url as? String
            val epubIconUrl = "emojis/1396447904264359997.webp?quality=lossless"
            if (latestManga != null && chapter != null && connectionsPreferences.enableDiscordRPC().get()) {
                if (latestManga.source == 0L) {
                    // Use EPUB local feed icon for local source EPUBs
                    DiscordRPCService.setScreen(
                        context = context,
                        discordScreen = DiscordScreen.EPUB_LOCAL_FEED,
                        readerData = ReaderData(
                            incognitoMode = latestManga.incognitoMode,
                            mangaId = latestManga.id,
                            mangaTitle = "EPUB: ${latestManga.title}",
                            thumbnailUrl = epubIconUrl,
                            chapterNumber = Pair(chapterNumberFloat, _chapters.value.size),
                            chapterTitle = if (connectionsPreferences.useChapterTitles().get()) chapter.name else chapter.chapterNumber.toString(),
                        ),
                    )
                } else if (coverUrl != null) {
                    DiscordRPCService.setReaderActivity(
                        context = context,
                        readerData = ReaderData(
                            incognitoMode = latestManga.incognitoMode,
                            mangaId = latestManga.id,
                            mangaTitle = "EPUB: ${latestManga.title}",
                            thumbnailUrl = coverUrl,
                            chapterNumber = Pair(chapterNumberFloat, _chapters.value.size),
                            chapterTitle = if (connectionsPreferences.useChapterTitles().get()) chapter.name else chapter.chapterNumber.toString(),
                        ),
                    )
                }
            }
        }
    }

    companion object {
        /**
         * Clears the EPUB image cache directory (epub_images) in the app's cacheDir.
         * @param context The application context.
         * @return The number of files deleted.
         */
        fun clearEpubCache(context: Context): Int {
            var deletedFiles = 0
            // 1. Clear epub_images
            val epubImagesDir = File(context.cacheDir, "epub_images")
            if (epubImagesDir.exists() && epubImagesDir.isDirectory) {
                epubImagesDir.listFiles()?.forEach {
                    if (it.delete()) deletedFiles++
                }
            }
            // 2. Clear temp EPUB files (epub_*.epub)
            context.cacheDir.listFiles()?.forEach {
                if (it.isFile && it.name.startsWith("epub_") && it.name.endsWith(".epub")) {
                    if (it.delete()) deletedFiles++
                }
            }
            // 3. Clear UniFileTempFileManager temp files (externalCacheDir/tmp)
            val tmpDir = File(context.externalCacheDir, "tmp")
            if (tmpDir.exists() && tmpDir.isDirectory) {
                tmpDir.listFiles()?.forEach {
                    if (it.delete()) deletedFiles++
                }
            }
            return deletedFiles
        }
    }
}
