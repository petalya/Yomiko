package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.manga.ChapterSettingsDialog
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.presentation.manga.EditCoverAction
import eu.kanade.presentation.manga.MangaScreen
import eu.kanade.presentation.manga.components.DeleteChaptersDialog
import eu.kanade.presentation.manga.components.MangaCoverDialog
import eu.kanade.presentation.manga.components.ScanlatorFilterDialog
import eu.kanade.presentation.manga.components.SetIntervalDialog
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.data.connections.discord.ReaderData
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.isLocalOrStub
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.migration.advanced.design.PreMigrationScreen
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.feed.SourceFeedScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.manga.merged.EditMergedSettingsDialog
import eu.kanade.tachiyomi.ui.manga.notes.MangaNotesScreen
import eu.kanade.tachiyomi.ui.manga.track.TrackInfoDialogHomeScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import exh.pagepreview.PagePreviewScreen
import exh.recs.RecommendsScreen
import exh.source.MERGED_SOURCE_ID
import exh.ui.ifSourcesLoaded
import exh.ui.metadata.MetadataViewScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaScreen(
    private val mangaId: Long,
    val fromSource: Boolean = false,
    private val smartSearchConfig: SourcesScreen.SmartSearchConfig? = null,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val lifecycleOwner = LocalLifecycleOwner.current
        val screenModel = rememberScreenModel {
            MangaScreenModel(context, lifecycleOwner.lifecycle, mangaId, fromSource, smartSearchConfig != null)
        }

        val state by screenModel.state.collectAsStateWithLifecycle()

        if (state is MangaScreenModel.State.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as MangaScreenModel.State.Success
        val isHttpSource = remember { successState.source is HttpSource }

        LaunchedEffect(successState.manga, screenModel.source) {
            if (isHttpSource) {
                try {
                    withIOContext {
                        assistUrl = getMangaUrl(screenModel.manga, screenModel.source)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to get manga URL" }
                }
            }
        }

        // SY -->
        LaunchedEffect(Unit) {
            screenModel.redirectFlow
                .take(1)
                .onEach {
                    navigator.replace(
                        MangaScreen(it.mangaId),
                    )
                }
                .launchIn(this)

            DiscordRPCService.setScreen(
                context, DiscordScreen.LIBRARY,
                ReaderData(
                    incognitoMode = Injekt.get<GetIncognitoState>().await(successState.manga.source, successState.manga.id),
                    mangaId = successState.manga.id,
                    chapterTitle = successState.manga.title,
                ),
            )
        }
        // SY <--

        MangaScreen(
            state = successState,
            mangaIncognitoState = screenModel.mangaIncognitoMode.value,
            snackbarHostState = screenModel.snackbarHostState,
            nextUpdate = successState.manga.expectedNextUpdate,
            isTabletUi = isTabletUi(),
            chapterSwipeStartAction = screenModel.chapterSwipeStartAction,
            chapterSwipeEndAction = screenModel.chapterSwipeEndAction,
            navigateUp = navigator::pop,
            onChapterClicked = { chapter ->
                val manga = successState.manga
                val source = successState.source
                val isEpub = chapter.url.contains(".epub") || chapter.url.contains("::")
                if (isEpub) {
                    navigator.push(eu.kanade.tachiyomi.ui.reader.epub.EpubReaderScreen(manga.id, chapter.id, chapter.url))
                } else if (source.id == 0L) {
                    navigator.push(eu.kanade.tachiyomi.ui.reader.NovelReaderScreen(manga.id, chapter.id))
                } else if (source.id == 10001L) {
                    navigator.push(eu.kanade.tachiyomi.ui.reader.NovelReaderScreen(manga.id, chapter.id))
                } else {
                    openChapter(context, chapter)
                }
            },
            onDownloadChapter = screenModel::runChapterDownloadActions.takeIf { !successState.source.isLocalOrStub() },
            onAddToLibraryClicked = {
                screenModel.toggleFavorite()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (!successState.manga.favorite && successState.hasLoggedInTrackers) screenModel.showTrackDialog()
            },
            // SY -->
            onWebViewClicked = {
                if (successState.mergedData == null) {
                    openMangaInWebView(
                        navigator,
                        screenModel.manga,
                        screenModel.source,
                    )
                } else {
                    openMergedMangaWebview(
                        context,
                        navigator,
                        successState.mergedData,
                    )
                }
            },
            onWebViewLongClicked = null,
            onTrackingClicked = screenModel::showTrackDialog,
            onTagSearch = {}, // If you want tag search, implement or connect to the correct function
            onMangaIncognitoToggled = screenModel::toggleMangaIncognitoMode,
            onFilterButtonClicked = screenModel::showSettingsDialog,
            onFilterLongClicked = {}, // If you want scanlator dialog, implement or connect to the correct function
            onRefresh = screenModel::fetchAllFromSource,
            onContinueReading = {
                continueReading(
                    navigator,
                    context,
                    successState.manga,
                    successState.source,
                    screenModel.getNextUnreadChapter(),
                    successState.chapters.map { it.chapter },
                )
            },
            onSearch = { query, global ->
                scope.launch {
                    performSearch(navigator, query, global)
                }
            },
            onCoverClicked = screenModel::showCoverDialog,
            onShareClicked = { shareManga(context, screenModel.manga, screenModel.source) },
            onDownloadActionClicked = screenModel::runDownloadAction,
            onEditCategoryClicked = screenModel::showChangeCategoryDialog,
            onEditFetchIntervalClicked = screenModel::showSetFetchIntervalDialog,
            onMigrateClicked = { migrateManga(navigator, successState.manga) },
            onEditNotesClicked = { navigator.push(eu.kanade.tachiyomi.ui.manga.notes.MangaNotesScreen(successState.manga)) },
            // SY -->
            onMetadataViewerClicked = { openMetadataViewer(navigator, successState.manga) },
            onEditInfoClicked = screenModel::showEditMangaInfoDialog,
            onRecommendClicked = { openRecommends(navigator, successState.source, successState.manga) },
            onMergedSettingsClicked = screenModel::showEditMergedSettingsDialog,
            onMergeClicked = { openSmartSearch(navigator, successState.manga) },
            onMergeWithAnotherClicked = { mergeWithAnother(navigator, context, successState.manga, screenModel::smartSearchMerge) },
            onOpenPagePreview = { page -> openPagePreview(context, screenModel.getNextUnreadChapter(), page) },
            onMorePreviewsClicked = { openMorePagePreviews(navigator, successState.manga) },
            previewsRowCount = successState.previewsRowCount,
            // SY <--
            onMultiBookmarkClicked = screenModel::bookmarkChapters,
            onMultiMarkAsReadClicked = screenModel::markChaptersRead,
            onMarkPreviousAsReadClicked = screenModel::markPreviousChapterRead,
            onMultiDeleteClicked = screenModel::showDeleteChapterDialog,
            onChapterSwipe = screenModel::chapterSwipe,
            onChapterSelected = { item, selected, userSelected, fromLongPress ->
                screenModel.toggleSelection(item, selected, userSelected, fromLongPress)
            },
            onAllChapterSelected = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
            onClickSourceSettingsClicked = null,
        )

        var showScanlatorsDialog by remember { mutableStateOf(false) }

        val onDismissRequest = { screenModel.dismissDialog() }
        when (val dialog = successState.dialog) {
            null -> {}
            is MangaScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        screenModel.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                    },
                )
            }
            is MangaScreenModel.Dialog.DeleteChapters -> {
                DeleteChaptersDialog(
                    chapterCount = dialog.chapters.size,
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.toggleAllSelection(false)
                        screenModel.deleteChapters(dialog.chapters)
                    },
                )
            }

            is MangaScreenModel.Dialog.DuplicateManga -> {
                DuplicateMangaDialog(
                    duplicates = dialog.duplicates,
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.toggleFavorite(onRemoved = {}, checkDuplicate = false) },
                    onOpenManga = { navigator.push(MangaScreen(it.id)) },
                    onMigrate = {
                        // SY -->
                        migrateManga(navigator, it, screenModel.manga!!.id)
                        // SY <--
                    },
                )
            }
            MangaScreenModel.Dialog.SettingsSheet -> ChapterSettingsDialog(
                onDismissRequest = onDismissRequest,
                manga = successState.manga,
                onDownloadFilterChanged = screenModel::setDownloadedFilter,
                onUnreadFilterChanged = screenModel::setUnreadFilter,
                onBookmarkedFilterChanged = screenModel::setBookmarkedFilter,
                onSortModeChanged = screenModel::setSorting,
                onDisplayModeChanged = screenModel::setDisplayMode,
                onSetAsDefault = screenModel::setCurrentSettingsAsDefault,
                onResetToDefault = screenModel::resetToDefaultSettings,
                scanlatorFilterActive = successState.scanlatorFilterActive,
                onScanlatorFilterClicked = { showScanlatorsDialog = true },
            )
            MangaScreenModel.Dialog.TrackSheet -> {
                NavigatorAdaptiveSheet(
                    screen = TrackInfoDialogHomeScreen(
                        mangaId = successState.manga.id,
                        mangaTitle = successState.manga.title,
                        sourceId = successState.source.id,
                    ),
                    enableSwipeDismiss = { it.lastItem is TrackInfoDialogHomeScreen },
                    onDismissRequest = onDismissRequest,
                )
            }
            MangaScreenModel.Dialog.FullCover -> {
                val sm = rememberScreenModel { MangaCoverScreenModel(successState.manga.id) }
                val manga by sm.state.collectAsState()
                if (manga != null) {
                    val getContent = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
                        if (it == null) return@rememberLauncherForActivityResult
                        sm.editCover(context, it)
                    }
                    MangaCoverDialog(
                        manga = manga!!,
                        snackbarHostState = sm.snackbarHostState,
                        isCustomCover = remember(manga) { manga!!.hasCustomCover() },
                        onShareClick = { sm.shareCover(context) },
                        onSaveClick = { sm.saveCover(context) },
                        onEditClick = {
                            when (it) {
                                EditCoverAction.EDIT -> getContent.launch("image/*")
                                EditCoverAction.DELETE -> sm.deleteCustomCover(context)
                            }
                        },
                        onDismissRequest = onDismissRequest,
                    )
                } else {
                    LoadingScreen(Modifier.systemBarsPadding())
                }
            }
            is MangaScreenModel.Dialog.SetFetchInterval -> {
                SetIntervalDialog(
                    interval = dialog.manga.fetchInterval,
                    nextUpdate = dialog.manga.expectedNextUpdate,
                    onDismissRequest = onDismissRequest,
                    onValueChanged = { interval: Int -> screenModel.setFetchInterval(dialog.manga, interval) }
                        .takeIf { screenModel.isUpdateIntervalEnabled },
                )
            }
            // SY -->
            is MangaScreenModel.Dialog.EditMangaInfo -> {
                EditMangaDialog(
                    manga = dialog.manga,
                    onDismissRequest = screenModel::dismissDialog,
                    onPositiveClick = screenModel::updateMangaInfo,
                )
            }
            is MangaScreenModel.Dialog.EditMergedSettings -> {
                EditMergedSettingsDialog(
                    mergedData = dialog.mergedData,
                    onDismissRequest = screenModel::dismissDialog,
                    onDeleteClick = screenModel::deleteMerge,
                    onPositiveClick = screenModel::updateMergeSettings,
                )
            }
            // SY <--
        }

        if (showScanlatorsDialog) {
            ScanlatorFilterDialog(
                availableScanlators = successState.availableScanlators,
                excludedScanlators = successState.excludedScanlators,
                onDismissRequest = { showScanlatorsDialog = false },
                onConfirm = screenModel::setExcludedScanlators,
            )
        }
    }

    private fun continueReading(navigator: Navigator, context: Context, manga: Manga, source: Source, unreadChapter: Chapter?, chapters: List<Chapter>) {
        val isEpub = unreadChapter?.url?.contains(".epub") == true || unreadChapter?.url?.contains("::") == true
        if (isEpub) {
            if (unreadChapter != null) {
                navigator.push(eu.kanade.tachiyomi.ui.reader.epub.EpubReaderScreen(manga.id, unreadChapter.id, unreadChapter.url))
            }
        } else if (source.id == 0L || source.id == 10001L) {
            // Find the chapter after the last read
            val lastReadIndex = chapters.indexOfLast { it.read }
            val nextChapter = when {
                chapters.isEmpty() -> null
                lastReadIndex == -1 -> chapters.first() // No chapters read, start from first
                lastReadIndex + 1 < chapters.size -> chapters[lastReadIndex + 1] // Next after last read
                else -> chapters.last() // All read, open last
            }
            if (nextChapter != null) {
                navigator.push(eu.kanade.tachiyomi.ui.reader.NovelReaderScreen(manga.id, nextChapter.id))
            }
        } else {
            if (unreadChapter == null) return
            openChapter(context, unreadChapter)
        }
    }

    private fun openChapter(context: Context, chapter: Chapter) {
        context.startActivity(ReaderActivity.newIntent(context, chapter.mangaId, chapter.id))
    }

    private fun getMangaUrl(manga_: Manga?, source_: Source?): String? {
        val manga = manga_ ?: return null
        val source = source_ as? HttpSource ?: return null

        return try {
            source.getMangaUrl(manga.toSManga())
        } catch (e: Exception) {
            null
        }
    }

    private fun openMangaInWebView(navigator: Navigator, manga_: Manga?, source_: Source?) {
        getMangaUrl(manga_, source_)?.let { url ->
            navigator.push(
                WebViewScreen(
                    url = url,
                    initialTitle = manga_?.title,
                    sourceId = source_?.id,
                ),
            )
        }
    }

    private fun shareManga(context: Context, manga_: Manga?, source_: Source?) {
        try {
            getMangaUrl(manga_, source_)?.let { url ->
                val intent = url.toUri().toShareIntent(context, type = "text/plain")
                context.startActivity(
                    Intent.createChooser(
                        intent,
                        context.stringResource(MR.strings.action_share),
                    ),
                )
            }
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    /**
     * Perform a search using the provided query.
     *
     * @param query the search query to the parent controller
     */
    private suspend fun performSearch(navigator: Navigator, query: String, global: Boolean) {
        if (global) {
            navigator.push(GlobalSearchScreen(query))
            return
        }

        if (navigator.size < 2) {
            return
        }

        when (val previousController = navigator.items[navigator.size - 2]) {
            is HomeScreen -> {
                navigator.pop()
                previousController.search(query)
            }
            is BrowseSourceScreen -> {
                navigator.pop()
                previousController.search(query)
            }
            // SY -->
            is SourceFeedScreen -> {
                navigator.pop()
                navigator.replace(BrowseSourceScreen(previousController.sourceId, query))
            }
            // SY <--
        }
    }

    /**
     * Performs a genre search using the provided genre name.
     *
     * @param genreName the search genre to the parent controller
     */
    private suspend fun performGenreSearch(navigator: Navigator, genreName: String, source: Source) {
        if (navigator.size < 2) {
            return
        }

        val previousController = navigator.items[navigator.size - 2]
        if (previousController is BrowseSourceScreen && source is HttpSource) {
            navigator.pop()
            previousController.searchGenre(genreName)
        } else {
            performSearch(navigator, genreName, global = false)
        }
    }

    /**
     * Copy Manga URL to Clipboard
     */
    private fun copyMangaUrl(context: Context, manga_: Manga?, source_: Source?) {
        val manga = manga_ ?: return
        val source = source_ as? HttpSource ?: return
        val url = source.getMangaUrl(manga.toSManga())
        context.copyToClipboard(url, url)
    }

    // SY -->

    /**
     * Initiates source migration for the specific manga.
     */
    private fun migrateManga(navigator: Navigator, manga: Manga, toMangaId: Long? = null) {
        // SY -->
        PreMigrationScreen.navigateToMigration(
            Injekt.get<SourcePreferences>().skipPreMigration().get(),
            navigator,
            manga.id,
            toMangaId,
        )
        // SY <--
    }

    private fun openMetadataViewer(navigator: Navigator, manga: Manga) {
        navigator.push(MetadataViewScreen(manga.id, manga.source))
    }

    private fun openMergedMangaWebview(context: Context, navigator: Navigator, mergedMangaData: MergedMangaData) {
        val sourceManager: SourceManager = Injekt.get()
        val mergedManga = mergedMangaData.manga.values.filterNot { it.source == MERGED_SOURCE_ID }
        val sources = mergedManga.map { sourceManager.getOrStub(it.source) }
        MaterialAlertDialogBuilder(context)
            .setTitle(MR.strings.action_open_in_web_view.getString(context))
            .setSingleChoiceItems(
                Array(mergedManga.size) { index -> sources[index].toString() },
                -1,
            ) { dialog, index ->
                dialog.dismiss()
                openMangaInWebView(navigator, mergedManga[index], sources[index] as? HttpSource)
            }
            .setNegativeButton(MR.strings.action_cancel.getString(context), null)
            .show()
    }

    private fun openMorePagePreviews(navigator: Navigator, manga: Manga) {
        navigator.push(PagePreviewScreen(manga.id))
    }

    private fun openPagePreview(context: Context, chapter: Chapter?, page: Int) {
        chapter ?: return
        context.startActivity(ReaderActivity.newIntent(context, chapter.mangaId, chapter.id, page))
    }
    // SY <--

    // EXH -->
    private fun openSmartSearch(navigator: Navigator, manga: Manga) {
        val smartSearchConfig = SourcesScreen.SmartSearchConfig(manga.title, manga.id)

        navigator.push(SourcesScreen(smartSearchConfig))
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun mergeWithAnother(
        navigator: Navigator,
        context: Context,
        manga: Manga,
        smartSearchMerge: suspend (Manga, Long) -> Manga,
    ) {
        launchUI {
            try {
                val mergedManga = withNonCancellableContext {
                    smartSearchMerge(manga, smartSearchConfig?.origMangaId!!)
                }

                navigator.popUntil { it is SourcesScreen }
                navigator.pop()
                navigator replace MangaScreen(mergedManga.id, true)
                context.toast(SYMR.strings.entry_merged)
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                context.toast(context.stringResource(SYMR.strings.failed_merge, e.message.orEmpty()))
            }
        }
    }
    // EXH <--

    // AZ -->
    private fun openRecommends(navigator: Navigator, source: Source?, manga: Manga) {
        source ?: return
        RecommendsScreen.Args.SingleSourceManga(manga.id, source.id)
            .let(::RecommendsScreen)
            .let(navigator::push)
    }
    // AZ <--
}
