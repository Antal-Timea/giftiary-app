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

    fun save() {
        val state = _uiState.value
        if (state.status != WishEditorStatus.EDITING || state.isSaving) return

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
                        is WishEditorTarget.New -> if (repository.getWishlist(target.wishlistId) == null) {
                            state.copy(status = WishEditorStatus.WISHLIST_NOT_FOUND)
                        } else {
                            state.copy(status = WishEditorStatus.EDITING)
                        }
                        is WishEditorTarget.Existing ->
                            repository.getWish(target.wishId)?.let { state.loaded(it) } ?: state.copy(status = WishEditorStatus.NOT_FOUND)
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

        fun WishEditorUiState.loaded(wish: Wish) = copy(
            status = WishEditorStatus.EDITING,
            name = wish.name,
            price = wish.estimatedPrice?.let { PriceFormat.formatForInput(it, Locale.getDefault()) }.orEmpty(),
            currencyCode = wish.estimatedPrice?.currencyCode ?: DefaultCurrency.CODE,
            productUrl = wish.productUrl.orEmpty(),
            note = wish.note.orEmpty(),
        )

        fun PriceFormat.ParseResult.toError(): PriceError? = when (this) {
            is PriceFormat.ParseResult.Valid -> null
            PriceFormat.ParseResult.Negative -> PriceError.NEGATIVE
            PriceFormat.ParseResult.TooPrecise -> PriceError.TOO_PRECISE
            PriceFormat.ParseResult.Invalid -> PriceError.INVALID
        }
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
)

enum class WishEditorStatus {
    LOADING,
    EDITING,
    NOT_FOUND,

    /** The wishlist the wish is for, or was on, has been deleted. */
    WISHLIST_NOT_FOUND,
    LOAD_FAILED,

    /** Stored locally; the screen should close. */
    SAVED,
}
