package app.folga.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import app.folga.android.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/**
 * Android implementation of [GoogleSignInProvider] backed by Jetpack
 * Credential Manager. The Web Client ID is read from the
 * `default_web_client_id` string resource that the Google Services Gradle
 * plugin generates from `google-services.json` (`oauth_client` entry with
 * `client_type: 3`). Because that resource only exists once the plugin has
 * run at least one `processDebugGoogleServices` task, it's safe to reference
 * here.
 *
 * NOTE: [contextHolder] must expose an Activity-scoped `Context` so the
 * Credential Manager can show its bottom sheet on Android 14+. Koin wires it
 * via [ActivityContextHolder] which `MainActivity` populates in `onCreate`.
 *
 * We deliberately request with `setFilterByAuthorizedAccounts(false)` so the
 * bottom sheet shows *every* Google account on the device, even brand new
 * ones that have never signed into this app. If we used `true` first-time
 * users would see a misleading "no accounts available" dialog.
 */
class CredentialManagerGoogleSignInProvider(
    private val contextHolder: ActivityContextHolder,
) : GoogleSignInProvider {

    override suspend fun signIn(): GoogleSignInResult {
        val context: Context = contextHolder.current()
            ?: return GoogleSignInResult.Failure(
                "Activity não disponível para iniciar o login com Google"
            )

        val webClientId = runCatching {
            context.getString(R.string.default_web_client_id)
        }.getOrNull()

        if (webClientId.isNullOrBlank()) {
            // Thrown when google-services.json has no Web OAuth client (i.e.
            // Google provider not configured in Firebase Console yet).
            return GoogleSignInResult.Failure(
                "Login com Google não configurado (falta client_type=3 no google-services.json)."
            )
        }

        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val credentialManager = CredentialManager.create(context)

        val response = try {
            credentialManager.getCredential(context, request)
        } catch (_: GetCredentialCancellationException) {
            return GoogleSignInResult.Cancelled
        } catch (_: NoCredentialException) {
            return GoogleSignInResult.Failure(
                "Nenhuma conta Google disponível no dispositivo."
            )
        } catch (e: GetCredentialException) {
            return GoogleSignInResult.Failure(
                e.localizedMessage ?: "Falha ao obter credencial do Google"
            )
        }

        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleSignInResult.Failure("Credencial inesperada retornada pelo Credential Manager")
        }

        val googleId = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (e: GoogleIdTokenParsingException) {
            return GoogleSignInResult.Failure(
                e.localizedMessage ?: "Token do Google inválido"
            )
        }

        return GoogleSignInResult.Success(
            idToken = googleId.idToken,
            email = googleId.id, // `id` is the email for Sign in with Google
            displayName = googleId.displayName ?: googleId.id,
        )
    }
}
