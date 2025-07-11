package eu.kanade.tachiyomi.ui.reader.epub

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import com.hippo.unifile.UniFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Screen-independent state-holder for reading local EPUB files.
 * Mirrors [NovelReaderViewModel] structure so UI layer can be reused.
 */
class EpubReaderViewModel(
    private val mangaId: Long,
    private val initialChapterId: Long,
    private val initialChapterUrl: String,
) : ScreenModel {
    private val chapterRepo: ChapterRepository = Injekt.get()
    private val getManga: GetManga = Injekt.get()
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get()

    private val _state = MutableStateFlow<EpubReaderState>(EpubReaderState.Loading)
    val state: StateFlow<EpubReaderState> = _state.asStateFlow()

    private var _chapters = mutableStateOf<List<Chapter>>(emptyList())
    val chapters: List<Chapter> get() = _chapters.value

    var manga: Manga? = null
        private set

    var currentChapterId: Long? = null
        private set
    private var currentChapterIndex: Int = -1

    // Debounce job for saving progress
    private var saveProgressJob: Job? = null
    private var lastSavedProgress: Float = -1f

    // Progress threshold for marking chapter as read (98%)
    private val readThreshold = 0.98f

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Load manga and chapter data
                manga = getManga.await(mangaId)
                if (manga == null) {
                    _state.value = EpubReaderState.Error("Manga not found")
                    return@launch
                }

                // Load all chapters for this manga
                _chapters.value = getChaptersByMangaId.await(mangaId).sortedBy { it.sourceOrder }

                // Find the initial chapter index
                val initialChapter = _chapters.value.find { it.id == initialChapterId }
                if (initialChapter == null) {
                    _state.value = EpubReaderState.Error("Chapter not found")
                    return@launch
                }

                // Set current chapter and load it
                currentChapterIndex = _chapters.value.indexOf(initialChapter)
                currentChapterId = initialChapter.id
                loadChapter(initialChapter)
            } catch (e: Exception) {
                _state.value = EpubReaderState.Error("Failed to load: ${e.message}")
            }
        }
    }

    private fun loadChapter(chapter: Chapter) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                _state.value = EpubReaderState.Loading

                // Extract content from the chapter
                val content = extractEpubContent(chapter.url)

                // Update current chapter ID
                currentChapterId = chapter.id
                currentChapterIndex = _chapters.value.indexOf(chapter)

                // Update state with success
                _state.value = EpubReaderState.Success(
                    bookTitle = manga?.title ?: "",
                    chapterTitle = chapter.name,
                    content = content,
                    bookmarked = chapter.bookmark,
                    hasPrev = currentChapterIndex > 0,
                    hasNext = currentChapterIndex < _chapters.value.size - 1,
                    progress = getSavedProgress(chapter.id),
                )

                // Refresh chapters list to ensure we have the latest data
                refreshChapters()
            } catch (e: Exception) {
                _state.value = EpubReaderState.Error("Failed to load chapter: ${e.message}")
            }
        }
    }

    fun prevChapter() {
        if (currentChapterIndex <= 0) return
        val prevChapter = _chapters.value.getOrNull(currentChapterIndex - 1) ?: return
        loadChapter(prevChapter)
    }

    fun nextChapter() {
        if (currentChapterIndex >= _chapters.value.size - 1) return
        val nextChapter = _chapters.value.getOrNull(currentChapterIndex + 1) ?: return
        loadChapter(nextChapter)
    }

    fun jumpToChapter(index: Int) {
        if (index < 0 || index >= _chapters.value.size || index == currentChapterIndex) return
        val chapter = _chapters.value[index]
        loadChapter(chapter)
    }

    fun toggleBookmark(chapter: Chapter) {
        CoroutineScope(Dispatchers.IO).launch {
            val newBookmark = !chapter.bookmark
            chapterRepo.update(ChapterUpdate(id = chapter.id, bookmark = newBookmark))

            // Update state if this is the current chapter
            val currentState = _state.value
            if (currentState is EpubReaderState.Success && chapter.id == currentChapterId) {
                _state.value = currentState.copy(bookmarked = newBookmark)
            }

            // Refresh chapters list to reflect the change
            refreshChapters()
        }
    }

    fun toggleRead(chapter: Chapter) {
        CoroutineScope(Dispatchers.IO).launch {
            val newReadState = !chapter.read
            chapterRepo.update(ChapterUpdate(id = chapter.id, read = newReadState))

            // Refresh chapters list to reflect the change
            refreshChapters()
        }
    }

    fun updateScrollProgressDebounced(progress: Float) {
        val chapterId = currentChapterId ?: return
        if (kotlin.math.abs(progress - lastSavedProgress) < 0.001) return
        lastSavedProgress = progress
        saveProgressJob?.cancel()
        saveProgressJob = CoroutineScope(Dispatchers.IO).launch {
            delay(500) // 0.5s debounce
            val percentInt = (progress * 1000).toLong()
            chapterRepo.update(ChapterUpdate(id = chapterId, lastPageRead = percentInt))

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

    fun getSavedProgress(chapterId: Long): Float {
        val chapter = _chapters.value.find { it.id == chapterId }
        return if (chapter != null && chapter.lastPageRead > 0) chapter.lastPageRead / 1000f else 0f
    }

    /**
     * Checks if HTML content is essentially blank/empty.
     */
    private fun isBlankHtml(html: String): Boolean {
        val text = Jsoup.parse(html).text().trim()
        return text.length < 80
    }

    private fun extractEpubContent(chapterUrl: String): String {
        try {
            // Load EPUB file and extract HTML for current chapter
            val epubPath = resolveEpubPath(chapterUrl)
            val inputStream = openEpubInputStream(epubPath) ?: throw java.io.FileNotFoundException(epubPath)
            val book = nl.siegmann.epublib.epub.EpubReader().readEpub(inputStream)

            // Try to match spine item by chapter URL (DB stores internal href) else fallback to index
            val spineResources = book.spine.spineReferences

            val internalHref = chapterUrl.substringAfter("::", "")
            // try exact match by href using book resources as LNReader does
            val resourceExact: nl.siegmann.epublib.domain.Resource? = if (internalHref.isNotEmpty()) {
                book.resources.getByHref(internalHref)
            } else {
                null
            }

            // fallback heuristics
            val firstSubstantialRes = spineResources.firstOrNull { ref ->
                val data = ref.resource.data ?: ref.resource.getData()
                if (data == null || data.isEmpty()) return@firstOrNull false
                val text = Jsoup.parse(String(data, Charsets.UTF_8)).text()
                text.length > 80
            }?.resource

            val resource = resourceExact ?: when {
                internalHref.isNotEmpty() -> spineResources.firstOrNull { it.resource.href.contains(internalHref) }?.resource
                firstSubstantialRes != null -> firstSubstantialRes
                else -> spineResources.getOrNull(currentChapterIndex)?.resource ?: spineResources.firstOrNull()?.resource
            }

            val rawBytes = resource?.data ?: resource?.getData() ?: ByteArray(0)
            val charsetName = resource?.inputEncoding ?: "UTF-8"
            var htmlStr: String = try {
                String(rawBytes, java.nio.charset.Charset.forName(charsetName))
            } catch (_: Exception) {
                String(rawBytes, Charsets.UTF_8)
            }

            // Rebuild HTML with our own CSS and body content like LNReader
            val doc = Jsoup.parse(htmlStr)
            val bodyInner = doc.body().html()
            val css = """
                <style>
                    body{color:#ffffff!important;background:#000000!important;margin:0;padding:0;font-size:1.1em;line-height:1.6}
                    img{max-width:100%;height:auto}
                </style>
            """.trimIndent()
            htmlStr = """
                <html>
                    <head>$css</head>
                    <body>$bodyInner</body>
                </html>
            """.trimIndent()

            if (isBlankHtml(htmlStr)) {
                val startIdx = spineResources.indexOfFirst { it.resource == resource }.coerceAtLeast(0)

                // forward scan
                for (idx in (startIdx + 1) until spineResources.size) {
                    val data = spineResources[idx].resource.data ?: spineResources[idx].resource.getData()
                    if (data != null && data.isNotEmpty()) {
                        val candidate = String(data, Charsets.UTF_8)
                        if (!isBlankHtml(candidate)) {
                            htmlStr = candidate
                            break
                        }
                    }
                }

                // if still blank, scan backwards
                if (isBlankHtml(htmlStr)) {
                    for (idx in (startIdx - 1) downTo 0) {
                        val data = spineResources[idx].resource.data ?: spineResources[idx].resource.getData()
                        if (data != null && data.isNotEmpty()) {
                            val candidate = String(data, Charsets.UTF_8)
                            if (!isBlankHtml(candidate)) {
                                htmlStr = candidate
                                break
                            }
                        }
                    }
                }
            }

            return htmlStr
        } catch (e: Exception) {
            Log.e("EpubReader", "Failed to extract EPUB content", e)
            return "<html><body><h1>Error</h1><p>Failed to load EPUB content: ${e.message}</p></body></html>"
        }
    }

    /**
     * Converts chapter URL or manga URL into an absolute .epub file path on disk.
     * Handles cases like:
     *  – "/storage/emulated/0/Tachiyomi/local/MyBook/book.epub::OEBPS/ch001.xhtml"
     *  – "file:///storage/emulated/0/Tachiyomi/local/MyBook/book.epub::OEBPS/ch001.xhtml"
     *  – "MyBook/book.epub::OEBPS/ch001.xhtml"  (relative to Local source dir)
     */
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
                Injekt.get<Application>().contentResolver.openInputStream(uri)
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
}

// ────────────────────────────── State ─────────────────────────────────────────
sealed class EpubReaderState {
    object Loading : EpubReaderState()
    data class Success(
        val bookTitle: String,
        val chapterTitle: String,
        val content: String, // Raw HTML or already-parsed content depending on renderer
        val hasNext: Boolean = false,
        val hasPrev: Boolean = false,
        val progress: Float = 0f,
        val bookmarked: Boolean = false,
    ) : EpubReaderState()
    data class Error(val message: String) : EpubReaderState()
}
