package app.giftiary.presentation.occasions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.repository.OccasionNotFoundException
import app.giftiary.domain.repository.OccasionRepository
import app.giftiary.domain.repository.PersonNotFoundException
import app.giftiary.domain.usecase.OccasionInput
import app.giftiary.domain.usecase.OccasionInputError
import app.giftiary.domain.usecase.SaveOccasionResult
import app.giftiary.domain.usecase.SaveOccasionUseCase
import app.giftiary.presentation.navigation.toUuidOrNull
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
import java.util.UUID

@HiltViewModel(assistedFactory = OccasionEditorViewModel.Factory::class)
class OccasionEditorViewModel @AssistedInject constructor(
    @Assisted private val target: OccasionEditorTarget,
    private val repository: OccasionRepository,
    private val saveOccasion: SaveOccasionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        when (target) {
            is OccasionEditorTarget.New -> OccasionEditorUiState(
                isNew = true,
                status = OccasionEditorStatus.EDITING,
                forPerson = target.personId != null,
                // The user's own occasions are mostly Christmas; a person's, a birthday.
                type = if (target.personId != null) OccasionType.BIRTHDAY else OccasionType.CHRISTMAS,
            )
            is OccasionEditorTarget.Existing -> OccasionEditorUiState(isNew = false, status = OccasionEditorStatus.LOADING)
        },
    )
    val uiState: StateFlow<OccasionEditorUiState> = _uiState.asStateFlow()

    init {
        if (target is OccasionEditorTarget.Existing) load(target.occasionId)
    }

    /** Every type change starts from repeating yearly; anniversaries and custom ones can then opt out. */
    fun onTypeChange(type: OccasionType) {
        _uiState.update {
            if (it.type == type) {
                it
            } else {
                it.copy(
                    type = type,
                    repeatsYearly = true,
                    errors = it.errors - OccasionInputError.TITLE_REQUIRED - OccasionInputError.YEAR_REQUIRED,
                )
            }
        }
    }

    fun onTitleChange(title: String) {
        _uiState.update { it.copy(title = title, errors = it.errors - OccasionInputError.TITLE_REQUIRED) }
    }

    fun onDayChange(day: String) {
        if (!day.isDigitsOnly(maxLength = 2)) return
        _uiState.update { it.copy(day = day, errors = it.errors - DATE_ERRORS) }
    }

    fun onMonthChange(month: Int) {
        _uiState.update { it.copy(month = month, errors = it.errors - DATE_ERRORS) }
    }

    fun onYearChange(year: String) {
        if (!year.isDigitsOnly(maxLength = 4)) return
        _uiState.update { it.copy(year = year, errors = it.errors - DATE_ERRORS) }
    }

    fun onRepeatsYearlyChange(repeatsYearly: Boolean) {
        _uiState.update { it.copy(repeatsYearly = repeatsYearly, errors = it.errors - OccasionInputError.YEAR_REQUIRED) }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(writeFailed = false) }
    }

    fun save() {
        val state = _uiState.value
        if (state.status != OccasionEditorStatus.EDITING || state.isBusy) return

        val input = OccasionInput(
            type = state.type,
            title = state.title,
            day = state.day.toIntOrNull(),
            month = state.month,
            year = state.year.toIntOrNull(),
            repeatsYearly = state.repeatsYearly,
        )
        runWrite {
            val result = when (target) {
                is OccasionEditorTarget.New -> saveOccasion.add(target.personId, input)
                is OccasionEditorTarget.Existing -> saveOccasion.update(target.occasionId, input)
            }
            _uiState.update {
                when (result) {
                    SaveOccasionResult.Saved -> it.copy(status = OccasionEditorStatus.CLOSED)
                    is SaveOccasionResult.Invalid -> it.copy(isBusy = false, errors = result.errors)
                }
            }
        }
    }

    fun onDeleteRequested() {
        if (target is OccasionEditorTarget.Existing) _uiState.update { it.copy(confirmingDelete = true) }
    }

    fun onDeleteDismissed() {
        _uiState.update { it.copy(confirmingDelete = false) }
    }

    fun onDeleteConfirmed() {
        val occasionId = (target as? OccasionEditorTarget.Existing)?.occasionId ?: return
        _uiState.update { it.copy(confirmingDelete = false) }
        if (_uiState.value.isBusy) return
        runWrite {
            repository.deleteOccasion(occasionId)
            _uiState.update { it.copy(status = OccasionEditorStatus.CLOSED) }
        }
    }

    private fun runWrite(write: suspend () -> Unit) {
        _uiState.update { it.copy(isBusy = true, writeFailed = false) }
        viewModelScope.launch {
            try {
                write()
            } catch (e: CancellationException) {
                throw e
            } catch (_: OccasionNotFoundException) {
                _uiState.update { it.copy(isBusy = false, status = OccasionEditorStatus.NOT_FOUND) }
            } catch (_: PersonNotFoundException) {
                _uiState.update { it.copy(isBusy = false, status = OccasionEditorStatus.PERSON_NOT_FOUND) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isBusy = false, writeFailed = true) }
            }
        }
    }

    private fun load(id: UUID) {
        viewModelScope.launch {
            try {
                val occasion = repository.getOccasion(id)
                _uiState.update {
                    if (occasion == null) {
                        it.copy(status = OccasionEditorStatus.NOT_FOUND)
                    } else {
                        it.copy(
                            status = OccasionEditorStatus.EDITING,
                            forPerson = occasion.personId != null,
                            type = occasion.type,
                            title = occasion.title.orEmpty(),
                            day = occasion.day.toString(),
                            month = occasion.month,
                            year = occasion.year?.toString().orEmpty(),
                            repeatsYearly = occasion.repeatsYearly,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(status = OccasionEditorStatus.LOAD_FAILED) }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(target: OccasionEditorTarget): OccasionEditorViewModel
    }

    private companion object {
        val DATE_ERRORS = setOf(
            OccasionInputError.DAY_REQUIRED,
            OccasionInputError.MONTH_REQUIRED,
            OccasionInputError.DAY_NOT_IN_MONTH,
            OccasionInputError.NOT_A_LEAP_YEAR,
            OccasionInputError.INVALID_YEAR,
            OccasionInputError.YEAR_REQUIRED,
        )

        fun String.isDigitsOnly(maxLength: Int) = length <= maxLength && all { it in '0'..'9' }
    }
}

sealed interface OccasionEditorTarget {
    /** A new occasion of [personId], or of the user's own if it is null. */
    data class New(val personId: UUID?) : OccasionEditorTarget
    data class Existing(val occasionId: UUID) : OccasionEditorTarget

    companion object {
        /**
         * The new-occasion target for a route's person id: none means the user's own occasion, a
         * malformed one nothing at all, never quietly the user's own.
         */
        fun newFor(personId: String?): New? = when (personId) {
            null -> New(null)
            else -> personId.toUuidOrNull()?.let(::New)
        }
    }
}

data class OccasionEditorUiState(
    val isNew: Boolean,
    val status: OccasionEditorStatus,
    /** Whether the occasion is a person's; the user's own occasions offer other types. */
    val forPerson: Boolean = true,
    val type: OccasionType = OccasionType.BIRTHDAY,
    val title: String = "",
    val day: String = "",
    val month: Int? = null,
    val year: String = "",
    val repeatsYearly: Boolean = true,
    val errors: Set<OccasionInputError> = emptySet(),
    /** A save or delete is in progress; further actions are ignored. */
    val isBusy: Boolean = false,
    val confirmingDelete: Boolean = false,
    val writeFailed: Boolean = false,
) {
    /**
     * Christmas is not offered for a person, nor a birthday or name day for the user's own
     * occasions, which have no one to show them for; an existing occasion's type stays selectable.
     */
    val typeOptions: List<OccasionType>
        get() {
            val offered = if (forPerson) PERSON_OCCASION_TYPES else OWN_OCCASION_TYPES
            return if (type in offered) offered else offered + type
        }

    val showsTitle: Boolean get() = type == OccasionType.CUSTOM

    /** Types that always repeat yearly would only get a meaningless control. */
    val showsRepeatsYearly: Boolean get() = !type.alwaysRepeatsYearly

    val yearRequired: Boolean get() = showsRepeatsYearly && !repeatsYearly

    private companion object {
        val PERSON_OCCASION_TYPES = listOf(
            OccasionType.BIRTHDAY,
            OccasionType.NAME_DAY,
            OccasionType.ANNIVERSARY,
            OccasionType.CUSTOM,
        )
        val OWN_OCCASION_TYPES = listOf(
            OccasionType.CHRISTMAS,
            OccasionType.ANNIVERSARY,
            OccasionType.CUSTOM,
        )
    }
}

enum class OccasionEditorStatus {
    LOADING,
    EDITING,
    NOT_FOUND,
    PERSON_NOT_FOUND,
    LOAD_FAILED,

    /** The change was stored locally; the screen should close. */
    CLOSED,
}
