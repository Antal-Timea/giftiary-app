package app.gifty.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.gifty.data.local.converter.EnumConverters
import app.gifty.data.local.converter.TimeConverters
import app.gifty.data.local.converter.UuidConverter
import app.gifty.data.local.dao.GiftDao
import app.gifty.data.local.dao.GiftIdeaDao
import app.gifty.data.local.dao.OccasionDao
import app.gifty.data.local.dao.PersonDao
import app.gifty.data.local.dao.SyncOperationDao
import app.gifty.data.local.entity.GiftEntity
import app.gifty.data.local.entity.GiftIdeaEntity
import app.gifty.data.local.entity.OccasionEntity
import app.gifty.data.local.entity.PersonEntity
import app.gifty.data.local.entity.SyncOperationEntity
import app.gifty.data.local.entity.WishEntity
import app.gifty.data.local.entity.WishlistEntity

@Database(
    entities = [
        PersonEntity::class,
        OccasionEntity::class,
        GiftIdeaEntity::class,
        GiftEntity::class,
        WishlistEntity::class,
        WishEntity::class,
        SyncOperationEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(UuidConverter::class, TimeConverters::class, EnumConverters::class)
abstract class GiftyDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao

    abstract fun occasionDao(): OccasionDao

    abstract fun giftDao(): GiftDao

    abstract fun giftIdeaDao(): GiftIdeaDao

    abstract fun syncOperationDao(): SyncOperationDao

    companion object {
        const val NAME = "gifty.db"
    }
}
