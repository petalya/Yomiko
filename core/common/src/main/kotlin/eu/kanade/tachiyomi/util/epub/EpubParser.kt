package eu.kanade.tachiyomi.util.epub

import eu.kanade.tachiyomi.util.storage.EpubFile
import io.documentnode.epub4j.domain.Book
import io.documentnode.epub4j.domain.Resource
import io.documentnode.epub4j.domain.SpineReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Enhanced EPUB parser that parses EPUB files into a more usable model structure
 * for use with the epubify library
 */
class EpubParser {

    /**
     * Parse an EPUB file into an EpubDocument model
     *
     * @param file The EPUB file to parse
     * @return The parsed EpubDocument
     */
    suspend fun parseFile(file: File): EpubDocument = withContext(Dispatchers.IO) {
        val inputStream = FileInputStream(file)
        try {
            try {
                val doc = parse(inputStream)
                doc
            } catch (e: NullPointerException) {
                throw IllegalStateException("Malformed EPUB: missing or corrupt resource (NPE)", e)
            }
        } finally {
            try {
                inputStream.close()
            } catch (e: IOException) {
                // Ignore
            }
        }
    }

    /**
     * Parse an EPUB from an input stream
     *
     * @param inputStream The EPUB input stream to parse
     * @return The parsed EpubDocument
     */
    suspend fun parse(inputStream: InputStream): EpubDocument = withContext(Dispatchers.IO) {
        try {
            val epubFile = EpubFile(inputStream)
            val book = epubFile.book

            val title = book.title ?: "Unknown"
            val author = book.metadata.authors.firstOrNull()?.let { "${it.firstname} ${it.lastname}".trim() }
            val metadata = extractMetadata(book)

            val chapters = extractChapters(book)
            val tocEntries = extractTableOfContents(book)

            EpubDocument(
                title = title,
                author = author,
                chapters = chapters,
                tableOfContents = tocEntries,
                metadata = metadata,
                bookId = book.metadata.identifiers.firstOrNull()?.value ?: "unknown",
            )
        } catch (e: NullPointerException) {
            throw IllegalStateException("Malformed EPUB: missing or corrupt resource (NPE)", e)
        }
    }

    private fun extractMetadata(book: Book): EpubMetadata {
        val metadata = book.metadata
        val otherMetadata = mutableMapOf<String, String>()

        // We need to handle metadata differently since the API structure is complex
        // Just extract what we can access directly

        return EpubMetadata(
            title = book.title ?: "Unknown",
            creator = book.metadata.authors.firstOrNull()?.let { "${it.firstname} ${it.lastname}".trim() },
            contributor = metadata.contributors.firstOrNull()?.let { "${it.firstname} ${it.lastname}".trim() },
            publisher = metadata.publishers.firstOrNull(),
            description = metadata.descriptions.firstOrNull(),
            subject = metadata.subjects,
            language = metadata.language,
            identifier = metadata.identifiers.firstOrNull()?.value,
            date = metadata.dates.firstOrNull()?.value,
            rights = metadata.rights.firstOrNull(),
            source = null, // Omit source as it's not directly accessible
            otherMetadata = otherMetadata,
        )
    }

