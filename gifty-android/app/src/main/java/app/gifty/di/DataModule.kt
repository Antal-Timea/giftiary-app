package app.gifty.di

import app.gifty.data.repository.GiftIdeaRepositoryImpl
import app.gifty.data.repository.GiftRepositoryImpl
import app.gifty.data.repository.OccasionRepositoryImpl
import app.gifty.data.repository.PersonRepositoryImpl
import app.gifty.data.repository.SyncStatusRepositoryImpl
import app.gifty.data.sync.ForegroundSyncCoordinator
import app.gifty.data.sync.FullSyncRequester
import app.gifty.data.sync.SyncRequester
import app.gifty.data.sync.SyncScheduler
import app.gifty.data.sync.WorkManagerSyncScheduler
import app.gifty.data.user.ActiveOwner
import app.gifty.data.user.OwnerSession
import app.gifty.domain.repository.AccountSession
import app.gifty.domain.repository.GiftIdeaRepository
import app.gifty.domain.repository.GiftRepository
import app.gifty.domain.repository.OccasionRepository
import app.gifty.domain.repository.PersonRepository
import app.gifty.domain.repository.SyncStatusRepository
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
