package tachiyomi.source.local.metadata

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import nl.siegmann.epublib.domain.Book

/**
 * Fills manga and chapter metadata using this epub file's metadata.
 */
fun fillMetadata(book: Book, manga: SManga, chapter: SChapter) {
    // Title
    book.title?.let { title ->
        manga.title = title
        chapter.name = title
    }
    // Author(s)
    val authors = book.metadata.authors
    if (authors.isNotEmpty()) {
        manga.author = authors.joinToString(", ") { it.firstname + " " + it.lastname }.trim()
    }
    // Description
    book.metadata.descriptions.firstOrNull()?.let { desc ->
        manga.description = desc
    }
    // Publisher as scanlator
    book.metadata.publishers.firstOrNull()?.let { publisher ->
        chapter.scanlator = publisher
    }
    // Date
    book.metadata.dates.firstOrNull()?.let { date ->
        val dateValue = date.value
        if (dateValue is java.util.Date) {
            chapter.date_upload = dateValue.time
        } else {
            chapter.date_upload = 0L
        }
    }
}

fun fillMangaMetadata(book: Book, manga: SManga) {
    // Title
    book.title?.let { title ->
        manga.title = title
    }
    // Author(s)
    val authors = book.metadata.authors
    if (authors.isNotEmpty()) {
        manga.author = authors.joinToString(", ") { it.firstname + " " + it.lastname }.trim()
    }
    // Description
    book.metadata.descriptions.firstOrNull()?.let { desc ->
        manga.description = desc
    }
}

fun fillChapterMetadata(book: Book, chapter: SChapter) {
    // Publisher as scanlator
    book.metadata.publishers.firstOrNull()?.let { publisher ->
        chapter.scanlator = publisher
    }
    // Date
    book.metadata.dates.firstOrNull()?.let { date ->
        val dateValue = date.value
        if (dateValue is java.util.Date) {
            chapter.date_upload = dateValue.time
        } else {
            chapter.date_upload = 0L
        }
    }
}
