package app.folga.android

import android.app.Application
import app.folga.di.initKoin
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.logger.Level

class FolgaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
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
}
