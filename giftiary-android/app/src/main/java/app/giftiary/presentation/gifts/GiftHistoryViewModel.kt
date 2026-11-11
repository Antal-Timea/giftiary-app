package app.giftiary.presentation.gifts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.domain.repository.GiftPhotoRepository
import app.giftiary.domain.repository.GiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject

/**
 * The gift history: every actual gift, newest first, narrowed by direction and a search, grouped
 * by year and month. Gift ideas are plans, not history, and are never listed here.
 */
@HiltViewModel
class GiftHistoryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    repository: GiftRepository,
    photoRepository: GiftPhotoRepository,
) : ViewModel() {

    private val filter = savedStateHandle.getStateFlow(KEY_FILTER, GiftFilter.ALL)
    private val query = savedStateHandle.getStateFlow(KEY_QUERY, "")

    // The gift shown beside the list on a wide window; saved, so it survives process death.
    private val selection = savedStateHandle.getStateFlow<String?>(KEY_SELECTION, null)

    private val gifts = repository.observeGifts()
        .map<List<GiftEntry>, GiftLoad> { GiftLoad.Loaded(it) }
        .catch { emit(GiftLoad.Failed) }

    // The history is small and filtered in memory, so switching filters and typing stay instant.
    private val content = combine(gifts, filter, query) { load, filter, query -> historyContent(load, filter, query) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    /** Read only for the rows on screen: loading the history, or searching it, reads no photo. */
    val photos = ShownPhotos(viewModelScope, photoRepository::loadPhoto)

    private val list = combine(content, filter, query) { content, filter, query ->
        GiftHistoryUiState(filter = filter, query = query, content = content)
    }
        // A selection the shown list does not hold (deleted, filtered or searched out, unreadable)
        // is dropped rather than kept as a dead id; undoing the filter does not bring it back.
        .onEach { forgetSelectionUnlessShown(it.content) }

    val uiState: StateFlow<GiftHistoryUiState> =
        combine(list, selection) { list, saved ->
            // Only a gift of the shown list is selected: while loading, a restored one waits.
            list.copy(selectedGiftId = saved.toUuidOrNull()?.takeIf { list.content.shows(it) })
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = GiftHistoryUiState(filter = filter.value, query = query.value),
        )

    fun onFilterChange(filter: GiftFilter) {
        savedStateHandle[KEY_FILTER] = filter
    }

    fun onQueryChange(query: String) {
        savedStateHandle[KEY_QUERY] = query
    }

    fun onGiftSelected(entry: GiftEntry) {
        savedStateHandle[KEY_SELECTION] = entry.gift.id.toString()
    }

    fun onSelectionCleared() {
        savedStateHandle[KEY_SELECTION] = null
    }

    private fun forgetSelectionUnlessShown(content: GiftHistoryContent) {
        val saved = selection.value ?: return
        // Loading and failure prove nothing about the selection, so it is kept for later.
        if (content == GiftHistoryContent.Loading || content == GiftHistoryContent.LoadFailed) return
        val id = saved.toUuidOrNull()
        if (id == null || !content.shows(id)) onSelectionCleared()
    }

    private sealed interface GiftLoad {
        data class Loaded(val gifts: List<GiftEntry>) : GiftLoad
        data object Failed : GiftLoad
    }

    private fun historyContent(load: GiftLoad, filter: GiftFilter, query: String): GiftHistoryContent = when (load) {
        GiftLoad.Failed -> GiftHistoryContent.LoadFailed
        is GiftLoad.Loaded -> {
            val search = query.trim()
            val matching = load.gifts.filter { filter.matches(it.gift.direction) && it.matches(search) }
            when {
                load.gifts.isEmpty() -> GiftHistoryContent.NoGifts
                matching.isEmpty() -> GiftHistoryContent.NoMatches(total = load.gifts.size)
                else -> GiftHistoryContent.Loaded(groupByYearAndMonth(matching))
            }
        }
    }

    private companion object {
        const val KEY_FILTER = "filter"
        const val KEY_QUERY = "query"
        const val KEY_SELECTION = "selection"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun String?.toUuidOrNull(): UUID? = this?.let { runCatching { UUID.fromString(it) }.getOrNull() }

/** By what it was, or by the people it connects; an occasion matches by its own title. */
private fun GiftEntry.matches(search: String): Boolean =
    search.isEmpty() ||
        listOfNotNull(gift.name, giver?.name, recipient?.name, occasion?.title).any { it.contains(search, ignoreCase = true) }

/** Newest first: years, the months within them, and the gifts within those, latest added first on a shared day. */
internal fun groupByYearAndMonth(gifts: List<GiftEntry>): List<GiftYear> =
    gifts
        .sortedWith(compareByDescending<GiftEntry> { it.gift.givenAt }.thenByDescending { it.gift.createdAt })
        .groupBy { it.gift.givenAt.year }
        .map { (year, ofYear) ->
            GiftYear(
                year = year,
                months = ofYear.groupBy { YearMonth.from(it.gift.givenAt) }.map { (month, ofMonth) -> GiftMonth(month, ofMonth) },
            )
        }

/** The history's direction filters. There is deliberately no ideas filter: ideas are not history. */
enum class GiftFilter {
    ALL,
    GIVEN,
    RECEIVED,
    ;

    fun matches(direction: GiftDirection): Boolean = when (this) {
        ALL -> true
        GIVEN -> direction == GiftDirection.GIVEN
        RECEIVED -> direction == GiftDirection.RECEIVED
    }
}

data class GiftHistoryUiState(
    val filter: GiftFilter = GiftFilter.ALL,
    val query: String = "",
    val content: GiftHistoryContent = GiftHistoryContent.Loading,
    /** The gift shown beside the list; only ever one the list currently shows. */
    val selectedGiftId: UUID? = null,
)

data class GiftYear(val year: Int, val months: List<GiftMonth>)

data class GiftMonth(val month: YearMonth, val gifts: List<GiftEntry>)

internal fun GiftHistoryContent.shows(giftId: UUID): Boolean =
    this is GiftHistoryContent.Loaded && gifts.any { it.gift.id == giftId }

sealed interface GiftHistoryContent {
    /** Only until Room's first emission. */
    data object Loading : GiftHistoryContent

    /** Room could not be read, so there is no saved history to show either. */
    data object LoadFailed : GiftHistoryContent

    /** No gifts at all, whatever the filter or search. */
    data object NoGifts : GiftHistoryContent

    /** There are [total] gifts, but none match the filter and search. */
    data class NoMatches(val total: Int) : GiftHistoryContent

    data class Loaded(val years: List<GiftYear>) : GiftHistoryContent {
        val gifts: List<GiftEntry> get() = years.flatMap { year -> year.months.flatMap { it.gifts } }
    }
}
