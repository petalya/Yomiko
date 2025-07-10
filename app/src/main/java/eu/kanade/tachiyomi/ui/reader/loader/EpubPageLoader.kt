package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.storage.EpubFile
import mihon.core.common.archive.ArchiveReader
import java.io.File
import java.io.InputStream

/**
 * Loader used to load a chapter from a .epub file.
 */
internal class EpubPageLoader(
    epubFile: File,
    private val spineHref: String? = null
) : PageLoader() {

    // Convert ArchiveReader to InputStream for EpubFile
    private val epub = EpubFile(epubFile.inputStream())

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        if (spineHref != null) {
            // Only load the specific internal chapter (spine item)
            val resource = epub.book.spine.spineReferences.find { it.resource.href == spineHref }?.resource
            if (resource != null) {
                val html = resource.reader.readText()
                val text = org.jsoup.Jsoup.parse(html).body().text()
                return listOf(ReaderPage(0).apply {
                    url = text
                    imageUrl = null
                    stream = null
                    status = Page.State.Ready
                })
            } else {
                // Fallback: show error page
                return listOf(ReaderPage(0).apply {
                    url = "Could not find chapter content."
                    imageUrl = null
                    stream = null
                    status = Page.State.Ready
                })
            }
        }
        val imagePages = epub.getImagesFromPages()
        return if (imagePages.isNotEmpty()) {
            imagePages.mapIndexed { i, path ->
                val streamFn = { epub.getInputStream(path)!! }
                ReaderPage(i).apply {
                    stream = streamFn
                    status = Page.State.Ready
                }
            }
        } else {
            // Text-based EPUB: create a single ReaderPage with the text content
            val text = epub.getTextFromPages()
            listOf(ReaderPage(0).apply {
                url = text
                imageUrl = null
                stream = null
                status = Page.State.Ready
            })
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }

    override fun recycle() {
        super.recycle()
        epub.close()
    }
}
