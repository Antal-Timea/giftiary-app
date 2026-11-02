package app.giftiary.presentation.giftideas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.repository.GiftIdeaEntry
import app.giftiary.domain.repository.GiftIdeaRepository
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

@HiltViewModel(assistedFactory = GiftIdeaDetailViewModel.Factory::class)
class GiftIdeaDetailViewModel @AssistedInject constructor(
    @Assisted private val giftIdeaId: UUID,
    private val repository: GiftIdeaRepository,
) : ViewModel() {

    private val actions = MutableStateFlow(GiftIdeaDetailActions())

    private val idea = repository.observeGiftIdea(giftIdeaId)
        .map<GiftIdeaEntry?, GiftIdeaDetailContent> { entry ->
            if (entry == null) GiftIdeaDetailContent.NotFound else GiftIdeaDetailContent.Loaded(entry)
        }
        .catch { emit(GiftIdeaDetailContent.LoadFailed) }

    private val waitingToSync = repository.observeWaitingToSync(giftIdeaId).catch { emit(false) }

    val uiState: StateFlow<GiftIdeaDetailUiState> =
        combine(idea, actions, waitingToSync) { content, actions, waiting ->
            // Once deleted, the idea is gone from Room too; keep reporting the deletion, not "not found".
            GiftIdeaDetailUiState(
                content = if (actions.deleted) GiftIdeaDetailContent.Deleted else content,
                actions = actions,
                waitingToSync = waiting,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = GiftIdeaDetailUiState(),
        )

    /** A plain status change needs no confirmation; it is easy to change back. */
    fun onStatusChange(status: GiftIdeaStatus) {
        runWrite { repository.updateStatus(giftIdeaId, status) }
    }

    fun onDeleteRequested() {
        actions.update { it.copy(confirmingDelete = true) }
    }

    fun onDeleteDismissed() {
        actions.update { it.copy(confirmingDelete = false) }
    }

    fun onDeleteConfirmed() {
        actions.update { it.copy(confirmingDelete = false) }
        runWrite {
            repository.deleteGiftIdea(giftIdeaId)
            actions.update { it.copy(deleted = true) }
        }
    }

    fun onWriteErrorShown() {
        actions.update { it.copy(writeFailed = false) }
    }

    private fun runWrite(write: suspend () -> Unit) {
        if (actions.value.isBusy) return
        actions.update { it.copy(isBusy = true, writeFailed = false) }
        viewModelScope.launch {
            try {
                write()
                actions.update { it.copy(isBusy = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                actions.update { it.copy(isBusy = false, writeFailed = true) }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(giftIdeaId: UUID): GiftIdeaDetailViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

data class GiftIdeaDetailUiState(
    val content: GiftIdeaDetailContent = GiftIdeaDetailContent.Loading,
    val actions: GiftIdeaDetailActions = GiftIdeaDetailActions(),
    /** Saved on this device, not yet in the account; never inferred from the absence of an error. */
    val waitingToSync: Boolean = false,
)

data class GiftIdeaDetailActions(
    val confirmingDelete: Boolean = false,
    /** A status change or deletion is being stored. */
    val isBusy: Boolean = false,
    val writeFailed: Boolean = false,
    val deleted: Boolean = false,
)

sealed interface GiftIdeaDetailContent {
    /** Only until Room's first emission. */
    data object Loading : GiftIdeaDetailContent

    /** Missing, deleted elsewhere, or already given. */
    data object NotFound : GiftIdeaDetailContent
    data object LoadFailed : GiftIdeaDetailContent

    /** Deleted from this screen; it should close. */
    data object Deleted : GiftIdeaDetailContent
    data class Loaded(val entry: GiftIdeaEntry) : GiftIdeaDetailContent
}
