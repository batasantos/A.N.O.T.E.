package br.unesp.anote

import android.app.Activity
import android.content.Context
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/**
 * "Continuar com o Google" no cadastro. Usado so para preencher nome e e-mail:
 * o app NAO guarda o token do Google nem usa a conta Google para entrar. O login
 * continua sendo e-mail + senha do app (ver [Accounts]).
 */
object GoogleSignIn {

    data class Profile(val name: String, val email: String)

    sealed class Result {
        data class Ok(val profile: Profile) : Result()
        object Cancelled : Result()
        object Failed : Result()
    }

    private fun clientId(context: Context) = context.getString(R.string.google_web_client_id).trim()

    /** O botao so aparece se o Client ID (tipo Web) foi colado em res/values/google.xml. */
    fun isConfigured(context: Context): Boolean = clientId(context).endsWith(".apps.googleusercontent.com")

    fun request(activity: Activity, done: (Result) -> Unit) {
        val option = GetSignInWithGoogleOption.Builder(clientId(activity)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        CredentialManager.create(activity).getCredentialAsync(
            activity,
            request,
            CancellationSignal(),
            ContextCompat.getMainExecutor(activity),
            object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                override fun onResult(result: GetCredentialResponse) {
                    done(parse(result))
                }

                override fun onError(e: GetCredentialException) {
                    done(if (e is GetCredentialCancellationException) Result.Cancelled else Result.Failed)
                }
            }
        )
    }

    private fun parse(response: GetCredentialResponse): Result {
        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) return Result.Failed
        return try {
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            Result.Ok(Profile(name = google.displayName ?: google.givenName ?: "", email = google.id))
        } catch (e: GoogleIdTokenParsingException) {
            Result.Failed
        }
    }
}
