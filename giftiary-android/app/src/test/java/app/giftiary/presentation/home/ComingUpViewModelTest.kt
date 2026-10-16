package app.giftiary.presentation.home

import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.OccasionPlan
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.SyncStatus
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.domain.usecase.ObserveUpcomingOccasionsUseCase
import app.giftiary.presentation.sync.SyncStatusContent
import app.giftiary.presentation.sync.SyncStatusViewModel
import app.giftiary.testing.FakeGiftIdeaRepository
import app.giftiary.testing.FakeOccasionRepository
import app.giftiary.testing.FakeSyncStatusRepository
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.MutableClock
import app.giftiary.testing.ideaDetails
import app.giftiary.testing.ideaEntry
import app.giftiary.testing.occasion
import app.giftiary.testing.person
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ComingUpViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeOccasionRepository()
    private val anna = person("Anna")
    private val clock = MutableClock(Instant.parse("2026-05-10T10:00:00Z"))

    init {
        repository.people.value = mapOf(anna.id to anna)
    }

    @Test
    fun `nothing is shown before the occasions are read, then at most five, soonest first`() = runTest {
        repository.givenOccasions(*(11..17).map { day -> occasion(anna.id, birthday(day = day)) }.toTypedArray())
        val viewModel = viewModel()
        assertEquals(ComingUpContent.Loading, viewModel.content.value)

        collect(viewModel.content)

        val items = (viewModel.content.value as ComingUpContent.Loaded).items
        assertEquals((11..15).toList(), items.map { it.upcoming.date.dayOfMonth })
    }

    @Test
    fun `nothing coming up is an empty list, not a failure`() = runTest {
        val viewModel = viewModel()
        collect(viewModel.content)

        assertEquals(ComingUpContent.Loaded(emptyList()), viewModel.content.value)
    }

    @Test
    fun `every row opens its occasion's plan, a person's occasion as well as the user's own`() = runTest {
        val annasBirthday = occasion(anna.id, birthday(day = 12))
        val christmas = occasion(null, OccasionDetails(OccasionType.CHRISTMAS, null, 24, 5, null, true))
        repository.givenOccasions(annasBirthday, christmas)
        val viewModel = viewModel()
        collect(viewModel.content)

        assertEquals(
            listOf(annasBirthday.id, christmas.id),
            (viewModel.content.value as ComingUpContent.Loaded).items.map { it.occasionId },
        )
    }

    @Test
    fun `occasions that cannot be read leave the sync status shown as it is`() = runTest {
        repository.readFailure = IOException("disk")
        val syncRepository = FakeSyncStatusRepository().apply { status.value = SyncStatus(waitingCount = 2, attention = emptyList()) }
        val comingUp = viewModel()
        val syncStatus = SyncStatusViewModel(syncRepository)

        collect(comingUp.content)
        collect(syncStatus.content)

        assertEquals(ComingUpContent.Unavailable, comingUp.content.value)
        assertEquals(SyncStatusContent.Loaded(SyncStatus(waitingCount = 2, attention = emptyList())), syncStatus.content.value)
    }

    private fun viewModel() = ComingUpViewModel(ObserveUpcomingOccasionsUseCase(repository, clock, ZoneOffset.UTC), ideas)

    // Planning summaries

    private val ideas = FakeGiftIdeaRepository().apply { people[anna.id] = anna }

    @Test
    fun `an occasion with open ideas carries its plan, one without carries none`() = runTest {
        val birthday = occasion(anna.id, birthday(day = 12))
        val nameDay = occasion(anna.id, birthday(day = 14))
        repository.givenOccasions(birthday, nameDay)
        ideas.givenIdeas(
            ideaFor(birthday, GiftIdeaStatus.IDEA),
            ideaFor(birthday, GiftIdeaStatus.PURCHASED),
            // Linked to an occasion not shown: never counted for another one.
            ideaFor(occasion(anna.id, birthday(day = 30)), GiftIdeaStatus.PLANNED),
        )
        val viewModel = viewModel()
        collect(viewModel.content)

        assertEquals(listOf(OccasionPlan(2, 1), null), items(viewModel).map { it.plan })
    }

    @Test
    fun `plans follow new ideas, a purchase and an idea being given`() = runTest {
        val birthday = occasion(anna.id, birthday(day = 12))
        repository.givenOccasions(birthday)
        val viewModel = viewModel()
        collect(viewModel.content)
        assertEquals(null, items(viewModel).single().plan)

        val scarf = ideas.addGiftIdea(ideaDetails(anna).copy(occasionId = birthday.id))
        assertEquals(OccasionPlan(1, 0), items(viewModel).single().plan)

        ideas.updateStatus(scarf.id, GiftIdeaStatus.PURCHASED)
        assertEquals(OccasionPlan(1, 1), items(viewModel).single().plan)

        ideas.markAsGiven(scarf.id, LocalDate.of(2026, 5, 12), null)
        assertEquals("a given idea leaves the active ideas", null, items(viewModel).single().plan)
    }

    @Test
    fun `ideas that cannot be read leave the occasions shown, without plans`() = runTest {
        repository.givenOccasions(occasion(anna.id, birthday(day = 12)))
        ideas.readFailure = IOException("disk")
        val viewModel = viewModel()
        collect(viewModel.content)

        assertEquals(listOf<OccasionPlan?>(null), items(viewModel).map { it.plan })
    }

    @Test
    fun `occasions that cannot be read are still unavailable, whatever the ideas`() = runTest {
        repository.readFailure = IOException("disk")
        val viewModel = viewModel()
        collect(viewModel.content)

        assertEquals(ComingUpContent.Unavailable, viewModel.content.value)
    }

    private fun ideaFor(occasion: Occasion, status: GiftIdeaStatus) =
        ideaEntry(ideaDetails(anna, status = status).copy(occasionId = occasion.id), anna)

    private fun items(viewModel: ComingUpViewModel) = (viewModel.content.value as ComingUpContent.Loaded).items

    private fun birthday(day: Int) = OccasionDetails(OccasionType.BIRTHDAY, null, day, 5, null, true)

    private fun TestScope.collect(content: StateFlow<*>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { content.collect {} }
    }
}
