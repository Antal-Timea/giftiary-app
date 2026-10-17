package app.giftiary.di

import app.giftiary.BuildConfig
import app.giftiary.data.remote.AccountBoundClients
import app.giftiary.data.remote.RemoteReader
import app.giftiary.data.remote.RemoteWriter
import app.giftiary.data.remote.SupabaseRemoteReader
import app.giftiary.data.remote.SupabaseRemoteWriter
import app.giftiary.data.remote.SupabaseWishlistSharingRepository
import app.giftiary.domain.repository.WishlistSharingRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RemoteModule {

    @Provides
    @Singleton
    fun provideAccountBoundClients(): AccountBoundClients =
        AccountBoundClients(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY, OkHttp.create())

    @Provides
    fun provideRemoteReader(clients: AccountBoundClients): RemoteReader = SupabaseRemoteReader(clients)

    @Provides
    fun provideRemoteWriter(clients: AccountBoundClients): RemoteWriter = SupabaseRemoteWriter(clients)

    @Provides
    fun provideWishlistSharingRepository(impl: SupabaseWishlistSharingRepository): WishlistSharingRepository = impl
}
