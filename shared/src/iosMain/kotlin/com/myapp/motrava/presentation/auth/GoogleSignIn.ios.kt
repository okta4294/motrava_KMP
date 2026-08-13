package com.myapp.motrava.presentation.auth

import androidx.compose.runtime.Composable
import cocoapods.GoogleSignIn.GIDSignIn
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UIKit.UIApplication
import kotlin.coroutines.resume

@Composable
actual fun getPlatformContext(): Any? = null

actual suspend fun getGoogleIdToken(context: Any?): String? = suspendCancellableCoroutine { cont ->
    val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
    if (rootViewController == null) {
        cont.resume(null)
        return@suspendCancellableCoroutine
    }
    
    GIDSignIn.sharedInstance.signInWithPresentingViewController(rootViewController) { signInResult, error ->
        if (error != null) {
            cont.resume(null)
        } else {
            val idToken = signInResult?.user?.idToken?.tokenString
            cont.resume(idToken)
        }
    }
}
