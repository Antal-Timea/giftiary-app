package app.giftiary.di

import android.content.Context
import androidx.room.Room
import app.giftiary.data.local.GiftiaryDatabase
import app.giftiary.data.local.MIGRATION_1_2
import app.giftiary.data.local.MIGRATION_2_3
import app.giftiary.data.local.MIGRATION_3_4
import app.giftiary.data.local.MIGRATION_4_5
import app.giftiary.data.local.dao.GiftDao
import app.giftiary.data.local.dao.GiftIdeaDao
import app.giftiary.data.local.dao.LegacyOwnershipDao
import app.giftiary.data.local.dao.OccasionDao
import app.giftiary.data.local.dao.PersonDao
import app.giftiary.data.local.dao.SyncConflictDao
import app.giftiary.data.local.dao.SyncOperationDao
import app.giftiary.data.local.dao.WishDao
import app.giftiary.data.local.dao.WishlistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GiftiaryDatabase =
        Room.databaseBuilder(context, GiftiaryDatabase::class.java, GiftiaryDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()

    @Provides
    fun provideGiftDao(database: GiftiaryDatabase): GiftDao = database.giftDao()

    @Provides
    fun provideGiftIdeaDao(database: GiftiaryDatabase): GiftIdeaDao = database.giftIdeaDao()

    @Provides
    fun provideOccasionDao(database: GiftiaryDatabase): OccasionDao = database.occasionDao()

    @Provides
    fun providePersonDao(database: GiftiaryDatabase): PersonDao = database.personDao()

    @Provides
    fun provideSyncOperationDao(database: GiftiaryDatabase): SyncOperationDao = database.syncOperationDao()

    @Provides
    fun provideSyncConflictDao(database: GiftiaryDatabase): SyncConflictDao = database.syncConflictDao()

    @Provides
    fun provideLegacyOwnershipDao(database: GiftiaryDatabase): LegacyOwnershipDao = database.legacyOwnershipDao()

    @Provides
    fun provideWishlistDao(database: GiftiaryDatabase): WishlistDao = database.wishlistDao()

    @Provides
    fun provideWishDao(database: GiftiaryDatabase): WishDao = database.wishDao()
}
