package app.folga.di

import app.folga.data.FirebaseAuthRepository
import app.folga.data.FirebasePhotoStorageRepository
import app.folga.data.FirestoreAllowedEmailRepository
import app.folga.data.FirestoreFolgaRepository
import app.folga.data.FirestoreMessagingTokenRepository
import app.folga.data.FirestoreSwapRepository
import app.folga.data.FirestoreUserRepository
import app.folga.domain.AllowedEmailRepository
import app.folga.domain.AuthRepository
import app.folga.domain.FolgaRepository
import app.folga.domain.MessagingTokenRepository
import app.folga.domain.PhotoStorageRepository
import app.folga.domain.SwapRepository
import app.folga.domain.UserRepository
import app.folga.ui.admin.AdminViewModel
import app.folga.ui.completarcadastro.CompletarCadastroViewModel
import app.folga.ui.folgas.FolgasViewModel
import app.folga.ui.login.LoginViewModel
import app.folga.ui.profile.ProfileViewModel
import app.folga.ui.register.RegisterViewModel
import app.folga.ui.reports.ReportsViewModel
import app.folga.ui.swap.SwapsViewModel
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

fun initKoin(appDeclaration: KoinAppDeclaration = {}): KoinApplication =
    startKoin {
        appDeclaration()
        modules(appModule, platformModule)
    }

expect val platformModule: org.koin.core.module.Module

val appModule = module {
    single<UserRepository> { FirestoreUserRepository() }
    single<FolgaRepository> { FirestoreFolgaRepository() }
    single<SwapRepository> { FirestoreSwapRepository() }
    single<AllowedEmailRepository> { FirestoreAllowedEmailRepository() }
    single<AuthRepository> { FirebaseAuthRepository(get(), get()) }
    single<PhotoStorageRepository> { FirebasePhotoStorageRepository() }
    single<MessagingTokenRepository> { FirestoreMessagingTokenRepository() }

    factory { LoginViewModel(get(), get()) }
    factory { RegisterViewModel(get()) }
    factory { CompletarCadastroViewModel(get()) }
    factory { FolgasViewModel(get(), get(), get(), get()) }
    factory { SwapsViewModel(get(), get(), get(), get()) }
    factory { AdminViewModel(get(), get(), get()) }
    factory { ProfileViewModel(get(), get(), get()) }
    factory { ReportsViewModel(get(), get(), get(), get()) }
}
