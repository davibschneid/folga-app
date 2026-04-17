package app.folga

import androidx.compose.ui.window.ComposeUIViewController
import app.folga.di.initKoin

fun MainViewController() = ComposeUIViewController(
    configure = {
        initKoin()
    }
) { App() }
