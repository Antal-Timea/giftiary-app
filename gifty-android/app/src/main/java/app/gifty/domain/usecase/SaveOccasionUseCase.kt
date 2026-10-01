package app.gifty.domain.usecase

import app.gifty.domain.model.Occasion
import app.gifty.domain.model.OccasionDateProblem
import app.gifty.domain.model.OccasionType
import app.gifty.domain.repository.OccasionDetails
import app.gifty.domain.repository.OccasionRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Validates and normalizes user-entered occasion details before they are stored. Whether a
 * referenced person exists and belongs to the user is checked by the repository inside the
 * write transaction, so it cannot change between the check and the save.
 */
class SaveOccasionUseCase @Inject constructor(
    private val repository: OccasionRepository,
) {

    /** @throws app.gifty.domain.repository.PersonNotFoundException if the person is not available. */
    suspend fun add(personId: UUID?, input: OccasionInput): SaveOccasionResult =
        withValidInput(input) { details -> repository.addOccasion(personId, details) }

    /** @throws app.gifty.domain.repository.OccasionNotFoundException if the occasion no longer exists. */
    suspend fun update(id: UUID, input: OccasionInput): SaveOccasionResult =
        withValidInput(input) { details -> repository.updateOccasion(id, details) }

    private suspend fun withValidInput(
        input: OccasionInput,
        save: suspend (OccasionDetails) -> Unit,
    ): SaveOccasionResult {
        val title = input.title?.trim()?.ifEmpty { null }
        val day = input.day
        val month = input.month
        val repeatsYearly = input.type.alwaysRepeatsYearly || input.repeatsYearly

        val errors = buildSet {
            if (input.type == OccasionType.CUSTOM && title == null) add(OccasionInputError.TITLE_REQUIRED)
            if (day == null) add(OccasionInputError.DAY_REQUIRED)
            if (month == null) add(OccasionInputError.MONTH_REQUIRED)
            if (!repeatsYearly && input.year == null) add(OccasionInputError.YEAR_REQUIRED)
            if (day != null && month != null) {
                when (Occasion.dateProblem(day, month, input.year)) {
                    null -> Unit
                    OccasionDateProblem.NOT_A_LEAP_YEAR -> add(OccasionInputError.NOT_A_LEAP_YEAR)
                    OccasionDateProblem.INVALID_YEAR -> add(OccasionInputError.INVALID_YEAR)
                    OccasionDateProblem.INVALID_MONTH,
                    OccasionDateProblem.DAY_NOT_IN_MONTH,
                    -> add(OccasionInputError.DAY_NOT_IN_MONTH)
                }
            }
        }
        if (errors.isNotEmpty() || day == null || month == null) return SaveOccasionResult.Invalid(errors)

        save(
            OccasionDetails(
                type = input.type,
                title = if (input.type == OccasionType.CUSTOM) title else null,
                day = day,
                month = month,
                year = input.year,
                repeatsYearly = repeatsYearly,
            ),
        )
        return SaveOccasionResult.Saved
    }
}

/** Raw editor input; day and month are null while the user has not provided them. */
data class OccasionInput(
    val type: OccasionType,
    val title: String?,
    val day: Int?,
    val month: Int?,
    val year: Int?,
    val repeatsYearly: Boolean,
)

sealed interface SaveOccasionResult {
    data object Saved : SaveOccasionResult
    data class Invalid(val errors: Set<OccasionInputError>) : SaveOccasionResult
}

enum class OccasionInputError {
    TITLE_REQUIRED,
    DAY_REQUIRED,
    MONTH_REQUIRED,
    DAY_NOT_IN_MONTH,
    NOT_A_LEAP_YEAR,
    INVALID_YEAR,

    /** A one-time occasion has no meaningful date without a year. */
    YEAR_REQUIRED,
}
