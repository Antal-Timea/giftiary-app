package app.gifty.presentation.giftideas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gifty.domain.model.DefaultCurrency
import app.gifty.domain.model.GiftIdeaStatus
import app.gifty.domain.model.Money
import app.gifty.domain.model.Occasion
import app.gifty.domain.model.Person
import app.gifty.domain.repository.GiftIdeaEntry
import app.gifty.domain.repository.GiftIdeaNotFoundException
import app.gifty.domain.repository.GiftIdeaRepository
import app.gifty.domain.repository.OccasionNotFoundException
import app.gifty.domain.repository.OccasionRepository
import app.gifty.domain.repository.PersonNotFoundException
import app.gifty.domain.repository.PersonRepository
import app.gifty.domain.usecase.GiftIdeaInput
import app.gifty.domain.usecase.GiftIdeaInputError
import app.gifty.domain.usecase.SaveGiftIdeaResult
import app.gifty.domain.usecase.SaveGiftIdeaUseCase
import app.gifty.presentation.gifts.PriceError
import app.gifty.presentation.gifts.PriceFormat
import app.gifty.presentation.gifts.fitsPerson
import app.gifty.presentation.gifts.generalOccasions
import app.gifty.presentation.gifts.occasionChoices
import app.gifty.presentation.gifts.personChoices
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * Backs adding and editing a gift idea. Existing prices are pre-filled with the app's current
 * default locale.
 */
