package app.folga.ui.common

import androidx.compose.runtime.Composable

/**
 * Launcher pra abrir o seletor de imagens nativo da plataforma. O
 * composable `rememberImagePicker` devolve uma função que, quando
 * chamada, abre o picker — se o usuário escolher uma imagem, o callback
 * `onPicked` recebe os bytes brutos (já decodificados do URI). Se ele
 * cancelar, o callback não é chamado.
 *
 * Implementação platform-specific:
 * - androidMain: ActivityResultContracts.GetContent com MIME image,
 *   + ContentResolver.openInputStream pra ler os bytes.
 * - iosMain: stub que não abre picker (não vamos priorizar iOS nesse PR).
 */
@Composable
expect fun rememberImagePicker(onPicked: (ByteArray) -> Unit): () -> Unit
