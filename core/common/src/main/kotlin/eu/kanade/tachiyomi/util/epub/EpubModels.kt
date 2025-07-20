package eu.kanade.tachiyomi.util.epub

/**
 * Represents an EPUB document with all its components and metadata
 */
data class EpubDocument(
    val title: String,
    val author: String?,
    val coverImage: ByteArray?,
    val chapters: List<EpubChapter>,
    val tableOfContents: List<EpubTableOfContentsEntry>,
    val metadata: EpubMetadata,
    val bookId: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EpubDocument

        if (title != other.title) return false
        if (author != other.author) return false
        if (coverImage != null) {
            if (other.coverImage == null) return false
            if (!coverImage.contentEquals(other.coverImage)) return false
        } else if (other.coverImage != null) return false
        if (chapters != other.chapters) return false
        if (tableOfContents != other.tableOfContents) return false
        if (metadata != other.metadata) return false
        if (bookId != other.bookId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + (author?.hashCode() ?: 0)
        result = 31 * result + (coverImage?.contentHashCode() ?: 0)
        result = 31 * result + chapters.hashCode()
        result = 31 * result + tableOfContents.hashCode()
        result = 31 * result + metadata.hashCode()
        result = 31 * result + bookId.hashCode()
        return result
    }
}

/**
 * Represents a single chapter in an EPUB document
 */
data class EpubChapter(
    val id: String,
    val href: String,
    val title: String?,
    val content: String,
    val mediaType: String,
    val position: Int,
    val embeddedResources: Map<String, ByteArray> = emptyMap(),
) {
    val isHtml: Boolean get() = mediaType.contains("html", ignoreCase = true) || mediaType.contains("xhtml", ignoreCase = true)
}

/**
 * Represents a table of contents entry with optional nested entries
 */
data class EpubTableOfContentsEntry(
    val id: String,
    val href: String,
    val title: String,
    val level: Int = 0,
    val children: List<EpubTableOfContentsEntry> = emptyList(),
)

/**
 * Represents the metadata of an EPUB document
 */
data class EpubMetadata(
    val title: String,
    val creator: String?,
    val contributor: String?,
    val publisher: String?,
    val description: String?,
    val subject: List<String>,
    val language: String?,
    val identifier: String?,
    val date: String?,
    val rights: String?,
    val source: String?,
    val otherMetadata: Map<String, String>,
)

/**
 * Represents a content block within an EPUB chapter
 * Could be text, image, or other media
 */
sealed class EpubContentBlock {
    data class Text(val content: String) : EpubContentBlock()
    
    /**
     * A paragraph block that should be rendered with proper paragraph styling
     * including first-line indentation and vertical margins
     */
    data class Paragraph(val content: String) : EpubContentBlock()
    
    /**
     * Explicit line break
     */
    object LineBreak : EpubContentBlock()
    
    /**
     * Block quote with nested content blocks
     */
    data class BlockQuote(val content: List<EpubContentBlock>) : EpubContentBlock()
    
    data class Image(val src: String, val alt: String?, val data: ByteArray? = null) : EpubContentBlock() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Image

            if (src != other.src) return false
            if (alt != other.alt) return false
            if (data != null) {
                if (other.data == null) return false
                if (!data.contentEquals(other.data)) return false
            } else if (other.data != null) return false

            return true
        }

        override fun hashCode(): Int {
            var result = src.hashCode()
            result = 31 * result + (alt?.hashCode() ?: 0)
            result = 31 * result + (data?.contentHashCode() ?: 0)
            return result
        }
    }
    data class Header(val level: Int, val content: String) : EpubContentBlock()
    data class Link(val href: String, val content: String) : EpubContentBlock()
    data class ListBlock(val items: List<String>, val ordered: Boolean) : EpubContentBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : EpubContentBlock()
    data class Embed(val mediaType: String, val src: String, val data: ByteArray? = null) : EpubContentBlock() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Embed

            if (mediaType != other.mediaType) return false
            if (src != other.src) return false
            if (data != null) {
                if (other.data == null) return false
                if (!data.contentEquals(other.data)) return false
            } else if (other.data != null) return false

            return true
        }

        override fun hashCode(): Int {
            var result = mediaType.hashCode()
            result = 31 * result + src.hashCode()
            result = 31 * result + (data?.contentHashCode() ?: 0)
            return result
        }
    }
}

/**
 * Settings for the EPUB reader
 */
data class EpubReaderSettings(
    val fontSize: Float = 16f,
    val fontFamily: String? = null,
    val lineSpacing: Float = 1.5f,
    val alignment: TextAlignment = TextAlignment.LEFT,
    val margins: Float = 16f,
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val isScrollMode: Boolean = false,
)

enum class TextAlignment {
    LEFT, CENTER, RIGHT, JUSTIFY
}

enum class ReaderTheme {
    LIGHT, SEPIA, MINT, BLUE_GRAY, BLACK
} 