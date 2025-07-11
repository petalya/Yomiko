package eu.kanade.tachiyomi.ui.browse.source.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.core.preference.asState
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.interactor.GetExhSavedSearch
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.SourceFeedUI
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.all.MangaDex
import exh.source.getMainSource
import exh.source.mangaDexSourceIds
import exh.util.nullIfBlank
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.interactor.CountFeedSavedSearchBySourceId
import tachiyomi.domain.source.interactor.DeleteFeedSavedSearchById
import tachiyomi.domain.source.interactor.GetFeedSavedSearchBySourceId
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceIdFeed
import tachiyomi.domain.source.interactor.InsertFeedSavedSearch
import tachiyomi.domain.source.model.EXHSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import java.util.concurrent.Executors
import tachiyomi.domain.manga.model.Manga as DomainManga

open class SourceFeedScreenModel(
    val sourceId: Long,
    uiPreferences: UiPreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getFeedSavedSearchBySourceId: GetFeedSavedSearchBySourceId = Injekt.get(),
    private val getSavedSearchBySourceIdFeed: GetSavedSearchBySourceIdFeed = Injekt.get(),
    private val countFeedSavedSearchBySourceId: CountFeedSavedSearchBySourceId = Injekt.get(),
    private val insertFeedSavedSearch: InsertFeedSavedSearch = Injekt.get(),
    private val deleteFeedSavedSearchById: DeleteFeedSavedSearchById = Injekt.get(),
    private val getExhSavedSearch: GetExhSavedSearch = Injekt.get(),
) : StateScreenModel<SourceFeedState>(SourceFeedState()) {

    val source = sourceManager.getOrStub(sourceId)

    val sourceIsMangaDex = sourceId in mangaDexSourceIds

    private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()

    val startExpanded by uiPreferences.expandFilters().asState(screenModelScope)

    init {
        if (source is CatalogueSource) {
            setFilters(source.getFilterList())

            // Preload EPUB covers if this is the local source
            if (source.id == 0L) {
                screenModelScope.launchIO {
                    (source as? tachiyomi.source.local.LocalSource)?.preloadEpubCovers()
                }
            }

            screenModelScope.launchIO {
                val searches = loadSearches()
                mutableState.update { it.copy(savedSearches = searches) }
            }

            getFeedSavedSearchBySourceId.subscribe(source.id)
                .onEach {
                    val items = getSourcesToGetFeed(it)
                    mutableState.update { state ->
                        state.copy(
                            items = items,
                            hideEntriesInLibraryState = sourcePreferences.hideInLibraryItems().get(),
                        )
                    }
                    getFeed(items)
                }
                .launchIn(screenModelScope)
        }
    }

    fun setFilters(filters: FilterList) {
        mutableState.update { it.copy(filters = filters) }
    }

    private suspend fun hasTooManyFeeds(): Boolean {
        return countFeedSavedSearchBySourceId.await(source.id) > 10
    }

    fun createFeed(savedSearchId: Long) {
        screenModelScope.launchNonCancellable {
            insertFeedSavedSearch.await(
                FeedSavedSearch(
                    id = -1,
                    source = source.id,
                    savedSearch = savedSearchId,
                    global = false,
                ),
            )
        }
    }

    fun deleteFeed(feed: FeedSavedSearch) {
        screenModelScope.launchNonCancellable {
            deleteFeedSavedSearchById.await(feed.id)
        }
    }

    private suspend fun getSourcesToGetFeed(feedSavedSearch: List<FeedSavedSearch>): ImmutableList<SourceFeedUI> {
        if (source !is CatalogueSource) return persistentListOf()
        val savedSearches = getSavedSearchBySourceIdFeed.await(source.id)
            .associateBy { it.id }

        return (
            listOfNotNull(
                if (source.supportsLatest) {
                    SourceFeedUI.Latest(null)
                } else {
                    null
                },
                SourceFeedUI.Browse(null),
            ) + feedSavedSearch
                .map { SourceFeedUI.SourceSavedSearch(it, savedSearches[it.savedSearch]!!, null) }
            )
            .toImmutableList()
    }

    /**
     * Initiates get manga per feed.
     */
    private fun getFeed(feedSavedSearch: List<SourceFeedUI>) {
        if (source !is CatalogueSource) return
        screenModelScope.launch {
            feedSavedSearch.map { sourceFeed ->
                async {
                    val page = try {
                        withContext(coroutineDispatcher) {
                            when (sourceFeed) {
                                is SourceFeedUI.Browse -> source.getPopularManga(1)
                                is SourceFeedUI.Latest -> source.getLatestUpdates(1)
                                is SourceFeedUI.SourceSavedSearch -> source.getSearchManga(
                                    page = 1,
                                    query = sourceFeed.savedSearch.query.orEmpty(),
                                    filters = getFilterList(sourceFeed.savedSearch, source),
                                )
                            }
                        }.mangas
                    } catch (e: Exception) {
                        emptyList()
                    }

                    val titles = withIOContext {
                        networkToLocalManga(page.map { it.toDomainManga(source.id) })
                    }

                    mutableState.update { state ->
                        state.copy(
                            items = state.items.map { item ->
                                if (item.id == sourceFeed.id) sourceFeed.withResults(titles) else item
                            }.toImmutableList(),
                        )
                    }
                }
            }.awaitAll()
        }
    }

    private val filterSerializer = FilterSerializer()

    private fun getFilterList(savedSearch: SavedSearch, source: CatalogueSource): FilterList {
        val filters = savedSearch.filtersJson ?: return FilterList()
        return runCatching {
            val originalFilters = source.getFilterList()
            filterSerializer.deserialize(
                filters = originalFilters,
                json = Json.decodeFromString(filters),
            )
            originalFilters
        }.getOrElse { FilterList() }
    }

    @Composable
    fun getManga(initialManga: DomainManga): State<DomainManga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .collectLatest { manga ->
                    if (manga == null) return@collectLatest
                    value = manga
                }
        }
    }
    private suspend fun loadSearches() =
        getExhSavedSearch.await(source.id, (source as CatalogueSource)::getFilterList)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, EXHSavedSearch::name))
            .toImmutableList()

    fun onFilter(onBrowseClick: (query: String?, filters: String?) -> Unit) {
        if (source !is CatalogueSource) return
        screenModelScope.launchIO {
            val allDefault = state.value.filters == source.getFilterList()
            dismissDialog()
            if (allDefault) {
                onBrowseClick(
                    state.value.searchQuery?.nullIfBlank(),
                    null,
                )
            } else {
                onBrowseClick(
                    state.value.searchQuery?.nullIfBlank(),
                    Json.encodeToString(filterSerializer.serialize(state.value.filters)),
                )
            }
        }
    }

    fun onSavedSearch(
        search: EXHSavedSearch,
        onBrowseClick: (query: String?, searchId: Long) -> Unit,
        onToast: (StringResource) -> Unit,
    ) {
        if (source !is CatalogueSource) return
        screenModelScope.launchIO {
            if (search.filterList == null && state.value.filters.isNotEmpty()) {
                withUIContext {
                    onToast(SYMR.strings.save_search_invalid)
                }
                return@launchIO
            }

            val allDefault = search.filterList != null && search.filterList == source.getFilterList()
            dismissDialog()

            if (!allDefault) {
                onBrowseClick(
                    state.value.searchQuery?.nullIfBlank(),
                    search.id,
                )
            }
        }
    }

    fun onSavedSearchAddToFeed(
        search: EXHSavedSearch,
        onToast: (StringResource) -> Unit,
    ) {
        screenModelScope.launchIO {
            if (hasTooManyFeeds()) {
                withUIContext {
                    onToast(SYMR.strings.too_many_in_feed)
                }
                return@launchIO
            }
            openAddFeed(search.id, search.name)
        }
    }

    fun onMangaDexRandom(onRandomFound: (String) -> Unit) {
        screenModelScope.launchIO {
            val random = source.getMainSource<MangaDex>()?.fetchRandomMangaUrl()
                ?: return@launchIO
            onRandomFound(random)
        }
    }

    fun onHideEntriesInLibraryChange(state: Boolean) {
        mutableState.update { it.copy(hideEntriesInLibraryState = state) }
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun openFilterSheet() {
        mutableState.update { it.copy(dialog = Dialog.Filter) }
    }

    fun openDeleteFeed(feed: FeedSavedSearch) {
        mutableState.update { it.copy(dialog = Dialog.DeleteFeed(feed)) }
    }

    fun openAddFeed(feedId: Long, name: String) {
        mutableState.update { it.copy(dialog = Dialog.AddFeed(feedId, name)) }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed class Dialog {
        data object Filter : Dialog()
        data class DeleteFeed(val feed: FeedSavedSearch) : Dialog()
        data class AddFeed(val feedId: Long, val name: String) : Dialog()
    }

    override fun onDispose() {
        super.onDispose()
        coroutineDispatcher.close()
    }
}

@Immutable
data class SourceFeedState(
    val searchQuery: String? = null,
    val items: ImmutableList<SourceFeedUI> = persistentListOf(),
    val filters: FilterList = FilterList(),
    val savedSearches: ImmutableList<EXHSavedSearch> = persistentListOf(),
    val dialog: SourceFeedScreenModel.Dialog? = null,
    val hideEntriesInLibraryState: Boolean? = null,
) {
    val isLoading
        get() = items.isEmpty()
}
