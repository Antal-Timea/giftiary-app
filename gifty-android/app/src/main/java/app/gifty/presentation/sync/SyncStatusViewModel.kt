package app.gifty.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gifty.domain.model.AttentionItem
import app.gifty.domain.model.SyncStatus
import app.gifty.domain.repository.SyncStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The local sync status for the Home row and the attention screen. Reading it never syncs. */
@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    private val repository: SyncStatusRepository,
) : ViewModel() {

    val content: StateFlow<SyncStatusContent> = repository.observeSyncStatus()
        .map<SyncStatus, SyncStatusContent> { SyncStatusContent.Loaded(it) }
        .catch { emit(SyncStatusContent.Unavailable) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SyncStatusContent.Loading,
        )

    fun onDismiss(item: AttentionItem) {
        if (!item.canDismiss) return
        viewModelScope.launch {
            try {
                repository.dismissConflict(item.id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The conflict stays listed, so dismissing it can simply be tried again.
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

sealed interface SyncStatusContent {
    /** The local status has not been read yet; this says nothing about the network. */
    data object Loading : SyncStatusContent

    data object Unavailable : SyncStatusContent

    data class Loaded(val status: SyncStatus) : SyncStatusContent
}
