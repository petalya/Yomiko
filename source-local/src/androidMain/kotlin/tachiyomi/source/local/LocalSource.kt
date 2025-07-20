package tachiyomi.source.local

import android.content.Context
import android.util.LruCache
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.storage.EpubFile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import logcat.LogPriority
import mihon.core.common.archive.ZipWriter
import mihon.core.common.archive.archiveReader
import nl.adaptivity.xmlutil.core.AndroidXmlReader
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.core.metadata.comicinfo.COMIC_INFO_FILE
import tachiyomi.core.metadata.comicinfo.ComicInfo
import tachiyomi.core.metadata.comicinfo.ComicInfoPublishingStatus
import tachiyomi.core.metadata.comicinfo.copyFromComicInfo
import tachiyomi.core.metadata.comicinfo.getComicInfo
import tachiyomi.core.metadata.tachiyomi.MangaDetails
import tachiyomi.domain.chapter.service.ChapterRecognition
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.source.local.filter.OrderBy
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.io.Archive
import tachiyomi.source.local.io.Format
import tachiyomi.source.local.io.LocalSourceFileSystem
import tachiyomi.source.local.metadata.fillChapterMetadata
import tachiyomi.source.local.metadata.fillMangaMetadata
import uy.kohesive.injekt.injectLazy
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.days
import tachiyomi.domain.source.model.Source as DomainSource

