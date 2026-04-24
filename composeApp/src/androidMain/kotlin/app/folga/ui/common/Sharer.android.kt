package app.folga.ui.common

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Actual do Android: monta um `Intent.ACTION_SEND` com text/plain e
 * embrulha num `Intent.createChooser` pra que o sistema sempre mostre
 * o seletor de apps (WhatsApp, Gmail, Drive, etc.) — sem cair direto
 * no app default do usuário.
 *
 * `FLAG_ACTIVITY_NEW_TASK` é necessário porque `LocalContext.current`
 * é o ApplicationContext em alguns flows (preview/ProvideAndroidContext)
 * e startActivity de Application sem essa flag lança.
 */
@Composable
actual fun rememberSharer(): (subject: String, body: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { subject, body ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            val chooser = Intent.createChooser(intent, subject).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }
}
