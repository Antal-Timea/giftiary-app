package app.giftiary.data.local.converter

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

/**
 * Instants are stored as epoch milliseconds so they sort and compare numerically in SQL;
 * sub-millisecond precision is dropped. Dates are stored as ISO-8601 (yyyy-MM-dd) text.
 */
object TimeConverters {

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
}
