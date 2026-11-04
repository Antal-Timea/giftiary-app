package app.giftiary.domain.usecase

import app.giftiary.domain.repository.PersonRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Validates and normalizes user-entered person details before they are stored.
 */
class SavePersonUseCase @Inject constructor(
    private val repository: PersonRepository,
) {

    suspend fun add(name: String, relationship: String?): SavePersonResult =
        withValidInput(name, relationship) { validName, validRelationship ->
            repository.addPerson(validName, validRelationship).id
        }

    /** @throws app.giftiary.domain.repository.PersonNotFoundException if the person no longer exists. */
    suspend fun update(id: UUID, name: String, relationship: String?): SavePersonResult =
        withValidInput(name, relationship) { validName, validRelationship ->
            repository.updatePerson(id, validName, validRelationship)
            id
        }

    private suspend fun withValidInput(
        name: String,
        relationship: String?,
        save: suspend (name: String, relationship: String?) -> UUID,
    ): SavePersonResult {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return SavePersonResult.BlankName
        return SavePersonResult.Saved(save(trimmedName, relationship?.trim()?.ifEmpty { null }))
    }
}

sealed interface SavePersonResult {
    data class Saved(val personId: UUID) : SavePersonResult
    data object BlankName : SavePersonResult
}
