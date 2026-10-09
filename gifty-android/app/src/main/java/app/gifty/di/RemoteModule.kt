package app.gifty.di

import app.gifty.BuildConfig
import app.gifty.data.remote.AccountBoundClients
import app.gifty.data.remote.RemoteReader
import app.gifty.data.remote.SupabaseRemoteReader
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
}
