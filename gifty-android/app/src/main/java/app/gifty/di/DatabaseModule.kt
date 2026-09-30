package app.gifty.di

import android.content.Context
import androidx.room.Room
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.local.dao.PersonDao
import app.gifty.data.local.dao.SyncOperationDao
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
    fun provideDatabase(@ApplicationContext context: Context): GiftyDatabase =
        Room.databaseBuilder(context, GiftyDatabase::class.java, GiftyDatabase.NAME).build()

    @Provides
    fun providePersonDao(database: GiftyDatabase): PersonDao = database.personDao()

    @Provides
    fun provideSyncOperationDao(database: GiftyDatabase): SyncOperationDao = database.syncOperationDao()
}
