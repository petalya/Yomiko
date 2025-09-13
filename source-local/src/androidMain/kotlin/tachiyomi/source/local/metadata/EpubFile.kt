package tachiyomi.source.local.metadata

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import io.documentnode.epub4j.domain.Author
import io.documentnode.epub4j.domain.Book
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Fills manga and chapter metadata using this epub file's metadata.
 */
fun fillMetadata(book: Book, manga: SManga, chapter: SChapter) {
    // Title
    book.title?.takeIf { it.isNotBlank() }?.let { title ->
        manga.title = title
        chapter.name = title
    }
    // Author(s)
    formatAuthors(book.metadata.authors)?.let { manga.author = it }
    // Description
    book.metadata.descriptions.firstOrNull()?.takeIf { it.isNotBlank() }?.let { desc ->
        manga.description = desc
    }
    // Publisher as scanlator
    book.metadata.publishers.firstOrNull()?.let { publisher ->
        chapter.scanlator = publisher
    }
    // Date
    book.metadata.dates.firstOrNull()?.value?.let { value ->
        parseDateMillis(value)?.let { chapter.date_upload = it }
    }
}

fun fillMangaMetadata(book: Book, manga: SManga) {
    // Title
    book.title?.takeIf { it.isNotBlank() }?.let { title -> manga.title = title }
    // Author(s)
    formatAuthors(book.metadata.authors)?.let { manga.author = it }
    // Description
    book.metadata.descriptions.firstOrNull()?.takeIf { it.isNotBlank() }?.let { desc -> manga.description = desc }
}

fun fillChapterMetadata(book: Book, chapter: SChapter) {
    // Publisher as scanlator
    book.metadata.publishers.firstOrNull()?.let { publisher ->
        chapter.scanlator = publisher
    }
    // Date
    book.metadata.dates.firstOrNull()?.value?.let { value ->
        parseDateMillis(value)?.let { chapter.date_upload = it }
    }
}

private fun formatAuthors(authors: List<Author>): String? {
    if (authors.isEmpty()) return null
    val parts = authors.mapNotNull { a ->
        listOfNotNull(
            a.firstname?.takeIf { it.isNotBlank() },
            a.lastname?.takeIf { it.isNotBlank() },
        ).takeIf { it.isNotEmpty() }?.joinToString(" ")
    }.filter { it.isNotBlank() }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

private fun parseDateMillis(value: Any): Long? = when (value) {
    is java.util.Date -> value.time
    is String -> parseDateStringToMillis(value)
    else -> null
}

private fun parseDateStringToMillis(s: String): Long? {
    val str = s.trim()
    // Try common ISO formats
    val tryParsers: List<() -> Long?> = listOf(
        {
            runCatching { Instant.parse(str).toEpochMilli() }.getOrNull()
        },
        {
            runCatching { OffsetDateTime.parse(str, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
        },
        {
            runCatching { LocalDateTime.parse(str, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
        },
        {
            runCatching { LocalDate.parse(str, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        },
    )
    for (p in tryParsers) {
        p()?.let { return it }
    }
    return null
}
