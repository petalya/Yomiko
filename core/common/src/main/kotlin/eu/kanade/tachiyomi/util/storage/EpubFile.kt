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

    /**
     * Extracts the cover image from the EPUB and saves it as 'cover.jpg' in the given directory.
     * Returns true if successful, false otherwise.
     *
     * @param outputDir The directory where 'cover.jpg' should be saved.
     */
    fun extractAndSaveCoverImage(outputDir: java.io.File): Boolean {
        try {
            // 1. Try to get the cover image using the public property - use direct data access when possible
            val coverResource: Resource? = book.coverImage
                ?: findCoverImageResource()

            if (coverResource == null) return false

            // 2. Read image bytes - avoid unnecessary operations
            val imageBytes = coverResource.data ?: return false

            // 3. Prepare output file - use direct file operations for better performance
            val outputFile = java.io.File(outputDir, "cover.jpg")
            outputFile.writeBytes(imageBytes)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Finds the cover image resource in the EPUB.
     * Uses optimized search criteria to find the cover image faster.
     */
    private fun findCoverImageResource(): Resource? {
        // First check resources with "cover" in the ID (most reliable)
        book.resources.all.firstOrNull {
            it.id?.contains("cover", ignoreCase = true) == true &&
                it.mediaType?.name?.startsWith("image/") == true
        }?.let { return it }

        // Then check resources with "cover" in the href
        book.resources.all.firstOrNull {
            it.href?.contains("cover", ignoreCase = true) == true &&
                it.mediaType?.name?.startsWith("image/") == true
        }?.let { return it }

        // Finally, just return the first image if nothing else found
        return book.resources.all.firstOrNull {
            it.mediaType?.name?.startsWith("image/") == true
        }
    }
}
