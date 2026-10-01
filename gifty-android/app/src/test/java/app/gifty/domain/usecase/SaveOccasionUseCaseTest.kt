package app.gifty.domain.usecase

import app.gifty.domain.model.OccasionType
import app.gifty.testing.FakeOccasionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SaveOccasionUseCaseTest {

    private val personId = UUID.randomUUID()
    private val repository = FakeOccasionRepository().apply { knownPersonIds += personId }
    private val saveOccasion = SaveOccasionUseCase(repository)

    private val birthday = OccasionInput(
        type = OccasionType.BIRTHDAY,
        title = null,
        day = 12,
        month = 5,
        year = null,
        repeatsYearly = true,
    )

    @Test
    fun `birthday without a year is valid`() = runTest {
        assertEquals(SaveOccasionResult.Saved, saveOccasion.add(personId, birthday))
        assertNull(repository.currentOccasions.single().year)
    }

    @Test
    fun `29 February without a year is valid`() = runTest {
        assertEquals(SaveOccasionResult.Saved, saveOccasion.add(personId, birthday.copy(day = 29, month = 2)))
    }

    @Test
    fun `29 February in a known common year is rejected`() = runTest {
        val result = saveOccasion.add(personId, birthday.copy(day = 29, month = 2, year = 2025))

        assertEquals(SaveOccasionResult.Invalid(setOf(OccasionInputError.NOT_A_LEAP_YEAR)), result)
        assertTrue(repository.currentOccasions.isEmpty())
    }

    @Test
    fun `a day that does not exist in the month is rejected`() = runTest {
        val result = saveOccasion.add(personId, birthday.copy(day = 31, month = 4))

        assertEquals(SaveOccasionResult.Invalid(setOf(OccasionInputError.DAY_NOT_IN_MONTH)), result)
    }

    @Test
    fun `missing day and month are reported together`() = runTest {
        val result = saveOccasion.add(personId, birthday.copy(day = null, month = null))

        assertEquals(
            SaveOccasionResult.Invalid(setOf(OccasionInputError.DAY_REQUIRED, OccasionInputError.MONTH_REQUIRED)),
            result,
        )
    }

    @Test
    fun `custom occasion requires a title that is not just whitespace`() = runTest {
        val custom = birthday.copy(type = OccasionType.CUSTOM)

        listOf(null, "", "   ").forEach { title ->
            assertEquals(
                SaveOccasionResult.Invalid(setOf(OccasionInputError.TITLE_REQUIRED)),
                saveOccasion.add(personId, custom.copy(title = title)),
            )
        }
    }

    @Test
    fun `custom title is trimmed`() = runTest {
        saveOccasion.add(personId, birthday.copy(type = OccasionType.CUSTOM, title = "  Our anniversary "))

        assertEquals("Our anniversary", repository.currentOccasions.single().title)
    }

    @Test
    fun `non-custom occasions need no title and drop a leftover one`() = runTest {
        saveOccasion.add(personId, birthday.copy(title = "Left over from switching type"))

        assertNull(repository.currentOccasions.single().title)
    }

    @Test
    fun `only custom occasions can be one-time`() = runTest {
        saveOccasion.add(personId, birthday.copy(repeatsYearly = false))
        saveOccasion.add(
            personId,
            birthday.copy(type = OccasionType.CUSTOM, title = "Graduation", year = 2027, repeatsYearly = false),
        )

        assertEquals(listOf(true, false), repository.currentOccasions.map { it.repeatsYearly })
    }

    @Test
    fun `a one-time occasion needs a year`() = runTest {
        val result = saveOccasion.add(
            personId,
            birthday.copy(type = OccasionType.CUSTOM, title = "Graduation", repeatsYearly = false),
        )

        assertEquals(SaveOccasionResult.Invalid(setOf(OccasionInputError.YEAR_REQUIRED)), result)
    }

    @Test
    fun `update applies the same validation`() = runTest {
        saveOccasion.add(personId, birthday)
        val id = repository.currentOccasions.single().id

        assertEquals(
            SaveOccasionResult.Invalid(setOf(OccasionInputError.DAY_NOT_IN_MONTH)),
            saveOccasion.update(id, birthday.copy(day = 30, month = 2)),
        )
        assertEquals(SaveOccasionResult.Saved, saveOccasion.update(id, birthday.copy(year = 1990)))
        assertEquals(1990, repository.currentOccasions.single().year)
    }
}