actual class LocalSource(
    private val context: Context,
    private val fileSystem: LocalSourceFileSystem,
    private val coverManager: LocalCoverManager,
    // SY -->
    private val allowHiddenFiles: () -> Boolean,
    // SY <--
) : CatalogueSource, UnmeteredSource {

    private val json: Json by injectLazy()
    private val xml: XML by injectLazy()

    // Add a cache to avoid re-processing the same EPUBs
    private val epubCoverCache = LruCache<String, Boolean>(100)

    @Suppress("PrivatePropertyName")
    private val PopularFilters = FilterList(OrderBy.Popular(context))

    @Suppress("PrivatePropertyName")
    private val LatestFilters = FilterList(OrderBy.Latest(context))

    override val name: String = context.stringResource(MR.strings.local_source)

    override val id: Long = ID

    override val lang: String = "other"

    override fun toString() = name

    override val supportsLatest: Boolean = true

    // Browse related
    override suspend fun getPopularManga(page: Int) = getSearchManga(page, "", PopularFilters)

    override suspend fun getLatestUpdates(page: Int) = getSearchManga(page, "", LatestFilters)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = withIOContext {
        val lastModifiedLimit = if (filters === LatestFilters) {
            System.currentTimeMillis() - LATEST_THRESHOLD
        } else {
            0L
        }
        // SY -->
        val allowLocalSourceHiddenFolders = allowHiddenFiles()
        // SY <--

        var mangaDirs = fileSystem.getFilesInBaseDirectory()
            // Filter out files that are hidden and is not a folder
            .filter {
                it.isDirectory &&
                    /* SY --> */ (
                        !it.name.orEmpty().startsWith('.') ||
                            allowLocalSourceHiddenFolders
                        ) /* SY <-- */
            }
            .distinctBy { it.name }
            .filter {
                if (lastModifiedLimit == 0L && query.isBlank()) {
                    true
                } else if (lastModifiedLimit == 0L) {
                    it.name.orEmpty().contains(query, ignoreCase = true)
                } else {
                    it.lastModified() >= lastModifiedLimit
                }
            }

        filters.forEach { filter ->
            when (filter) {
                is OrderBy.Popular -> {
                    mangaDirs = if (filter.state!!.ascending) {
                        mangaDirs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() })
                    } else {
                        mangaDirs.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() })
                    }
                }
                is OrderBy.Latest -> {
                    mangaDirs = if (filter.state!!.ascending) {
                        mangaDirs.sortedBy(UniFile::lastModified)
                    } else {
                        mangaDirs.sortedByDescending(UniFile::lastModified)
                    }
                }
                else -> {
                    /* Do nothing */
                }
            }
        }

        val mangas = mangaDirs
            .map { mangaDir ->
                async {
                    SManga.create().apply {
                        val mangaDirFiles = mangaDir.listFiles().orEmpty()
                        val epubFile = mangaDirFiles.firstOrNull { it.extension.equals("epub", true) }
                        val comicInfoFile = mangaDirFiles.firstOrNull { it.name == COMIC_INFO_FILE }
                        if (epubFile != null) {
                            val epubLastModified = epubFile.lastModified()
                            val comicInfoLastModified = comicInfoFile?.lastModified() ?: 0L
                            if (comicInfoFile != null && comicInfoLastModified >= epubLastModified) {
                                // Use ComicInfo.xml cache
                                comicInfoFile.openInputStream().use { stream ->
                                    val comicInfo = AndroidXmlReader(stream, StandardCharsets.UTF_8.name()).use { xmlReader ->
                                        xml.decodeFromReader<ComicInfo>(xmlReader)
                                    }
                                    this.copyFromComicInfo(comicInfo)
                                }
                            } else {
                                // Parse EPUB and update ComicInfo.xml
                                epubFile.openInputStream()?.use { inputStream ->
                                    try {
                                        val epub = eu.kanade.tachiyomi.util.storage.EpubFile(inputStream)
                                        val book = epub.book
                                        book.title?.takeIf { it.isNotBlank() }?.let { title = it }
                                        val authors = book.metadata.authors
                                        if (authors.isNotEmpty()) {
                                            author = authors.joinToString(", ") { it.firstname + " " + it.lastname }.trim()
                                        }
                                        book.metadata.descriptions.firstOrNull()?.let { desc ->
                                            description = desc
                                        }
                                        // Save ComicInfo.xml
                                        val comicInfo = this.getComicInfo()
                                        mangaDir.createFile(COMIC_INFO_FILE)?.openOutputStream()?.use { out ->
                                            out.write(xml.encodeToString(ComicInfo.serializer(), comicInfo).toByteArray())
                                        }
                                    } catch (e: Exception) {
                                        // fallback to directory name for title, leave author as null
                                        title = mangaDir.name.orEmpty()
                                    }
                                }
                            }
                        } else {
                            title = mangaDir.name.orEmpty()
                        }
                        url = mangaDir.name.orEmpty()

                        // Try to find the cover first before extraction (faster)
                        val existingCover = coverManager.find(mangaDir.name.orEmpty())
                        if (existingCover != null) {
                            thumbnail_url = existingCover.uri.toString()
                        } else {
                            // Extract EPUB cover if needed - but only check for EPUBs if no cover exists
                            val hasEpubs = mangaDirFiles.any { it.extension.equals("epub", true) }
                            if (hasEpubs) {
                                extractCoversFromEpubs(mangaDirFiles.filter { it.extension.equals("epub", true) }, this)
                            }
                        }
                    }
                }
            }
            .awaitAll()

        MangasPage(mangas, false)
    }

    // SY -->
    fun updateMangaInfo(manga: SManga) {
        val mangaDirFiles = fileSystem.getFilesInMangaDirectory(manga.url)
        val existingFile = mangaDirFiles
            .firstOrNull { it.name == COMIC_INFO_FILE }
        val comicInfoArchiveFile = mangaDirFiles
            .firstOrNull { it.name == COMIC_INFO_ARCHIVE }
        val comicInfoArchiveReader = comicInfoArchiveFile?.archiveReader(context)
        val existingComicInfo =
            (existingFile?.openInputStream() ?: comicInfoArchiveReader?.getInputStream(COMIC_INFO_FILE))?.use {
                AndroidXmlReader(it, StandardCharsets.UTF_8.name()).use { xmlReader ->
                    xml.decodeFromReader<ComicInfo>(xmlReader)
                }
            }
        val newComicInfo = if (existingComicInfo != null) {
            manga.run {
                existingComicInfo.copy(
                    series = ComicInfo.Series(title),
                    summary = description?.let { ComicInfo.Summary(it) },
                    writer = author?.let { ComicInfo.Writer(it) },
                    penciller = artist?.let { ComicInfo.Penciller(it) },
                    genre = genre?.let { ComicInfo.Genre(it) },
                    publishingStatus = ComicInfo.PublishingStatusTachiyomi(
                        ComicInfoPublishingStatus.toComicInfoValue(status.toLong()),
                    ),
                )
            }
        } else {
            manga.getComicInfo()
        }

        fileSystem.getMangaDirectory(manga.url)?.let {
            copyComicInfoFile(
                xml.encodeToString(ComicInfo.serializer(), newComicInfo).byteInputStream(),
                it,
                comicInfoArchiveReader?.encrypted ?: false,
            )
        }
    }
    // SY <--

    // Manga details related
    override suspend fun getMangaDetails(manga: SManga): SManga = withIOContext {
        coverManager.find(manga.url)?.let {
            manga.thumbnail_url = it.uri.toString()
        }

        // Augment manga details based on metadata files
        try {
            val mangaDir = fileSystem.getMangaDirectory(manga.url) ?: error("${manga.url} is not a valid directory")
            val mangaDirFiles = mangaDir.listFiles().orEmpty()

            val comicInfoFile = mangaDirFiles
                .firstOrNull { it.name == COMIC_INFO_FILE }
            val noXmlFile = mangaDirFiles
                .firstOrNull { it.name == ".noxml" }
            val legacyJsonDetailsFile = mangaDirFiles
                .firstOrNull { it.extension == "json" }
            // SY -->
            val comicInfoArchiveFile = mangaDirFiles
                .firstOrNull { it.name == COMIC_INFO_ARCHIVE }
            // SY <--

            when {
                // Top level ComicInfo.xml
                comicInfoFile != null -> {
                    noXmlFile?.delete()
                    setMangaDetailsFromComicInfoFile(comicInfoFile.openInputStream(), manga)
                }
                // SY -->
                comicInfoArchiveFile != null -> {
                    noXmlFile?.delete()

                    comicInfoArchiveFile.archiveReader(context).getInputStream(COMIC_INFO_FILE)
                        ?.let { setMangaDetailsFromComicInfoFile(it, manga) }
                }

                // SY <--

                // Old custom JSON format
                // TODO: remove support for this entirely after a while
                legacyJsonDetailsFile != null -> {
                    json.decodeFromStream<MangaDetails>(legacyJsonDetailsFile.openInputStream()).run {
                        title?.let { manga.title = it }
                        author?.let { manga.author = it }
                        artist?.let { manga.artist = it }
                        description?.let { manga.description = it }
                        genre?.let { manga.genre = it.joinToString() }
                        status?.let { manga.status = it }
                    }
                    // Replace with ComicInfo.xml file
                    val comicInfo = manga.getComicInfo()
                    mangaDir
                        .createFile(COMIC_INFO_FILE)
                        ?.openOutputStream()
                        ?.use {
                            val comicInfoString = xml.encodeToString(ComicInfo.serializer(), comicInfo)
                            it.write(comicInfoString.toByteArray())
                            legacyJsonDetailsFile.delete()
                        }
                }

                // Copy ComicInfo.xml from chapter archive to top level if found
                noXmlFile == null -> {
                    val chapterArchives = mangaDirFiles.filter(Archive::isSupported)

                    val copiedFile = copyComicInfoFileFromArchive(chapterArchives, mangaDir)

                    // SY -->
                    if (copiedFile != null && copiedFile.name != COMIC_INFO_ARCHIVE) {
                        setMangaDetailsFromComicInfoFile(copiedFile.openInputStream(), manga)
                    } else if (copiedFile != null && copiedFile.name == COMIC_INFO_ARCHIVE) {
                        copiedFile.archiveReader(context).getInputStream(COMIC_INFO_FILE)
                            ?.let { setMangaDetailsFromComicInfoFile(it, manga) }
                    } // SY <--
                    else {
                        // Avoid re-scanning
                        mangaDir.createFile(".noxml")
                    }
                }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Error setting manga details from local metadata for ${manga.title}" }
        }

        return@withIOContext manga
    }

    private fun copyComicInfoFileFromArchive(chapterArchives: List<UniFile>, folder: UniFile): UniFile? {
        for (chapter in chapterArchives) {
            chapter.archiveReader(context).use { reader ->
                reader.getInputStream(COMIC_INFO_FILE)?.use { stream ->
                    return copyComicInfoFile(stream, folder, /* SY --> */ reader.encrypted /* SY <-- */)
                }
            }
        }
        return null
    }

    private fun copyComicInfoFile(
        comicInfoFileStream: InputStream,
        folder: UniFile,
        // SY -->
        encrypt: Boolean,
        // SY <--
    ): UniFile? {
        // SY -->
        if (encrypt) {
            val comicInfoArchiveFile = folder.createFile(COMIC_INFO_ARCHIVE)
            comicInfoArchiveFile?.let { archive ->
                ZipWriter(context, archive, encrypt = true).use { writer ->
                    writer.write(comicInfoFileStream.use { it.readBytes() }, COMIC_INFO_FILE)
                }
            }
            return comicInfoArchiveFile
        } else {
            // SY <--
            return folder.createFile(COMIC_INFO_FILE)?.apply {
                openOutputStream().use { outputStream ->
                    comicInfoFileStream.use { it.copyTo(outputStream) }
                }
            }
        }
    }

    private fun setMangaDetailsFromComicInfoFile(stream: InputStream, manga: SManga) {
        val comicInfo = AndroidXmlReader(stream, StandardCharsets.UTF_8.name()).use {
            xml.decodeFromReader<ComicInfo>(it)
        }

        manga.copyFromComicInfo(comicInfo)
    }

    // Chapters
    override suspend fun getChapterList(manga: SManga): List<SChapter> = withIOContext {
        val chapters = mutableListOf<SChapter>()
        val files = fileSystem.getFilesInMangaDirectory(manga.url)
            .filterNot { it.name.orEmpty().startsWith('.') }
            .filter { it.isDirectory || Archive.isSupported(it) || it.extension.equals("epub", true) }

        // First, try to extract covers from any EPUB files
        if (manga.thumbnail_url.isNullOrBlank()) {
            extractCoversFromEpubs(files, manga)
        }

        for (chapterFile in files) {
            val format = Format.valueOf(chapterFile)
            if (format is Format.Epub) {
                // Extract chapters from EPUB TOC (Table of Contents)
                val epubInputStream = format.file.openInputStream()!!
                val epub = eu.kanade.tachiyomi.util.storage.EpubFile(epubInputStream)
                val book = epub.book
                fillMangaMetadata(book, manga)
                val tocRefs = book.tableOfContents.tocReferences
                val seenNames = mutableSetOf<String>()
                var index = 0
                for (tocRef in tocRefs) {
                    val chapterName = tocRef.title?.takeIf { it.isNotBlank() } ?: continue
                    if (!seenNames.add(chapterName)) continue // skip duplicates
                    val href = tocRef.resource?.href ?: continue
                    val sChapter = SChapter.create().apply {
                        url = "${manga.url}/${chapterFile.name}::$href"
                        name = chapterName
                        date_upload = chapterFile.lastModified()
                        chapter_number = index.toFloat() + 1f
                        scanlator = null
                    }
                    android.util.Log.d("LocalSource", "Adding TOC chapter: name=$chapterName, url=${sChapter.url}")
                    fillChapterMetadata(book, sChapter)
                    chapters.add(sChapter)
                    index++
                }
                epub.close()
            } else {
                // Default: one chapter per file/dir/archive
                val sChapter = SChapter.create().apply {
                    url = "${manga.url}/${chapterFile.name}"
                    name = if (chapterFile.isDirectory) {
                        chapterFile.name
                    } else {
                        chapterFile.nameWithoutExtension
                    }.orEmpty()
                    date_upload = chapterFile.lastModified()
                    chapter_number = tachiyomi.domain.chapter.service.ChapterRecognition
                        .parseChapterNumber(manga.title, this.name, this.chapter_number.toDouble())
                        .toFloat()
                }
                chapters.add(sChapter)
            }
        }
        
        // Copy the cover from the first chapter found if still not available
        if (manga.thumbnail_url.isNullOrBlank()) {
            chapters.lastOrNull()?.let { chapter ->
                try {
                    updateCover(chapter, manga)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Error updating cover for ${manga.title}" }
                }
            }
        }
        
        chapters.sortedWith { c1, c2 ->
            val c = c2.chapter_number.compareTo(c1.chapter_number)
            if (c == 0) c2.name.compareToCaseInsensitiveNaturalOrder(c1.name) else c
        }
    }

    /**
     * Extracts covers from EPUB files in the manga directory
     * Optimized version that only processes one EPUB at a time to improve feed loading performance
     * Uses a cache to avoid re-processing the same files
     */
    private fun extractCoversFromEpubs(files: List<UniFile>, manga: SManga): Boolean {
        val epubFiles = files.filter { it.extension.equals("epub", true) }
        if (epubFiles.isEmpty()) return false
        
        val mangaDir = fileSystem.getMangaDirectory(manga.url) ?: return false
        val mangaDirPath = mangaDir.filePath ?: return false
        
        // Check cache first to avoid re-processing
        val cacheKey = "${manga.url}:cover"
        if (epubCoverCache.get(cacheKey) == true) {
            // We've already tried to extract the cover for this manga
            return false
        }
        
        // Only process the first EPUB file instead of all of them for faster loading
        val epubFile = epubFiles.first()
        try {
            epubFile.openInputStream()?.use { inputStream ->
                val epub = EpubFile(inputStream)
                val coverResource = epub.book.coverImage
                    ?: epub.book.resources.all.firstOrNull {
                        val isImage = it.mediaType?.name?.startsWith("image/") == true
                        val idMatch = it.id?.contains("cover", ignoreCase = true) == true
                        val hrefMatch = it.href?.contains("cover", ignoreCase = true) == true
                        isImage && (idMatch || hrefMatch)
                    }
                if (coverResource != null) {
                    coverResource.inputStream.use { coverStream ->
                        // Use the same process as other covers
                        val coverFile = coverManager.update(manga, coverStream)
                        if (coverFile != null) {
                            manga.thumbnail_url = coverFile.uri.toString()
                            epubCoverCache.put(cacheKey, true)
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error extracting cover from EPUB ${epubFile.name}" }
        }
        
        // Cache the result even if we failed to avoid repeated attempts
        epubCoverCache.put(cacheKey, true)
        return false
    }

    /**
     * Preloads covers for EPUB files in the background.
     * This improves user experience by starting the cover extraction process early.
     */
    suspend fun preloadEpubCovers() = withIOContext {
        try {
            // Get all manga directories
            val mangaDirs = fileSystem.getFilesInBaseDirectory()
                .filter { it.isDirectory && !it.name.orEmpty().startsWith('.') }
                .take(10) // Limit to first 10 to avoid excessive processing
            
            // Process each directory in parallel with a limit
            mangaDirs.map { mangaDir ->
                async {
                    val manga = SManga.create().apply {
                        title = mangaDir.name.orEmpty()
                        url = mangaDir.name.orEmpty()
                    }
                    
                    // Skip if cover already exists
                    if (coverManager.find(manga.url) != null) return@async
                    
                    // Find EPUB files
                    val epubFiles = fileSystem.getFilesInMangaDirectory(manga.url)
                        ?.filter { it.extension.equals("epub", true) }
                        ?: return@async
                    
                    if (epubFiles.isNotEmpty()) {
                        extractCoversFromEpubs(epubFiles, manga)
                    }
                }
            }.awaitAll()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error preloading EPUB covers" }
        }
    }

    // Filters
    override fun getFilterList() = FilterList(OrderBy.Popular(context))

    // Unused stuff
    override suspend fun getPageList(chapter: SChapter): List<Page> = throw UnsupportedOperationException("Unused")

    fun getFormat(chapter: SChapter): Format {
        try {
            val (mangaDirName, chapterName) = chapter.url.split('/', limit = 2)
            return fileSystem.getBaseDirectory()
                ?.findFile(mangaDirName)
                ?.findFile(chapterName)
                ?.let(Format.Companion::valueOf)
                ?: throw Exception(context.stringResource(MR.strings.chapter_not_found))
        } catch (e: Format.UnknownFormatException) {
            throw Exception(context.stringResource(MR.strings.local_invalid_format))
        } catch (e: Exception) {
            throw e
        }
    }

    private fun updateCover(chapter: SChapter, manga: SManga): UniFile? {
        return try {
            when (val format = getFormat(chapter)) {
                is Format.Directory -> {
                    val entry = format.file.listFiles()
                        ?.sortedWith { f1, f2 ->
                            f1.name.orEmpty().compareToCaseInsensitiveNaturalOrder(
                                f2.name.orEmpty(),
                            )
                        }
                        ?.find {
                            !it.isDirectory && ImageUtil.isImage(it.name) { it.openInputStream() }
                        }

                    entry?.let { coverManager.update(manga, it.openInputStream()) }
                }
                is Format.Archive -> {
                    format.file.archiveReader(context).use { reader ->
                        val entry = reader.useEntries { entries ->
                            entries
                                .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                                .find { it.isFile && ImageUtil.isImage(it.name) { reader.getInputStream(it.name)!! } }
                        }

                        entry?.let { coverManager.update(manga, reader.getInputStream(it.name)!!, reader.encrypted) }
                    }
                }
                is Format.Epub -> {
                    EpubFile(format.file.openInputStream()!!).use { epub ->
                        val entry = epub.getImagesFromPages().firstOrNull()
                        entry?.let { coverManager.update(manga, epub.getInputStream(it)!!) }
                    }
                }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Error updating cover for ${manga.title}" }
            null
        }
    }

    companion object {
        const val ID = 0L
        const val HELP_URL = "https://mihon.app/docs/guides/local-source/"

        // SY -->
        const val COMIC_INFO_ARCHIVE = "ComicInfo.cbm"
        // SY <--

        private val LATEST_THRESHOLD = 7.days.inWholeMilliseconds
    }
}

fun Manga.isLocal(): Boolean = source == LocalSource.ID

fun Source.isLocal(): Boolean = id == LocalSource.ID

fun DomainSource.isLocal(): Boolean = id == LocalSource.ID
