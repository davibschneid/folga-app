package app.folga.ui.common

import androidx.compose.runtime.Composable

/**
 * iOS no-op: o crash handler vive no `FolgaApplication` (Android-only).
 * Se um dia rolar um equivalente em iOS, plugamos aqui.
 */
@Composable
actual fun CrashShareLink() {
}
