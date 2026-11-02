package app.giftiary.presentation.wishlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.DefaultCurrency
import app.giftiary.domain.model.Wish
import app.giftiary.domain.repository.WishNotFoundException
import app.giftiary.domain.repository.WishlistNotFoundException
import app.giftiary.domain.repository.WishlistRepository
import app.giftiary.domain.usecase.SaveWishResult
import app.giftiary.domain.usecase.SaveWishUseCase
import app.giftiary.domain.usecase.WishInput
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
 * Backs adding a wish to a wishlist and editing one. A wish stays on its wishlist; its photo, if
 * it has one, is kept as it is, since photos cannot be edited yet.
 */
@HiltViewModel(assistedFactory = WishEditorViewModel.Factory::class)
class WishEditorViewModel @AssistedInject constructor(
    @Assisted private val target: WishEditorTarget,
    private val repository: WishlistRepository,
    private val saveWish: SaveWishUseCase,
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
        _uiState.update { it.copy(saveFailed = false) }
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
        if (state.status != WishEditorStatus.EDITING || state.isBusy) return

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
        )

        _uiState.update { it.copy(isSaving = true, saveFailed = false) }
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
