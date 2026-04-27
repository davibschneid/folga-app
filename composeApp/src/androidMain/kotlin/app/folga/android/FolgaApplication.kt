package app.folga.android

import android.app.Application
import android.util.Log
import app.folga.di.initKoin
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.logger.Level

class FolgaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Crash logger: instalado no Application pra rodar exatamente uma
        // vez por processo. Em MainActivity.onCreate o handler encadeava a
        // cada recriação (rotação, dark mode), gerando N arquivos de
        // crash duplicados — flagged pelo Devin Review no PR #57.
        installCrashLogger()
        initKoin {
            androidLogger(Level.INFO)
            androidContext(this@FolgaApplication)
        }
        // Notificação canal precisa existir antes de qualquer push chegar.
        // FolgaMessagingService cria sob demanda também, mas pré-criar
        // aqui evita race quando o primeiro push chega minutos depois
        // do install (canal ainda não existe -> push não toca).
        FolgaMessagingService.ensureChannel(this)
        // Sincroniza o token FCM com o doc do usuário toda vez que ele
        // estiver logado (login ou app reabrindo já logado). Cobertura
        // do caso onNewToken não disparar pós-login.
        FcmTokenSyncer(get(), get()).start()
    }

    /**
     * Captura crashes não-tratados pra um arquivo legível pelo usuário sem
     * precisar de adb. Sem isso, quando o app fecha "do nada" (caso do bug
     * recorrente do botão Sair), não há rastro acessível ao tester. Escreve
     * em `getExternalFilesDir(null)/crashes/` — diretório acessível pelo
     * gerenciador de arquivos do Android sem permissão de runtime.
     *
     * Encadeia o handler default no final pra que o sistema ainda mostre o
     * "App parou" e o processo seja encerrado normalmente — sem isso, o
     * crash ficaria silencioso e mais difícil de diagnosticar.
     */
    private fun installCrashLogger() {
        // `getExternalFilesDir(null)` pode retornar null em devices sem
        // storage externo montado (SD card removível em certos cenários).
        // Quando isso acontece, `File(null, "crashes")` resolve pra
        // "/crashes" no root — `mkdirs()` falha silencioso e o crash log
        // some. Cai pra `filesDir` (storage interno) como fallback —
        // menos acessível pro usuário, mas pelo menos o arquivo é
        // gravado e pode ser puxado via adb se necessário.
        val base = getExternalFilesDir(null) ?: filesDir
        val dir = File(base, "crashes").apply { mkdirs() }
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
            // Tentativa anterior tinha um swallow específico pra
            // FirebaseFirestoreException PERMISSION_DENIED aqui. Não
            // funcionou: a exceção, quando vinha da coroutine do
            // recomposer (AndroidUiDispatcher + BroadcastFrameClock),
            // era engolida — o app não fechava — mas o recomposer
            // completava excepcionalmente e a UI congelava (nenhum
            // botão respondia). A fonte de verdade agora é o
            // `firestore.disableNetwork()` no `signOut`, que evita
            // o erro chegar no listener. Mantemos a propagação pra
            // cima pra não mascarar nenhum outro crash.
            previous?.uncaughtException(thread, throwable)
        }
    }

    private companion object {
        const val TAG = "FolgaCrash"
    }
}
