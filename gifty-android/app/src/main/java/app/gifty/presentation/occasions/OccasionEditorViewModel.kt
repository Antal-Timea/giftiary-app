package app.gifty.presentation.occasions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gifty.domain.model.OccasionType
import app.gifty.domain.repository.OccasionNotFoundException
import app.gifty.domain.repository.OccasionRepository
import app.gifty.domain.repository.PersonNotFoundException
import app.gifty.domain.usecase.OccasionInput
import app.gifty.domain.usecase.OccasionInputError
import app.gifty.domain.usecase.SaveOccasionResult
import app.gifty.domain.usecase.SaveOccasionUseCase
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
        OccasionEditorUiState(
            isNew = target is OccasionEditorTarget.New,
            status = if (target is OccasionEditorTarget.New) OccasionEditorStatus.EDITING else OccasionEditorStatus.LOADING,
        ),
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
    data class New(val personId: UUID) : OccasionEditorTarget
    data class Existing(val occasionId: UUID) : OccasionEditorTarget
}

data class OccasionEditorUiState(
    val isNew: Boolean,
    val status: OccasionEditorStatus,
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
    /** Christmas is not offered for a person, but an existing one stays selectable. */
    val typeOptions: List<OccasionType>
        get() = if (type in PERSON_OCCASION_TYPES) PERSON_OCCASION_TYPES else PERSON_OCCASION_TYPES + type

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
