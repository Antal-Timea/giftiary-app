package app.giftiary.di

import app.giftiary.data.reminders.ReminderScheduler
import app.giftiary.data.reminders.ReminderSettingsRepositoryImpl
import app.giftiary.data.reminders.WorkManagerReminderScheduler
import app.giftiary.data.repository.GiftIdeaRepositoryImpl
import app.giftiary.data.repository.GiftRepositoryImpl
import app.giftiary.data.repository.OccasionRepositoryImpl
import app.giftiary.data.repository.PersonRepositoryImpl
import app.giftiary.data.repository.SyncStatusRepositoryImpl
import app.giftiary.data.repository.WishlistRepositoryImpl
import app.giftiary.data.sync.ForegroundSyncCoordinator
import app.giftiary.data.sync.FullSyncRequester
import app.giftiary.data.sync.SyncRequester
import app.giftiary.data.sync.SyncScheduler
import app.giftiary.data.sync.WorkManagerSyncScheduler
import app.giftiary.data.user.ActiveOwner
import app.giftiary.data.user.OwnerSession
import app.giftiary.domain.repository.AccountSession
import app.giftiary.domain.repository.GiftIdeaRepository
import app.giftiary.domain.repository.GiftRepository
import app.giftiary.domain.repository.OccasionRepository
import app.giftiary.domain.repository.PersonRepository
import app.giftiary.domain.repository.ReminderSettingsRepository
import app.giftiary.domain.repository.SyncStatusRepository
import app.giftiary.domain.repository.WishlistRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindGiftRepository(impl: GiftRepositoryImpl): GiftRepository

    @Binds
    abstract fun bindGiftIdeaRepository(impl: GiftIdeaRepositoryImpl): GiftIdeaRepository

    @Binds
    abstract fun bindOccasionRepository(impl: OccasionRepositoryImpl): OccasionRepository

    @Binds
    abstract fun bindPersonRepository(impl: PersonRepositoryImpl): PersonRepository

    @Binds
    abstract fun bindSyncStatusRepository(impl: SyncStatusRepositoryImpl): SyncStatusRepository

    @Binds
    abstract fun bindActiveOwner(impl: OwnerSession): ActiveOwner

    @Binds
    abstract fun bindAccountSession(impl: OwnerSession): AccountSession

    @Binds
    abstract fun bindSyncRequester(impl: ForegroundSyncCoordinator): SyncRequester

    @Binds
    abstract fun bindFullSyncRequester(impl: ForegroundSyncCoordinator): FullSyncRequester

    @Binds
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler

    @Binds
    abstract fun bindWishlistRepository(impl: WishlistRepositoryImpl): WishlistRepository

    @Binds
    abstract fun bindReminderScheduler(impl: WorkManagerReminderScheduler): ReminderScheduler

    @Binds
    abstract fun bindReminderSettingsRepository(impl: ReminderSettingsRepositoryImpl): ReminderSettingsRepository

    companion object {

        @Provides
        fun provideClock(): Clock = Clock.systemUTC()

        /** Not a singleton: each new consumer gets the device's zone as it is then. */
        @Provides
        fun provideZoneId(): ZoneId = ZoneId.systemDefault()

        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
