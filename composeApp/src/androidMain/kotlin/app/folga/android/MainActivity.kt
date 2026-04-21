package app.folga.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.folga.App
import app.folga.auth.ActivityContextHolder
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    // Credential Manager needs an Activity-scoped Context to show its bottom
    // sheet. We publish `this` to a Koin-held holder in onCreate and clear it
    // in onDestroy so GoogleSignInProvider can grab a live Activity when the
    // user taps "Entrar com Google".
    private val activityContextHolder: ActivityContextHolder by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityContextHolder.set(this)
        setContent {
            App()
        }
    }

    override fun onDestroy() {
        activityContextHolder.clear(this)
        super.onDestroy()
    }
}
