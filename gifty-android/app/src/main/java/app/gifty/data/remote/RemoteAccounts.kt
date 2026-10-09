package app.gifty.data.remote

import io.github.jan.supabase.auth.Auth
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
}
