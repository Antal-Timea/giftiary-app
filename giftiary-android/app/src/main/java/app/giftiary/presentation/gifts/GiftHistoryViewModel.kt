package app.giftiary.presentation.gifts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.domain.repository.GiftIdeaEntry
import app.giftiary.domain.repository.GiftIdeaRepository
import app.giftiary.domain.repository.GiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class GiftHistoryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    repository: GiftRepository,
    giftIdeaRepository: GiftIdeaRepository,
) : ViewModel() {

    private val filter = savedStateHandle.getStateFlow(KEY_FILTER, GiftFilter.ALL)

    private val gifts = repository.observeGifts()
        .map<List<GiftEntry>, Load<GiftEntry>> { Load.Loaded(it) }
        .catch { emit(Load.Failed) }

    private val ideas = giftIdeaRepository.observeGiftIdeas()
        .map<List<GiftIdeaEntry>, Load<GiftIdeaEntry>> { Load.Loaded(it) }
        .catch { emit(Load.Failed) }

    // Both collections are small and filtered in memory, so switching filters stays instant.
    // Gifts and ideas stay separate: "All" means every actual gift, never ideas.
    val uiState: StateFlow<GiftHistoryUiState> =
        combine(gifts, ideas, filter) { gifts, ideas, filter ->
            GiftHistoryUiState(
                filter = filter,
                content = if (filter == GiftFilter.IDEAS) ideasContent(ideas) else giftsContent(gifts, filter),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = GiftHistoryUiState(filter = filter.value, content = GiftHistoryContent.Loading),
        )

    fun onFilterChange(filter: GiftFilter) {
        savedStateHandle[KEY_FILTER] = filter
    }

    private fun giftsContent(load: Load<GiftEntry>, filter: GiftFilter): GiftHistoryContent = when (load) {
        Load.Failed -> GiftHistoryContent.LoadFailed
        is Load.Loaded -> {
            val matching = load.items.filter { filter.matches(it.gift.direction) }
            when {
                load.items.isEmpty() -> GiftHistoryContent.NoGifts
                matching.isEmpty() -> GiftHistoryContent.NoMatches
                else -> GiftHistoryContent.Loaded(matching)
            }
        }
    }

    private fun ideasContent(load: Load<GiftIdeaEntry>): GiftHistoryContent = when (load) {
        Load.Failed -> GiftHistoryContent.LoadFailed
        is Load.Loaded -> if (load.items.isEmpty()) GiftHistoryContent.NoIdeas else GiftHistoryContent.IdeasLoaded(load.items)
    }

    private sealed interface Load<out T> {
        data class Loaded<T>(val items: List<T>) : Load<T>
        data object Failed : Load<Nothing>
    }

    private companion object {
        const val KEY_FILTER = "filter"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Filters of the gift area. The first three narrow actual gifts; [IDEAS] shows plans instead. */
enum class GiftFilter {
    ALL,
    GIVEN,
    RECEIVED,
    IDEAS,
    ;

    fun matches(direction: GiftDirection): Boolean = when (this) {
        ALL -> true
        GIVEN -> direction == GiftDirection.GIVEN
        RECEIVED -> direction == GiftDirection.RECEIVED
        IDEAS -> false
    }
}

data class GiftHistoryUiState(
    val filter: GiftFilter,
    val content: GiftHistoryContent,
)

sealed interface GiftHistoryContent {
    /** Only until Room's first emission. */
    data object Loading : GiftHistoryContent
    data object LoadFailed : GiftHistoryContent

    /** No gifts at all, regardless of the filter. */
    data object NoGifts : GiftHistoryContent

    /** There are gifts, but none match the selected filter. */
    data object NoMatches : GiftHistoryContent
    data class Loaded(val gifts: List<GiftEntry>) : GiftHistoryContent

    /** The ideas filter is selected and there are no active ideas. */
    data object NoIdeas : GiftHistoryContent
    data class IdeasLoaded(val ideas: List<GiftIdeaEntry>) : GiftHistoryContent
}
