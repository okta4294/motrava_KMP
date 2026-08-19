package com.myapp.motrava.presentation.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

@Composable
actual fun getPlatformContext(): Any? = LocalContext.current

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

actual suspend fun getGoogleIdToken(context: Any?): String? {
    Log.d("GoogleSignIn", "getGoogleIdToken called with context: $context")
    val androidContext = context as? Context ?: run {
        Log.e("GoogleSignIn", "Context is not an Android Context!")
        return null
    }
    val activity = androidContext.findActivity()
        ?: run {
            Log.e("GoogleSignIn", "Could not find Activity from context!")
            throw IllegalStateException("Google Sign-In requires an Activity context")
        }

    val credentialManager = CredentialManager.create(activity)
    
    val clientId = "318583772805-t71mahm5fcs34416ccji0r4rd2ce2eca.apps.googleusercontent.com"
    
    val signInOption = GetSignInWithGoogleOption.Builder(clientId).build()
    
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    try {
        val result = credentialManager.getCredential(activity, request)
        val credential = result.credential

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        return null
    } catch (e: GetCredentialException) {
        Log.e("GoogleSignIn", "GetCredentialException: type=${e.type}, message=${e.message}", e)
        
        // Fallback: try with GetGoogleIdOption
        return try {
            getGoogleIdTokenFallback(activity, credentialManager, clientId)
        } catch (fallbackError: Exception) {
            null
        }
    } catch (e: Exception) {
        Log.e("GoogleSignIn", "Error: ${e.message}")
        return null
    }
}

private suspend fun getGoogleIdTokenFallback(
    activity: Activity,
    credentialManager: CredentialManager,
    clientId: String
): String? {
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(false)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    val result = credentialManager.getCredential(activity, request)
    val credential = result.credential

    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
    return null
}
