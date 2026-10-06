package app.gifty.di

import app.gifty.BuildConfig
import app.gifty.data.auth.AUTH_CALLBACK_HOST
import app.gifty.data.auth.AUTH_CALLBACK_SCHEME
import app.gifty.data.auth.AUTH_CALLBACK_URL
import app.gifty.data.auth.SupabaseAuthRepository
import app.gifty.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    abstract fun bindAuthRepository(impl: SupabaseAuthRepository): AuthRepository

    companion object {

        @Provides
        @Singleton
        fun provideSupabaseClient(): SupabaseClient {
            check(BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
                "Supabase is not configured: set supabase.url and supabase.publishableKey in local.properties"
            }
            return createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY) {
                // The SDK logs whole sessions, tokens included, at debug level.
                defaultLogLevel = LogLevel.WARNING
                install(Auth) {
                    // PKCE: the confirmation link carries a one-time code, not the tokens themselves.
                    flowType = FlowType.PKCE
                    scheme = AUTH_CALLBACK_SCHEME
                    host = AUTH_CALLBACK_HOST
                    defaultRedirectUrl = AUTH_CALLBACK_URL
                }
            }
        }

        @Provides
        fun provideAuth(client: SupabaseClient): Auth = client.auth
    }
}
