package app.gifty.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import java.io.IOException

/**
 * Runs [block] as [account] and reports every failure as a [RemoteException]; cancellation passes
 * through unchanged and is never reported as a failure. Nothing is retried.
 */
internal suspend fun <T> AccountBoundClients.call(account: RemoteAccount, block: suspend (SupabaseClient) -> T): T =
    try {
        withClient(account, block)
    } catch (e: CancellationException) {
        throw e
    } catch (e: RemoteException) {
        throw e
    } catch (e: RestException) {
        throw RemoteException(failureFor(e), e)
    } catch (e: IOException) {
        throw RemoteException(RemoteFailure.NETWORK, e)
    } catch (e: SerializationException) {
        throw RemoteException(RemoteFailure.SERVER, e)
    } catch (e: Exception) {
        // Anything else the SDK throws; never a cancellation, which is rethrown above.
        throw RemoteException(RemoteFailure.SERVER, e)
    }

// Decided by PostgREST's structured SQLSTATE code where there is one, never by message text.
private fun failureFor(e: RestException): RemoteFailure {
    val code = (e as? PostgrestRestException)?.code
    return when {
        e.statusCode == 401 -> RemoteFailure.UNAUTHENTICATED
        code == "23505" -> RemoteFailure.DUPLICATE
        // Row level security and missing privileges.
        code == "42501" -> RemoteFailure.FORBIDDEN
        // Class 23: integrity constraints (check, not null, foreign key); class 22: invalid data.
        code != null && (code.startsWith("23") || code.startsWith("22")) -> RemoteFailure.CONSTRAINT_VIOLATION
        e.statusCode == 403 -> RemoteFailure.FORBIDDEN
        else -> RemoteFailure.SERVER
    }
}
