package app.giftiary.domain.usecase

import app.giftiary.domain.model.GiftDirection
import app.giftiary.testing.FakeGiftRepository
import app.giftiary.testing.person
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SaveGiftUseCaseTest {

    private val anna = person("Anna")
    private val repository = FakeGiftRepository().apply { people[anna.id] = anna }
    private val saveGift = SaveGiftUseCase(repository)

    private val givenToAnna = GiftInput(
        direction = GiftDirection.GIVEN,
        giverPersonId = null,
        recipientPersonId = anna.id,
        occasionId = null,
        name = "Book",
        givenAt = LocalDate.of(2026, 3, 12),
        price = null,
        productUrl = null,
        note = null,
    )

    @Test
    fun `blank name is rejected`() = runTest {
        listOf("", "   ").forEach { name ->
            assertEquals(
                SaveGiftResult.Invalid(setOf(GiftInputError.NAME_REQUIRED)),
                saveGift.add(givenToAnna.copy(name = name)),
            )
        }
        assertNull(repository.lastSaved)
    }

    @Test
    fun `name, link and note are trimmed and blank optional text becomes absent`() = runTest {
        saveGift.add(givenToAnna.copy(name = "  Signed book ", productUrl = "  ", note = "  Loved it  "))

        val saved = repository.lastSaved!!
        assertEquals("Signed book", saved.name)
        assertNull(saved.productUrl)
        assertEquals("Loved it", saved.note)
    }

    @Test
    fun `a given gift needs a recipient`() = runTest {
        val result = saveGift.add(givenToAnna.copy(recipientPersonId = null))

        assertEquals(SaveGiftResult.Invalid(setOf(GiftInputError.RECIPIENT_REQUIRED)), result)
    }

    @Test
    fun `a received gift needs a giver`() = runTest {
        val result = saveGift.add(givenToAnna.copy(direction = GiftDirection.RECEIVED, recipientPersonId = null))

        assertEquals(SaveGiftResult.Invalid(setOf(GiftInputError.GIVER_REQUIRED)), result)
    }

    @Test
    fun `a received gift from a person is saved with the user as recipient`() = runTest {
        val result = saveGift.add(
            givenToAnna.copy(direction = GiftDirection.RECEIVED, giverPersonId = anna.id, recipientPersonId = null),
        )

        assertEquals(SaveGiftResult.Saved, result)
        assertNull(repository.lastSaved!!.recipientPersonId)
    }

    @Test
    fun `all problems are reported together`() = runTest {
        val result = saveGift.add(givenToAnna.copy(name = " ", recipientPersonId = null))

        assertEquals(
            SaveGiftResult.Invalid(setOf(GiftInputError.NAME_REQUIRED, GiftInputError.RECIPIENT_REQUIRED)),
            result,
        )
    }
}
