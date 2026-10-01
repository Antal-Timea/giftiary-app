package app.gifty.presentation.gifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gifty.domain.repository.GiftEntry
import app.gifty.domain.repository.GiftRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

@HiltViewModel(assistedFactory = GiftDetailViewModel.Factory::class)
class GiftDetailViewModel @AssistedInject constructor(
    @Assisted private val giftId: UUID,
    private val repository: GiftRepository,
) : ViewModel() {

    private val actions = MutableStateFlow(GiftDetailActions())

    private val gift = repository.observeGift(giftId)
        .map<GiftEntry?, GiftDetailContent> { entry ->
            if (entry == null) GiftDetailContent.NotFound else GiftDetailContent.Loaded(entry)
        }
        .catch { emit(GiftDetailContent.LoadFailed) }

    val uiState: StateFlow<GiftDetailUiState> =
        combine(gift, actions) { content, actions ->
            // Once deleted, the gift is gone from Room too; keep reporting the deletion, not "not found".
            GiftDetailUiState(content = if (actions.deleted) GiftDetailContent.Deleted else content, actions = actions)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = GiftDetailUiState(),
        )

    fun onDeleteRequested() {
        actions.update { it.copy(confirmingDelete = true) }
    }

    fun onDeleteDismissed() {
        actions.update { it.copy(confirmingDelete = false) }
    }

    fun onDeleteConfirmed() {
        if (actions.value.isDeleting) return
        actions.update { it.copy(confirmingDelete = false, isDeleting = true, deleteFailed = false) }
        viewModelScope.launch {
            try {
                repository.deleteGift(giftId)
                actions.update { it.copy(isDeleting = false, deleted = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                actions.update { it.copy(isDeleting = false, deleteFailed = true) }
            }
        }
    }

    fun onDeleteErrorShown() {
        actions.update { it.copy(deleteFailed = false) }
    }

    @AssistedFactory
    interface Factory {
        fun create(giftId: UUID): GiftDetailViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

data class GiftDetailUiState(
    val content: GiftDetailContent = GiftDetailContent.Loading,
    val actions: GiftDetailActions = GiftDetailActions(),
)

data class GiftDetailActions(
    val confirmingDelete: Boolean = false,
    val isDeleting: Boolean = false,
    val deleteFailed: Boolean = false,
    val deleted: Boolean = false,
)

sealed interface GiftDetailContent {
    /** Only until Room's first emission. */
    data object Loading : GiftDetailContent
    data object NotFound : GiftDetailContent
    data object LoadFailed : GiftDetailContent

    /** Deleted from this screen; it should close. */
    data object Deleted : GiftDetailContent
    data class Loaded(val entry: GiftEntry) : GiftDetailContent
}
