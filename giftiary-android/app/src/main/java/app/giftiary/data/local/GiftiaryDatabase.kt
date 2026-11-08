package app.giftiary.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.giftiary.data.local.converter.EnumConverters
import app.giftiary.data.local.converter.TimeConverters
import app.giftiary.data.local.converter.UuidConverter
import app.giftiary.data.local.dao.AccountDataDao
import app.giftiary.data.local.dao.GiftDao
import app.giftiary.data.local.dao.GiftIdeaDao
import app.giftiary.data.local.dao.OccasionDao
import app.giftiary.data.local.dao.PersonDao
import app.giftiary.data.local.dao.PhotoTransferDao
import app.giftiary.data.local.dao.SyncConflictDao
import app.giftiary.data.local.dao.SyncOperationDao
import app.giftiary.data.local.dao.WishDao
import app.giftiary.data.local.dao.WishlistDao
import app.giftiary.data.local.entity.GiftEntity
import app.giftiary.data.local.entity.GiftIdeaEntity
import app.giftiary.data.local.entity.OccasionEntity
import app.giftiary.data.local.entity.PersonEntity
import app.giftiary.data.local.entity.PhotoTransferEntity
import app.giftiary.data.local.entity.SyncConflictEntity
import app.giftiary.data.local.entity.SyncOperationEntity
import app.giftiary.data.local.entity.WishEntity
import app.giftiary.data.local.entity.WishlistEntity

@Database(
    entities = [
        PersonEntity::class,
        OccasionEntity::class,
        GiftIdeaEntity::class,
        GiftEntity::class,
        WishlistEntity::class,
        WishEntity::class,
        SyncOperationEntity::class,
        SyncConflictEntity::class,
        PhotoTransferEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(UuidConverter::class, TimeConverters::class, EnumConverters::class)
abstract class GiftiaryDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao

    abstract fun occasionDao(): OccasionDao

    abstract fun giftDao(): GiftDao

    abstract fun giftIdeaDao(): GiftIdeaDao

    abstract fun syncOperationDao(): SyncOperationDao

    abstract fun syncConflictDao(): SyncConflictDao

    abstract fun wishlistDao(): WishlistDao

    abstract fun wishDao(): WishDao

    abstract fun accountDataDao(): AccountDataDao

    abstract fun photoTransferDao(): PhotoTransferDao

    companion object {
        const val NAME = "giftiary.db"
    }
}
