package app.giftiary.domain.usecase

import app.giftiary.testing.FakePersonRepository
import app.giftiary.testing.person
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavePersonUseCaseTest {

    private val repository = FakePersonRepository()
    private val savePerson = SavePersonUseCase(repository)

    @Test
    fun `blank name is rejected without saving`() = runTest {
        listOf("", "   ", "\t\n").forEach { name ->
            assertEquals(SavePersonResult.BlankName, savePerson.add(name, "Friend"))
        }
        assertTrue(repository.currentPeople.isEmpty())
    }

    @Test
    fun `name and relationship are trimmed before saving`() = runTest {
        val result = savePerson.add("  Anna  ", "  Sister ")

        val saved = repository.currentPeople.single()
        assertEquals("the new person is named, to be opened", SavePersonResult.Saved(saved.id), result)
        assertEquals("Anna", saved.name)
        assertEquals("Sister", saved.relationship)
    }

    @Test
    fun `blank relationship is stored as absent`() = runTest {
        savePerson.add("Anna", "   ")

        assertNull(repository.currentPeople.single().relationship)
    }

    @Test
    fun `update applies the same validation`() = runTest {
        val anna = person("Anna", relationship = "Sister")
        repository.givenPeople(anna)

        assertEquals(SavePersonResult.BlankName, savePerson.update(anna.id, " ", null))
        assertEquals(SavePersonResult.Saved(anna.id), savePerson.update(anna.id, " Anna K. ", ""))

        val updated = repository.currentPeople.single()
        assertEquals("Anna K.", updated.name)
        assertNull(updated.relationship)
    }
}
