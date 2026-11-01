package app.giftiary.presentation.gifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.domain.repository.GiftPhotoRepository
import app.giftiary.domain.repository.GiftRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

@HiltViewModel(assistedFactory = GiftDetailViewModel.Factory::class)
class GiftDetailViewModel @AssistedInject constructor(
    @Assisted private val giftId: UUID,
    private val repository: GiftRepository,
    private val photos: GiftPhotoRepository,
) : ViewModel() {

    private val actions = MutableStateFlow(GiftDetailActions())

    private val gift = repository.observeGift(giftId)
        .map<GiftEntry?, GiftDetailContent> { entry ->
            if (entry == null) GiftDetailContent.NotFound else GiftDetailContent.Loaded(entry)
        }
        .catch { emit(GiftDetailContent.LoadFailed) }

    // Read once per photo: from this device, or downloaded the first time it is shown here.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val photo = repository.observeGift(giftId)
        .map { it?.gift?.photoPath }
        .distinctUntilChanged()
        .flatMapLatest { path ->
            if (path == null) {
                flowOf(null)
            } else {
                val loaded = flow {
                    emit(GiftDetailPhoto(photo = null, loading = true))
                    emit(GiftDetailPhoto(photo = photos.loadPhoto(path)?.let(::LoadedPhoto), loading = false))
                }
                combine(loaded, photos.observeUploadPending(path)) { photo, pending -> photo.copy(uploadPending = pending) }
            }
        }
        .catch { emit(null) }

    val uiState: StateFlow<GiftDetailUiState> =
        combine(gift, actions, photo) { content, actions, photo ->
            // Once deleted, the gift is gone from Room too; keep reporting the deletion, not "not found".
            GiftDetailUiState(content = if (actions.deleted) GiftDetailContent.Deleted else content, actions = actions, photo = photo)
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
    /** Null for a gift without a photo. */
    val photo: GiftDetailPhoto? = null,
)

data class GiftDetailPhoto(
    val photo: LoadedPhoto?,
    val loading: Boolean,
    val uploadPending: Boolean = false,
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