@HiltViewModel(assistedFactory = GiftIdeaEditorViewModel.Factory::class)
class GiftIdeaEditorViewModel @AssistedInject constructor(
    @Assisted private val target: GiftIdeaEditorTarget,
    personRepository: PersonRepository,
    private val occasionRepository: OccasionRepository,
    private val giftIdeaRepository: GiftIdeaRepository,
    private val saveGiftIdea: SaveGiftIdeaUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        when (target) {
            is GiftIdeaEditorTarget.New ->
                GiftIdeaEditorUiState(isNew = true, status = GiftIdeaEditorStatus.EDITING, recipientId = target.personId)
            is GiftIdeaEditorTarget.Existing ->
                GiftIdeaEditorUiState(isNew = false, status = GiftIdeaEditorStatus.LOADING)
        },
    )
    val uiState: StateFlow<GiftIdeaEditorUiState> = _uiState.asStateFlow()

    init {
        if (target is GiftIdeaEditorTarget.Existing) load(target.giftIdeaId)
        viewModelScope.launch {
            personRepository.observePeople()
                .catch { _uiState.update { it.copy(status = GiftIdeaEditorStatus.LOAD_FAILED) } }
                .collect { people -> _uiState.update { it.withActivePeople(people) } }
        }
        observeOccasionsOfRecipient()
        observeGeneralOccasions()
    }

    fun onRecipientChange(person: Person) {
        _uiState.update {
            val keepsOccasion = it.selectedOccasion?.fitsPerson(person.id) == true
            it.copy(
                recipientId = person.id,
                occasionId = if (keepsOccasion) it.occasionId else null,
                errors = it.errors - GiftIdeaInputError.RECIPIENT_REQUIRED,
                writeError = null,
            )
        }
    }

    fun onOccasionChange(occasion: Occasion?) {
        _uiState.update { it.copy(occasionId = occasion?.id, writeError = null) }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, errors = it.errors - GiftIdeaInputError.NAME_REQUIRED) }
    }

    fun onIdeaStatusChange(status: GiftIdeaStatus) {
        _uiState.update { it.copy(ideaStatus = status) }
    }

    fun onEstimatedPriceChange(price: String) {
        _uiState.update { it.copy(estimatedPrice = price, estimatedPriceError = null) }
    }

    fun onActualPriceChange(price: String) {
        _uiState.update { it.copy(actualPrice = price, actualPriceError = null) }
    }

    fun onProductUrlChange(url: String) {
        _uiState.update { it.copy(productUrl = url) }
    }

    fun onNoteChange(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun onWriteErrorShown() {
        _uiState.update { it.copy(writeError = null) }
    }

    fun save() {
        val state = _uiState.value
        if (state.status != GiftIdeaEditorStatus.EDITING || state.isBusy) return

        val estimated = PriceFormat.parse(state.estimatedPrice, state.estimatedCurrencyCode)
        val actual = PriceFormat.parse(state.actualPrice, state.actualCurrencyCode)
        val input = GiftIdeaInput(
            recipientPersonId = state.recipientId,
            occasionId = state.occasionId,
            name = state.name,
            status = state.ideaStatus,
            estimatedPrice = estimated.moneyOrNull(),
            actualPrice = actual.moneyOrNull(),
            productUrl = state.productUrl,
            note = state.note,
        )
        val errors = saveGiftIdea.validate(input)
        val estimatedError = estimated.toError()
        val actualError = actual.toError()
        if (errors.isNotEmpty() || estimatedError != null || actualError != null) {
            _uiState.update {
                it.copy(errors = errors, estimatedPriceError = estimatedError, actualPriceError = actualError)
            }
            return
        }

        _uiState.update { it.copy(isBusy = true, writeError = null) }
        viewModelScope.launch {
            try {
                val result = when (target) {
                    is GiftIdeaEditorTarget.New -> saveGiftIdea.add(input)
                    is GiftIdeaEditorTarget.Existing -> saveGiftIdea.update(target.giftIdeaId, input)
                }
                _uiState.update {
                    when (result) {
                        SaveGiftIdeaResult.Saved -> it.copy(status = GiftIdeaEditorStatus.CLOSED)
                        is SaveGiftIdeaResult.Invalid -> it.copy(isBusy = false, errors = result.errors)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: GiftIdeaNotFoundException) {
                _uiState.update { it.copy(isBusy = false, status = GiftIdeaEditorStatus.NOT_FOUND) }
            } catch (_: PersonNotFoundException) {
                _uiState.update { it.copy(isBusy = false, writeError = GiftIdeaWriteError.PERSON_UNAVAILABLE) }
            } catch (_: OccasionNotFoundException) {
                _uiState.update { it.copy(isBusy = false, writeError = GiftIdeaWriteError.OCCASION_UNAVAILABLE) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isBusy = false, writeError = GiftIdeaWriteError.FAILED) }
            }
        }
    }

    private fun load(id: UUID) {
        viewModelScope.launch {
            try {
                val entry = giftIdeaRepository.getGiftIdea(id)
                _uiState.update { if (entry == null) it.copy(status = GiftIdeaEditorStatus.NOT_FOUND) else it.loaded(entry) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(status = GiftIdeaEditorStatus.LOAD_FAILED) }
            }
        }
    }

    // The user's own occasions fit any idea or gift; they are offered whoever it is for. A failed
    // read only leaves them out: the editor and any occasion it was loaded with stay usable.
    private fun observeGeneralOccasions() {
        viewModelScope.launch {
            occasionRepository.observeOccasions()
                .map(::generalOccasions)
                .catch { emit(emptyList()) }
                .collect { occasions -> _uiState.update { it.copy(generalOccasions = occasions) } }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeOccasionsOfRecipient() {
        viewModelScope.launch {
            _uiState.map { it.recipientId }
                .distinctUntilChanged()
                .flatMapLatest { personId ->
                    if (personId == null) flowOf(emptyList()) else occasionRepository.observeOccasionsForPerson(personId)
                }
                .catch { emit(emptyList()) }
                .collect { occasions -> _uiState.update { it.copy(recipientOccasions = occasions) } }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(target: GiftIdeaEditorTarget): GiftIdeaEditorViewModel
    }

    private companion object {

        fun GiftIdeaEditorUiState.withActivePeople(people: List<Person>): GiftIdeaEditorUiState {
            val selectedIsKnown =
                recipientId == null || people.any { it.id == recipientId } || historicalRecipient?.id == recipientId
            return copy(
                activePeople = people,
                peopleLoaded = true,
                // A preselected person who is gone or not the user's is dropped rather than shown blank.
                recipientId = if (selectedIsKnown || status == GiftIdeaEditorStatus.LOADING) recipientId else null,
            )
        }

        fun GiftIdeaEditorUiState.loaded(entry: GiftIdeaEntry): GiftIdeaEditorUiState {
            val idea = entry.idea
            val locale = Locale.getDefault()
            return copy(
                status = GiftIdeaEditorStatus.EDITING,
                recipientId = idea.recipientPersonId,
                historicalRecipient = entry.recipient,
                occasionId = idea.occasionId,
                historicalOccasion = entry.occasion,
                name = idea.name,
                ideaStatus = idea.status,
                estimatedPrice = idea.estimatedPrice?.let { PriceFormat.formatForInput(it, locale) }.orEmpty(),
                estimatedCurrencyCode = idea.estimatedPrice?.currencyCode ?: DefaultCurrency.CODE,
                actualPrice = idea.actualPrice?.let { PriceFormat.formatForInput(it, locale) }.orEmpty(),
                actualCurrencyCode = idea.actualPrice?.currencyCode ?: DefaultCurrency.CODE,
                productUrl = idea.productUrl.orEmpty(),
                note = idea.note.orEmpty(),
            )
        }

        fun PriceFormat.ParseResult.moneyOrNull(): Money? = (this as? PriceFormat.ParseResult.Valid)?.money

        fun PriceFormat.ParseResult.toError(): PriceError? = when (this) {
            is PriceFormat.ParseResult.Valid -> null
            PriceFormat.ParseResult.Negative -> PriceError.NEGATIVE
            PriceFormat.ParseResult.TooPrecise -> PriceError.TOO_PRECISE
            PriceFormat.ParseResult.Invalid -> PriceError.INVALID
        }
    }
}

sealed interface GiftIdeaEditorTarget {
    /** [personId] preselects the recipient, e.g. when adding from a person's page. */
    data class New(val personId: UUID? = null) : GiftIdeaEditorTarget
    data class Existing(val giftIdeaId: UUID) : GiftIdeaEditorTarget
}

data class GiftIdeaEditorUiState(
    val isNew: Boolean,
    val status: GiftIdeaEditorStatus,
    val recipientId: UUID? = null,
    val occasionId: UUID? = null,
    val name: String = "",
    val ideaStatus: GiftIdeaStatus = GiftIdeaStatus.IDEA,
    val estimatedPrice: String = "",
    val estimatedCurrencyCode: String = DefaultCurrency.CODE,
    val actualPrice: String = "",
    val actualCurrencyCode: String = DefaultCurrency.CODE,
    val productUrl: String = "",
    val note: String = "",
    val activePeople: List<Person> = emptyList(),
    val peopleLoaded: Boolean = false,
    val recipientOccasions: List<Occasion> = emptyList(),
    val generalOccasions: List<Occasion> = emptyList(),
    /** The recipient and occasion the idea was loaded with; they may have been deleted since. */
    val historicalRecipient: Person? = null,
    val historicalOccasion: Occasion? = null,
    val errors: Set<GiftIdeaInputError> = emptySet(),
    val estimatedPriceError: PriceError? = null,
    val actualPriceError: PriceError? = null,
    /** A save is in progress; further saves are ignored. */
    val isBusy: Boolean = false,
    val writeError: GiftIdeaWriteError? = null,
) {
    val recipientOptions: List<Person> get() = personChoices(activePeople, historicalRecipient, recipientId)

    val selectedRecipient: Person? get() = recipientOptions.find { it.id == recipientId }

    val occasionOptions: List<Occasion?> get() = occasionChoices(recipientOccasions, generalOccasions, historicalOccasion, occasionId)

    val selectedOccasion: Occasion? get() = occasionOptions.find { it?.id == occasionId && it != null }

    /**
     * The actual price matters once something is bought. It stays visible while it holds a
     * value, so moving the status back does not hide or discard what was entered.
     */
    val showsActualPrice: Boolean get() = ideaStatus == GiftIdeaStatus.PURCHASED || actualPrice.isNotBlank()

    /** Nobody to plan for yet; the user has to add a person first. */
    val needsPerson: Boolean get() = peopleLoaded && recipientOptions.isEmpty()
}

enum class GiftIdeaEditorStatus {
    LOADING,
    EDITING,
    NOT_FOUND,
    LOAD_FAILED,

    /** The idea was stored locally; the screen should close. */
    CLOSED,
}

enum class GiftIdeaWriteError {
    FAILED,

    /** The chosen person was deleted meanwhile. */
    PERSON_UNAVAILABLE,

    /** The chosen occasion was deleted meanwhile or does not belong to the recipient. */
    OCCASION_UNAVAILABLE,
}
