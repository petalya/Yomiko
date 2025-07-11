package eu.kanade.tachiyomi.util.storage

import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.domain.Resource
import nl.siegmann.epublib.epub.EpubReader
import org.jsoup.Jsoup
import java.io.Closeable
import java.io.InputStream

/**
 * Wrapper over ZipFile to load files in epub format.
 */
class EpubFile(epubInputStream: InputStream) : Closeable {
    val book: Book = EpubReader().readEpub(epubInputStream)

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
     * Returns an input stream for reading the contents of the specified resource.
     */
    fun getInputStream(entryName: String): InputStream? {
        val resource: Resource? = book.resources.getByHref(entryName)
        return resource?.inputStream
    }
}
