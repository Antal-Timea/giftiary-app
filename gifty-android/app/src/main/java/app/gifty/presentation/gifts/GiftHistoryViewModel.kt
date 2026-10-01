package app.gifty.presentation.gifts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gifty.domain.model.GiftDirection
import app.gifty.domain.repository.GiftEntry
import app.gifty.domain.repository.GiftRepository
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
) : ViewModel() {

    private val filter = savedStateHandle.getStateFlow(KEY_FILTER, GiftFilter.ALL)

    private val gifts = repository.observeGifts()
        .map<List<GiftEntry>, GiftsLoad> { GiftsLoad.Loaded(it) }
        .catch { emit(GiftsLoad.Failed) }

    // The whole history is filtered in memory: it is small, and switching filters stays instant.
    val uiState: StateFlow<GiftHistoryUiState> =
        combine(gifts, filter) { load, filter ->
            GiftHistoryUiState(
                filter = filter,
                content = when (load) {
                    GiftsLoad.Failed -> GiftHistoryContent.LoadFailed
                    is GiftsLoad.Loaded -> {
                        val matching = load.gifts.filter { filter.matches(it.gift.direction) }
                        when {
                            load.gifts.isEmpty() -> GiftHistoryContent.NoGifts
                            matching.isEmpty() -> GiftHistoryContent.NoMatches
                            else -> GiftHistoryContent.Loaded(matching)
                        }
                    }
                },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = GiftHistoryUiState(filter = filter.value, content = GiftHistoryContent.Loading),
        )

    fun onFilterChange(filter: GiftFilter) {
        savedStateHandle[KEY_FILTER] = filter
    }

    private sealed interface GiftsLoad {
        data class Loaded(val gifts: List<GiftEntry>) : GiftsLoad
        data object Failed : GiftsLoad
    }

    private companion object {
        const val KEY_FILTER = "filter"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** History filters. Gift ideas will join these once they exist. */
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
}
