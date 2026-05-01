package app.folga.di

import app.folga.data.*
import app.folga.domain.*
import app.folga.ui.admin.AdminViewModel
import app.folga.ui.completarcadastro.CompletarCadastroViewModel
import app.folga.ui.folgas.FolgasViewModel
import app.folga.ui.login.LoginViewModel
import app.folga.ui.profile.ProfileViewModel
import app.folga.ui.register.RegisterViewModel
import app.folga.ui.reports.ReportsViewModel
import app.folga.ui.swap.SwapsViewModel
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

fun initKoin(appDeclaration: KoinAppDeclaration = {}): KoinApplication =
    startKoin {
        appDeclaration()
        modules(appModule, platformModule, networkModule)
    }

expect val platformModule: org.koin.core.module.Module

val networkModule = module {
    single {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    isLenient = true
                })
            }
            install(Logging) {
                level = LogLevel.INFO
            }
        }
    }
}

val appModule = module {
    single<UserRepository> { FirestoreUserRepository() }
    single<FolgaRepository> { FirestoreFolgaRepository() }
    single<SwapRepository> { FirestoreSwapRepository() }
    single<AllowedEmailRepository> { FirestoreAllowedEmailRepository() }
    single<PhotoStorageRepository> { FirebasePhotoStorageRepository() }
    single<MessagingTokenRepository> { FirestoreMessagingTokenRepository() }
    single<HolidayRepository> { KtorHolidayRepository(get()) }

    // AuthRepository depende de MessagingTokenRepository pra limpar o
    // fcmToken no signOut — registrado depois pra Koin resolver a ordem.
    single<AuthRepository> { FirebaseAuthRepository(get(), get(), get()) }

    // LoginViewModel recebe AllowedEmailRepository pra gate do
    // "Esqueci minha senha" — não pode usar UserRepository porque a
    // rule do `users` exige `isSignedIn()` e esse fluxo é deslogado.
    factory { LoginViewModel(get(), get(), get<AllowedEmailRepository>()) }
    factory { RegisterViewModel(get()) }
    factory { CompletarCadastroViewModel(get()) }
    factory { FolgasViewModel(get(), get(), get(), get(), get()) }
    factory { SwapsViewModel(get(), get(), get(), get()) }
    factory { AdminViewModel(get(), get(), get()) }
    factory { ProfileViewModel(get(), get(), get()) }
    factory { ReportsViewModel(get(), get(), get(), get()) }
}
