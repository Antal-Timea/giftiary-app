package app.gifty.domain.usecase

import app.gifty.testing.FakePersonRepository
import app.gifty.testing.person
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
            assertEquals(SavePersonResult.BLANK_NAME, savePerson.add(name, "Friend"))
        }
        assertTrue(repository.currentPeople.isEmpty())
    }

    @Test
    fun `name and relationship are trimmed before saving`() = runTest {
        val result = savePerson.add("  Anna  ", "  Sister ")

        assertEquals(SavePersonResult.SAVED, result)
        val saved = repository.currentPeople.single()
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

        assertEquals(SavePersonResult.BLANK_NAME, savePerson.update(anna.id, " ", null))
        assertEquals(SavePersonResult.SAVED, savePerson.update(anna.id, " Anna K. ", ""))

        val updated = repository.currentPeople.single()
        assertEquals("Anna K.", updated.name)
        assertNull(updated.relationship)
    }
}
