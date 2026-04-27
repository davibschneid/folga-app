package app.folga.android

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.folga.App
import app.folga.auth.ActivityContextHolder
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    // Credential Manager needs an Activity-scoped Context to show its bottom
    // sheet. We publish `this` to a Koin-held holder in onCreate and clear it
    // in onDestroy so GoogleSignInProvider can grab a live Activity when the
    // user taps "Entrar com Google".
    private val activityContextHolder: ActivityContextHolder by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashLogger()
        activityContextHolder.set(this)
        setContent {
            App()
        }
    }

    override fun onDestroy() {
        activityContextHolder.clear(this)
        super.onDestroy()
    }

    /**
     * Captura crashes não-tratados pra um arquivo legível pelo usuário sem
     * precisar de adb. Sem isso, quando o app fecha "do nada" (caso do bug
     * recorrente do botão Sair), não há rastro acessível ao tester. Escreve
     * em `getExternalFilesDir(null)/crashes/` — diretório acessível pelo
     * gerenciador de arquivos do Android sem permissão de runtime.
     *
     * Encadeia o handler default no final pra que o sistema ainda mostre o
     * "App parou" e o processo seja encerrado normalmente — sem isso, um
     * crash silencioso mais difícil de diagnosticar.
     */
    private fun installCrashLogger() {
        val dir = File(getExternalFilesDir(null), "crashes").apply { mkdirs() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val file = File(dir, "crash-$ts.txt")
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                file.writeText(
                    buildString {
                        appendLine("Thread: ${thread.name}")
                        appendLine("When: $ts")
                        appendLine("Path: ${file.absolutePath}")
                        appendLine()
                        append(sw.toString())
                    },
                )
                Log.e(TAG, "Crash gravado em ${file.absolutePath}", throwable)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private companion object {
        const val TAG = "FolgaCrash"
    }
}
