package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.storage.EpubFile
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Loader used to load a chapter from a .epub file.
 * Optimized with caching and lazy loading for better performance.
 */
internal class EpubPageLoader(
    private val epubFile: File,
    private val spineHref: String? = null,
) : PageLoader() {

    // Lazy initialize the EpubFile to avoid unnecessary loading
    private val epub by lazy { EpubFile(epubFile.inputStream()) }
    
    // Cache for loaded resources to avoid repeated processing
    private val resourceCache = ConcurrentHashMap<String, ByteArray>()

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        if (spineHref != null) {
            return getSpineChapterPage()
        }
        
        // Check if this is an image-based or text-based EPUB
        val imagePages = epub.getImagesFromPages()
        return if (imagePages.isNotEmpty()) {
            getImagePages(imagePages)
        } else {
            getTextPage()
        }
    }
    
    private fun getSpineChapterPage(): List<ReaderPage> {
        // Only load the specific internal chapter (spine item) - use the optimized method
        try {
            val text = spineHref?.let { epub.getTextFromPage(it) }
            if (text != null) {
                return listOf(
                    ReaderPage(0).apply {
                        url = text
                        imageUrl = null
                        stream = null
                        status = Page.State.Ready
                    },
                )
            }
            
            // Fallback to old method if optimized method fails
            val resource = epub.book.spine.spineReferences.find { it.resource.href == spineHref }?.resource
            if (resource != null) {
                val html = resource.reader.readText()
                val extractedText = org.jsoup.Jsoup.parse(html).body().text()
                return listOf(
                    ReaderPage(0).apply {
                        url = extractedText
                        imageUrl = null
                        stream = null
                        status = Page.State.Ready
                    },
                )
            }
        } catch (e: Exception) {
            // Log error but continue to fallback
        }
        
        // Fallback: show error page
        return listOf(
            ReaderPage(0).apply {
                url = "Could not find chapter content."
                imageUrl = null
                stream = null
                status = Page.State.Ready
            },
        )
    }
    
    private fun getImagePages(imagePages: List<String>): List<ReaderPage> {
        return imagePages.mapIndexed { i, path ->
            ReaderPage(i).apply {
                url = path
                imageUrl = path
                status = Page.State.Queue
            }
        }
    }
    
    private fun getTextPage(): List<ReaderPage> {
        // Text-based EPUB: create a single ReaderPage with the text content
        val text = epub.getTextFromPages()
        return listOf(
            ReaderPage(0).apply {
                url = text
                imageUrl = null
                stream = null
                status = Page.State.Ready
            },
        )
    }

    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
        
        // Only load image pages on demand
        if (page.status == Page.State.Queue && page.url != null) {
            try {
                page.stream = { getImageStream(page.url!!) }
                page.status = Page.State.Ready
            } catch (e: Exception) {
                page.status = Page.State.Error(e)
            }
        }
    }
    
    private fun getImageStream(path: String): InputStream {
        // Check cache first
        val cachedData = resourceCache[path]
        if (cachedData != null) {
            return cachedData.inputStream()
        }
        
        // Load and cache the resource
        val inputStream = epub.getInputStream(path)!!
        val bytes = inputStream.readBytes()
        resourceCache[path] = bytes
        return bytes.inputStream()
    }

    override fun recycle() {
        super.recycle()
        epub.close()
        resourceCache.clear()
    }
}
