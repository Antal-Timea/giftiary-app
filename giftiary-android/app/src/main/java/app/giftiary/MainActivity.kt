package app.giftiary

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.giftiary.data.auth.AuthCallbackHandler
import app.giftiary.data.auth.PasswordRecovery
import app.giftiary.data.auth.ProviderSignIn
import app.giftiary.data.sync.FullSyncRequester
import app.giftiary.presentation.components.ProvideWidthClass
import app.giftiary.presentation.navigation.GiftiaryApp
import app.giftiary.presentation.sharing.PendingWishlistJoin
import app.giftiary.presentation.theme.GiftiaryTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authCallbackHandler: AuthCallbackHandler

    @Inject
    lateinit var fullSyncRequester: FullSyncRequester

    @Inject
    lateinit var pendingWishlistJoin: PendingWishlistJoin

    @Inject
    lateinit var passwordRecovery: PasswordRecovery

    @Inject
    lateinit var providerSignIn: ProviderSignIn

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleLink(intent)
        setContent {
            GiftiaryTheme {
                // Every layout below adapts to the window's width; resizing updates it.
                ProvideWidthClass {
                    GiftiaryApp()
                }
            }
        }
    }

    // Coming back is the chance to push what earlier attempts left, e.g. while offline, and to
    // fetch what other devices changed meanwhile.
    override fun onStart() {
        super.onStart()
        fullSyncRequester.requestFullSync()
    }

    // A provider's callback link arrives in onNewIntent first, so resuming while its sign-in still
    // waits means the Custom Tab was closed without one.
    override fun onResume() {
        super.onResume()
        providerSignIn.onAppResumed()
    }

    // singleTask: a confirmation or provider callback link opened while Giftiary runs arrives here,
    // not in a second copy, and closes the Custom Tab above it.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(intent: Intent) {
        when (val link = incomingLinkOf(intent)) {
            is IncomingLink.Join -> pendingWishlistJoin.offer(link.join)
            is IncomingLink.Recovery -> passwordRecovery.start(link.link)
            IncomingLink.Other -> if (!authCallbackHandler.handle(intent)) return
        }
        // The secret is held in memory from here on; the activity's own intent no longer carries it.
        intent.data = null
    }
}
