package app.giftiary.di

import app.giftiary.BuildConfig
import app.giftiary.data.remote.AccountBoundClients
import app.giftiary.data.remote.AccountDeletionRemote
import app.giftiary.data.remote.RemoteReader
import app.giftiary.data.remote.RemoteWriter
import app.giftiary.data.remote.SupabaseAccountDeletionRemote
import app.giftiary.data.remote.SupabaseProfileRepository
import app.giftiary.data.remote.SupabaseRemoteReader
import app.giftiary.data.remote.SupabaseRemoteWriter
import app.giftiary.data.remote.SupabaseWishlistSharingRepository
import app.giftiary.domain.repository.ProfileRepository
import app.giftiary.domain.repository.WishlistSharingRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteModule {

    @Binds
    abstract fun bindWishlistSharingRepository(impl: SupabaseWishlistSharingRepository): WishlistSharingRepository

    @Binds
    abstract fun bindProfileRepository(impl: SupabaseProfileRepository): ProfileRepository

    @Binds
    abstract fun bindAccountDeletionRemote(impl: SupabaseAccountDeletionRemote): AccountDeletionRemote

    companion object {

        @Provides
        @Singleton
        fun provideAccountBoundClients(): AccountBoundClients =
            AccountBoundClients(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY, OkHttp.create())

        @Provides
        fun provideRemoteReader(clients: AccountBoundClients): RemoteReader = SupabaseRemoteReader(clients)

        @Provides
        fun provideRemoteWriter(clients: AccountBoundClients): RemoteWriter = SupabaseRemoteWriter(clients)
    }
}
