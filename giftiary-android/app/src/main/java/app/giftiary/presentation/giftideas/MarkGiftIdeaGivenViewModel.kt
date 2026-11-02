package app.giftiary.presentation.giftideas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.DefaultCurrency
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.repository.GiftIdeaAlreadyGivenException
import app.giftiary.domain.repository.GiftIdeaNotFoundException
import app.giftiary.domain.repository.GiftIdeaRepository
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
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

/**
 * Confirms the two things a real gift needs that an idea does not have for sure: when it was
 * given and what it finally cost. Everything else is taken from the idea.
 */
@HiltViewModel(assistedFactory = MarkGiftIdeaGivenViewModel.Factory::class)
class MarkGiftIdeaGivenViewModel @AssistedInject constructor(
    @Assisted private val giftIdeaId: UUID,
    private val repository: GiftIdeaRepository,
    clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MarkGivenUiState(givenAt = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()),
    )
    val uiState: StateFlow<MarkGivenUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val entry = repository.getGiftIdea(giftIdeaId)
                _uiState.update { state ->
                    if (entry == null) {
                        state.copy(status = MarkGivenStatus.NOT_AVAILABLE)
                    } else {
                        // What was actually paid is the best guess for the final price, then the estimate.
                        val price = entry.idea.actualPrice ?: entry.idea.estimatedPrice
                        state.copy(
                            status = MarkGivenStatus.READY,
                            ideaName = entry.idea.name,
                            recipientName = entry.recipient?.name,
                            occasion = entry.occasion,
                            estimate = entry.idea.estimatedPrice,
                            price = price?.let { PriceFormat.formatForInput(it, Locale.getDefault()) }.orEmpty(),
                            currencyCode = price?.currencyCode ?: DefaultCurrency.CODE,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(status = MarkGivenStatus.LOAD_FAILED) }
            }
        }
    }

    fun onDateChange(date: LocalDate) {
        _uiState.update { it.copy(givenAt = date) }
    }

    fun onPriceChange(price: String) {
        _uiState.update { it.copy(price = price, priceError = null) }
    }

    fun confirm() {
        val state = _uiState.value
        if (state.status != MarkGivenStatus.READY || state.isBusy) return

        val price = when (val result = PriceFormat.parse(state.price, state.currencyCode)) {
            is PriceFormat.ParseResult.Valid -> result.money
            PriceFormat.ParseResult.Negative -> return _uiState.update { it.copy(priceError = PriceError.NEGATIVE) }
            PriceFormat.ParseResult.TooPrecise -> return _uiState.update { it.copy(priceError = PriceError.TOO_PRECISE) }
            PriceFormat.ParseResult.Invalid -> return _uiState.update { it.copy(priceError = PriceError.INVALID) }
        }

        _uiState.update { it.copy(isBusy = true, writeFailed = false) }
        viewModelScope.launch {
            try {
                val gift = repository.markAsGiven(giftIdeaId, state.givenAt, price)
                _uiState.update { it.copy(isBusy = false, status = MarkGivenStatus.DONE, giftId = gift.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: GiftIdeaAlreadyGivenException) {
                // Final: the idea is already a gift, so there is nothing left to confirm or retry.
                _uiState.update { it.copy(isBusy = false, status = MarkGivenStatus.ALREADY_GIVEN) }
            } catch (_: GiftIdeaNotFoundException) {
                _uiState.update { it.copy(isBusy = false, status = MarkGivenStatus.NOT_AVAILABLE) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isBusy = false, writeFailed = true) }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(giftIdeaId: UUID): MarkGiftIdeaGivenViewModel
    }
}

data class MarkGivenUiState(
    val givenAt: LocalDate,
    val status: MarkGivenStatus = MarkGivenStatus.LOADING,
    val ideaName: String = "",
    val recipientName: String? = null,
    val occasion: Occasion? = null,
    /** Shown beside the final price for reference; the price field starts from what was paid, if known. */
    val estimate: Money? = null,
    val price: String = "",
    val currencyCode: String = DefaultCurrency.CODE,
    val priceError: PriceError? = null,
    /** The conversion is being stored; further confirmations are ignored. */
    val isBusy: Boolean = false,
    /** The last confirmation could not be stored; the input stays for another try. */
    val writeFailed: Boolean = false,
    /** The gift created from the idea, once [status] is [MarkGivenStatus.DONE]. */
    val giftId: UUID? = null,
)

enum class MarkGivenStatus {
    LOADING,
    READY,

    /** The idea is missing, deleted or already given. */
    NOT_AVAILABLE,
    LOAD_FAILED,

    /** Given meanwhile, e.g. on another screen: the idea can no longer be confirmed. */
    ALREADY_GIVEN,

    /** The gift was recorded; the screen should move on to it. */
    DONE,
}
