package app.gifty.domain.usecase

import app.gifty.domain.model.UpcomingOccasion
import app.gifty.domain.model.nextOccurrence
import app.gifty.domain.model.turningAge
import app.gifty.domain.repository.OccasionEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The occasions falling from [today] through [windowDays] days later, soonest first. */
fun upcomingOccasions(entries: List<OccasionEntry>, today: LocalDate, windowDays: Int): List<UpcomingOccasion> =
    entries.mapNotNull { entry ->
        val date = entry.occasion.nextOccurrence(onOrAfter = today) ?: return@mapNotNull null
        val daysUntil = ChronoUnit.DAYS.between(today, date)
        if (daysUntil > windowDays) return@mapNotNull null
        UpcomingOccasion(entry.occasion, entry.person, date, daysUntil.toInt(), turningAge(entry.occasion, date))
    }.sortedWith(
        compareBy<UpcomingOccasion>({ it.date }, { it.person?.name ?: it.occasion.title.orEmpty() }, { it.occasion.id }),
    )

/** What a reminder on [today] is about: occasions falling today and exactly a week later. */
fun dueReminders(entries: List<OccasionEntry>, today: LocalDate): List<UpcomingOccasion> =
    upcomingOccasions(entries, today, REMINDER_LEAD_DAYS)
        .filter { it.daysUntil == 0 || it.daysUntil == REMINDER_LEAD_DAYS }

const val REMINDER_LEAD_DAYS = 7
