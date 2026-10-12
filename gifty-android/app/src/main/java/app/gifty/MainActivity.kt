package app.gifty

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.gifty.data.auth.AuthCallbackHandler
import app.gifty.data.sync.SyncRequester
import app.gifty.presentation.navigation.GiftyApp
import app.gifty.presentation.theme.GiftyTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authCallbackHandler: AuthCallbackHandler

    @Inject
    lateinit var syncRequester: SyncRequester

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) authCallbackHandler.handle(intent)
        setContent {
            GiftyTheme {
                GiftyApp()
            }
        }
    }

    // Coming back is the chance to push what earlier attempts left, e.g. while offline.
    override fun onStart() {
        super.onStart()
        syncRequester.requestSync()
    }

    // singleTask: a confirmation link opened while Gifty runs arrives here, not in a second copy.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        authCallbackHandler.handle(intent)
    }
}
