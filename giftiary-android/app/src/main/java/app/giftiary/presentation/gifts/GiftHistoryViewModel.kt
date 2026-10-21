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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GiftHistoryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    repository: GiftRepository,
    giftIdeaRepository: GiftIdeaRepository,
) : ViewModel() {

    private val filter = savedStateHandle.getStateFlow(KEY_FILTER, GiftFilter.ALL)

    // The gift or idea shown beside the list on a wide window; saved, so it survives process death.
    private val selection = savedStateHandle.getStateFlow<String?>(KEY_SELECTION, null)

    private val gifts = repository.observeGifts()
        .map<List<GiftEntry>, Load<GiftEntry>> { Load.Loaded(it) }
        .catch { emit(Load.Failed) }

    private val ideas = giftIdeaRepository.observeGiftIdeas()
        .map<List<GiftIdeaEntry>, Load<GiftIdeaEntry>> { Load.Loaded(it) }
        .catch { emit(Load.Failed) }

    // Both collections are small and filtered in memory, so switching filters stays instant.
    // Gifts and ideas stay separate: "All" means every actual gift, never ideas.
    private val list = combine(gifts, ideas, filter) { gifts, ideas, filter ->
        GiftHistoryUiState(
            filter = filter,
            content = if (filter == GiftFilter.IDEAS) ideasContent(ideas) else giftsContent(gifts, filter),
        )
    }
        // A selection the shown list does not hold (deleted, archived, filtered out, unreadable) is
        // dropped rather than kept as a dead id; switching the filter back does not bring it back.
        .onEach { forgetSelectionUnlessShown(it.content) }

    val uiState: StateFlow<GiftHistoryUiState> =
        combine(list, selection) { list, saved ->
            // Only an item of the shown list is selected: while loading, a restored one waits.
            list.copy(selection = GiftHistorySelection.fromSaved(saved)?.takeIf { list.content.shows(it) })
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = GiftHistoryUiState(filter = filter.value, content = GiftHistoryContent.Loading),
        )

    fun onFilterChange(filter: GiftFilter) {
        savedStateHandle[KEY_FILTER] = filter
    }

    fun onGiftSelected(entry: GiftEntry) {
        savedStateHandle[KEY_SELECTION] = GiftHistorySelection.Gift(entry.gift.id).toSaved()
    }

    fun onIdeaSelected(entry: GiftIdeaEntry) {
        savedStateHandle[KEY_SELECTION] = GiftHistorySelection.Idea(entry.idea.id).toSaved()
    }

    fun onSelectionCleared() {
        savedStateHandle[KEY_SELECTION] = null
    }

    private fun forgetSelectionUnlessShown(content: GiftHistoryContent) {
        val saved = selection.value ?: return
        // Loading and failure prove nothing about the selection, so it is kept for later.
        if (content == GiftHistoryContent.Loading || content == GiftHistoryContent.LoadFailed) return
        val selected = GiftHistorySelection.fromSaved(saved)
        if (selected == null || !content.shows(selected)) onSelectionCleared()
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
        const val KEY_SELECTION = "selection"
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
    /** The gift or idea shown beside the list; only ever one the list currently shows. */
    val selection: GiftHistorySelection? = null,
)

/** A gift or an idea of the history, by id. */
sealed interface GiftHistorySelection {
    val id: UUID

    data class Gift(override val id: UUID) : GiftHistorySelection
    data class Idea(override val id: UUID) : GiftHistorySelection

    /** One saved value, so a restored selection can never pair one kind with another's id. */
    fun toSaved(): String = when (this) {
        is Gift -> "$GIFT_PREFIX$id"
        is Idea -> "$IDEA_PREFIX$id"
    }

    companion object {
        private const val GIFT_PREFIX = "gift:"
        private const val IDEA_PREFIX = "idea:"

        /** Null for anything unreadable, which the history then drops. */
        fun fromSaved(value: String?): GiftHistorySelection? {
            if (value == null) return null
            val id = runCatching { UUID.fromString(value.substringAfter(':')) }.getOrNull() ?: return null
            return when {
                value.startsWith(GIFT_PREFIX) -> Gift(id)
                value.startsWith(IDEA_PREFIX) -> Idea(id)
                else -> null
            }
        }
    }
}

/** Whether this content lists the selected item; a gift is never in the ideas list, nor the reverse. */
internal fun GiftHistoryContent.shows(selection: GiftHistorySelection): Boolean = when (selection) {
    is GiftHistorySelection.Gift -> this is GiftHistoryContent.Loaded && gifts.any { it.gift.id == selection.id }
    is GiftHistorySelection.Idea -> this is GiftHistoryContent.IdeasLoaded && ideas.any { it.idea.id == selection.id }
}

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
