package app.folga.ui.common

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Implementação Android: procura pelo crash mais recente em
 * `getExternalFilesDir(null)/crashes/` (com fallback pra `filesDir`,
 * mesmo path que o `FolgaApplication.installCrashLogger` escreve) e
 * mostra um link "Compartilhar último crash" se existir.
 *
 * Usa `LaunchedEffect(Unit)` em vez de `remember` simples pra
 * re-checar a pasta a cada recomposição da tela inteira (ex.: usuário
 * volta pra Login depois de um crash) — `listFiles()` é I/O e não
 * deve ficar no path quente da composição.
 */
@Composable
actual fun CrashShareLink() {
    val context = LocalContext.current
    var latest by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(Unit) {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, "crashes")
        latest = dir.listFiles()?.maxByOrNull { it.lastModified() }
    }
    val crash = latest ?: return
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Compartilhar último crash",
            color = Color(0xFFB00020),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable {
                val text = runCatching { crash.readText() }.getOrElse { "Falha ao ler ${crash.absolutePath}: ${it.message}" }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "EasyShift crash log — ${crash.name}")
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                val chooser = Intent.createChooser(intent, "Compartilhar crash").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            },
        )
    }
}
