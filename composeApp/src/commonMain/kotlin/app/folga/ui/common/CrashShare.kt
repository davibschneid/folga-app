package app.folga.ui.common

import androidx.compose.runtime.Composable

/**
 * Mostra um link "Compartilhar último crash" se houver um arquivo de
 * crash gravado pelo handler do `FolgaApplication`. Clicar abre o share
 * sheet com o conteúdo do crash mais recente — pra que o usuário possa
 * mandar pro suporte sem precisar de gerenciador de arquivos / adb.
 *
 * - androidMain: lê de `getExternalFilesDir(null)/crashes/` (ou
 *   `filesDir/crashes/` como fallback) e usa `Intent.ACTION_SEND`.
 * - iosMain: no-op (handler de crash só existe no Android).
 */
@Composable
expect fun CrashShareLink()
