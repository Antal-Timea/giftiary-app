package app.giftiary.domain.usecase

import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.testing.FakeGiftIdeaRepository
import app.giftiary.testing.person
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SaveGiftIdeaUseCaseTest {

    private val anna = person("Anna")
    private val repository = FakeGiftIdeaRepository().apply { people[anna.id] = anna }
    private val saveGiftIdea = SaveGiftIdeaUseCase(repository)

    private val scarfForAnna = GiftIdeaInput(
        recipientPersonId = anna.id,
        occasionId = null,
        name = "Scarf",
        status = GiftIdeaStatus.IDEA,
        estimatedPrice = null,
        actualPrice = null,
        productUrl = null,
        note = null,
    )

    @Test
    fun `a name and a recipient are required`() = runTest {
        val result = saveGiftIdea.add(scarfForAnna.copy(name = "  ", recipientPersonId = null))

        assertEquals(
            SaveGiftIdeaResult.Invalid(setOf(GiftIdeaInputError.NAME_REQUIRED, GiftIdeaInputError.RECIPIENT_REQUIRED)),
            result,
        )
        assertNull(repository.lastSaved)
    }

    @Test
    fun `text is trimmed and blank optional text becomes absent`() = runTest {
        saveGiftIdea.add(scarfForAnna.copy(name = " Wool scarf ", productUrl = " ", note = " Green "))

        val saved = repository.lastSaved!!
        assertEquals("Wool scarf", saved.name)
        assertNull(saved.productUrl)
        assertEquals("Green", saved.note)
    }

    @Test
    fun `the chosen status is kept`() = runTest {
        saveGiftIdea.add(scarfForAnna.copy(status = GiftIdeaStatus.PURCHASED))

        assertEquals(GiftIdeaStatus.PURCHASED, repository.lastSaved!!.status)
    }
}
