package app.giftiary.presentation.gifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.DefaultCurrency
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.Person
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.domain.repository.GiftNotFoundException
import app.giftiary.domain.repository.GiftRepository
import app.giftiary.domain.repository.OccasionNotFoundException
import app.giftiary.domain.repository.OccasionRepository
import app.giftiary.domain.repository.PersonNotFoundException
import app.giftiary.domain.repository.PersonRepository
import app.giftiary.domain.usecase.GiftInput
import app.giftiary.domain.usecase.GiftInputError
import app.giftiary.domain.usecase.SaveGiftResult
import app.giftiary.domain.usecase.SaveGiftUseCase
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
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

/**
 * Backs adding and editing a gift. The editor covers the two everyday cases, "I gave" and
 * "I received", so it edits one person; the other side is the user.
 *
 * Existing prices are pre-filled with the app's current default locale.
 */
@HiltViewModel(assistedFactory = GiftEditorViewModel.Factory::class)
class GiftEditorViewModel @AssistedInject constructor(
    @Assisted private val target: GiftEditorTarget,
    personRepository: PersonRepository,
    private val occasionRepository: OccasionRepository,
    private val giftRepository: GiftRepository,
    private val saveGift: SaveGiftUseCase,
    clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState(clock))
    val uiState: StateFlow<GiftEditorUiState> = _uiState.asStateFlow()

    init {
        if (target is GiftEditorTarget.Existing) load(target.giftId)
        viewModelScope.launch {
            personRepository.observePeople()
                .catch { _uiState.update { it.copy(status = GiftEditorStatus.LOAD_FAILED) } }
                .collect { people -> _uiState.update { it.withActivePeople(people) } }
        }
        observeOccasionsOfSelectedPerson()
        observeGeneralOccasions()
    }

    fun onDirectionChange(direction: GiftDirection) {
        _uiState.update {
            if (it.direction == direction) {
                it
            } else {
                it.copy(
                    direction = direction,
                    // Another person on the user's side only makes sense for the direction it came with.
                    otherParticipantId = null,
                    errors = it.errors - GiftInputError.RECIPIENT_REQUIRED - GiftInputError.GIVER_REQUIRED,
                )
            }
        }
    }

    fun onPersonChange(person: Person) {
        _uiState.update {
            val keepsOccasion = it.selectedOccasion?.fitsPerson(person.id) == true
            it.copy(
                personId = person.id,
                occasionId = if (keepsOccasion) it.occasionId else null,
                errors = it.errors - GiftInputError.RECIPIENT_REQUIRED - GiftInputError.GIVER_REQUIRED,
                writeError = null,
            )
        }
    }

    fun onOccasionChange(occasion: Occasion?) {
        _uiState.update { it.copy(occasionId = occasion?.id, writeError = null) }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, errors = it.errors - GiftInputError.NAME_REQUIRED) }
    }

    fun onDateChange(date: LocalDate) {
        _uiState.update { it.copy(givenAt = date) }
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

    fun onWriteErrorShown() {
        _uiState.update { it.copy(writeError = null) }
    }

    fun save() {
        val state = _uiState.value
        if (state.status != GiftEditorStatus.EDITING || state.isBusy) return

        val priceResult = PriceFormat.parse(state.price, state.currencyCode)
        val price = (priceResult as? PriceFormat.ParseResult.Valid)?.money
        val input = state.toInput(price)
        val errors = saveGift.validate(input)
        val priceError = priceResult.toError()
        if (errors.isNotEmpty() || priceError != null) {
            _uiState.update { it.copy(errors = errors, priceError = priceError) }
            return
        }

        _uiState.update { it.copy(isBusy = true, writeError = null) }
        viewModelScope.launch {
            try {
                val result = when (target) {
                    is GiftEditorTarget.New -> saveGift.add(input)
                    is GiftEditorTarget.Existing -> saveGift.update(target.giftId, input)
                }
                _uiState.update {
                    when (result) {
                        SaveGiftResult.Saved -> it.copy(status = GiftEditorStatus.CLOSED)
                        is SaveGiftResult.Invalid -> it.copy(isBusy = false, errors = result.errors)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: GiftNotFoundException) {
                _uiState.update { it.copy(isBusy = false, status = GiftEditorStatus.NOT_FOUND) }
            } catch (_: PersonNotFoundException) {
                _uiState.update { it.copy(isBusy = false, writeError = GiftWriteError.PERSON_UNAVAILABLE) }
            } catch (_: OccasionNotFoundException) {
                _uiState.update { it.copy(isBusy = false, writeError = GiftWriteError.OCCASION_UNAVAILABLE) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isBusy = false, writeError = GiftWriteError.FAILED) }
            }
        }
    }

    private fun load(id: UUID) {
        viewModelScope.launch {
            try {
                val entry = giftRepository.getGift(id)
                _uiState.update { if (entry == null) it.copy(status = GiftEditorStatus.NOT_FOUND) else it.loaded(entry) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(status = GiftEditorStatus.LOAD_FAILED) }
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
    private fun observeOccasionsOfSelectedPerson() {
        viewModelScope.launch {
            _uiState.map { it.personId }
                .distinctUntilChanged()
                .flatMapLatest { personId ->
                    if (personId == null) flowOf(emptyList()) else occasionRepository.observeOccasionsForPerson(personId)
                }
                .catch { emit(emptyList()) }
                .collect { occasions -> _uiState.update { it.copy(personOccasions = occasions) } }
        }
    }

    private fun initialState(clock: Clock): GiftEditorUiState {
        val today = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()
        return when (target) {
            is GiftEditorTarget.New -> GiftEditorUiState(
                isNew = true,
                status = GiftEditorStatus.EDITING,
                direction = target.direction ?: GiftDirection.GIVEN,
                personId = target.personId,
                givenAt = today,
            )
            is GiftEditorTarget.Existing -> GiftEditorUiState(isNew = false, status = GiftEditorStatus.LOADING, givenAt = today)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(target: GiftEditorTarget): GiftEditorViewModel
    }

    private companion object {

        fun GiftEditorUiState.withActivePeople(people: List<Person>): GiftEditorUiState {
            val selectedIsKnown = personId == null || people.any { it.id == personId } || historicalPerson?.id == personId
            return copy(
                activePeople = people,
                peopleLoaded = true,
                // A preselected person who is gone or not the user's is dropped rather than shown blank.
                personId = if (selectedIsKnown || status == GiftEditorStatus.LOADING) personId else null,
            )
        }

        fun GiftEditorUiState.loaded(entry: GiftEntry): GiftEditorUiState {
            val gift = entry.gift
            val isGiven = gift.direction == GiftDirection.GIVEN
            return copy(
                status = GiftEditorStatus.EDITING,
                direction = gift.direction,
                personId = if (isGiven) gift.recipientPersonId else gift.giverPersonId,
                otherParticipantId = if (isGiven) gift.giverPersonId else gift.recipientPersonId,
                historicalPerson = if (isGiven) entry.recipient else entry.giver,
                occasionId = gift.occasionId,
                historicalOccasion = entry.occasion,
                name = gift.name,
                givenAt = gift.givenAt,
                price = gift.price?.let { PriceFormat.formatForInput(it, Locale.getDefault()) }.orEmpty(),
                currencyCode = gift.price?.currencyCode ?: DefaultCurrency.CODE,
                productUrl = gift.productUrl.orEmpty(),
                note = gift.note.orEmpty(),
            )
        }

        fun GiftEditorUiState.toInput(price: Money?): GiftInput {
            val isGiven = direction == GiftDirection.GIVEN
            return GiftInput(
                direction = direction,
                giverPersonId = if (isGiven) otherParticipantId else personId,
                recipientPersonId = if (isGiven) personId else otherParticipantId,
                occasionId = occasionId,
                name = name,
                givenAt = givenAt,
                price = price,
                productUrl = productUrl,
                note = note,
            )
        }

        fun PriceFormat.ParseResult.toError(): PriceError? = when (this) {
            is PriceFormat.ParseResult.Valid -> null
            PriceFormat.ParseResult.Negative -> PriceError.NEGATIVE
            PriceFormat.ParseResult.TooPrecise -> PriceError.TOO_PRECISE
            PriceFormat.ParseResult.Invalid -> PriceError.INVALID
        }
    }
}

sealed interface GiftEditorTarget {
    /** [direction] and [personId] preselect the editor, e.g. when adding from a person's page. */
    data class New(val direction: GiftDirection? = null, val personId: UUID? = null) : GiftEditorTarget
    data class Existing(val giftId: UUID) : GiftEditorTarget
}

data class GiftEditorUiState(
    val isNew: Boolean,
    val status: GiftEditorStatus,
    val givenAt: LocalDate,
    val direction: GiftDirection = GiftDirection.GIVEN,
    /** The person the user gave to or received from. */
    val personId: UUID? = null,
    /** Set only for gifts between two other people, which this editor keeps but cannot create. */
    val otherParticipantId: UUID? = null,
    val occasionId: UUID? = null,
    val name: String = "",
    val price: String = "",
    val currencyCode: String = DefaultCurrency.CODE,
    val productUrl: String = "",
    val note: String = "",
    val activePeople: List<Person> = emptyList(),
    val peopleLoaded: Boolean = false,
    val personOccasions: List<Occasion> = emptyList(),
    val generalOccasions: List<Occasion> = emptyList(),
    /** The person and occasion the gift was loaded with; they may have been deleted since. */
    val historicalPerson: Person? = null,
    val historicalOccasion: Occasion? = null,
    val errors: Set<GiftInputError> = emptySet(),
    val priceError: PriceError? = null,
    /** A save is in progress; further saves are ignored. */
    val isBusy: Boolean = false,
    val writeError: GiftWriteError? = null,
) {
    /** Active people, plus the gift's original person while it stays selected, even if deleted. */
    val personOptions: List<Person>
        get() = personChoices(activePeople, historicalPerson, personId)

    val selectedPerson: Person? get() = personOptions.find { it.id == personId }

    /** "No occasion", the selected person's active occasions, the general ones and, while selected, the original one. */
    val occasionOptions: List<Occasion?>
        get() = occasionChoices(personOccasions, generalOccasions, historicalOccasion, occasionId)

    val selectedOccasion: Occasion? get() = occasionOptions.find { it?.id == occasionId && it != null }

    /** Nobody to give to or receive from yet; the user has to add a person first. */
    val needsPerson: Boolean get() = peopleLoaded && personOptions.isEmpty()
}

enum class GiftEditorStatus {
    LOADING,
    EDITING,
    NOT_FOUND,
    LOAD_FAILED,

    /** The gift was stored locally; the screen should close. */
    CLOSED,
}

enum class PriceError {
    INVALID,
    NEGATIVE,
    TOO_PRECISE,
}

enum class GiftWriteError {
    FAILED,

    /** The chosen person was deleted meanwhile. */
    PERSON_UNAVAILABLE,

    /** The chosen occasion was deleted meanwhile or no longer fits the person. */
    OCCASION_UNAVAILABLE,
}
