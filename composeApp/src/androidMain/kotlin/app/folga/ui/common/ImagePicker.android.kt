package app.folga.ui.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Actual do Android: GetContent com MIME image devolve um Uri do
 * MediaStore / DocumentProvider, que é lido via ContentResolver pra
 * bytes brutos. A leitura acontece num escopo de coroutine no
 * Dispatchers.IO pra não bloquear a main thread — imagens da câmera
 * podem passar de 5MB.
 *
 * Se o usuário cancelar o picker, o contract devolve null e o
 * callback não é chamado. Se a leitura do stream falhar (URI inválido,
 * permissão negada), silenciosamente ignoramos — mensagem de erro cabe
 * no ViewModel chamador.
 */
@Composable
actual fun rememberImagePicker(onPicked: (ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes != null) onPicked(bytes)
        }
    }
    return remember(launcher) {
        { launcher.launch("image/*") }
    }
}
