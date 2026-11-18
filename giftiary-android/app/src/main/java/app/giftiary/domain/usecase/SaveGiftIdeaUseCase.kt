package app.giftiary.domain.usecase

import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.PhotoEdit
import app.giftiary.domain.repository.GiftIdeaDetails
import app.giftiary.domain.repository.GiftIdeaRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Validates and normalizes user-entered gift idea details before they are stored. Whether the
 * recipient and occasion are available is checked by the repository inside the write transaction.
 */
class SaveGiftIdeaUseCase @Inject constructor(
    private val repository: GiftIdeaRepository,
) {

    suspend fun add(input: GiftIdeaInput): SaveGiftIdeaResult =
        withValidInput(input) { details -> repository.addGiftIdea(details) }

    /**
     * @throws app.giftiary.domain.repository.GiftIdeaNotFoundException if the idea is no longer active.
     * @throws app.giftiary.domain.repository.GiftPhotoUnavailableException if a new photo is not on this device.
     */
    suspend fun update(id: UUID, input: GiftIdeaInput): SaveGiftIdeaResult =
        withValidInput(input) { details -> repository.updateGiftIdea(id, details) }

    fun validate(input: GiftIdeaInput): Set<GiftIdeaInputError> = buildSet {
        if (input.name.isBlank()) add(GiftIdeaInputError.NAME_REQUIRED)
        if (input.recipientPersonId == null) add(GiftIdeaInputError.RECIPIENT_REQUIRED)
    }

    private suspend fun withValidInput(
        input: GiftIdeaInput,
        save: suspend (GiftIdeaDetails) -> Unit,
    ): SaveGiftIdeaResult {
        val errors = validate(input)
        val recipientPersonId = input.recipientPersonId
        if (errors.isNotEmpty() || recipientPersonId == null) return SaveGiftIdeaResult.Invalid(errors)

        save(
            GiftIdeaDetails(
                recipientPersonId = recipientPersonId,
                occasionId = input.occasionId,
                name = input.name.trim(),
                status = input.status,
                estimatedPrice = input.estimatedPrice,
                actualPrice = input.actualPrice,
                productUrl = input.productUrl.trimToNull(),
                note = input.note.trimToNull(),
                photo = input.photo,
            ),
        )
        return SaveGiftIdeaResult.Saved
    }

    private fun String?.trimToNull(): String? = this?.trim()?.ifEmpty { null }
}

/** Editor input; prices have already been parsed into [Money] by the UI. */
data class GiftIdeaInput(
    val recipientPersonId: UUID?,
    val occasionId: UUID?,
    val name: String,
    val status: GiftIdeaStatus,
    val estimatedPrice: Money?,
    val actualPrice: Money?,
    val productUrl: String?,
    val note: String?,
    val photo: PhotoEdit,
)

sealed interface SaveGiftIdeaResult {
    data object Saved : SaveGiftIdeaResult
    data class Invalid(val errors: Set<GiftIdeaInputError>) : SaveGiftIdeaResult
}

enum class GiftIdeaInputError {
    NAME_REQUIRED,
    RECIPIENT_REQUIRED,
}
