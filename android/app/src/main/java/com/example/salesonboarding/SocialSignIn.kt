package com.example.salesonboarding

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

/**
 * Sign-in with a Google account, through Firebase Authentication, which is free on
 * Firebase's no-cost plan. An account can only be used if a company invited its email
 * (agents) or the Firebase owner gave it admin access (admins): the same checks and
 * database rules as for email and password decide that.
 */
enum class SocialProvider(val label: String) {
    GOOGLE("Google")
}

/** The person closed the Google window: no message is shown. */
class SignInCancelledException : Exception()

object SocialSignInSetup {
    /**
     * Google's web client ID, which the Firebase build tool writes from google-services.json
     * once Google sign-in is turned on in the Firebase console. Looked up by name, so the app
     * still builds (with the button hidden) before that is done.
     */
    fun googleClientId(context: Context): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id).takeIf { it.isNotBlank() }
    }
}

/** What the sign-in screen needs: the providers set up in this build, and how to start one. */
class SocialSignIn(
    val providers: List<SocialProvider>,
    val start: (provider: SocialProvider, onResult: (Result<SignedInUser>) -> Unit) -> Unit
)

@Composable
fun rememberSocialSignIn(membershipStore: AdminDataStore): SocialSignIn {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleClientId = remember { SocialSignInSetup.googleClientId(context) }

    return remember(googleClientId) {
        SocialSignIn(
            providers = listOfNotNull(SocialProvider.GOOGLE.takeIf { googleClientId != null }),
            start = { provider, onResult ->
                scope.launch {
                    val clientId = googleClientId
                    if (clientId == null) {
                        onResult(Result.failure(IllegalStateException("Google sign-in is not set up in this app.")))
                        return@launch
                    }
                    try {
                        val credential = googleCredential(context, clientId)
                        signInWithProvider(membershipStore, credential, provider.label, onResult)
                    } catch (_: GetCredentialCancellationException) {
                        onResult(Result.failure(SignInCancelledException()))
                    } catch (_: NoCredentialException) {
                        onResult(Result.failure(IllegalStateException("No Google account is on this phone. Add one in Settings, or sign in with your password.")))
                    } catch (error: GetCredentialException) {
                        onResult(Result.failure(IllegalStateException("Google sign-in did not finish. Check the connection and try again.", error)))
                    }
                }
            }
        )
    }
}

/** Shows Google's own account picker and returns the chosen account as a Firebase credential. */
private suspend fun googleCredential(context: Context, clientId: String): AuthCredential {
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
        .build()
    val credential = CredentialManager.create(context).getCredential(context, request).credential
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        return GoogleAuthProvider.getCredential(GoogleIdTokenCredential.createFrom(credential.data).idToken, null)
    }
    throw IllegalStateException("Google did not return an account. Try again.")
}
