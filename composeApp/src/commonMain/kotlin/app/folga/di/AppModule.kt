package app.folga.di

import app.folga.data.FirebaseAuthRepository
import app.folga.data.SqlDelightFolgaRepository
import app.folga.data.SqlDelightSwapRepository
import app.folga.data.SqlDelightUserRepository
import app.folga.db.DatabaseDriverFactory
import app.folga.db.FolgaDatabase
import app.folga.domain.AuthRepository
import app.folga.domain.FolgaRepository
import app.folga.domain.SwapRepository
import app.folga.domain.UserRepository
import app.folga.ui.folgas.FolgasViewModel
import app.folga.ui.login.LoginViewModel
import app.folga.ui.register.RegisterViewModel
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
    single { FolgaDatabase(get<DatabaseDriverFactory>().createDriver()) }

    single<UserRepository> { SqlDelightUserRepository(get()) }
    single<FolgaRepository> { SqlDelightFolgaRepository(get()) }
    single<SwapRepository> { SqlDelightSwapRepository(get()) }
    single<AuthRepository> { FirebaseAuthRepository(get()) }

    factory { LoginViewModel(get()) }
    factory { RegisterViewModel(get()) }
    factory { FolgasViewModel(get(), get(), get()) }
    factory { SwapsViewModel(get(), get(), get(), get()) }
}
