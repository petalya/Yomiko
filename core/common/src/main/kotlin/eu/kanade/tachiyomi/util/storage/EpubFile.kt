package eu.kanade.tachiyomi.util.storage

import io.documentnode.epub4j.domain.Book
import io.documentnode.epub4j.domain.Resource
import io.documentnode.epub4j.epub.EpubReader
import org.jsoup.Jsoup
import java.io.Closeable
import java.io.InputStream

/**
 * Wrapper over ZipFile to load files in epub format.
 */
class EpubFile(epubInputStream: InputStream) : Closeable {
    private val _book: Book by lazy { EpubReader().readEpub(epubInputStream) }
    val book: Book
        get() = _book

    override fun close() {
        // No resources to close for epublib Book
    }

    /**
     * Returns the paths of all images found in the epub file.
     */
    fun getImagesFromPages(): List<String> {
        val imagePaths = mutableListOf<String>()
        for (resource in book.resources.all) {
            val mediaType = resource.mediaType?.name ?: continue
            if (mediaType.startsWith("image/")) {
                imagePaths.add(resource.href)
            }
        }
        return imagePaths
    }

    /**
     * Returns the concatenated plain text content from all XHTML pages in the epub file.
     */
    fun getTextFromPages(): String {
        val stringBuilder = StringBuilder()
        for (spineRef in book.spine.spineReferences) {
            val resource = spineRef.resource
            val html = resource.reader.readText()
            val text = Jsoup.parse(html).body().text()
            stringBuilder.append(text).append("\n\n")
        }
        return stringBuilder.toString().trim()
    }

    /**
     * Returns the plain text content from a specific XHTML page in the epub file.
     * This is more efficient than loading all pages when only one is needed.
     *
     * @param href The href of the resource to extract text from
     * @return The plain text content of the specified resource
     */
    fun getTextFromPage(href: String): String? {
        val resource = book.resources.getByHref(href) ?: return null
        val html = resource.reader.readText()
        return Jsoup.parse(html).body().text()
    }

    /**
     * Returns an input stream for reading the contents of the specified resource.
     */
    fun getInputStream(entryName: String): InputStream? {
        val resource: Resource? = book.resources.getByHref(entryName)
        return resource?.inputStream
    }
}