    private fun isChapterFile(href: String): Boolean {
        val lower = href.lowercase()
        return lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".xml")
    }

    private fun isImageFile(href: String, mediaType: String?): Boolean {
        val lower = href.lowercase()
        return lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".png") ||
            lower.endsWith(".gif") ||
            (mediaType?.startsWith("image/") == true)
    }

    private fun extractChapters(book: Book): List<EpubChapter> {
        val tocHrefs = mutableListOf<Pair<String, String>>() // Pair<href, title>

        // Flatten TOC entries to a list of hrefs and titles (normalized)
        fun collectToc(entries: List<io.documentnode.epub4j.domain.TOCReference>) {
            for (entry in entries) {
                if (entry.resource == null) {
                    logcat { "[EPUB] Skipped TOC entry with null resource: title='${entry.title}'" }
                    continue
                }
                entry.completeHref?.let { tocHrefs.add(it.substringBefore('#') to (entry.title ?: "")) }
                if (entry.children.isNotEmpty()) collectToc(entry.children)
            }
        }
        collectToc(book.tableOfContents.tocReferences)

        val chapters = mutableListOf<EpubChapter>()
        val spineRefs = book.spine.spineReferences
        logcat { "[EPUB] Spine has ${spineRefs.size} references" }
        var currentTOC: Pair<String, String>? = null
        var accumulatedContent = StringBuilder()
        var accumulatedResources = mutableMapOf<String, ByteArray>()
        var chapterStartSpineIndex = 0
        var chapterMediaType = "text/html"
        var chapterId = ""
        var chapterPosition = 0
        var lastMatchedTOCIndex = -1

        fun finalizeChapter() {
            if ((currentTOC != null && accumulatedContent.isNotEmpty()) || accumulatedContent.isNotEmpty()) {
                // Wrap accumulated body content in a single HTML document
                val mergedBody = accumulatedContent.toString()
                val finalContent = "<html><body>$mergedBody</body></html>"
                chapters.add(
                    EpubChapter(
                        id = chapterId.ifEmpty { "chapter_$chapterPosition" },
                        href = currentTOC?.first ?: "",
                        title = currentTOC?.second?.ifEmpty { "Chapter ${chapters.size + 1}" } ?: "Chapter ${chapters.size + 1}",
                        content = finalContent,
                        mediaType = chapterMediaType,
                        position = chapterStartSpineIndex,
                        embeddedResources = accumulatedResources.toMap(),
                    ),
                )
            }
            accumulatedContent = StringBuilder()
            accumulatedResources = mutableMapOf()
        }

        for ((spineIndex, spineRef) in spineRefs.withIndex()) {
            val resource = spineRef.resource
            if (resource == null) {
                logcat { "[EPUB] Skipped null resource in spine at index $spineIndex" }
                continue
            }
            val href = resource.href
            if (href == null) {
                logcat { "[EPUB] Skipped resource with null href at index $spineIndex" }
                continue
            }
            val mediaType = resource.mediaType?.name ?: ""
            val isChapter = isChapterFile(href) && shouldIncludeInChapters(spineRef, resource)
            val isImage = isImageFile(href, mediaType)
            val tocIdx = tocHrefs.indexOfFirst { it.first == href }

            if (isImage) {
                // Finalize any accumulated chapter before adding image as its own chapter
                finalizeChapter()
                chapters.add(
                    EpubChapter(
                        id = resource.id ?: "image_$spineIndex",
                        href = href,
                        title = "",
                        content = "", // Images handled separately in rendering
                        mediaType = mediaType,
                        position = spineIndex,
                        embeddedResources = extractEmbeddedResources(resource, book),
                    ),
                )
                // Reset accumulators
                currentTOC = null
                accumulatedContent = StringBuilder()
                accumulatedResources = mutableMapOf()
                continue
            }

            if (isChapter) {
                if (tocIdx != -1 && tocIdx != lastMatchedTOCIndex) {
                    // New TOC entry found, finalize previous chapter
                    finalizeChapter()
                    currentTOC = tocHrefs[tocIdx]
                    chapterStartSpineIndex = spineIndex
                    chapterMediaType = mediaType
                    chapterId = resource.id ?: "chapter_$spineIndex"
                    chapterPosition = spineIndex
                    lastMatchedTOCIndex = tocIdx
                }
                val content = String(resource.data ?: ByteArray(0), Charsets.UTF_8)
                // Extract only the <body> content using Jsoup, ignoring xmlns and namespaces
                val doc = Jsoup.parse(content)
                val body = doc.body().html()
                val finalBody = body.ifBlank { content }
                accumulatedContent.append(finalBody)
                accumulatedResources.putAll(extractEmbeddedResources(resource, book))
            } else {
                logcat { "[EPUB] Skipped non-chapter/non-image spine: $href (spine $spineIndex)" }
            }
        }
        // Finalize last chapter
        finalizeChapter()
        return chapters.sortedBy { it.position }
    }

    private fun extractEmbeddedResources(resource: Resource, book: Book): Map<String, ByteArray> {
        val resources = mutableMapOf<String, ByteArray>()
        try {
            val content = String(resource.data ?: return emptyMap(), Charsets.UTF_8)
            // Always parse the whole document for <img> tags, not just <body>
            val doc = Jsoup.parse(content, "", org.jsoup.parser.Parser.xmlParser())
            val basePath = resource.href.substringBeforeLast('/', "")

            // Extract images from the entire document
            doc.select("img").forEach { img ->
                val src = img.attr("src")
                if (src.isNotEmpty()) {
                    val decodedSrc = try {
                        java.net.URLDecoder.decode(src, "UTF-8")
                    } catch (e: Exception) {
                        src
                    }
                    if (src.startsWith("data:")) {
                        // Handle base64 encoded images
                        val base64Data = src.substringAfter("base64,")
                        if (base64Data.isNotEmpty()) {
                            try {
                                val imageData = java.util.Base64.getDecoder().decode(base64Data)
                                resources[decodedSrc] = imageData
                            } catch (e: Exception) {
                                // Failed to decode base64 data
                            }
                        }
                    } else {
                        // Handle regular image references
                        val resolvedPath = resolveRelativePath(basePath, src)
                        val decodedResolved = try {
                            java.net.URLDecoder.decode(resolvedPath, "UTF-8")
                        } catch (e: Exception) {
                            resolvedPath
                        }
                        val imageResource = book.resources.getByHref(decodedResolved)
                            // Try alternate paths if direct path fails
                            ?: if (decodedResolved != decodedSrc) {
                                book.resources.getByHref(decodedSrc)
                            } else {
                                null
                                    ?: findResourceByRelativePath(book, decodedResolved, decodedSrc)
                            }
                        imageResource?.data?.let { data ->
                            resources[decodedSrc] = data
                            if (decodedSrc != decodedResolved) {
                                resources[decodedResolved] = data
                            }
                            // Also store by decoded filename for fallback lookup
                            val filename = decodedSrc.substringAfterLast('/')
                            if (filename.isNotEmpty()) {
                                resources[filename] = data
                            }
                        }
                    }
                }
            }
            // Extract SVG <image xlink:href=...> images
            doc.select("svg image[xlink:href]").forEach { svgImage ->
                val href = svgImage.attr("xlink:href")
                if (href.isNotEmpty()) {
                    val decodedHref = try {
                        java.net.URLDecoder.decode(href, "UTF-8")
                    } catch (e: Exception) {
                        href
                    }
                    if (href.startsWith("data:")) {
                        val base64Data = href.substringAfter("base64,")
                        if (base64Data.isNotEmpty()) {
                            try {
                                val imageData = java.util.Base64.getDecoder().decode(base64Data)
                                resources[decodedHref] = imageData
                            } catch (e: Exception) {
                                // Failed to decode base64 data
                            }
                        }
                    } else {
                        val resolvedPath = resolveRelativePath(basePath, href)
                        val decodedResolved = try {
                            java.net.URLDecoder.decode(resolvedPath, "UTF-8")
                        } catch (e: Exception) {
                            resolvedPath
                        }
                        val imageResource = book.resources.getByHref(decodedResolved)
                            ?: if (decodedResolved != decodedHref) {
                                book.resources.getByHref(decodedHref)
                            } else {
                                null
                                    ?: findResourceByRelativePath(book, decodedResolved, decodedHref)
                            }
                        imageResource?.data?.let { data ->
                            resources[decodedHref] = data
                            if (decodedHref != decodedResolved) {
                                resources[decodedResolved] = data
                            }
                            val filename = decodedHref.substringAfterLast('/')
                            if (filename.isNotEmpty()) {
                                resources[filename] = data
                            }
                        }
                    }
                }
            }

            // Extract CSS (unchanged)
            doc.select("link[rel=stylesheet]").forEach { link ->
                val href = link.attr("href")
                if (href.isNotEmpty()) {
                    val resolvedPath = resolveRelativePath(basePath, href)
                    val cssResource = book.resources.getByHref(resolvedPath)
                        ?: if (resolvedPath != href) book.resources.getByHref(resolvedPath) else null
                    cssResource?.data?.let { data ->
                        resources[href] = data
                    }
                }
            }
        } catch (e: Exception) {
            // Failed to extract embedded resources, continue anyway
        }
        return resources
    }

    /**
     * Helper function to find a resource when the direct path resolution fails
     * Tries different variations of the path to handle quirky EPUB files
     */
    private fun findResourceByRelativePath(book: Book, resolvedPath: String, originalPath: String): Resource? {
        val filename = resolvedPath.substringAfterLast('/')
        val resourceByFilename = book.resources.getByHref(filename)
            ?: if (filename != resolvedPath) book.resources.getByHref(resolvedPath) else null
        if (resourceByFilename != null) return resourceByFilename

        // Try looking for the resource in common image directories
        val commonImageDirs = listOf("images/", "Images/", "image/", "Image/", "img/", "Img/")
        for (dir in commonImageDirs) {
            val pathWithDir = "$dir$filename"
            val resourceWithDir = book.resources.getByHref(pathWithDir)
                ?: if (pathWithDir != resolvedPath) book.resources.getByHref(resolvedPath) else null
            if (resourceWithDir != null) return resourceWithDir
        }

        // Try looking through all resources for a partial match
        return book.resources.all.find { resource ->
            resource.href.endsWith(filename) || (resolvedPath != originalPath && resource.href.endsWith(resolvedPath))
        }
    }

    private fun resolveRelativePath(basePath: String, relativePath: String): String {
        // Handle data URIs and absolute paths
        if (relativePath.startsWith("data:") || relativePath.startsWith("http")) {
            return relativePath
        }

        if (relativePath.startsWith("/")) {
            return relativePath.removePrefix("/")
        }

        if (basePath.isEmpty()) return relativePath

        // Split the base path into components
        val baseComponents = basePath.split("/").toMutableList()

        // If the base path doesn't end with a filename, it's already a directory
        if (!basePath.contains(".")) {
            // It's a directory path, nothing to remove
        } else {
            // Remove the filename component if present
            baseComponents.removeLastOrNull()
        }

        // Process the relative path
        val relComponents = relativePath.split("/").toMutableList()

        // Process any "../" components
        while (relComponents.isNotEmpty() && relComponents[0] == "..") {
            relComponents.removeAt(0)
            if (baseComponents.isNotEmpty()) {
                baseComponents.removeAt(baseComponents.size - 1)
            }
        }

        // Combine the path components
        val resultPath = (baseComponents + relComponents).joinToString("/")
        return resultPath
    }

    private fun shouldIncludeInChapters(spineRef: SpineReference, resource: Resource): Boolean {
        if (resource.mediaType == null) return false
        val mediaType = resource.mediaType.name

        return !spineRef.isLinear ||
            mediaType.contains("html", ignoreCase = true) ||
            mediaType.contains("xhtml", ignoreCase = true) ||
            mediaType.contains("svg", ignoreCase = true) ||
            mediaType.contains("xml", ignoreCase = true)
    }

    private fun extractTableOfContents(book: Book): List<EpubTableOfContentsEntry> {
        val entries = mutableListOf<EpubTableOfContentsEntry>()
        book.tableOfContents.tocReferences.forEachIndexed { index, tocRef ->
            if (tocRef.resource == null) {
                logcat { "[EPUB] Skipped TOC entry with null resource in extractTableOfContents: title='${tocRef.title}'" }
                return@forEachIndexed
            }
            val entry = EpubTableOfContentsEntry(
                id = tocRef.resourceId ?: "toc_$index",
                href = tocRef.completeHref,
                title = tocRef.title ?: "Section ${index + 1}",
                level = 0,
                children = extractTocChildren(tocRef.children, 1),
            )
            entries.add(entry)
        }
        return entries
    }

    private fun extractTocChildren(children: List<io.documentnode.epub4j.domain.TOCReference>, level: Int): List<EpubTableOfContentsEntry> {
        return children.mapIndexedNotNull { index, tocRef ->
            if (tocRef.resource == null) {
                logcat { "[EPUB] Skipped TOC child with null resource at level $level: title='${tocRef.title}'" }
                return@mapIndexedNotNull null
            }
            EpubTableOfContentsEntry(
                id = tocRef.resourceId ?: "toc_sub_$level-$index",
                href = tocRef.completeHref,
                title = tocRef.title ?: "Section $level.${index + 1}",
                level = level,
                children = extractTocChildren(tocRef.children, level + 1),
            )
        }
    }

    /**
     * Extract content blocks from HTML content
     */
    fun extractContentBlocks(htmlContent: String): List<EpubContentBlock> {
        val blocks = mutableListOf<EpubContentBlock>()
        try {
            // Try normal HTML parsing
            val doc = Jsoup.parse(htmlContent)
            parseBlockElements(doc.body(), blocks)
        } catch (e: Exception) {
            // Ignore, will try XML below
        }
        if (blocks.isEmpty()) {
            // Try XML parser as fallback
            try {
                val xmlDoc = Jsoup.parse(htmlContent, "", org.jsoup.parser.Parser.xmlParser())
                // Try to find <body>, else <html>
                val body = xmlDoc.selectFirst("body") ?: xmlDoc.selectFirst("html")
                if (body != null) {
                    parseBlockElements(body, blocks)
                }
            } catch (e: Exception) {
                // Ignore, will fallback to raw text
            }
        }
        if (blocks.isEmpty()) {
            blocks.add(EpubContentBlock.Text(htmlContent))
        }
        return blocks
    }

    private fun isBlockLevelElement(tagName: String): Boolean {
        return when (tagName.lowercase()) {
            "address", "article", "aside", "blockquote", "canvas", "dd", "div", "dl", "dt", "fieldset",
            "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "li",
            "main", "nav", "noscript", "ol", "p", "pre", "section", "table", "tfoot", "ul", "video", "tr", "td", "th",
            -> true
            else -> false
        }
    }

    private fun parseBlockElements(element: Element, blocks: MutableList<EpubContentBlock>) {
        for (child in element.children()) {
            when (val tag = child.tagName().lowercase()) {
                "p" -> {
                    val text = child.wholeText().trim()
                    if (text.isNotEmpty()) {
                        blocks.add(EpubContentBlock.Paragraph(text))
                    }
                    parseInlineElements(child, blocks)
                }
                "div" -> {
                    val hasBlockChild = child.children().any { isBlockLevelElement(it.tagName()) }
                    if (!hasBlockChild) {
                        val text = child.wholeText().trim()
                        if (text.isNotEmpty()) {
                            blocks.add(EpubContentBlock.Paragraph(text))
                        }
                        parseInlineElements(child, blocks)
                    } else {
                        // Treat as a container, recurse
                        parseBlockElements(child, blocks)
                    }
                }
                "br" -> {
                    blocks.add(EpubContentBlock.LineBreak)
                }
                "span" -> {
                    val text = child.wholeText().trim()
                    if (text.isNotEmpty()) {
                        blocks.add(EpubContentBlock.Text(text))
                    }
                    parseInlineElements(child, blocks)
                }
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    val level = tag.substring(1).toIntOrNull() ?: 1
                    blocks.add(EpubContentBlock.Header(level, child.text()))
                }
                "img" -> {
                    blocks.add(
                        EpubContentBlock.Image(
                            src = child.attr("src"),
                            alt = child.attr("alt"),
                            data = null,
                        ),
                    )
                }
                // Support SVG <image xlink:href=...>
                "image" -> {
                    val xlinkHref = child.attr("xlink:href")
                    if (xlinkHref.isNotEmpty()) {
                        blocks.add(
                            EpubContentBlock.Image(
                                src = xlinkHref,
                                alt = child.attr("alt"), // SVG images may not have alt, but keep for consistency
                                data = null,
                            ),
                        )
                    }
                }
                "a" -> {
                    blocks.add(
                        EpubContentBlock.Link(
                            href = child.attr("href"),
                            content = child.text(),
                        ),
                    )
                }
                "ul", "ol" -> {
                    val items = child.select("li").map { li ->
                        val itemBlocks = mutableListOf<EpubContentBlock>()
                        parseBlockElements(li, itemBlocks)
                        itemBlocks.toList()
                    }
                    blocks.add(
                        EpubContentBlock.ListBlock(
                            items = items,
                            ordered = tag == "ol",
                        ),
                    )
                }
                "table" -> {
                    val headers = child.select("thead th").map { it.text() }
                    val rows = child.select("tbody tr").map { row ->
                        row.select("td").map { it.text() }
                    }
                    blocks.add(
                        EpubContentBlock.Table(
                            headers = headers,
                            rows = rows,
                        ),
                    )
                }
                else -> {
                    // Recursively process other elements
                    parseBlockElements(child, blocks)
                }
            }
        }
    }

    private fun parseInlineElements(element: Element, blocks: MutableList<EpubContentBlock>) {
        for (child in element.children()) {
            val tag = child.tagName().lowercase()
            when (tag) {
                "br" -> {
                    blocks.add(EpubContentBlock.LineBreak)
                }
                "img" -> {
                    blocks.add(
                        EpubContentBlock.Image(
                            src = child.attr("src"),
                            alt = child.attr("alt"),
                            data = null,
                        ),
                    )
                }
                // Support SVG <image xlink:href=...>
                "image" -> {
                    val xlinkHref = child.attr("xlink:href")
                    if (xlinkHref.isNotEmpty()) {
                        blocks.add(
                            EpubContentBlock.Image(
                                src = xlinkHref,
                                alt = child.attr("alt"),
                                data = null,
                            ),
                        )
                    }
                }
                "a" -> {
                    blocks.add(
                        EpubContentBlock.Link(
                            href = child.attr("href"),
                            content = child.text(),
                        ),
                    )
                }
                else -> {
                    // Recursively process other inline elements
                    parseInlineElements(child, blocks)
                }
            }
        }
    }
}
