package app.gifty.di

import app.gifty.data.repository.GiftIdeaRepositoryImpl
import app.gifty.data.repository.GiftRepositoryImpl
import app.gifty.data.repository.OccasionRepositoryImpl
import app.gifty.data.repository.PersonRepositoryImpl
import app.gifty.data.user.CurrentUserIdProvider
import app.gifty.data.user.LocalUserIdProvider
import app.gifty.domain.repository.GiftIdeaRepository
import app.gifty.domain.repository.GiftRepository
import app.gifty.domain.repository.OccasionRepository
import app.gifty.domain.repository.PersonRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

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
    abstract fun bindCurrentUserIdProvider(impl: LocalUserIdProvider): CurrentUserIdProvider

    companion object {

        @Provides
        fun provideClock(): Clock = Clock.systemUTC()
    }
}
