package app.giftiary.presentation.wishlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.DefaultCurrency
import app.giftiary.domain.model.Wish
import app.giftiary.domain.repository.GiftPhotoRepository
import app.giftiary.domain.repository.GiftPhotoUnavailableException
import app.giftiary.domain.repository.WishNotFoundException
import app.giftiary.domain.repository.WishlistNotFoundException
import app.giftiary.domain.repository.WishlistRepository
import app.giftiary.domain.usecase.SaveWishResult
import app.giftiary.domain.usecase.SaveWishUseCase
import app.giftiary.domain.usecase.WishInput
import app.giftiary.presentation.gifts.GiftPhotoError
import app.giftiary.presentation.gifts.LoadedPhoto
import app.giftiary.presentation.gifts.PriceError
import app.giftiary.presentation.gifts.PriceFormat
import app.giftiary.presentation.gifts.toError
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * Backs adding a wish to a wishlist and editing one. A wish stays on its wishlist. A picked or
 * captured photo is stored on this device at once and becomes the wish's only when it is saved.
 */
@HiltViewModel(assistedFactory = WishEditorViewModel.Factory::class)
class WishEditorViewModel @AssistedInject constructor(
    @Assisted private val target: WishEditorTarget,
    private val repository: WishlistRepository,
    private val saveWish: SaveWishUseCase,
    private val photos: GiftPhotoRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        WishEditorUiState(isNew = target is WishEditorTarget.New, status = WishEditorStatus.LOADING),
    )
    val uiState: StateFlow<WishEditorUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, showNameRequired = false) }
    }

    fun onPriceChange(price: String) {
        _uiState.update { it.copy(price = price, priceError = null) }
    }

    fun onProductUrlChange(url: String) {
        _uiState.update { it.copy(productUrl = url) }
    }

    fun onNoteChange(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun onSaveErrorShown() {
        _uiState.update { it.copy(saveFailed = false, photoUnavailableOnSave = false) }
    }

    /** [source]: the content URI the photo picker returned. */
    fun onPhotoPicked(source: String) {
        if (_uiState.value.photoBusy) return
        importPhoto { photos.importPhoto(source) }
    }

    /** Where the camera app is to write a new photo; null if there is nowhere, which is reported. */
    fun newCaptureTarget(): String? = try {
        photos.newCaptureTarget()
    } catch (e: Exception) {
        _uiState.update { it.copy(photoError = GiftPhotoError.CAMERA_UNAVAILABLE) }
        null
    }

    /** The camera app wrote a photo into [target] (from [newCaptureTarget]). */
    fun onPhotoCaptured(target: String) {
        if (_uiState.value.photoBusy) {
            onCaptureCancelled(target)
            return
        }
        importPhoto { photos.importCapture(target) }
    }

    /** The camera app returned without a photo. */
    fun onCaptureCancelled(target: String) {
        viewModelScope.launch { photos.discardCapture(target) }
    }

    /** No camera app could be opened for [target]. */
    fun onCameraUnavailable(target: String) {
        onCaptureCancelled(target)
        _uiState.update { it.copy(photoError = GiftPhotoError.CAMERA_UNAVAILABLE) }
    }

    private fun importPhoto(import: suspend () -> String) {
        _uiState.update { it.copy(photoBusy = true, photoError = null) }
        viewModelScope.launch {
            try {
                val path = import()
                val bytes = photos.loadPhoto(path)
                _uiState.update { it.copy(photoBusy = false, photoPath = path, photo = bytes?.let(::LoadedPhoto)) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(photoBusy = false, photoError = GiftPhotoError.UNREADABLE) }
            }
        }
    }

    fun onPhotoRemoved() {
        _uiState.update { it.copy(photoPath = null, photo = null, photoError = null) }
    }

    fun onPhotoErrorShown() {
        _uiState.update { it.copy(photoError = null) }
    }

    fun onDeleteRequested() {
        _uiState.update { if (target is WishEditorTarget.Existing && it.status == WishEditorStatus.EDITING) it.copy(confirmingDelete = true) else it }
    }

    fun onDeleteDismissed() {
        _uiState.update { it.copy(confirmingDelete = false) }
    }

    fun onDeleteErrorShown() {
        _uiState.update { it.copy(deleteFailed = false) }
    }

    fun onDeleteConfirmed() {
        val wishId = (target as? WishEditorTarget.Existing)?.wishId ?: return
        if (_uiState.value.status != WishEditorStatus.EDITING || _uiState.value.isBusy) return

        _uiState.update { it.copy(confirmingDelete = false, isDeleting = true, deleteFailed = false) }
        viewModelScope.launch {
            try {
                repository.deleteWish(wishId)
                _uiState.update { it.copy(status = WishEditorStatus.DELETED) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(isDeleting = false, deleteFailed = true) }
            }
        }
    }

    fun save() {
        val state = _uiState.value
        // A photo still being read would be left out; the save waits for it.
        if (state.status != WishEditorStatus.EDITING || state.isBusy || state.photoBusy) return

        val price = PriceFormat.parse(state.price, state.currencyCode)
        val priceError = price.toError()
        val nameRequired = state.name.isBlank()
        if (priceError != null || nameRequired) {
            _uiState.update { it.copy(priceError = priceError, showNameRequired = nameRequired) }
            return
        }
        val input = WishInput(
            name = state.name,
            note = state.note,
            estimatedPrice = (price as PriceFormat.ParseResult.Valid).money,
            productUrl = state.productUrl,
            photoPath = state.photoPath,
        )

        _uiState.update { it.copy(isSaving = true, saveFailed = false, photoUnavailableOnSave = false) }
        viewModelScope.launch {
            try {
                val result = when (target) {
                    is WishEditorTarget.New -> saveWish.add(target.wishlistId, input)
                    is WishEditorTarget.Existing -> saveWish.update(target.wishId, input)
                }
                _uiState.update {
                    when (result) {
                        SaveWishResult.SAVED -> it.copy(status = WishEditorStatus.SAVED)
                        SaveWishResult.BLANK_NAME -> it.copy(isSaving = false, showNameRequired = true)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: WishlistNotFoundException) {
                _uiState.update { it.copy(isSaving = false, status = WishEditorStatus.WISHLIST_NOT_FOUND) }
            } catch (_: WishNotFoundException) {
                _uiState.update { it.copy(isSaving = false, status = WishEditorStatus.NOT_FOUND) }
            } catch (_: GiftPhotoUnavailableException) {
                _uiState.update { it.copy(isSaving = false, photoPath = null, photo = null, photoUnavailableOnSave = true) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            try {
                _uiState.update { state ->
                    when (target) {
                        is WishEditorTarget.New -> when (val wishlist = repository.getWishlist(target.wishlistId)) {
                            null -> state.copy(status = WishEditorStatus.WISHLIST_NOT_FOUND)
                            else -> state.copy(status = WishEditorStatus.EDITING, wishlistName = wishlist.name)
                        }
                        is WishEditorTarget.Existing -> when (val wish = repository.getWish(target.wishId)) {
                            null -> state.copy(status = WishEditorStatus.NOT_FOUND)
                            else -> state.loaded(wish, repository.getWishlist(wish.wishlistId)?.name)
                        }
                    }
                }
                _uiState.value.photoPath?.let { path ->
                    val bytes = photos.loadPhoto(path)
                    // Only if the user has not picked or removed a photo meanwhile.
                    _uiState.update { if (it.photoPath == path) it.copy(photo = bytes?.let(::LoadedPhoto)) else it }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(status = WishEditorStatus.LOAD_FAILED) }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(target: WishEditorTarget): WishEditorViewModel
    }

    private companion object {

        fun WishEditorUiState.loaded(wish: Wish, wishlistName: String?) = copy(
            status = WishEditorStatus.EDITING,
            wishlistName = wishlistName,
            name = wish.name,
            savedName = wish.name,
            price = wish.estimatedPrice?.let { PriceFormat.formatForInput(it, Locale.getDefault()) }.orEmpty(),
            currencyCode = wish.estimatedPrice?.currencyCode ?: DefaultCurrency.CODE,
            productUrl = wish.productUrl.orEmpty(),
            note = wish.note.orEmpty(),
            photoPath = wish.photoPath,
        )
    }
}

sealed interface WishEditorTarget {
    data class New(val wishlistId: UUID) : WishEditorTarget
    data class Existing(val wishId: UUID) : WishEditorTarget
}

data class WishEditorUiState(
    val isNew: Boolean,
    val status: WishEditorStatus,
    val name: String = "",
    val price: String = "",
    /** The currency of the price field: the wish's own, or the app's default for a new price. */
    val currencyCode: String = DefaultCurrency.CODE,
    val productUrl: String = "",
    val note: String = "",
    val showNameRequired: Boolean = false,
    val priceError: PriceError? = null,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    /** The list the wish is on, which it cannot leave; null until known. */
    val wishlistName: String? = null,
    /** The name as stored, which the delete confirmation names even while [name] is being edited. */
    val savedName: String = "",
    val confirmingDelete: Boolean = false,
    val isDeleting: Boolean = false,
    val deleteFailed: Boolean = false,
    /** The photo the wish will be saved with; null for none. */
    val photoPath: String? = null,
    /** [photoPath]'s image once read; null while reading or if it is not available now. */
    val photo: LoadedPhoto? = null,
    /** A picked or captured photo is being stored. */
    val photoBusy: Boolean = false,
    val photoError: GiftPhotoError? = null,
    /** The save was refused because the picked photo is no longer on this device. */
    val photoUnavailableOnSave: Boolean = false,
) {
    /** A save or delete is in progress; further ones are ignored. */
    val isBusy: Boolean get() = isSaving || isDeleting
}

enum class WishEditorStatus {
    LOADING,
    EDITING,
    NOT_FOUND,

    /** The wishlist the wish is for, or was on, has been deleted. */
    WISHLIST_NOT_FOUND,
    LOAD_FAILED,

    /** Stored locally; the screen should close. */
    SAVED,

    /** The wish was deleted; the screen should close, along with the wish's page. */
    DELETED,
}
