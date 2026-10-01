package app.gifty.domain.usecase

import app.gifty.domain.repository.PersonRepository
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
            repository.addPerson(validName, validRelationship)
        }

    /** @throws app.gifty.domain.repository.PersonNotFoundException if the person no longer exists. */
    suspend fun update(id: UUID, name: String, relationship: String?): SavePersonResult =
        withValidInput(name, relationship) { validName, validRelationship ->
            repository.updatePerson(id, validName, validRelationship)
        }

    private suspend fun withValidInput(
        name: String,
        relationship: String?,
        save: suspend (name: String, relationship: String?) -> Unit,
    ): SavePersonResult {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return SavePersonResult.BLANK_NAME
        save(trimmedName, relationship?.trim()?.ifEmpty { null })
        return SavePersonResult.SAVED
    }
}

enum class SavePersonResult {
    SAVED,
    BLANK_NAME,
}
