package app.gifty.domain.usecase

import app.gifty.domain.model.OccasionType
import app.gifty.domain.model.UpcomingOccasion
import app.gifty.domain.repository.OccasionDetails
import app.gifty.testing.FakeOccasionRepository
import app.gifty.testing.occasion
import app.gifty.testing.person
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveUpcomingOccasionsUseCaseTest {

    private val budapest = ZoneId.of("Europe/Budapest")
    private val repository = FakeOccasionRepository()
    private val anna = person("Anna")
    private val bela = person("Béla")

    init {
        repository.people.value = mapOf(anna.id to anna, bela.id to bela)
    }

    @Test
    fun `today is the device's day, and the window runs from today through day 60, soonest first`() = runTest {
        // 22:30 UTC on 9 May is already 10 May in Budapest.
        val emissions = observe(start = Instant.parse("2026-05-09T22:30:00Z"), zone = budapest)
        repository.givenOccasions(
            occasion(bela.id, yearly(day = 10, month = 5)),
            occasion(anna.id, yearly(day = 10, month = 5)),
            occasion(anna.id, yearly(day = 9, month = 5)),
            occasion(bela.id, yearly(day = 9, month = 7, type = OccasionType.NAME_DAY)),
            occasion(anna.id, yearly(day = 10, month = 7, type = OccasionType.NAME_DAY)),
            occasion(anna.id, yearly(day = 1, month = 6, year = 1990)),
            occasion(null, OccasionDetails(OccasionType.CUSTOM, "Trip", 20, 5, 2026, repeatsYearly = false)),
            occasion(null, OccasionDetails(OccasionType.CUSTOM, "Past", 1, 5, 2026, repeatsYearly = false)),
        )
        runCurrent()

        assertEquals(
            listOf(
                Row("Anna", LocalDate.of(2026, 5, 10), 0, null),
                Row("Béla", LocalDate.of(2026, 5, 10), 0, null),
                Row("Trip", LocalDate.of(2026, 5, 20), 10, null),
                Row("Anna", LocalDate.of(2026, 6, 1), 22, 36),
                Row("Béla", LocalDate.of(2026, 7, 9), 60, null),
            ),
            emissions.last().map(::row),
        )
    }

    @Test
    fun `the list moves on at local midnight, and not before`() = runTest {
        val emissions = observe(start = Instant.parse("2026-05-10T21:59:30Z"), zone = budapest) // 23:59:30 local
        repository.givenOccasions(
            occasion(anna.id, yearly(day = 10, month = 5)),
            occasion(bela.id, yearly(day = 11, month = 5)),
        )
        runCurrent()
        assertEquals(listOf(Row("Anna", LocalDate.of(2026, 5, 10), 0, null), Row("Béla", LocalDate.of(2026, 5, 11), 1, null)), emissions.last().map(::row))
        val before = emissions.size

        advanceTimeBy(29_999)
        runCurrent()
        assertEquals("nothing new before midnight", before, emissions.size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(before + 1, emissions.size)
        assertEquals(listOf(Row("Béla", LocalDate.of(2026, 5, 11), 0, null)), emissions.last().map(::row))
    }

    @Test
    fun `a day is as long as the local clock makes it`() = runTest {
        // The night clocks go back in Budapest: 25 hours from midnight to midnight.
        assertNextDayAfter(Instant.parse("2026-10-24T22:00:00Z"), budapest, Duration.ofHours(25))
        // The night clocks go forward: 23 hours.
        assertNextDayAfter(Instant.parse("2026-03-28T23:00:00Z"), budapest, Duration.ofHours(23))
        // In Santiago clocks go forward at midnight, so 6 September starts at 01:00, an hour after 23:00.
        assertNextDayAfter(Instant.parse("2026-09-06T03:00:00Z"), ZoneId.of("America/Santiago"), Duration.ofHours(1))
    }

    @Test
    fun `changes to occasions show at once`() = runTest {
        val emissions = observe(start = Instant.parse("2026-05-10T10:00:00Z"), zone = ZoneOffset.UTC)
        runCurrent()
        assertEquals(emptyList<Row>(), emissions.last().map(::row))

        repository.givenOccasions(occasion(anna.id, yearly(day = 12, month = 5)))
        runCurrent()

        assertEquals(listOf(Row("Anna", LocalDate.of(2026, 5, 12), 2, null)), emissions.last().map(::row))
    }

    private fun TestScope.assertNextDayAfter(start: Instant, zone: ZoneId, length: Duration) {
        val emissions = observeDates(start, zone)
        runCurrent()
        val first = emissions.single()

        advanceTimeBy(length.toMillis() - 1)
        runCurrent()
        assertEquals("$zone: no new day before $length", listOf(first), emissions)

        advanceTimeBy(1)
        runCurrent()
        assertEquals("$zone: a new day after $length", listOf(first, first.plusDays(1)), emissions)
    }

    /** The dates the use case counts from, seen through an occasion falling every day of the year. */
    private fun TestScope.observeDates(start: Instant, zone: ZoneId): List<LocalDate> {
        val repository = FakeOccasionRepository()
        val days = (1..12).flatMap { month -> (1..LocalDate.of(2026, month, 1).lengthOfMonth()).map { day -> occasion(null, yearly(day, month)) } }
        repository.givenOccasions(*days.toTypedArray())
        val dates = mutableListOf<LocalDate>()
        backgroundScope.launch {
            ObserveUpcomingOccasionsUseCase(repository, schedulerClock(start), zone)().collect { dates += it.first().date }
        }
        return dates
    }

    private fun TestScope.observe(start: Instant, zone: ZoneId): List<List<UpcomingOccasion>> {
        val emissions = mutableListOf<List<UpcomingOccasion>>()
        backgroundScope.launch {
            ObserveUpcomingOccasionsUseCase(repository, schedulerClock(start), zone)().collect { emissions += it }
        }
        return emissions
    }

    /** A wall clock that shows [start] now and moves with the test's virtual time, as delays do. */
    private fun TestScope.schedulerClock(start: Instant): Clock = object : Clock() {
        private val origin = testScheduler.currentTime
        override fun instant(): Instant = start.plusMillis(testScheduler.currentTime - origin)
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = throw UnsupportedOperationException()
    }

    private fun yearly(day: Int, month: Int, year: Int? = null, type: OccasionType = OccasionType.BIRTHDAY) =
        OccasionDetails(type, null, day, month, year, repeatsYearly = true)

    private data class Row(val name: String, val date: LocalDate, val daysUntil: Int, val turningAge: Int?)

    private fun row(upcoming: UpcomingOccasion) =
        Row(upcoming.person?.name ?: upcoming.occasion.title.orEmpty(), upcoming.date, upcoming.daysUntil, upcoming.turningAge)
}
