package app.giftiary

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.giftiary.data.auth.AuthCallbackHandler
import app.giftiary.data.sync.FullSyncRequester
import app.giftiary.presentation.navigation.GiftiaryApp
import app.giftiary.presentation.theme.GiftiaryTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authCallbackHandler: AuthCallbackHandler

    @Inject
    lateinit var fullSyncRequester: FullSyncRequester

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) authCallbackHandler.handle(intent)
        setContent {
            GiftiaryTheme {
                GiftiaryApp()
            }
        }
    }

    // Coming back is the chance to push what earlier attempts left, e.g. while offline, and to
    // fetch what other devices changed meanwhile.
    override fun onStart() {
        super.onStart()
        fullSyncRequester.requestFullSync()
    }

    // singleTask: a confirmation link opened while Giftiary runs arrives here, not in a second copy.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        authCallbackHandler.handle(intent)
    }
}
