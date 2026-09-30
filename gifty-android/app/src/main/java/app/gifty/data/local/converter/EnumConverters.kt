package app.gifty.data.local.converter

import androidx.room.TypeConverter
import app.gifty.data.local.entity.RemoteState
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.domain.model.GiftDirection
import app.gifty.domain.model.GiftIdeaStatus
import app.gifty.domain.model.OccasionType

/**
 * Enums are stored by name, so renaming a constant requires a migration; reordering is safe.
 */
object EnumConverters {

    @TypeConverter
    fun fromRemoteState(value: RemoteState): String = value.name

    @TypeConverter
    fun toRemoteState(value: String): RemoteState = RemoteState.valueOf(value)

    @TypeConverter
    fun fromOccasionType(value: OccasionType): String = value.name

    @TypeConverter
    fun toOccasionType(value: String): OccasionType = OccasionType.valueOf(value)

    @TypeConverter
    fun fromGiftDirection(value: GiftDirection): String = value.name

    @TypeConverter
    fun toGiftDirection(value: String): GiftDirection = GiftDirection.valueOf(value)

    @TypeConverter
    fun fromGiftIdeaStatus(value: GiftIdeaStatus): String = value.name

    @TypeConverter
    fun toGiftIdeaStatus(value: String): GiftIdeaStatus = GiftIdeaStatus.valueOf(value)

    @TypeConverter
    fun fromSyncEntityType(value: SyncEntityType): String = value.name

    @TypeConverter
    fun toSyncEntityType(value: String): SyncEntityType = SyncEntityType.valueOf(value)

    @TypeConverter
    fun fromSyncOperation(value: SyncOperation): String = value.name

    @TypeConverter
    fun toSyncOperation(value: String): SyncOperation = SyncOperation.valueOf(value)
}
