package app.gifty.domain.usecase

import app.gifty.domain.model.UpcomingOccasion
import app.gifty.domain.repository.OccasionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * The occasions falling from today through [WINDOW_DAYS] days from now, soonest first. "Today" is
 * the device's calendar day, and the list moves on by itself at local midnight.
 */
class ObserveUpcomingOccasionsUseCase @Inject constructor(
    private val repository: OccasionRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) {

    operator fun invoke(): Flow<List<UpcomingOccasion>> =
        combine(repository.observeOccasions(), localDates()) { entries, today -> upcomingOccasions(entries, today, WINDOW_DAYS) }

    /**
     * Today, and each new day once it starts. The wait is worked out again from the clock after
     * every day, since a day is not always 24 hours long where clocks change.
     */
    private fun localDates(): Flow<LocalDate> = flow {
        while (true) {
            val now = clock.instant()
            val today = now.atZone(zone).toLocalDate()
            emit(today)
            // atStartOfDay picks the first valid time when midnight itself is skipped.
            val nextDay = today.plusDays(1).atStartOfDay(zone).toInstant()
            delay(Duration.between(now, nextDay).toMillis().coerceAtLeast(1))
        }
    }.distinctUntilChanged()

    companion object {
        const val WINDOW_DAYS = 60
    }
}
