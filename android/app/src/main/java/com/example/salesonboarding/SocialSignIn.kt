package com.example.salesonboarding

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

/**
 * Sign-in with a Google or Facebook account, through Firebase Authentication. Both are free
 * on Firebase's no-cost plan. An account can only be used if its email was invited by the
 * company's administrator (agents) or given admin access by the Firebase owner (admins):
 * the same database rules as for email and password decide that.
 */
enum class SocialProvider(val label: String) {
    GOOGLE("Google"),
    FACEBOOK("Facebook")
}

/** The person closed the Google or Facebook window: no message is shown. */
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

    /** Set by facebook.appId in android/local.properties. */
    fun facebookEnabled(context: Context): Boolean = context.getString(R.string.facebook_app_id).isNotBlank()
}

/** What the sign-in screen needs: the providers set up in this build, and how to start one. */
class SocialSignIn(
    val providers: List<SocialProvider>,
    val start: (provider: SocialProvider, companyId: String, role: UserRole, onResult: (Result<SignedInUser>) -> Unit) -> Unit
)

@Composable
fun rememberSocialSignIn(membershipStore: AdminDataStore): SocialSignIn {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleClientId = remember { SocialSignInSetup.googleClientId(context) }
    val facebookOn = remember { SocialSignInSetup.facebookEnabled(context) }
    // Facebook answers through a callback, so the waiting sign-in is kept here.
    val waitingForFacebook = remember { arrayOfNulls<(AuthCredential?, Throwable?) -> Unit>(1) }
    val startFacebook = if (facebookOn) {
        rememberFacebookLogin { credential, error ->
            waitingForFacebook[0]?.invoke(credential, error)
            waitingForFacebook[0] = null
        }
    } else null

    return remember(googleClientId, startFacebook) {
        SocialSignIn(
            providers = listOfNotNull(
                SocialProvider.GOOGLE.takeIf { googleClientId != null },
                SocialProvider.FACEBOOK.takeIf { startFacebook != null }
            ),
            start = { provider, companyId, role, onResult ->
                val finish: (AuthCredential?, Throwable?) -> Unit = { credential, error ->
                    when {
                        credential != null ->
                            signInWithProvider(membershipStore, credential, provider.label, companyId, role, onResult)
                        error != null -> onResult(Result.failure(error))
                        else -> onResult(Result.failure(SignInCancelledException()))
                    }
                }
                when (provider) {
                    SocialProvider.GOOGLE -> scope.launch {
                        val clientId = googleClientId
                        if (clientId == null) {
                            finish(null, IllegalStateException("Google sign-in is not set up in this app."))
                            return@launch
                        }
                        try {
                            finish(googleCredential(context, clientId), null)
                        } catch (_: GetCredentialCancellationException) {
                            finish(null, null)
                        } catch (_: NoCredentialException) {
                            finish(null, IllegalStateException("No Google account is on this phone. Add one in Settings, or sign in with your password."))
                        } catch (error: GetCredentialException) {
                            finish(null, IllegalStateException("Google sign-in did not finish. Check the connection and try again.", error))
                        }
                    }
                    SocialProvider.FACEBOOK -> {
                        waitingForFacebook[0] = finish
                        startFacebook?.invoke()
                            ?: finish(null, IllegalStateException("Facebook sign-in is not set up in this app."))
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

/** Facebook's login window, asking only for the email address and public profile. */
@Composable
private fun rememberFacebookLogin(onDone: (AuthCredential?, Throwable?) -> Unit): () -> Unit {
    val callbackManager = remember { CallbackManager.Factory.create() }
    val launcher = rememberLauncherForActivityResult(
        LoginManager.getInstance().createLogInActivityResultContract(callbackManager, null)
    ) { }
    DisposableEffect(callbackManager) {
        val loginManager = LoginManager.getInstance()
        loginManager.registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(result: LoginResult) {
                val credential = FacebookAuthProvider.getCredential(result.accessToken.token)
                // Firebase keeps the session; the app does not keep a Facebook one as well.
                loginManager.logOut()
                onDone(credential, null)
            }

            override fun onCancel() = onDone(null, null)

            override fun onError(error: FacebookException) =
                onDone(null, IllegalStateException("Facebook sign-in did not finish. Check the connection and try again.", error))
        })
        onDispose { loginManager.unregisterCallback(callbackManager) }
    }
    return remember(launcher) { { launcher.launch(listOf("email", "public_profile")) } }
}
