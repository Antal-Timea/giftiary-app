package app.giftiary.presentation.people

import androidx.lifecycle.SavedStateHandle
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.domain.model.OccasionType
import app.giftiary.testing.FakeOccasionRepository
import app.giftiary.testing.FakePersonRepository
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.keepSubscribed
import app.giftiary.testing.occasion
import app.giftiary.testing.person
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class PeopleViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakePersonRepository()
    private val occasions = FakeOccasionRepository()
    private val savedState = SavedStateHandle()

    @Test
    fun `people from the repository are shown and follow its changes`() = runTest {
        val anna = person("Anna")
        repository.givenPeople(anna)
        val viewModel = PeopleViewModel(repository, occasions, savedState)
        keepSubscribed(viewModel.uiState)

        assertEquals(PeopleContent.Loaded(listOf(anna)), viewModel.uiState.value.content)

        val bela = person("Béla")
        repository.givenPeople(anna, bela)
        assertEquals(PeopleContent.Loaded(listOf(anna, bela)), viewModel.uiState.value.content)
    }

    @Test
    fun `no people is an empty loaded list`() = runTest {
        val viewModel = PeopleViewModel(repository, occasions, savedState)
        keepSubscribed(viewModel.uiState)

        assertEquals(PeopleContent.Loaded(emptyList()), viewModel.uiState.value.content)
    }

    @Test
    fun `failing to observe people shows a load error`() = runTest {
        repository.peopleFlowFailure = IOException("disk")
        val viewModel = PeopleViewModel(repository, occasions, savedState)
        keepSubscribed(viewModel.uiState)

        assertEquals(PeopleContent.LoadFailed, viewModel.uiState.value.content)
    }

    @Test
    fun `a selected person can be cleared again`() = runTest {
        val anna = person("Anna")
        repository.givenPeople(anna, person("Béla"))
        val viewModel = PeopleViewModel(repository, occasions, savedState)
        keepSubscribed(viewModel.uiState)
        assertNull(viewModel.uiState.value.selectedPersonId)

        viewModel.onPersonSelected(anna)
        assertEquals(anna.id, viewModel.uiState.value.selectedPersonId)

        viewModel.onSelectionCleared()
        assertNull(viewModel.uiState.value.selectedPersonId)
    }

    @Test
    fun `the selection is restored from saved state, as after process death`() = runTest {
        val anna = person("Anna")
        repository.givenPeople(anna)
        PeopleViewModel(repository, occasions, savedState).onPersonSelected(anna)

        val restored = PeopleViewModel(repository, occasions, savedState)
        assertNull("nothing selected before the people load", restored.uiState.value.selectedPersonId)
        keepSubscribed(restored.uiState)

        assertEquals(anna.id, restored.uiState.value.selectedPersonId)
    }

    @Test
    fun `a restored selection is not shown while the people cannot be loaded, and is kept for later`() = runTest {
        val annaId = person("Anna").id.toString()
        savedState[SELECTED_PERSON_KEY] = annaId
        repository.peopleFlowFailure = IOException("disk")
        val viewModel = PeopleViewModel(repository, occasions, savedState)
        keepSubscribed(viewModel.uiState)

        assertEquals(PeopleContent.LoadFailed, viewModel.uiState.value.content)
        assertNull(viewModel.uiState.value.selectedPersonId)
        // Only a loaded list proves the person gone.
        assertEquals(annaId, savedState.get<String>(SELECTED_PERSON_KEY))
    }

    @Test
    fun `a selected person deleted elsewhere is no longer selected, and the saved id is dropped`() = runTest {
        val anna = person("Anna")
        val bela = person("Béla")
        repository.givenPeople(anna, bela)
        val viewModel = PeopleViewModel(repository, occasions, savedState)
        keepSubscribed(viewModel.uiState)
        viewModel.onPersonSelected(anna)

        repository.givenPeople(bela)

        assertNull(viewModel.uiState.value.selectedPersonId)
        assertNull(savedState.get<String>(SELECTED_PERSON_KEY))
    }

    @Test
    fun `a search keeps the people whose name or relationship matches, ignoring case and spaces`() = runTest {
        val anna = person("Anna Kovács", relationship = "Sister")
        val bela = person("Béla", relationship = "Brother")
        val cili = person("Cili")
        repository.givenPeople(anna, bela, cili)
        val viewModel = PeopleViewModel(repository, occasions, savedState)
        keepSubscribed(viewModel.uiState)
        assertEquals(listOf(anna, bela, cili), viewModel.uiState.value.matchingPeople)

        viewModel.onQueryChange("  kovács ")
        assertEquals(listOf(anna), viewModel.uiState.value.matchingPeople)

        viewModel.onQueryChange("BROTHER")
        assertEquals(listOf(bela), viewModel.uiState.value.matchingPeople)

        viewModel.onQueryChange("Robert")
        assertEquals(emptyList<Any>(), viewModel.uiState.value.matchingPeople)
        assertEquals(PeopleContent.Loaded(listOf(anna, bela, cili)), viewModel.uiState.value.content)

        viewModel.onQueryChange("")
        assertEquals(listOf(anna, bela, cili), viewModel.uiState.value.matchingPeople)
    }

    @Test
    fun `the search is restored from saved state`() = runTest {
        repository.givenPeople(person("Anna"), person("Béla"))
        PeopleViewModel(repository, occasions, savedState).onQueryChange("Béla")

        val restored = PeopleViewModel(repository, occasions, savedState)
        keepSubscribed(restored.uiState)

        assertEquals("Béla", restored.uiState.value.query)
        assertEquals(listOf("Béla"), restored.uiState.value.matchingPeople.map { it.name })
    }

    @Test
    fun `each person's birthday is offered for their row, other occasions are not`() = runTest {
        val anna = person("Anna")
        val bela = person("Béla")
        repository.givenPeople(anna, bela)
        occasions.people.value = mapOf(anna.id to anna, bela.id to bela)
        val birthday = occasion(anna.id)
        val nameDay = occasion(bela.id, OccasionDetails(OccasionType.NAME_DAY, null, 4, 6, null, true))
        occasions.givenOccasions(birthday, nameDay)
        val viewModel = PeopleViewModel(repository, occasions, savedState)
        keepSubscribed(viewModel.uiState)

        assertEquals(mapOf(anna.id to birthday), viewModel.uiState.value.birthdays)
    }

    @Test
    fun `birthdays that cannot be read leave the people shown`() = runTest {
        val anna = person("Anna")
        repository.givenPeople(anna)
        occasions.readFailure = IOException("disk")
        val viewModel = PeopleViewModel(repository, occasions, savedState)
        keepSubscribed(viewModel.uiState)

        assertEquals(PeopleContent.Loaded(listOf(anna)), viewModel.uiState.value.content)
        assertEquals(emptyMap<Any, Any>(), viewModel.uiState.value.birthdays)
    }

    private companion object {
        // The saved-state key is part of what a restored process reads back.
        const val SELECTED_PERSON_KEY = "selectedPersonId"
    }
}
