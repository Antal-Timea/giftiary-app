package app.gifty.presentation.people

import app.gifty.domain.model.OccasionType
import app.gifty.domain.repository.OccasionDetails
import app.gifty.testing.FakeOccasionRepository
import app.gifty.testing.FakePersonRepository
import app.gifty.testing.MainDispatcherRule
import app.gifty.testing.occasion
import app.gifty.testing.person
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PersonDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val people = FakePersonRepository()
    private val occasions = FakeOccasionRepository()
    private val anna = person("Anna", relationship = "Sister")

    @Test
    fun `shows the person with their occasions in calendar order`() = runTest {
        val nameDay = occasion(anna.id, OccasionDetails(OccasionType.NAME_DAY, null, 26, 7, null, true))
        val birthday = occasion(anna.id, OccasionDetails(OccasionType.BIRTHDAY, null, 12, 3, null, true))
        people.givenPeople(anna)
        occasions.givenOccasions(nameDay, birthday)
        val viewModel = PersonDetailViewModel(anna.id, people, occasions)
        collectState(viewModel)

        assertEquals(PersonDetailUiState.Loaded(anna, listOf(birthday, nameDay)), viewModel.uiState.value)
    }

    @Test
    fun `edits to the person and new occasions appear without reloading`() = runTest {
        people.givenPeople(anna)
        val viewModel = PersonDetailViewModel(anna.id, people, occasions)
        collectState(viewModel)

        people.updatePerson(anna.id, "Anna K.", "Sister")
        occasions.knownPersonIds += anna.id
        val added = occasions.addOccasion(anna.id, OccasionDetails(OccasionType.BIRTHDAY, null, 12, 3, null, true))

        assertEquals(
            PersonDetailUiState.Loaded(anna.copy(name = "Anna K."), listOf(added)),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `a missing or deleted person shows not found`() = runTest {
        people.givenPeople(anna)
        val viewModel = PersonDetailViewModel(anna.id, people, occasions)
        collectState(viewModel)

        people.deletePerson(anna.id)

        assertEquals(PersonDetailUiState.NotFound, viewModel.uiState.value)
    }

    private fun TestScope.collectState(viewModel: PersonDetailViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }
}
