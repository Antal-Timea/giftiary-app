package app.giftiary.data.local.converter

import androidx.room.TypeConverter
import java.util.UUID

object UuidConverter {

    @TypeConverter
    fun fromUuid(value: UUID?): String? = value?.toString()

    @TypeConverter
    fun toUuid(value: String?): UUID? = value?.let(UUID::fromString)
}
