package app.gifty.di

import app.gifty.data.repository.GiftIdeaRepositoryImpl
import app.gifty.data.repository.GiftRepositoryImpl
import app.gifty.data.repository.OccasionRepositoryImpl
import app.gifty.data.repository.PersonRepositoryImpl
import app.gifty.data.user.ActiveOwner
import app.gifty.data.user.OwnerSession
import app.gifty.domain.repository.AccountSession
import app.gifty.domain.repository.GiftIdeaRepository
import app.gifty.domain.repository.GiftRepository
import app.gifty.domain.repository.OccasionRepository
import app.gifty.domain.repository.PersonRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
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
    abstract fun bindActiveOwner(impl: OwnerSession): ActiveOwner

    @Binds
    abstract fun bindAccountSession(impl: OwnerSession): AccountSession

    companion object {

        @Provides
        fun provideClock(): Clock = Clock.systemUTC()

        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
