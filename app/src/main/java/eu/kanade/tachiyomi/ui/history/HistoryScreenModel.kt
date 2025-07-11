package eu.kanade.tachiyomi.ui.history

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateMapOf
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.presentation.history.HistoryUiModel
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.history.interactor.RemoveHistory
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class HistoryScreenModel(
    private val addTracks: AddTracks = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val getHistory: GetHistory = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getNextChapters: GetNextChapters = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val removeHistory: RemoveHistory = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
    private val sourceManager: SourceManager = Injekt.get(),
) : StateScreenModel<HistoryScreenModel.State>(State()) {

    private val _events: Channel<Event> = Channel(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            state.map { it.searchQuery }
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    getHistory.subscribe(query ?: "")
                        .distinctUntilChanged()
                        .catch { error ->
                            logcat(LogPriority.ERROR, error)
                            _events.send(Event.InternalError)
                        }
                        .map { it.toHistoryUiModels().toImmutableList() }
                        .flowOn(Dispatchers.IO)
                }
                .collect { newList -> mutableState.update { it.copy(list = newList) } }
        }
    }

    private fun List<HistoryWithRelations>.toHistoryUiModels(): List<HistoryUiModel> {
        return map { history -> HistoryUiModel.Item(history) }
            .insertSeparators { before, after ->
                val beforeDate = before?.item?.readAt?.time?.toLocalDate()
                val afterDate = after?.item?.readAt?.time?.toLocalDate()
                when {
                    beforeDate != afterDate && afterDate != null -> HistoryUiModel.Header(afterDate)
                    // Return null to avoid adding a separator between two items.
                    else -> null
                }
            }
    }

    suspend fun getNextChapter(): Chapter? {
        return withIOContext { getNextChapters.await(onlyUnread = false).firstOrNull() }
    }

    fun resumeManga(chapter: Chapter?) {
        screenModelScope.launchIO {
            if (chapter == null) return@launchIO
            val nextChapters = getNextChapters.await(chapter.mangaId, chapter.id, onlyUnread = false)
            if (nextChapters.firstOrNull()?.read == true) {
                sendNextChapterEvent(nextChapters.toMutableList().apply { add(0, chapter) })
            } else {
                sendNextChapterEvent(nextChapters)
            }
        }
    }

    private suspend fun sendNextChapterEvent(chapters: List<Chapter>) {
        val chapter = chapters.firstOrNull()
        _events.send(Event.OpenChapter(chapter))
    }

    fun toggleExpandHistory(historyItem: HistoryWithRelations) {
        val mangaId = historyItem.mangaId
        screenModelScope.launch {
            val currentState = mutableState.value
            val isExpanded = currentState.expandedStates[mangaId] ?: false
            mutableState.update {
                it.copy(expandedStates = it.expandedStates.apply { this[mangaId] = !isExpanded })
            }

            if (!isExpanded) { // If expanding, load previous history if not already loaded
                if ((
                        currentState.list?.find {
                            it is HistoryUiModel.Item && it.item.mangaId == mangaId
                        } as? HistoryUiModel.Item
                        )?.previousHistory == null
                ) {
                    loadPreviousHistory(historyItem)
                }
            }
        }
    }

    private fun loadPreviousHistory(historyItem: HistoryWithRelations) {
        screenModelScope.launch {
            val previousHistoryList = getHistory.await(historyItem.mangaId)
                .filter { (it.readAt?.time ?: 0) > 0 }
                .sortedByDescending { it.readAt }
                .map { HistoryWithRelations.from(it, historyItem) }
//                .drop(1) //remove current history from previous history list
                .toImmutableList()

            mutableState.update { currentState ->
                val newList = currentState.list?.map { uiModel ->
                    if (uiModel is HistoryUiModel.Item && uiModel.item.mangaId == historyItem.mangaId) {
                        uiModel.copy(previousHistory = previousHistoryList)
                    } else {
                        uiModel
                    }
                }?.toImmutableList()

                currentState.copy(list = newList ?: currentState.list) // Update list only if not null
            }
        }
    }

    fun removeFromHistory(history: HistoryWithRelations) {
        screenModelScope.launchIO {
            removeHistory.await(history)
        }
    }

    fun removeAllFromHistory(mangaId: Long) {
        screenModelScope.launchIO {
            removeHistory.await(mangaId)
        }
    }

    fun removeAllHistory() {
        screenModelScope.launchIO {
            val result = removeHistory.awaitAll()
            if (!result) return@launchIO
            _events.send(Event.HistoryCleared)
        }
    }

    fun updateSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        return getCategories.await().filterNot { it.isSystemCategory }
    }

    private fun moveMangaToCategory(mangaId: Long, categories: Category?) {
        val categoryIds = listOfNotNull(categories).map { it.id }
        moveMangaToCategory(mangaId, categoryIds)
    }

    private fun moveMangaToCategory(mangaId: Long, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            setMangaCategories.await(mangaId, categoryIds)
        }
    }

    fun moveMangaToCategoriesAndAddToLibrary(manga: Manga, categories: List<Long>) {
        moveMangaToCategory(manga.id, categories)
        if (manga.favorite) return

        screenModelScope.launchIO {
            updateManga.awaitUpdateFavorite(manga.id, true)
        }
    }

    private suspend fun getMangaCategoryIds(manga: Manga): List<Long> {
        return getCategories.await(manga.id)
            .map { it.id }
    }

    fun addFavorite(mangaId: Long) {
        screenModelScope.launchIO {
            val manga = getManga.await(mangaId) ?: return@launchIO

            val duplicates = getDuplicateLibraryManga(manga)
            if (duplicates.isNotEmpty()) {
                mutableState.update { it.copy(dialog = Dialog.DuplicateManga(manga, duplicates)) }
                return@launchIO
            }

            addFavorite(manga)
        }
    }

    fun addFavorite(manga: Manga) {
        screenModelScope.launchIO {
            // Move to default category if applicable
            val categories = getCategories()
            val defaultCategoryId = libraryPreferences.defaultCategory().get().toLong()
            val defaultCategory = categories.find { it.id == defaultCategoryId }

            when {
                // Default category set
                defaultCategory != null -> {
                    val result = updateManga.awaitUpdateFavorite(manga.id, true)
                    if (!result) return@launchIO
                    moveMangaToCategory(manga.id, defaultCategory)
                }

                // Automatic 'Default' or no categories
                defaultCategoryId == 0L || categories.isEmpty() -> {
                    val result = updateManga.awaitUpdateFavorite(manga.id, true)
                    if (!result) return@launchIO
                    moveMangaToCategory(manga.id, null)
                }

                // Choose a category
                else -> showChangeCategoryDialog(manga)
            }

            // Sync with tracking services if applicable
            addTracks.bindEnhancedTrackers(manga, sourceManager.getOrStub(manga.source))
        }
    }

    /*SY -->fun showMigrateDialog(currentManga: Manga, duplicate: Manga) {
        mutableState.update { currentState ->
            currentState.copy(dialog = Dialog.Migrate(newManga = currentManga, oldManga = duplicate))
        }
    } SY <--*/

    fun showChangeCategoryDialog(manga: Manga) {
        screenModelScope.launch {
            val categories = getCategories()
            val selection = getMangaCategoryIds(manga)
            mutableState.update { currentState ->
                currentState.copy(
                    dialog = Dialog.ChangeCategory(
                        manga = manga,
                        initialSelection = categories.mapAsCheckboxState { it.id in selection }.toImmutableList(),
                    ),
                )
            }
        }
    }

    @Immutable
    data class State(
        val searchQuery: String? = null,
        val list: ImmutableList<HistoryUiModel>? = null,
        val dialog: Dialog? = null,
        val expandedStates: MutableMap<Long, Boolean> = mutableStateMapOf(),
    )

    sealed interface Dialog {
        data object DeleteAll : Dialog
        data class Delete(val history: HistoryWithRelations) : Dialog
        data class DuplicateManga(val manga: Manga, val duplicates: List<MangaWithChapterCount>) : Dialog
        data class ChangeCategory(
            val manga: Manga,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
        /* SY --> data class Migrate(val newManga: Manga, val oldManga: Manga) : Dialog SY <-- */
    }

    sealed interface Event {
        data class OpenChapter(val chapter: Chapter?) : Event
        data object InternalError : Event
        data object HistoryCleared : Event
    }
}
