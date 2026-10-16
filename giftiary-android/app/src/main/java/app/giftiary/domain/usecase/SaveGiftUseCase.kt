package app.giftiary.domain.usecase

import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.Money
import app.giftiary.domain.repository.GiftDetails
import app.giftiary.domain.repository.GiftRepository
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * Validates and normalizes user-entered gift details before they are stored. Whether referenced
 * people and occasions are available is checked by the repository inside the write transaction.
 */
class SaveGiftUseCase @Inject constructor(
    private val repository: GiftRepository,
) {

    suspend fun add(input: GiftInput): SaveGiftResult =
        withValidInput(input) { details -> repository.addGift(details) }

    /** @throws app.giftiary.domain.repository.GiftNotFoundException if the gift no longer exists. */
    suspend fun update(id: UUID, input: GiftInput): SaveGiftResult =
        withValidInput(input) { details -> repository.updateGift(id, details) }

    fun validate(input: GiftInput): Set<GiftInputError> = buildSet {
        if (input.name.isBlank()) add(GiftInputError.NAME_REQUIRED)
        // A gift the user gave must say to whom, and one they received from whom. Gifts between
        // two other people satisfy this too, since both participants are set.
        if (input.direction == GiftDirection.GIVEN && input.recipientPersonId == null) {
            add(GiftInputError.RECIPIENT_REQUIRED)
        }
        if (input.direction == GiftDirection.RECEIVED && input.giverPersonId == null) {
            add(GiftInputError.GIVER_REQUIRED)
        }
    }

    private suspend fun withValidInput(input: GiftInput, save: suspend (GiftDetails) -> Unit): SaveGiftResult {
        val errors = validate(input)
        if (errors.isNotEmpty()) return SaveGiftResult.Invalid(errors)

        save(
            GiftDetails(
                direction = input.direction,
                giverPersonId = input.giverPersonId,
                recipientPersonId = input.recipientPersonId,
                occasionId = input.occasionId,
                name = input.name.trim(),
                givenAt = input.givenAt,
                price = input.price,
                productUrl = input.productUrl.trimToNull(),
                note = input.note.trimToNull(),
            ),
        )
        return SaveGiftResult.Saved
    }

    private fun String?.trimToNull(): String? = this?.trim()?.ifEmpty { null }
}

/** Editor input; the price has already been parsed into [Money] by the UI. */
data class GiftInput(
    val direction: GiftDirection,
    val giverPersonId: UUID?,
    val recipientPersonId: UUID?,
    val occasionId: UUID?,
    val name: String,
    val givenAt: LocalDate,
    val price: Money?,
    val productUrl: String?,
    val note: String?,
)

sealed interface SaveGiftResult {
    data object Saved : SaveGiftResult
    data class Invalid(val errors: Set<GiftInputError>) : SaveGiftResult
}

enum class GiftInputError {
    NAME_REQUIRED,
    RECIPIENT_REQUIRED,
    GIVER_REQUIRED,
}
