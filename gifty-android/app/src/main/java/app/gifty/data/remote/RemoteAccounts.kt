package app.gifty.data.remote

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

/** Captures the signed-in account for remote work; see [RemoteAccount]. */
class RemoteAccounts @Inject constructor(
    private val auth: Auth,
) {

    /**
     * The current session's token, bound to [ownerUserId]. It is not refreshed here: a refresh
     * happens on the global session, which by then may belong to another account.
     *
     * @throws RemoteException see [RemoteAccount.capture]
     */
    fun capture(ownerUserId: UUID): RemoteAccount =
        RemoteAccount.capture(ownerUserId, auth.currentSessionOrNull()?.accessToken)

    /**
     * Emits when the session gets an access token it did not have just before (including the
     * current one on collection), e.g. after a refresh, so work refused for an expired token can be
     * tried again. Losing the session emits nothing, and the token itself never leaves this class.
     */
    val accessTokenChanges: Flow<Unit>
        get() = auth.sessionStatus
            .map { (it as? SessionStatus.Authenticated)?.session?.accessToken }
            .filterNotNull()
            .distinctUntilChanged()
            .map { }
}
